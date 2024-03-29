/*
 * Copyright 2020 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/mamoe/mirai/blob/master/LICENSE
 */

package net.mamoe.mirai.qqandroid.network

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.core.ByteReadPacket
import kotlinx.io.core.buildPacket
import kotlinx.io.core.use
import net.mamoe.mirai.event.*
import net.mamoe.mirai.event.events.BotOfflineEvent
import net.mamoe.mirai.event.events.BotOnlineEvent
import net.mamoe.mirai.message.ContactMessage
import net.mamoe.mirai.network.BotNetworkHandler
import net.mamoe.mirai.network.UnsupportedSMSLoginException
import net.mamoe.mirai.network.WrongPasswordException
import net.mamoe.mirai.qqandroid.QQAndroidBot
import net.mamoe.mirai.qqandroid.contact.*
import net.mamoe.mirai.qqandroid.event.PacketReceivedEvent
import net.mamoe.mirai.qqandroid.network.protocol.data.jce.StTroopNum
import net.mamoe.mirai.qqandroid.network.protocol.data.proto.MsgSvc
import net.mamoe.mirai.qqandroid.network.protocol.packet.*
import net.mamoe.mirai.qqandroid.network.protocol.packet.chat.GroupInfoImpl
import net.mamoe.mirai.qqandroid.network.protocol.packet.chat.receive.MessageSvc
import net.mamoe.mirai.qqandroid.network.protocol.packet.list.FriendList
import net.mamoe.mirai.qqandroid.network.protocol.packet.login.ConfigPushSvc
import net.mamoe.mirai.qqandroid.network.protocol.packet.login.Heartbeat
import net.mamoe.mirai.qqandroid.network.protocol.packet.login.StatSvc
import net.mamoe.mirai.qqandroid.network.protocol.packet.login.WtLogin
import net.mamoe.mirai.qqandroid.utils.PlatformSocket
import net.mamoe.mirai.qqandroid.utils.SocketException
import net.mamoe.mirai.qqandroid.utils.io.readPacketExact
import net.mamoe.mirai.qqandroid.utils.io.useBytes
import net.mamoe.mirai.qqandroid.utils.retryCatching
import net.mamoe.mirai.utils.*
import kotlin.coroutines.CoroutineContext
import kotlin.jvm.Volatile
import kotlin.time.ExperimentalTime

@Suppress("MemberVisibilityCanBePrivate")
@OptIn(MiraiInternalAPI::class)
internal class QQAndroidBotNetworkHandler(bot: QQAndroidBot) : BotNetworkHandler() {
    override val bot: QQAndroidBot by bot.unsafeWeakRef()
    override val supervisor: CompletableJob = SupervisorJob(bot.coroutineContext[Job])
    override val logger: MiraiLogger get() = bot.configuration.networkLoggerSupplier(this)

    override val coroutineContext: CoroutineContext = bot.coroutineContext + CoroutineExceptionHandler { _, throwable ->
        logger.error("Exception in NetworkHandler", throwable)
    }

    private lateinit var channel: PlatformSocket

    private var _packetReceiverJob: Job? = null
    private var heartbeatJob: Job? = null

    private val packetReceiveLock: Mutex = Mutex()

    override fun areYouOk(): Boolean {
        return this.isActive && ::channel.isInitialized && channel.isOpen
                && heartbeatJob?.isActive == true && _packetReceiverJob?.isActive == true
    }

    private suspend fun startPacketReceiverJobOrKill(cancelCause: CancellationException? = null): Job {
        _packetReceiverJob?.cancel(cancelCause)
        _packetReceiverJob?.join()

        return this.launch(CoroutineName("Incoming Packet Receiver")) {
            while (channel.isOpen && isActive) {
                val rawInput = try {
                    channel.read()
                } catch (e: CancellationException) {
                    return@launch
                } catch (e: Throwable) {
                    if (this@QQAndroidBotNetworkHandler.isActive) {
                        bot.launch { BotOfflineEvent.Dropped(bot, e).broadcast() }
                    }
                    return@launch
                }
                packetReceiveLock.withLock {
                    processPacket(rawInput)
                }
            }
        }.also { _packetReceiverJob = it }
    }

    private fun startHeartbeatJobOrKill(cancelCause: CancellationException? = null): Job {
        heartbeatJob?.cancel(cancelCause)

        return this@QQAndroidBotNetworkHandler.launch(CoroutineName("Heartbeat")) {
            while (this.isActive) {
                delay(bot.configuration.heartbeatPeriodMillis)
                val failException = doHeartBeat()
                if (failException != null) {
                    delay(bot.configuration.firstReconnectDelayMillis)
                    bot.launch { BotOfflineEvent.Dropped(bot, failException).broadcast() }
                    return@launch
                }
            }
        }.also { heartbeatJob = it }
    }

    @OptIn(MiraiExperimentalAPI::class)
    override suspend fun closeEverythingAndRelogin(host: String, port: Int, cause: Throwable?) {
        heartbeatJob?.cancel(CancellationException("relogin", cause))
        heartbeatJob?.join()
        if (::channel.isInitialized) {
            // if (channel.isOpen) {
            //     kotlin.runCatching {
            //         registerClientOnline(500)
            //     }.exceptionOrNull() ?: return
            //     logger.info("Cannot do fast relogin. Trying slow relogin")
            // }
            channel.close()
        }
        channel = PlatformSocket()
        // TODO: 2020/2/14 连接多个服务器, #52

        while (isActive) {
            try {
                channel.connect(coroutineContext + CoroutineName("Socket"), host, port)
                break
            } catch (e: SocketException) {
                logger.warning { "No route to host (Mostly due to no Internet connection). Retrying in 3s..." }
                delay(3000)
            }
        }
        logger.info { "Connected to server $host:$port" }
        startPacketReceiverJobOrKill(CancellationException("relogin", cause))

        var response: WtLogin.Login.LoginPacketResponse = WtLogin.Login.SubCommand9(bot.client).sendAndExpect()
        mainloop@ while (true) {
            when (response) {
                is WtLogin.Login.LoginPacketResponse.UnsafeLogin -> {
                    bot.configuration.loginSolver.onSolveUnsafeDeviceLoginVerify(bot, response.url)
                    response = WtLogin.Login.SubCommand9(bot.client).sendAndExpect()
                }

                is WtLogin.Login.LoginPacketResponse.Captcha -> when (response) {
                    is WtLogin.Login.LoginPacketResponse.Captcha.Picture -> {
                        var result = bot.configuration.loginSolver.onSolvePicCaptcha(bot, response.data)
                        if (result == null || result.length != 4) {
                            //refresh captcha
                            result = "ABCD"
                        }
                        response = WtLogin.Login.SubCommand2.SubmitPictureCaptcha(bot.client, response.sign, result)
                            .sendAndExpect()
                        continue@mainloop
                    }
                    is WtLogin.Login.LoginPacketResponse.Captcha.Slider -> {
                        val ticket = bot.configuration.loginSolver.onSolveSliderCaptcha(bot, response.url).orEmpty()
                        response = WtLogin.Login.SubCommand2.SubmitSliderCaptcha(bot.client, ticket).sendAndExpect()
                        continue@mainloop
                    }
                }

                is WtLogin.Login.LoginPacketResponse.Error ->
                    throw WrongPasswordException(response.toString())

                is WtLogin.Login.LoginPacketResponse.DeviceLockLogin -> {
                    response = WtLogin.Login.SubCommand20(
                        bot.client,
                        response.t402
                    ).sendAndExpect()
                    continue@mainloop
                }

                is WtLogin.Login.LoginPacketResponse.Success -> {
                    logger.info { "Login successful" }
                    break@mainloop
                }

                is WtLogin.Login.LoginPacketResponse.SMSVerifyCodeNeeded -> {
                    val message = "SMS required: $response, which isn't yet supported"
                    logger.error(message)
                    throw UnsupportedSMSLoginException(message)
                }
            }
        }

        // println("d2key=${bot.client.wLoginSigInfo.d2Key.toUHexString()}")
        registerClientOnline()
        startHeartbeatJobOrKill()
    }

    private suspend fun registerClientOnline(timeoutMillis: Long = 3000) {
        StatSvc.Register(bot.client).sendAndExpect<StatSvc.Register.Response>(timeoutMillis)
    }

    // caches
    private val _pendingEnabled = atomic(true)
    internal val pendingEnabled get() = _pendingEnabled.value

    @Volatile
    internal var pendingIncomingPackets: LockFreeLinkedList<KnownPacketFactories.IncomingPacket<*>>? =
        LockFreeLinkedList()

    /**
     * Don't use concurrently
     */
    suspend fun reloadFriendList() {
        logger.info { "开始加载好友信息" }
        var currentFriendCount = 0
        var totalFriendCount: Short
        while (true) {
            val data = FriendList.GetFriendGroupList(
                bot.client, currentFriendCount, 150, 0, 0
            ).sendAndExpect<FriendList.GetFriendGroupList.Response>(timeoutMillis = 5000, retry = 2)

            // self info
            data.selfInfo?.run {
                bot.selfInfo = this
//                            bot.remark = remark ?: ""
//                            bot.sex = sex
            }

            totalFriendCount = data.totalFriendCount
            data.friendList.forEach {
                // atomic
                bot.friends.delegate.addLast(
                    QQImpl(bot, bot.coroutineContext, it.friendUin, FriendInfoImpl(it))
                ).also { currentFriendCount++ }
            }
            logger.verbose { "正在加载好友列表 ${currentFriendCount}/${totalFriendCount}" }
            if (currentFriendCount >= totalFriendCount) {
                break
            }
            // delay(200)
        }
        logger.info { "好友列表加载完成, 共 ${currentFriendCount}个" }
    }

    suspend fun StTroopNum.reloadGroup() {
        retryCatching(3) {
            bot.groups.delegate.addLast(
                @Suppress("DuplicatedCode")
                GroupImpl(
                    bot = bot,
                    coroutineContext = bot.coroutineContext,
                    id = groupCode,
                    groupInfo = bot._lowLevelQueryGroupInfo(groupCode).apply {
                        this as GroupInfoImpl

                        if (this.delegate.groupName == null) {
                            this.delegate.groupName = groupName
                        }

                        if (this.delegate.groupMemo == null) {
                            this.delegate.groupMemo = groupMemo
                        }

                        if (this.delegate.groupUin == null) {
                            this.delegate.groupUin = groupUin
                        }

                        this.delegate.groupCode = this@reloadGroup.groupCode
                    },
                    members = bot._lowLevelQueryGroupMemberList(
                        groupUin,
                        groupCode,
                        dwGroupOwnerUin
                    )
                )
            )
        }.getOrThrow()
    }

    suspend fun reloadGroupList() {
        logger.info { "开始加载群组列表与群成员列表" }
        val troopListData = FriendList.GetTroopListSimplify(bot.client)
            .sendAndExpect<FriendList.GetTroopListSimplify.Response>(retry = 3)

        troopListData.groups.chunked(50).forEach { chunk ->
            coroutineScope {
                chunk.forEach {
                    launch {
                        retryCatching(3) { it.reloadGroup() }.getOrThrow()
                    }
                }
            }
        }
        logger.info { "群组列表与群成员加载完成, 共 ${troopListData.groups.size}个" }
    }

    @OptIn(MiraiExperimentalAPI::class, ExperimentalTime::class)
    override suspend fun init(): Unit = coroutineScope {
        check(bot.isActive) { "bot is dead therefore network can't init" }
        check(this@QQAndroidBotNetworkHandler.isActive) { "network is dead therefore can't init" }

        CancellationException("re-init").let { reInitCancellationException ->
            bot.friends.delegate.clear { it.cancel(reInitCancellationException) }
            bot.groups.delegate.clear { it.cancel(reInitCancellationException) }
        }

        if (!pendingEnabled) {
            pendingIncomingPackets = LockFreeLinkedList()
            _pendingEnabled.value = true
        }

        coroutineScope {
            launch { reloadFriendList() }
            launch { reloadGroupList() }
        }

        this@QQAndroidBotNetworkHandler.launch {
            logger.info { "Awaiting ConfigPushSvc.PushReq" }
            val resp =
                syncFromEventOrNull<ConfigPushSvc.PushReq.PushReqResponse, ConfigPushSvc.PushReq.PushReqResponse>(
                    10_000) { it }

            when (resp) {
                null -> logger.info { "Missing ConfigPushSvc.PushReq." }
                is ConfigPushSvc.PushReq.PushReqResponse.Success -> {
                    logger.info { "ConfigPushSvc.PushReq: success" }
                }
                is ConfigPushSvc.PushReq.PushReqResponse.ChangeServer -> {
                    logger.info { "Server requires reconnect." }
                    logger.info { "ChangeServer.unknown = ${resp.unknown}" }
                    logger.info { "Server list: ${resp.serverList.joinToString()}" }

                    resp.serverList.forEach {
                        bot.client.serverList.add(it.host to it.port)
                    }
                    BotOfflineEvent.RequireReconnect(bot).broadcast()
                }
            }
        }

        withTimeoutOrNull(30000) {
            launch { syncFromEvent<MessageSvc.PbGetMsg.GetMsgSuccess, Unit> { Unit } }
            MessageSvc.PbGetMsg(bot.client, MsgSvc.SyncFlag.START, currentTimeSeconds).sendAndExpect<Packet>()
        } ?: error("timeout syncing friend message history")

        bot.firstLoginSucceed = true

        _pendingEnabled.value = false
        pendingIncomingPackets?.forEach {
            @Suppress("UNCHECKED_CAST")
            KnownPacketFactories.handleIncomingPacket(
                it as KnownPacketFactories.IncomingPacket<Packet>,
                bot,
                it.flag2,
                it.consumer
            )
        }
        val list = pendingIncomingPackets
        pendingIncomingPackets = null // release, help gc
        list?.clear() // help gc

        BotOnlineEvent(bot).broadcast()
        Unit // dont remove. can help type inference
    }

    private suspend fun doHeartBeat(): Exception? {
        val lastException: Exception?
        try {
            kotlin.runCatching {
                Heartbeat.Alive(bot.client)
                    .sendAndExpect<Heartbeat.Alive.Response>(
                        timeoutMillis = bot.configuration.heartbeatTimeoutMillis,
                        retry = 2
                    )
                return null
            }
            Heartbeat.Alive(bot.client)
                .sendAndExpect<Heartbeat.Alive.Response>(
                    timeoutMillis = bot.configuration.heartbeatTimeoutMillis,
                    retry = 2
                )
            return null
        } catch (e: Exception) {
            lastException = e
        }
        return lastException
    }

    /**
     * 缓存超时处理的 [Job]. 超时后将清空缓存, 以免阻碍后续包的处理
     */
    @Volatile
    private var cachedPacketTimeoutJob: Job? = null

    /**
     * 缓存的包
     */
    private val cachedPacket: AtomicRef<ByteReadPacket?> = atomic(null)

    /**
     * 缓存的包还差多少长度
     */
    @Volatile
    private var expectingRemainingLength: Long = 0

    /**
     * 解析包内容.
     *
     * @param input 一个完整的包的内容, 去掉开头的 int 包长度
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun parsePacketAsync(input: ByteReadPacket): Job {
        return this.launch(
            start = CoroutineStart.ATOMIC
        ) {
            try {
                input.use { parsePacket(it) }
            } catch (e: Exception) {
                // 傻逼协程吞异常
                logger.error(e)
            }
        }
    }

    /**
     * 解析包内容
     * **注意**: 需要函数调用者 close 这个 [input]
     *
     * @param input 一个完整的包的内容, 去掉开头的 int 包长度
     */
    suspend fun parsePacket(input: ByteReadPacket) {
        generifiedParsePacket<Packet>(input)
    }

    // with generic type, less mistakes
    private suspend fun <P : Packet?> generifiedParsePacket(input: ByteReadPacket) {
        KnownPacketFactories.parseIncomingPacket(
            bot,
            input
        ) { packetFactory: PacketFactory<P>, packet: P, commandName: String, sequenceId: Int ->
            if (packet is MultiPacket<*>) {
                packet.forEach {
                    handlePacket(null, it, commandName, sequenceId)
                }
            }
            handlePacket(packetFactory, packet, commandName, sequenceId)
        }
    }

    /**
     * 处理解析完成的包.
     */
    suspend fun <P : Packet?> handlePacket(
        packetFactory: PacketFactory<P>?,
        packet: P,
        commandName: String,
        sequenceId: Int
    ) {
        // highest priority: pass to listeners (attached by sendAndExpect).
        if (packet != null && (bot.logger.isEnabled || logger.isEnabled)) {
            when (packet) {
                is Packet.NoLog -> {
                    // nothing to do
                }
                is ContactMessage -> packet.logMessageReceived()
                is Event -> bot.logger.verbose { "Event: ${packet.toString().singleLine()}" }
                else -> logger.verbose { "Packet: ${packet.toString().singleLine()}" }
            }
        }

        packetListeners.forEach { listener ->
            if (listener.filter(commandName, sequenceId) && packetListeners.remove(listener)) {
                listener.complete(packet)
            }
        }

        packetFactory?.run {
            when (this) {
                is OutgoingPacketFactory<P> -> bot.handle(packet)
                is IncomingPacketFactory<P> -> bot.handle(packet, sequenceId)?.sendWithoutExpect()
            }
        }

        if (packet != null && PacketReceivedEvent(packet).broadcast().isCancelled) {
            return
        }

        if (packet is Event) {
            if (packet is BroadcastControllable) {
                if (packet.shouldBroadcast) packet.broadcast()
            } else {
                packet.broadcast()
            }

            if (packet is CancellableEvent && packet.isCancelled) return
        }
    }

    /**
     * 处理从服务器接收过来的包. 这些包可能是粘在一起的, 也可能是不完整的. 将会自动处理.
     * 处理后的包会调用 [parsePacketAsync]
     */
    private fun processPacket(rawInput: ByteReadPacket) {
        if (rawInput.remaining == 0L) {
            return
        }

        val cache = cachedPacket.value
        if (cache == null) {
            // 没有缓存
            var length: Int = rawInput.readInt() - 4
            if (rawInput.remaining == length.toLong()) {
                // 捷径: 当包长度正好, 直接传递剩余数据.
                cachedPacketTimeoutJob?.cancel()
                parsePacketAsync(rawInput)
                return
            }
            // 循环所有完整的包
            while (rawInput.remaining >= length) {
                parsePacketAsync(rawInput.readPacketExact(length))

                if (rawInput.remaining == 0L) {
                    cachedPacket.value = null // 表示包长度正好
                    cachedPacketTimeoutJob?.cancel()
                    return
                }
                length = rawInput.readInt() - 4
            }

            if (rawInput.remaining != 0L) {
                // 剩余的包长度不够, 缓存后接收下一个包
                expectingRemainingLength = length - rawInput.remaining
                cachedPacket.value = rawInput
            } else {
                cachedPacket.value = null // 表示包长度正好
                cachedPacketTimeoutJob?.cancel()
                return
            }
        } else {
            // 有缓存
            val expectingLength = expectingRemainingLength
            if (rawInput.remaining >= expectingLength) {
                // 剩余长度够, 连接上去, 处理这个包.
                parsePacketAsync(buildPacket {
                    writePacket(cache)
                    writePacket(rawInput, expectingLength)
                })
                cachedPacket.value = null // 缺少的长度已经给上了.
                cachedPacketTimeoutJob?.cancel()

                if (rawInput.remaining != 0L) {
                    return processPacket(rawInput) // 继续处理剩下内容
                } else {
                    // 处理好了.
                    return
                }
            } else {
                // 剩余不够, 连接上去
                expectingRemainingLength -= rawInput.remaining
                // do not inline `packet`. atomicfu unsupported
                val packet = buildPacket {
                    writePacket(cache)
                    writePacket(rawInput)
                }
                cachedPacket.value = packet
            }
        }

        cachedPacketTimeoutJob?.cancel()
        cachedPacketTimeoutJob = launch {
            delay(1000)
            if (cachedPacketTimeoutJob == this.coroutineContext[Job] && cachedPacket.getAndSet(null) != null) {
                PacketLogger.verbose { "等待另一部分包时超时. 将舍弃已接收的半个包" }
            }
        }
    }


    /**
     * 发送一个包, 但不期待任何返回.
     * 不推荐使用它, 可能产生意外的情况.
     */
    suspend fun OutgoingPacket.sendWithoutExpect() {
        check(bot.isActive) { "bot is dead therefore can't send any packet" }
        check(this@QQAndroidBotNetworkHandler.isActive) { "network is dead therefore can't send any packet" }
        logger.verbose("Send: ${this.commandName}")
        channel.send(delegate)
    }

    class TimeoutException(override val message: String?) : Exception()

    /**
     * 发送一个包, 挂起协程直到接收到指定的返回包或超时
     */
    suspend fun <E : Packet> OutgoingPacket.sendAndExpect(timeoutMillis: Long = 5000, retry: Int = 2): E {
        require(timeoutMillis > 100) { "timeoutMillis must > 100" }
        require(retry >= 0) { "retry must >= 0" }

        check(bot.isActive) { "bot is dead therefore can't send any packet" }
        check(this@QQAndroidBotNetworkHandler.isActive) { "network is dead therefore can't send any packet" }

        suspend fun doSendAndReceive(handler: PacketListener, data: Any, length: Int): E {
            when (data) {
                is ByteArray -> channel.send(data, 0, length)
                is ByteReadPacket -> channel.send(data)
                else -> error("Internal error: unexpected data type: ${data::class.simpleName}")
            }

            logger.verbose { "Send done: $commandName" }

            @Suppress("UNCHECKED_CAST")
            return withTimeoutOrNull(timeoutMillis) {
                handler.await()
                // 不要 `withTimeout`. timeout 的报错会不正常.
            } as E? ?: throw TimeoutException("timeout receiving response of $commandName")
        }

        if (retry == 0) {
            val handler = PacketListener(commandName = commandName, sequenceId = sequenceId)
            packetListeners.addLast(handler)
            try {
                return doSendAndReceive(handler, delegate, 0) // no need
            } finally {
                packetListeners.remove(handler)
            }
        } else this.delegate.useBytes { data, length ->
            return retryCatching(retry + 1) {
                val handler = PacketListener(commandName = commandName, sequenceId = sequenceId)
                packetListeners.addLast(handler)
                try {
                    doSendAndReceive(handler, data, length)
                } finally {
                    packetListeners.remove(handler)
                }
            }.getOrThrow()
        }
    }

    @PublishedApi
    internal val packetListeners = LockFreeLinkedList<PacketListener>()

    @PublishedApi
    internal inner class PacketListener(
        // callback
        val commandName: String,
        val sequenceId: Int
    ) : CompletableDeferred<Packet?> by CompletableDeferred(supervisor) {
        fun filter(commandName: String, sequenceId: Int) =
            this.commandName == commandName && this.sequenceId == sequenceId
    }

    override fun close(cause: Throwable?) {
        if (::channel.isInitialized) {
            channel.close()
        }
        super.close(cause)
    }

    override suspend fun join() = supervisor.join()
}
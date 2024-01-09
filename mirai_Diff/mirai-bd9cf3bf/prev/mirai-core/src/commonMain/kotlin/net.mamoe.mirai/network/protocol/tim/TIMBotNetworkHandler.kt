package net.mamoe.mirai.network.protocol.tim

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.core.*
import net.mamoe.mirai.Bot
import net.mamoe.mirai.event.ListeningStatus
import net.mamoe.mirai.event.broadcast
import net.mamoe.mirai.event.events.BeforePacketSendEvent
import net.mamoe.mirai.event.events.BotLoginSucceedEvent
import net.mamoe.mirai.event.events.PacketSentEvent
import net.mamoe.mirai.event.events.ServerPacketReceivedEvent
import net.mamoe.mirai.event.subscribe
import net.mamoe.mirai.network.BotNetworkHandler
import net.mamoe.mirai.network.BotSession
import net.mamoe.mirai.network.protocol.tim.handler.*
import net.mamoe.mirai.network.protocol.tim.packet.*
import net.mamoe.mirai.network.protocol.tim.packet.event.ServerEventPacket
import net.mamoe.mirai.network.protocol.tim.packet.login.*
import net.mamoe.mirai.network.session
import net.mamoe.mirai.qqAccount
import net.mamoe.mirai.utils.BotNetworkConfiguration
import net.mamoe.mirai.utils.OnlineStatus
import net.mamoe.mirai.utils.io.*
import net.mamoe.mirai.utils.solveCaptcha
import kotlin.coroutines.CoroutineContext

/**
 * 包处理协程调度器.
 *
 * JVM: 独立的 4 thread 调度器
 */
expect val NetworkDispatcher: CoroutineDispatcher

/**
 * [BotNetworkHandler] 的 TIM PC 协议实现
 *
 * @see BotNetworkHandler
 */
internal class TIMBotNetworkHandler internal constructor(private val bot: Bot) :
    BotNetworkHandler<TIMBotNetworkHandler.BotSocketAdapter>, PacketHandlerList() {

    override val coroutineContext: CoroutineContext =
        NetworkDispatcher + CoroutineExceptionHandler { _, e ->
            bot.logger.error("An exception was thrown in a coroutine under TIMBotNetworkHandler", e)
        } + SupervisorJob()


    override lateinit var socket: BotSocketAdapter
        private set

    internal val temporaryPacketHandlers = mutableListOf<TemporaryPacketHandler<*, *>>()
    private val handlersLock = Mutex()

    private var heartbeatJob: Job? = null


    override suspend fun addHandler(temporaryPacketHandler: TemporaryPacketHandler<*, *>) {
        handlersLock.withLock {
            temporaryPacketHandlers.add(temporaryPacketHandler)
        }
        temporaryPacketHandler.send(this[ActionPacketHandler].session)
    }

    override suspend fun login(configuration: BotNetworkConfiguration): LoginResult =
        withContext(this.coroutineContext) {
            TIMProtocol.SERVER_IP.forEach { ip ->
                bot.logger.info("Connecting server $ip")
                socket = BotSocketAdapter(ip, configuration)

                loginResult = CompletableDeferred()

                socket.resendTouch().takeIf { it != LoginResult.TIMEOUT }?.let { return@withContext it }

                println()
                bot.logger.warning("Timeout. Retrying next server")

                socket.close()
            }
            return@withContext LoginResult.TIMEOUT
        }

    internal var loginResult: CompletableDeferred<LoginResult> = CompletableDeferred()


    //private | internal
    private fun onLoggedIn(sessionKey: ByteArray) {
        require(size == 0) { "Already logged in" }
        val session = BotSession(bot, sessionKey, socket)

        add(EventPacketHandler(session).asNode(EventPacketHandler))
        add(ActionPacketHandler(session).asNode(ActionPacketHandler))
        bot.logger.info("Successfully logged in")
    }

    private lateinit var sessionKey: ByteArray

    override suspend fun awaitDisconnection() {
        heartbeatJob?.join()
    }

    override suspend fun close(cause: Throwable?) {
        super.close(cause)

        this.heartbeatJob?.cancelChildren(CancellationException("handler closed"))
        this.heartbeatJob?.join()//等待 cancel 完成
        this.heartbeatJob = null

        if (!this.loginResult.isCompleted && !this.loginResult.isCancelled) {
            this.loginResult.cancel(CancellationException("socket closed"))
            this.loginResult.join()
        }

        this.forEach {
            it.instance.close()
        }

        this.socket.close()
    }

    override suspend fun sendPacket(packet: OutgoingPacket) = socket.sendPacket(packet)

    internal inner class BotSocketAdapter(override val serverIp: String, val configuration: BotNetworkConfiguration) :
        DataPacketSocketAdapter {
        override val channel: PlatformDatagramChannel = PlatformDatagramChannel(serverIp, 8000)

        override val isOpen: Boolean get() = channel.isOpen

        private lateinit var loginHandler: LoginHandler

        private suspend fun processReceive() {
            while (channel.isOpen) {
                val buffer = IoBuffer.Pool.borrow()

                try {
                    channel.read(buffer)// JVM: withContext(IO)
                } catch (e: ClosedChannelException) {
                    close()
                    return
                } catch (e: ReadPacketInternalException) {
                    bot.logger.error("Socket channel read failed: ${e.message}")
                    continue
                } catch (e: Throwable) {
                    bot.logger.error("Caught unexpected exceptions", e)
                    continue
                } finally {
                    if (!buffer.canRead() || buffer.readRemaining == 0) {//size==0
                        //bot.logger.debug("processReceive: Buffer cannot be read")
                        buffer.release(IoBuffer.Pool)
                        continue
                    }// sometimes exceptions are thrown without this `if` clause
                }


                launch {
                    // `.use`: Ensure that the packet is consumed **totally**
                    // so that all the buffers are released
                    ByteReadPacket(buffer, IoBuffer.Pool).use {
                        try {
                            distributePacket(it.parseServerPacket(buffer.readRemaining))
                        } catch (e: Exception) {
                            bot.logger.error(e)
                        }
                    }
                }
            }
        }

        internal suspend fun resendTouch(): LoginResult /* = coroutineScope */ {
            if (::loginHandler.isInitialized) loginHandler.close()

            loginHandler = LoginHandler(configuration)


            val expect = expectPacket<TouchResponsePacket>()
            launch { processReceive() }
            launch {
                if (withTimeoutOrNull(configuration.touchTimeout.millisecondsLong) { expect.join() } == null) {
                    loginResult.complete(LoginResult.TIMEOUT)
                }
            }
            sendPacket(TouchPacket(bot.qqAccount, serverIp))

            return loginResult.await()
        }

        private suspend inline fun <reified P : ServerPacket> expectPacket(): CompletableDeferred<P> {
            val receiving = CompletableDeferred<P>(coroutineContext[Job])
            subscribe<ServerPacketReceivedEvent<*>> {
                if (it.packet is P && it.bot === bot) {
                    receiving.complete(it.packet)
                    ListeningStatus.STOPPED
                } else
                    ListeningStatus.LISTENING
            }
            return receiving
        }

        override suspend fun distributePacket(packet: ServerPacket) {
            try {
                packet.decode()
            } catch (e: Exception) {
                bot.logger.error("When distributePacket", e)
                bot.logger.debug("Packet=$packet")
                bot.logger.debug("Packet size=" + packet.input.readBytes().size)
                bot.logger.debug("Packet data=" + packet.input.readBytes().toUHexString())
                packet.close()
                throw e
            }

            packet.use {
                packet::class.takeUnless { ResponsePacket::class.isInstance(packet) }
                    ?.simpleName?.takeUnless { it.endsWith("Encrypted") || it.endsWith("Raw") }
                    ?.let {
                        bot.logger.verbose("Packet received: $packet")
                    }

                // Remove first to release the lock
                handlersLock.withLock {
                    temporaryPacketHandlers.filter { it.filter(session, packet) }
                        .also { temporaryPacketHandlers.removeAll(it) }
                }.forEach {
                    it.doReceiveWithoutExceptions(packet)
                }

                if (packet is ServerEventPacket) {
                    // Ensure the response packet is sent
                    sendPacket(packet.ResponsePacket(bot.qqAccount, sessionKey))
                }

                if (ServerPacketReceivedEvent(bot, packet).broadcast().cancelled) {
                    return
                }

                // They should be called in sequence because packet is lock-free
                loginHandler.onPacketReceived(packet)
                this@TIMBotNetworkHandler.forEach {
                    it.instance.onPacketReceived(packet)
                }
            }
        }

        override suspend fun sendPacket(packet: OutgoingPacket): Unit = withContext(coroutineContext) {
            check(channel.isOpen) { "channel is not open" }

            if (BeforePacketSendEvent(bot, packet).broadcast().cancelled) {
                return@withContext
            }

            packet.delegate.use { built ->
                val buffer = IoBuffer.Pool.borrow()
                try {
                    built.readAvailable(buffer)
                    val shouldBeSent = buffer.readRemaining
                    check(channel.send(buffer) == shouldBeSent) { "Buffer is not entirely sent. Required sent length=$shouldBeSent, but after channel.send, buffer remains ${buffer.readBytes().toUHexString()}" }//JVM: withContext(IO)
                } catch (e: SendPacketInternalException) {
                    bot.logger.error("Caught SendPacketInternalException: ${e.cause?.message}")
                    bot.reinitializeNetworkHandler(configuration, e)
                    return@withContext
                } finally {
                    buffer.release(IoBuffer.Pool)
                }
            }

            packet.takeUnless { _ ->
                packet.packetId is KnownPacketId && packet.packetId.builder?.let {
                    it::class.annotations.filterIsInstance<NoLog>().any()
                } == true
            }?.let {
                bot.logger.verbose("Packet sent:     $it")
            }

            PacketSentEvent(bot, packet).broadcast()

            Unit
        }

        override val owner: Bot get() = this@TIMBotNetworkHandler.bot

        override fun close() {
            if (::loginHandler.isInitialized) loginHandler.close()
            this.channel.close()
        }
    }


    /**
     * 处理登录过程
     */
    inner class LoginHandler(private val configuration: BotNetworkConfiguration) {
        private lateinit var token00BA: ByteArray
        private lateinit var token0825: ByteArray//56
        private var loginTime: Int = 0
        private lateinit var loginIP: String
        private var privateKey: ByteArray = getRandomByteArray(16)

        private lateinit var sessionResponseDecryptionKey: IoBuffer

        private var captchaSectionId: Int = 1
        private var captchaCache: IoBuffer? = null
            //set 为 null 时自动 release; get 为 null 时自动 borrow
            get() {
                if (field == null) field = IoBuffer.Pool.borrow()
                return field
            }
            set(value) {
                if (value == null) {
                    field?.release(IoBuffer.Pool)
                }
                field = value
            }

        suspend fun onPacketReceived(packet: ServerPacket) {//complex function, but it doesn't matter
            when (packet) {
                is TouchResponsePacket -> {
                    if (packet.serverIP != null) {//redirection
                        socket.close()
                        socket = BotSocketAdapter(packet.serverIP!!, socket.configuration)
                        bot.logger.info("Redirecting to ${packet.serverIP}")
                        loginResult.complete(socket.resendTouch())
                    } else {//password submission
                        this.loginIP = packet.loginIP
                        this.loginTime = packet.loginTime
                        this.token0825 = packet.token0825

                        socket.sendPacket(
                            SubmitPasswordPacket(
                                bot = bot.qqAccount,
                                password = bot.account.password,
                                loginTime = loginTime,
                                loginIP = loginIP,
                                privateKey = privateKey,
                                token0825 = token0825,
                                token00BA = null,
                                randomDeviceName = socket.configuration.randomDeviceName
                            )
                        )
                    }
                }

                is LoginResponseFailedPacket -> {
                    loginResult.complete(packet.loginResult)
                    return
                }

                is CaptchaCorrectPacket -> {
                    this.privateKey = getRandomByteArray(16)//似乎是必须的
                    this.token00BA = packet.token00BA

                    socket.sendPacket(
                        SubmitPasswordPacket(
                            bot = bot.qqAccount,
                            password = bot.account.password,
                            loginTime = loginTime,
                            loginIP = loginIP,
                            privateKey = privateKey,
                            token0825 = token0825,
                            token00BA = packet.token00BA,
                            randomDeviceName = socket.configuration.randomDeviceName
                        )
                    )
                }

                is LoginResponseCaptchaInitPacket -> {
                    //[token00BA]来源之一: 验证码
                    this.token00BA = packet.token00BA
                    this.captchaCache = packet.captchaPart1

                    if (packet.unknownBoolean) {
                        this.captchaSectionId = 1
                        socket.sendPacket(
                            RequestCaptchaTransmissionPacket(
                                bot.qqAccount,
                                this.token0825,
                                this.captchaSectionId++,
                                packet.token00BA
                            )
                        )
                    }
                }

                is CaptchaTransmissionResponsePacket -> {
                    //packet is ServerCaptchaWrongPacket
                    if (this.captchaSectionId == 0) {
                        bot.logger.warning("验证码错误, 请重新输入")
                        this.captchaSectionId = 1
                        this.captchaCache = null
                    }

                    this.captchaCache!!.writeFully(packet.captchaSectionN)
                    this.token00BA = packet.token00BA

                    if (packet.transmissionCompleted) {
                        val code = solveCaptcha(captchaCache!!)

                        this.captchaCache = null
                        if (code == null) {
                            this.captchaSectionId = 1//意味着正在刷新验证码
                            socket.sendPacket(OutgoingCaptchaRefreshPacket(bot.qqAccount, token0825))
                        } else {
                            this.captchaSectionId = 0//意味着已经提交验证码
                            socket.sendPacket(
                                SubmitCaptchaPacket(
                                    bot.qqAccount,
                                    token0825,
                                    code,
                                    packet.captchaToken
                                )
                            )
                        }
                    } else {
                        socket.sendPacket(
                            RequestCaptchaTransmissionPacket(
                                bot.qqAccount,
                                token0825,
                                captchaSectionId++,
                                packet.token00BA
                            )
                        )
                    }
                }

                is LoginResponseSuccessPacket -> {
                    this.sessionResponseDecryptionKey = packet.sessionResponseDecryptionKey
                    socket.sendPacket(
                        RequestSessionPacket(
                            bot.qqAccount,
                            socket.serverIp,
                            packet.token38,
                            packet.token88,
                            packet.encryptionKey
                        )
                    )
                }

                //是ClientPasswordSubmissionPacket之后服务器回复的可能之一
                is LoginResponseKeyExchangeResponsePacket -> {
                    this.privateKey = packet.privateKeyUpdate

                    socket.sendPacket(
                        SubmitPasswordPacket(
                            bot = bot.qqAccount,
                            password = bot.account.password,
                            loginTime = loginTime,
                            loginIP = loginIP,
                            privateKey = privateKey,
                            token0825 = token0825,
                            token00BA = packet.tokenUnknown ?: token00BA,
                            randomDeviceName = socket.configuration.randomDeviceName,
                            tlv0006 = packet.tlv0006
                        )
                    )
                }

                is SessionKeyResponsePacket -> {
                    sessionKey = packet.sessionKey
                    bot.logger.info("sessionKey = ${sessionKey.toUHexString()}")

                    heartbeatJob = launch {
                        while (socket.isOpen) {
                            delay(configuration.heartbeatPeriod.millisecondsLong)
                            with(session) {
                                class HeartbeatTimeoutException : CancellationException("heartbeat timeout")

                                if (withTimeoutOrNull(configuration.heartbeatTimeout.millisecondsLong) {
                                        HeartbeatPacket(
                                            bot.qqAccount,
                                            sessionKey
                                        ).sendAndExpect<HeartbeatPacket.Response>().join()
                                    } == null) {
                                    bot.logger.warning("Heartbeat timed out")
                                    bot.reinitializeNetworkHandler(configuration, HeartbeatTimeoutException())
                                    return@launch
                                }
                            }
                        }
                    }

                    loginResult.complete(LoginResult.SUCCESS)

                    setOnlineStatus(OnlineStatus.ONLINE)//required
                }

                is ServerLoginSuccessPacket -> {
                    BotLoginSucceedEvent(bot).broadcast()

                    onLoggedIn(sessionKey)
                    this.close()//The LoginHandler is useless since then
                }


                is ServerCaptchaPacket.Encrypted -> socket.distributePacket(packet.decrypt())
                is LoginResponseCaptchaInitPacket.Encrypted -> socket.distributePacket(packet.decrypt())
                is LoginResponseKeyExchangeResponsePacket.Encrypted -> socket.distributePacket(packet.decrypt(this.privateKey))
                is LoginResponseSuccessPacket.Encrypted -> socket.distributePacket(packet.decrypt(this.privateKey))
                is SessionKeyResponsePacket.Encrypted -> socket.distributePacket(packet.decrypt(this.sessionResponseDecryptionKey))
                is TouchResponsePacket.Encrypted -> socket.distributePacket(packet.decrypt())

                is UnknownServerPacket.Encrypted -> socket.distributePacket(packet.decrypt(sessionKey))
                else -> {

                }
            }
        }

        @Suppress("MemberVisibilityCanBePrivate")
        suspend fun setOnlineStatus(status: OnlineStatus) {
            socket.sendPacket(ChangeOnlineStatusPacket(bot.qqAccount, sessionKey, status))
        }

        fun close() {
            this.captchaCache = null

            if (::sessionResponseDecryptionKey.isInitialized && sessionResponseDecryptionKey.readRemaining != 0)
                this.sessionResponseDecryptionKey.release(IoBuffer.Pool)
        }
    }
}

@file:Suppress("MemberVisibilityCanBePrivate", "unused", "EXPERIMENTAL_API_USAGE", "EXPERIMENTAL_UNSIGNED_LITERALS", "NOTHING_TO_INLINE")

package net.mamoe.mirai.network

import kotlinx.coroutines.*
import kotlinx.io.core.ByteReadPacket
import net.mamoe.mirai.*
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.GroupId
import net.mamoe.mirai.contact.GroupInternalId
import net.mamoe.mirai.contact.QQ
import net.mamoe.mirai.message.Image
import net.mamoe.mirai.message.ImageId0x03
import net.mamoe.mirai.message.ImageId0x06
import net.mamoe.mirai.network.protocol.timpc.TIMBotNetworkHandler
import net.mamoe.mirai.network.protocol.timpc.handler.DataPacketSocketAdapter
import net.mamoe.mirai.network.protocol.timpc.handler.TemporaryPacketHandler
import net.mamoe.mirai.network.protocol.timpc.packet.OutgoingPacket
import net.mamoe.mirai.network.protocol.timpc.packet.Packet
import net.mamoe.mirai.network.protocol.timpc.packet.SessionKey
import net.mamoe.mirai.network.protocol.timpc.packet.action.*
import net.mamoe.mirai.utils.MiraiInternalAPI
import net.mamoe.mirai.utils.assertUnreachable
import net.mamoe.mirai.utils.getGTK
import net.mamoe.mirai.utils.internal.PositiveNumbers
import net.mamoe.mirai.utils.internal.coerceAtLeastOrFail
import net.mamoe.mirai.utils.secondsToMillis
import kotlin.coroutines.coroutineContext

/**
 * 构造 [BotSession] 的捷径
 */
@Suppress("FunctionName", "NOTHING_TO_INLINE")
internal inline fun TIMBotNetworkHandler.BotSession(sessionKey: SessionKey): BotSession = BotSession(bot, sessionKey)

/**
 * 登录会话. 当登录完成后, 客户端会拿到 sessionKey.
 * 此时建立 session, 然后开始处理事务.
 *
 * 本类中含有各平台相关扩展函数等.
 *
 * @author Him188moe
 */
@UseExperimental(MiraiInternalAPI::class)
expect class BotSession internal constructor(
    bot: Bot,
    sessionKey: SessionKey
) : BotSessionBase

/**
 * [BotSession] 平台通用基础
 */
@MiraiInternalAPI
// cannot be internal because of `public BotSession`
abstract class BotSessionBase internal constructor(
    val bot: Bot,
    internal val sessionKey: SessionKey
) {
    val socket: DataPacketSocketAdapter get() = bot.network.socket
    val NetworkScope: CoroutineScope get() = bot.network

    /**
     * Web api 使用
     */
    val cookies: String get() = _cookies

    /**
     * Web api 使用
     */
    val sKey: String get() = _sKey

    /**
     * Web api 使用
     */
    val gtk: Int get() = _gtk

    inline fun Int.qq(): QQ = bot.getQQ(this.coerceAtLeastOrFail(0).toUInt())
    inline fun Long.qq(): QQ = bot.getQQ(this.coerceAtLeastOrFail(0))
    inline fun UInt.qq(): QQ = bot.getQQ(this)

    suspend inline fun Int.group(): Group = bot.getGroup(this.coerceAtLeastOrFail(0).toUInt())
    suspend inline fun Long.group(): Group = bot.getGroup(this.coerceAtLeastOrFail(0))
    suspend inline fun UInt.group(): Group = bot.getGroup(GroupId(this))
    suspend inline fun GroupId.group(): Group = bot.getGroup(this)
    suspend inline fun GroupInternalId.group(): Group = bot.getGroup(this)

    suspend fun Image.getLink(): ImageLink = when (this.id) {
        is ImageId0x06 -> FriendImagePacket.RequestImageLink(bot.qqAccount, bot.sessionKey, id).sendAndExpect<FriendImageLink>()
        is ImageId0x03 -> GroupImagePacket.RequestImageLink(bot.qqAccount, bot.sessionKey, id).sendAndExpect<GroupImageLink>().requireSuccess()
        else -> assertUnreachable()
    }

    suspend inline fun Image.downloadAsByteArray(): ByteArray = getLink().downloadAsByteArray()
    suspend inline fun Image.download(): ByteReadPacket = getLink().download()




    // region internal

    @Suppress("PropertyName")
    internal var _sKey: String = ""
        set(value) {
            field = value
            _gtk = getGTK(value)
        }
    @Suppress("PropertyName")
    internal lateinit var _cookies: String
    private var _gtk: Int = 0

    /**
     * 发送一个数据包, 并期待接受一个特定的 [ServerPacket][P].
     * 这个方法会立即发出这个数据包然后返回一个 [CompletableDeferred].
     *
     * 实现方法:
     * ```kotlin
     * with(session){
     *  ClientPacketXXX(...).sendAndExpectAsync<ServerPacketXXX> {
     *   //it: ServerPacketXXX
     *  }
     * }
     * ```
     * @sample net.mamoe.mirai.network.protocol.timpc.packet.action.uploadImage
     *
     * @param checkSequence 是否筛选 `sequenceId`, 即是否筛选发出的包对应的返回包.
     * @param P 期待的包
     * @param handler 处理期待的包. **将会在调用本函数的 [coroutineContext] 下执行.**
     *
     * @see Bot.withSession 转换 receiver, 即 `this` 的指向, 为 [BotSession]
     */
    internal suspend inline fun <reified P : Packet, R> OutgoingPacket.sendAndExpectAsync(
        checkSequence: Boolean = true,
        noinline handler: suspend (P) -> R
    ): Deferred<R> {
        val deferred: CompletableDeferred<R> = CompletableDeferred(coroutineContext[Job])
        (bot.network as TIMBotNetworkHandler)
            .addHandler(TemporaryPacketHandler(P::class, deferred, this@BotSessionBase as BotSession, checkSequence, coroutineContext + deferred).also {
                it.toSend(this)
                it.onExpect(handler)
            })
        return deferred
    }

    internal suspend inline fun <reified P : Packet> OutgoingPacket.sendAndExpectAsync(checkSequence: Boolean = true): Deferred<P> =
        sendAndExpectAsync<P, P>(checkSequence) { it }

    internal suspend inline fun <reified P : Packet, R> OutgoingPacket.sendAndExpect(
        checkSequence: Boolean = true,
        timeoutMillis: Long = 5.secondsToMillis,
        crossinline mapper: (P) -> R
    ): R = withTimeout(timeoutMillis) { sendAndExpectAsync<P, R>(checkSequence) { mapper(it) }.await() }

    internal suspend inline fun <reified P : Packet> OutgoingPacket.sendAndExpect(
        checkSequence: Boolean = true,
        timeoutMillist: Long = 5.secondsToMillis
    ): P = withTimeout(timeoutMillist) { sendAndExpectAsync<P, P>(checkSequence) { it }.await() }

    internal suspend inline fun OutgoingPacket.send() =
        (socket as TIMBotNetworkHandler.BotSocketAdapter).sendPacket(this)

    // endregion
}


inline val BotSession.isOpen: Boolean get() = socket.isOpen
inline val BotSession.qqAccount: UInt get() = bot.account.id // 为了与群和好友的 id 区别.

/**
 * 取得 [BotNetworkHandler] 的 sessionKey.
 * 实际上是一个捷径.
 */
internal inline val BotNetworkHandler<*>.sessionKey: SessionKey get() = this.session.sessionKey

/**
 * 取得 [Bot] 的 [BotSession].
 * 实际上是一个捷径.
 */
inline val Bot.session: BotSession get() = this.network.session

/**
 * 取得 [Bot] 的 `sessionKey`.
 * 实际上是一个捷径.
 */
internal inline val Bot.sessionKey: SessionKey get() = this.session.sessionKey


/**
 * 发送数据包
 * @throws IllegalStateException 当 [BotNetworkHandler.socket] 未开启时
 */
internal suspend inline fun BotSession.sendPacket(packet: OutgoingPacket) = this.bot.sendPacket(packet)


inline fun BotSession.getQQ(@PositiveNumbers number: Long): QQ = this.bot.getQQ(number)
inline fun BotSession.getQQ(number: UInt): QQ = this.bot.getQQ(number)

suspend inline fun BotSession.getGroup(id: UInt): Group = this.bot.getGroup(id)
suspend inline fun BotSession.getGroup(@PositiveNumbers id: Long): Group = this.bot.getGroup(id)
suspend inline fun BotSession.getGroup(id: GroupId): Group = this.bot.getGroup(id)
suspend inline fun BotSession.getGroup(internalId: GroupInternalId): Group = this.bot.getGroup(internalId)
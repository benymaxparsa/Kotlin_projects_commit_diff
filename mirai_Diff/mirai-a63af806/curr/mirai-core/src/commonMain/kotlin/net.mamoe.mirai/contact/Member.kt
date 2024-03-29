/*
 * Copyright 2020 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/mamoe/mirai/blob/master/LICENSE
 */

@file:Suppress("unused")

package net.mamoe.mirai.contact

import net.mamoe.mirai.Bot
import net.mamoe.mirai.JavaFriendlyAPI
import net.mamoe.mirai.event.events.*
import net.mamoe.mirai.getFriendOrNull
import net.mamoe.mirai.message.MessageReceipt
import net.mamoe.mirai.message.data.Message
import net.mamoe.mirai.message.data.toMessage
import net.mamoe.mirai.utils.MiraiInternalAPI
import net.mamoe.mirai.utils.WeakRefProperty
import kotlin.jvm.JvmSynthetic
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

/**
 * 群成员.
 *
 * 群成员可能也是好友, 但他们在对象类型上不同.
 * 群成员可以通过 [asFriend] 得到相关好友对象.
 */
@Suppress("INAPPLICABLE_JVM_NAME")
@OptIn(MiraiInternalAPI::class, JavaFriendlyAPI::class)
abstract class Member : MemberJavaFriendlyAPI() {
    /**
     * 所在的群.
     */
    @WeakRefProperty
    abstract val group: Group

    /**
     * 成员的权限, 动态更新.
     *
     * @see MemberPermissionChangeEvent 权限变更事件. 由群主或机器人的操作触发.
     */
    abstract val permission: MemberPermission

    /**
     * 群名片. 可能为空.
     *
     * 管理员和群主都可修改任何人（包括群主）的群名片.
     *
     * 在修改时将会异步上传至服务器.
     *
     * @see [nameCardOrNick] 获取非空群名片或昵称
     *
     * @see MemberCardChangeEvent 群名片被管理员, 自己或 [Bot] 改动事件. 修改时也会触发此事件.
     * @throws PermissionDeniedException 无权限修改时
     */
    abstract var nameCard: String

    /**
     * 群头衔.
     *
     * 仅群主可以修改群头衔.
     *
     * 在修改时将会异步上传至服务器.
     *
     * @see MemberSpecialTitleChangeEvent 群名片被管理员, 自己或 [Bot] 改动事件. 修改时也会触发此事件.
     * @throws PermissionDeniedException 无权限修改时
     */
    abstract var specialTitle: String

    /**
     * 被禁言剩余时长. 单位为秒.
     *
     * @see isMuted 判断改成员是否处于禁言状态
     * @see mute 设置禁言
     * @see unmute 取消禁言
     */
    abstract val muteTimeRemaining: Int

    /**
     * 禁言.
     *
     * QQ 中最小操作和显示的时间都是一分钟.
     * 机器人可以实现精确到秒, 会被客户端显示为 1 分钟但不影响实际禁言时间.
     *
     * 管理员可禁言成员, 群主可禁言管理员和群员.
     *
     * @param durationSeconds 持续时间. 精确到秒. 范围区间表示为 `(0s, 30days]`. 超过范围则会抛出异常.
     * @return 机器人无权限时返回 `false`
     *
     * @see Int.minutesToSeconds
     * @see Int.hoursToSeconds
     * @see Int.daysToSeconds
     *
     * @see MemberMuteEvent 成员被禁言事件
     * @throws PermissionDeniedException 无权限修改时
     */
    @JvmSynthetic
    abstract suspend fun mute(durationSeconds: Int)

    /**
     * 解除禁言.
     *
     * 管理员可解除成员的禁言, 群主可解除管理员和群员的禁言.
     *
     * @see MemberUnmuteEvent 成员被取消禁言事件.
     * @throws PermissionDeniedException 无权限修改时
     */
    @JvmSynthetic
    abstract suspend fun unmute()

    /**
     * 踢出该成员.
     *
     * 管理员可踢出成员, 群主可踢出管理员和群员.
     *
     * @see MemberLeaveEvent.Kick 成员被踢出事件.
     * @throws PermissionDeniedException 无权限修改时
     */
    @JvmSynthetic
    abstract suspend fun kick(message: String = "")

    /**
     * 向这个对象发送消息.
     *
     * 单条消息最大可发送 4500 字符或 50 张图片.
     *
     * @see MessageSendEvent.FriendMessageSendEvent 发送好友信息事件, cancellable
     * @see MessageSendEvent.GroupMessageSendEvent  发送群消息事件. cancellable
     *
     * @throws EventCancelledException 当发送消息事件被取消时抛出
     * @throws BotIsBeingMutedException 发送群消息时若 [Bot] 被禁言抛出
     * @throws MessageTooLargeException 当消息过长时抛出
     *
     * @return 消息回执. 可进行撤回 ([MessageReceipt.recall])
     */
    @JvmSynthetic
    abstract override suspend fun sendMessage(message: Message): MessageReceipt<Member>

    /**
     * @see sendMessage
     */
    @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "VIRTUAL_MEMBER_HIDDEN", "OVERRIDE_BY_INLINE")
    @kotlin.internal.InlineOnly // purely virtual
    @JvmSynthetic
    suspend inline fun sendMessage(message: String): MessageReceipt<Member> {
        return sendMessage(message.toMessage())
    }

    final override fun toString(): String = "Member($id)"
}

/**
 * 得到此成员作为好友的对象.
 */
inline val Member.asFriend: Friend
    get() = this.bot.getFriendOrNull(this.id) ?: error("$this is not a friend")

/**
 * 得到此成员作为好友的对象.
 */
inline val Member.asFriendOrNull: Friend?
    get() = this.bot.getFriendOrNull(this.id)

/**
 * 判断此成员是否为好友
 */
inline val Member.isFriend: Boolean
    get() = this.bot.friends.contains(this.id)

/**
 * 如果此成员是好友, 则执行 [block] 并返回其返回值. 否则返回 `null`
 */
inline fun <R> Member.takeIfFriend(block: (Friend) -> R): R? {
    return this.asFriendOrNull?.let(block)
}

/**
 * 获取非空群名片或昵称.
 *
 * 若 [群名片][Member.nameCard] 不为空则返回群名片, 为空则返回 [QQ.nick]
 */
val Member.nameCardOrNick: String get() = this.nameCard.takeIf { it.isNotEmpty() } ?: this.nick

/**
 * 判断改成员是否处于禁言状态.
 */
fun Member.isMuted(): Boolean {
    return muteTimeRemaining != 0 && muteTimeRemaining != 0xFFFFFFFF.toInt()
}

/**
 * @see Member.mute
 */
@ExperimentalTime
suspend inline fun Member.mute(duration: Duration) {
    require(duration.inDays <= 30) { "duration must be at most 1 month" }
    require(duration.inSeconds > 0) { "duration must be greater than 0 second" }
    this.mute(duration.inSeconds.toInt())
}

/**
 * @see Member.mute
 */
suspend inline fun Member.mute(durationSeconds: Long) = this.mute(durationSeconds.toInt())
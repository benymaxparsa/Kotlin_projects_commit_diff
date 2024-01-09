/*
 * Copyright 2019-2020 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AFFERO GENERAL PUBLIC LICENSE version 3 license that can be found via the following link.
 *
 * https://github.com/mamoe/mirai/blob/master/LICENSE
 */
package net.mamoe.mirai.message.action

import net.mamoe.kjbb.JvmBlockingBridge
import net.mamoe.mirai.Bot
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.event.events.BotNudgedEvent
import net.mamoe.mirai.event.events.MemberNudgedEvent
import net.mamoe.mirai.utils.BotConfiguration
import net.mamoe.mirai.utils.BotConfiguration.MiraiProtocol
import net.mamoe.mirai.utils.MiraiExperimentalAPI
import net.mamoe.mirai.utils.SinceMirai

/**
 * 一个 "戳一戳" 消息.
 *
 * 仅在手机 QQ 8.4.0 左右版本才受支持. 其他客户端会忽略这些消息.
 *
 * @see User.nudge 创建 [Nudge] 对象
 * @see Bot.nudge 创建 [Nudge] 对象
 */
@MiraiExperimentalAPI
@SinceMirai("1.3.0")
public sealed class Nudge {
    /**
     * 戳的对象. 即 "A 戳了 B" 中的 "B".
     */
    public abstract val target: ContactOrBot // User or Bot

    /**
     * 发送戳一戳该成员的消息.
     *
     * 需要 [使用协议][BotConfiguration.protocol] [MiraiProtocol.ANDROID_PHONE].
     *
     * @param receiver 这条 "戳一戳" 消息的接收对象. (不是 "戳" 动作的对象, 而是接收 "A 戳了 B" 这条消息的对象)
     *
     * @see MemberNudgedEvent 成员被戳事件
     * @see BotNudgedEvent [Bot] 被戳事件
     *
     * @throws UnsupportedOperationException 当未使用 [安卓协议][MiraiProtocol.ANDROID_PHONE] 时抛出
     *
     * @return 成功发送时为 `true`. 若对方禁用 "戳一戳" 功能, 返回 `false`.
     */
    @JvmBlockingBridge
    @MiraiExperimentalAPI
    public suspend fun sendTo(receiver: Contact): Boolean {
        @Suppress("DEPRECATION_ERROR")
        return receiver.bot.sendNudge(this, receiver)
    }

    public companion object {
        /**
         * 发送戳一戳该成员的消息.
         *
         * 需要 [使用协议][BotConfiguration.protocol] [MiraiProtocol.ANDROID_PHONE].
         *
         * @see MemberNudgedEvent 成员被戳事件
         * @see BotNudgedEvent [Bot] 被戳事件
         *
         * @throws UnsupportedOperationException 当未使用 [安卓协议][MiraiProtocol.ANDROID_PHONE] 时抛出
         *
         * @return 成功发送时为 `true`. 若对方禁用 "戳一戳" 功能, 返回 `false`.
         */
        @MiraiExperimentalAPI
        @JvmBlockingBridge
        public suspend fun Contact.sendNudge(nudge: Nudge): Boolean = nudge.sendTo(this)
    }
}

/**
 * @see Bot.nudge
 * @see Nudge
 */
@MiraiExperimentalAPI
@SinceMirai("1.3.0")
public data class BotNudge(
    public override val target: Bot
) : Nudge()

/**
 * @see User.nudge
 * @see Nudge
 */
@MiraiExperimentalAPI
@SinceMirai("1.3.0")
public sealed class UserNudge : Nudge() {
    public abstract override val target: User
}

/**
 * @see Member.nudge
 * @see Nudge
 */
@MiraiExperimentalAPI
@SinceMirai("1.3.0")
public data class MemberNudge(
    public override val target: Member
) : UserNudge()

/**
 * @see Friend.nudge
 * @see Nudge
 */
@MiraiExperimentalAPI
@SinceMirai("1.3.0")
public data class FriendNudge(
    public override val target: Friend
) : UserNudge()
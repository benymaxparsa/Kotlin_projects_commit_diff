/*
 * Copyright 2020 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/mamoe/mirai/blob/master/LICENSE
 */

package net.mamoe.mirai.event.events

import net.mamoe.mirai.Bot
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.Member
import net.mamoe.mirai.contact.QQ
import net.mamoe.mirai.event.Event

/**
 * 有关一个 [Bot] 的事件
 */
interface BotEvent : Event {
    val bot: Bot
}

/**
 * [Bot] 被动接收的事件. 这些事件可能与机器人有关
 */
interface BotPassiveEvent : BotEvent

/**
 * 由 [Bot] 主动发起的动作的事件
 */
interface BotActiveEvent : BotEvent


/**
 * 有关群的事件
 */
interface GroupEvent : BotEvent {
    val group: Group
    override val bot: Bot
        get() = group.bot
}

/**
 * 有关群成员的事件
 */
interface GroupMemberEvent : GroupEvent {
    val member: Member
    override val group: Group
        get() = member.group
}


/**
 * 有关群的事件
 */
interface FriendEvent : BotEvent {
    val friend: QQ
    override val bot: Bot
        get() = friend.bot
}
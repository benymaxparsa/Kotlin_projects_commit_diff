/*
 * Copyright 2019-2020 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AFFERO GENERAL PUBLIC LICENSE version 3 license that can be found via the following link.
 *
 * https://github.com/mamoe/mirai/blob/master/LICENSE
 */

@file:Suppress("unused", "FunctionName")
@file:JvmMultifileClass
@file:JvmName("BotEventsKt")

package net.mamoe.mirai.event.events

import net.mamoe.mirai.Bot
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.event.AbstractEvent
import net.mamoe.mirai.internal.network.Packet
import net.mamoe.mirai.message.action.Nudge
import net.mamoe.mirai.message.MessageEvent
import net.mamoe.mirai.utils.MiraiExperimentalAPI
import net.mamoe.mirai.utils.MiraiInternalAPI
import net.mamoe.mirai.utils.SinceMirai
import kotlin.jvm.JvmMultifileClass
import kotlin.jvm.JvmName

// note: 若你使用 IntelliJ IDEA, 按 alt + 7 可打开结构


/**
 * [Bot] 登录完成, 好友列表, 群组列表初始化完成
 */
public data class BotOnlineEvent internal constructor(
    public override val bot: Bot
) : BotActiveEvent, AbstractEvent()

/**
 * [Bot] 离线.
 */
public sealed class BotOfflineEvent : BotEvent, AbstractEvent() {

    /**
     * 主动离线. 主动广播这个事件也可以让 [Bot] 关闭.
     */
    public data class Active(
        public override val bot: Bot,
        public override val cause: Throwable?
    ) : BotOfflineEvent(), BotActiveEvent, CauseAware

    /**
     * 被挤下线
     */
    public data class Force internal constructor(
        public override val bot: Bot,
        public val title: String,
        public val message: String
    ) : BotOfflineEvent(), Packet, BotPassiveEvent

    /**
     * 被服务器断开
     */
    @SinceMirai("1.1.0")
    @MiraiInternalAPI("This is very experimental and might be changed")
    public data class MsfOffline internal constructor(
        public override val bot: Bot,
        public override val cause: Throwable?
    ) : BotOfflineEvent(), Packet, BotPassiveEvent, CauseAware

    /**
     * 因网络问题而掉线
     */
    public data class Dropped internal constructor(
        public override val bot: Bot,
        public override val cause: Throwable?
    ) : BotOfflineEvent(), Packet, BotPassiveEvent, CauseAware

    /**
     * 因 returnCode = -10008 等原因掉线
     */
    @MiraiInternalAPI("This is very experimental and might be changed")
    @SinceMirai("1.2.0")
    public data class PacketFactory10008 internal constructor(
        public override val bot: Bot,
        public override val cause: Throwable
    ) : BotOfflineEvent(), Packet, BotPassiveEvent, CauseAware

    /**
     * 服务器主动要求更换另一个服务器
     */
    @MiraiInternalAPI
    public data class RequireReconnect internal constructor(
        public override val bot: Bot
    ) : BotOfflineEvent(), Packet,
        BotPassiveEvent

    @MiraiExperimentalAPI
    public interface CauseAware {
        public val cause: Throwable?
    }
}

/**
 * [Bot] 主动或被动重新登录. 在此事件广播前就已经登录完毕.
 */
public data class BotReloginEvent internal constructor(
    public override val bot: Bot,
    public val cause: Throwable?
) : BotEvent, BotActiveEvent, AbstractEvent()

/**
 * [Bot] 头像被修改（通过其他客户端修改了头像）. 在此事件广播前就已经修改完毕.
 * @see FriendAvatarChangedEvent
 */
public data class BotAvatarChangedEvent(
    public override val bot: Bot
) : BotEvent, Packet, AbstractEvent()

/**
 * [Bot] 的昵称被改变事件, 在此事件触发时 bot 已经完成改名
 * @see FriendNickChangedEvent
 */
@SinceMirai("1.2.0")
public data class BotNickChangedEvent(
    public override val bot: Bot,
    public val from: String,
    public val to: String
) : BotEvent, Packet, AbstractEvent()


@MiraiExperimentalAPI
@SinceMirai("1.3.0")
public sealed class BotNudgedEvent : AbstractEvent(), BotEvent, Packet {
    /**
     * 戳一戳的发起人，为 [Bot] 的某一好友, 或某一群员, 或 [Bot.selfQQ]
     */
    public abstract val from: User

    /** 戳一戳的动作名称 */
    public abstract val action: String

    /** 戳一戳中设置的自定义后缀 */
    public abstract val suffix: String

    @MiraiExperimentalAPI
    @SinceMirai("2.0.0")
    /** [Bot] 在群聊中被戳 */
    public sealed class InGroup : BotNudgedEvent(), GroupMemberEvent {
        abstract override val from: Member
        override val bot: Bot get() = from.bot

        /** [Bot] 在 [Group] 中被 [Member] 戳了 */
        public data class ByMember internal constructor(
            override val action: String,
            override val suffix: String,
            override val member: Member
        ) : InGroup() {
            override val from: Member
                get() = member

            override fun toString(): String {
                return "BotNudgedEvent.InGroup.ByMember(member=$member, action=$action, suffix=$suffix)"
            }
        }

        /** [Bot] 在 [Group] 中自己戳了自己 */
        public data class ByBot internal constructor(
            override val action: String,
            override val suffix: String,
            override val group: Group
        ) : InGroup() {
            override val member: Member get() = group.botAsMember
            override val from: Member get() = member

            override fun toString(): String {
                return "BotNudgedEvent.InGroup.ByBot(group=$group, action=$action, suffix=$suffix)"
            }
        }
    }

    @MiraiExperimentalAPI
    @SinceMirai("2.0.0")
    /** [Bot] 在私聊中被戳 */
    public sealed class InPrivateSession : BotNudgedEvent(), FriendEvent {
        abstract override val from: Friend
        override val bot: Bot get() = from.bot
        override val friend: Friend get() = from

        /** 在私聊中 [Friend] 戳了 [Bot] */
        public data class ByFriend internal constructor(
            override val friend: Friend,
            override val action: String,
            override val suffix: String
        ) : InPrivateSession() {
            override val from: Friend get() = friend
            override fun toString(): String {
                return "BotNudgedEvent.InPrivateSession.ByFriend(friend=$friend, action=$action, suffix=$suffix)"
            }
        }

        /** [Bot] 在私聊中自己戳了自己 */
        public data class ByBot internal constructor(
            /** [Bot] 的对话对象 */
            override val friend: Friend,
            override val action: String,
            override val suffix: String
        ) : InPrivateSession() {
            override val from: Friend get() = bot.selfQQ
            override fun toString(): String {
                return "BotNudgedEvent.InPrivateSession.ByBot(friend=$friend, action=$action, suffix=$suffix)"
            }
        }
    }
}

/*
/**
 * [Bot] 被 [戳][Nudge] 的事件.
 */
@MiraiExperimentalAPI
@SinceMirai("1.3.0")
public data class BotNudgedEvent internal constructor(
    /**
     * 戳一戳的发起人，为 [Bot] 的某一好友, 或某一群员, 或 [Bot.selfQQ]
     */
    public val from: User,
    /**
     * 戳一戳的动作名称
     */
    public val action: String,
    /**
     * 戳一戳中设置的自定义后缀
     */
    public val suffix: String,
) : BotEvent, Packet, AbstractEvent() {
    /**
     * 戳一戳的目标
     */
    public override val bot: Bot get() = from.bot

}
*/

/**
 * 戳一戳发起的会话环境, 可能是 [Friend] 或者 [Group]
 *
 * @see MessageEvent.subject
 */
@MiraiExperimentalAPI
@SinceMirai("2.0.0")
public val BotNudgedEvent.subject: Contact
    get() = when (val inlineFrom = from) {
        is Member -> inlineFrom.group
        else -> inlineFrom
    }

// region 图片

// endregion

/*
 * Copyright 2020 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/mamoe/mirai/blob/master/LICENSE
 */

@file:Suppress("EXPERIMENTAL_API_USAGE", "unused")

package net.mamoe.mirai.contact

import kotlinx.coroutines.CoroutineScope
import net.mamoe.mirai.Bot
import net.mamoe.mirai.event.events.EventCancelledException
import net.mamoe.mirai.event.events.MessageSendEvent.FriendMessageSendEvent
import net.mamoe.mirai.event.events.MessageSendEvent.GroupMessageSendEvent
import net.mamoe.mirai.message.MessageReceipt
import net.mamoe.mirai.message.data.Message
import net.mamoe.mirai.message.data.toMessage
import kotlin.jvm.JvmSynthetic

/**
 * 好友 对象.
 * 注意: 一个 [Friend] 实例并不是独立的, 它属于一个 [Bot].
 * 它不能被直接构造. 任何时候都应从 [Bot.getFriend] 或事件中获取.
 *
 * 对于同一个 [Bot] 任何一个人的 [Friend] 实例都是单一的.
 * 它不能被直接构造. 任何时候都应从 [Bot.getFriend] 或事件中获取.
 */
@Suppress("DEPRECATION_ERROR")
abstract class Friend : QQ(), CoroutineScope {
    /**
     * 请求头像下载链接
     */
    // @MiraiExperimentalAPI
    //suspend fun queryAvatar(): AvatarLink
    /**
     * QQ 号码
     */
    abstract override val id: Long

    /**
     * 昵称
     */
    abstract override val nick: String

    /**
     * 头像下载链接
     */
    override val avatarUrl: String
        get() = "http://q1.qlogo.cn/g?b=qq&nk=$id&s=640"

    /**
     * 向这个对象发送消息.
     *
     * 单条消息最大可发送 4500 字符或 50 张图片.
     *
     * @see FriendMessageSendEvent 发送好友信息事件, cancellable
     * @see GroupMessageSendEvent  发送群消息事件. cancellable
     *
     * @throws EventCancelledException 当发送消息事件被取消时抛出
     * @throws BotIsBeingMutedException 发送群消息时若 [Bot] 被禁言抛出
     * @throws MessageTooLargeException 当消息过长时抛出
     *
     * @return 消息回执. 可进行撤回 ([MessageReceipt.recall])
     */
    @JvmSynthetic
    abstract override suspend fun sendMessage(message: Message): MessageReceipt<Friend>

    @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "VIRTUAL_MEMBER_HIDDEN", "OVERRIDE_BY_INLINE")
    @kotlin.internal.InlineOnly // purely virtual
    @JvmSynthetic
    suspend inline fun sendMessage(message: String): MessageReceipt<Friend> {
        return sendMessage(message.toMessage())
    }

    final override fun toString(): String = "Friend($id)"
}
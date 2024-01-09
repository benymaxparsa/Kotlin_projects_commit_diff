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
import net.mamoe.mirai.event.events.BeforeImageUploadEvent
import net.mamoe.mirai.event.events.EventCancelledException
import net.mamoe.mirai.event.events.ImageUploadEvent
import net.mamoe.mirai.event.events.MessageSendEvent.FriendMessageSendEvent
import net.mamoe.mirai.event.events.MessageSendEvent.GroupMessageSendEvent
import net.mamoe.mirai.message.MessageReceipt
import net.mamoe.mirai.message.data.Message
import net.mamoe.mirai.message.data.OfflineFriendImage
import net.mamoe.mirai.message.data.toMessage
import net.mamoe.mirai.utils.ExternalImage
import net.mamoe.mirai.utils.OverFileSizeMaxException
import kotlin.jvm.JvmSynthetic

/**
 * 代表一个 **用户**.
 *
 * 其子类有 [群成员][Member] 和 [好友][Friend].
 * 虽然群成员也可能是好友, 但他们仍是不同的两个类型.
 *
 * 注意: 一个 [User] 实例并不是独立的, 它属于一个 [Bot].
 *
 * 对于同一个 [Bot] 任何一个人的 [User] 实例都是单一的.
 */
abstract class User : Contact(), CoroutineScope {
    /**
     * QQ 号码
     */
    abstract override val id: Long

    /**
     * 昵称
     */
    abstract val nick: String

    /**
     * 头像下载链接
     */
    open val avatarUrl: String
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
    abstract override suspend fun sendMessage(message: Message): MessageReceipt<User>

    /**
     * @see sendMessage
     */
    @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "VIRTUAL_MEMBER_HIDDEN", "OVERRIDE_BY_INLINE")
    @kotlin.internal.InlineOnly // purely virtual
    @JvmSynthetic
    suspend inline fun sendMessage(message: String): MessageReceipt<User> {
        return sendMessage(message.toMessage())
    }

    /**
     * 上传一个图片以备发送.
     *
     * @see BeforeImageUploadEvent 图片发送前事件, cancellable
     * @see ImageUploadEvent 图片发送完成事件
     *
     * @throws EventCancelledException 当发送消息事件被取消
     * @throws OverFileSizeMaxException 当图片文件过大而被服务器拒绝上传时. (最大大小约为 20 MB)
     */
    @JvmSynthetic
    abstract override suspend fun uploadImage(image: ExternalImage): OfflineFriendImage

    abstract override fun toString(): String
}
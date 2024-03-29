/*
 * Copyright 2019-2020 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AFFERO GENERAL PUBLIC LICENSE version 3 license that can be found via the following link.
 *
 * https://github.com/mamoe/mirai/blob/master/LICENSE
 */

@file:Suppress("EXPERIMENTAL_API_USAGE", "unused")

package net.mamoe.mirai.contact

import kotlinx.coroutines.CoroutineScope
import net.mamoe.mirai.Bot
import net.mamoe.mirai.event.events.*
import net.mamoe.mirai.message.MessageReceipt
import net.mamoe.mirai.message.action.FriendNudge
import net.mamoe.mirai.message.action.Nudge
import net.mamoe.mirai.message.data.Image
import net.mamoe.mirai.message.data.Message
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.message.data.isContentEmpty
import net.mamoe.mirai.message.recall
import net.mamoe.mirai.utils.ExternalImage
import net.mamoe.mirai.utils.MiraiExperimentalAPI
import net.mamoe.mirai.utils.OverFileSizeMaxException
import net.mamoe.mirai.utils.SinceMirai
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
public abstract class User : Contact(), CoroutineScope {
    /**
     * QQ 号码
     */
    public abstract override val id: Long

    /**
     * 昵称
     */
    public abstract val nick: String

    /**
     * 备注信息
     *
     * 仅 [Bot] 与 [User] 存在好友关系的时候才可能存在备注
     *
     * [Bot] 与 [User] 没有好友关系时永远为空[字符串][String] ("")
     *
     * @see [User.remarkOrNick]
     */
    public abstract val remark: String

    /**
     * 头像下载链接
     */
    public open val avatarUrl: String
        get() = "http://q1.qlogo.cn/g?b=qq&nk=$id&s=640"

    /**
     * 向这个对象发送消息.
     *
     * 单条消息最大可发送 4500 字符或 50 张图片.
     *
     * @see UserMessagePreSendEvent 发送消息前事件
     * @see UserMessagePostSendEvent 发送消息后事件
     *
     * @throws EventCancelledException 当发送消息事件被取消时抛出
     * @throws BotIsBeingMutedException 发送群消息时若 [Bot] 被禁言抛出
     * @throws MessageTooLargeException 当消息过长时抛出
     * @throws IllegalArgumentException 当消息内容为空时抛出 (详见 [Message.isContentEmpty])
     *
     * @return 消息回执. 可进行撤回 ([MessageReceipt.recall])
     */
    @JvmSynthetic
    public abstract override suspend fun sendMessage(message: Message): MessageReceipt<User>

    /**
     * 创建一个 "戳一戳" 消息
     *
     * @see FriendNudge.sendTo 发送这个戳一戳消息
     */
    @MiraiExperimentalAPI
    @SinceMirai("1.3.0")
    public abstract fun nudge(): Nudge

    /**
     * @see sendMessage
     */
    @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "VIRTUAL_MEMBER_HIDDEN", "OVERRIDE_BY_INLINE")
    @kotlin.internal.InlineOnly
    @JvmSynthetic
    public suspend inline fun sendMessage(message: String): MessageReceipt<User> {
        return sendMessage(PlainText(message))
    }

    /**
     * 上传一个图片以备发送.
     *
     * @see Image 查看有关图片的更多信息, 如上传图片
     *
     * @see BeforeImageUploadEvent 图片发送前事件, cancellable
     * @see ImageUploadEvent 图片发送完成事件
     *
     * @throws EventCancelledException 当发送消息事件被取消
     * @throws OverFileSizeMaxException 当图片文件过大而被服务器拒绝上传时. (最大大小约为 20 MB)
     */
    @JvmSynthetic
    public abstract override suspend fun uploadImage(image: ExternalImage): Image

    public abstract override fun toString(): String
}

/**
 * 获取非空备注或昵称.
 *
 * 若 [备注][User.remark] 不为空则返回备注, 为空则返回 [User.nick]
 */
public val User.remarkOrNick: String get() = this.remark.takeIf { it.isNotEmpty() } ?: this.nick

/**
 * 获取非空备注或群名片.
 *
 * 若 [备注][User.remark] 不为空则返回备注, 为空则返回 [Member.nameCard]
 */
public val Member.remarkOrNameCard: String get() = this.remark.takeIf { it.isNotEmpty() } ?: this.nameCard

/**
 * 获取非空备注或群名片或昵称.
 *
 * 若 [备注][User.remark] 不为空则返回备注, 为空则返回 [Member.nameCardOrNick]
 */
public val Member.remarkOrNameCardOrNick: String get() = this.remark.takeIf { it.isNotEmpty() } ?: this.nameCardOrNick


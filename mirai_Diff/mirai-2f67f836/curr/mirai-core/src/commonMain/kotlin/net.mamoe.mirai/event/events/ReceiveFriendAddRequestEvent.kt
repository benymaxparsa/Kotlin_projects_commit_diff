package net.mamoe.mirai.event.events

import net.mamoe.mirai.contact.QQ
import net.mamoe.mirai.network.data.EventPacket
import net.mamoe.mirai.utils.unsafeWeakRef
import kotlin.jvm.JvmOverloads

/**
 * 陌生人请求添加机器人账号为好友
 */
class ReceiveFriendAddRequestEvent(
    _qq: QQ,
    /**
     * 验证消息
     */
    val message: String
) : EventPacket {
    val qq: QQ by _qq.unsafeWeakRef()

    /**
     * 同意这个请求
     *
     * @param remark 备注名, 不设置则需为 `null`
     */
    @JvmOverloads // TODO: 2019/12/17 协议抽象
    suspend fun approve(remark: String? = null): Unit = qq.bot.approveFriendAddRequest(qq.id, remark)
}

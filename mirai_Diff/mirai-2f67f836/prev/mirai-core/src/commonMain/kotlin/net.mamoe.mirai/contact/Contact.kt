@file:Suppress("EXPERIMENTAL_API_USAGE")

package net.mamoe.mirai.contact

import net.mamoe.mirai.Bot
import net.mamoe.mirai.message.Message
import net.mamoe.mirai.message.MessageChain
import net.mamoe.mirai.message.chain
import net.mamoe.mirai.message.singleChain
import net.mamoe.mirai.network.BotSession
import net.mamoe.mirai.utils.LockFreeLinkedList
import net.mamoe.mirai.utils.MiraiInternalAPI
import net.mamoe.mirai.utils.joinToString
import net.mamoe.mirai.withSession
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract


/**
 * 联系人. 虽然叫做联系人, 但他的子类有 [QQ] 和 [群][Group].
 *
 * @author Him188moe
 */
interface Contact {
    /**
     * 这个联系人所属 [Bot]
     */
    val bot: Bot

    /**
     * 可以是 QQ 号码或者群号码 [GroupId].
     */
    val id: UInt

    /**
     * 向这个对象发送消息.
     *
     * 速度太快会被服务器屏蔽(无响应). 在测试中不延迟地发送 6 条消息就会被屏蔽之后的数据包 1 秒左右.
     */
    suspend fun sendMessage(message: MessageChain)
}

suspend inline fun Contact.sendMessage(message: Message) = sendMessage(message.chain())

suspend inline fun Contact.sendMessage(plain: String) = sendMessage(plain.singleChain())

/**
 * 以 [BotSession] 作为接收器 (receiver) 并调用 [block], 返回 [block] 的返回值.
 * 这个方法将能帮助使用在 [BotSession] 中定义的一些扩展方法, 如 [BotSession.sendAndExpectAsync]
 */
@UseExperimental(ExperimentalContracts::class)
inline fun <R> Contact.withSession(block: BotSession.() -> R): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return bot.withSession(block)
}

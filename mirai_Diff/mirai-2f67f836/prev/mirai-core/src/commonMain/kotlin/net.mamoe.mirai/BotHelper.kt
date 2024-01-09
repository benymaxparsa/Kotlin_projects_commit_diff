@file:JvmMultifileClass
@file:JvmName("BotHelperKt")
@file:Suppress("unused", "EXPERIMENTAL_API_USAGE", "NOTHING_TO_INLINE")

package net.mamoe.mirai

import net.mamoe.mirai.contact.*
import net.mamoe.mirai.network.BotNetworkHandler
import net.mamoe.mirai.network.BotSession
import net.mamoe.mirai.network.protocol.timpc.TIMBotNetworkHandler
import net.mamoe.mirai.network.protocol.timpc.packet.OutgoingPacket
import net.mamoe.mirai.network.protocol.timpc.packet.login.LoginResult
import net.mamoe.mirai.network.protocol.timpc.packet.login.requireSuccess
import net.mamoe.mirai.utils.BotConfiguration
import net.mamoe.mirai.utils.internal.PositiveNumbers
import net.mamoe.mirai.utils.internal.coerceAtLeastOrFail
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.jvm.JvmMultifileClass
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads

/*
 * 在 [Bot] 中的方法的捷径
 */

//Contacts
suspend inline fun Bot.getGroup(id: UInt): Group = this.getGroup(GroupId(id))


/**
 * 以 [BotSession] 作为接收器 (receiver) 并调用 [block], 返回 [block] 的返回值.
 * 这个方法将能帮助使用在 [BotSession] 中定义的一些扩展方法, 如 [BotSession.sendAndExpectAsync]
 */
@UseExperimental(ExperimentalContracts::class)
inline fun <R> Bot.withSession(block: BotSession.() -> R): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return with(this.network.session) { block() }
}

/**
 * 发送数据包
 * @throws IllegalStateException 当 [BotNetworkHandler.socket] 未开启时
 */
internal suspend inline fun Bot.sendPacket(packet: OutgoingPacket) =
    (this.network as TIMBotNetworkHandler).socket.sendPacket(packet)

/**
 * 使用在默认配置基础上修改的配置进行登录
 */
@UseExperimental(ExperimentalContracts::class)
suspend inline fun Bot.login(configuration: BotConfiguration.() -> Unit): LoginResult {
    contract {
        callsInPlace(configuration, InvocationKind.EXACTLY_ONCE)
    }
    return this.reinitializeNetworkHandler(BotConfiguration().apply(configuration))
}

/**
 * 使用默认的配置 ([BotConfiguration.Default]) 登录, 返回登录结果
 */
suspend inline fun Bot.login(): LoginResult = this.reinitializeNetworkHandler(BotConfiguration.Default)

/**
 * 使用默认的配置 ([BotConfiguration.Default]) 登录, 返回 [this]
 */
suspend inline fun Bot.alsoLogin(): Bot = apply { login().requireSuccess() }

/**
 * 使用在默认配置基础上修改的配置进行登录, 返回 [this]
 */
@UseExperimental(ExperimentalContracts::class)
suspend inline fun Bot.alsoLogin(configuration: BotConfiguration.() -> Unit): Bot {
    contract {
        callsInPlace(configuration, InvocationKind.EXACTLY_ONCE)
    }
    this.reinitializeNetworkHandler(BotConfiguration().apply(configuration)).requireSuccess()
    return this
}

/**
 * 使用默认的配置 ([BotConfiguration.Default]) 登录, 返回 [this]
 */
suspend inline fun Bot.alsoLogin(message: String): Bot {
    return this.apply {
        login().requireSuccess { message } // requireSuccess is inline, so no performance waste
    }
}

/**
 * 取得机器人的 QQ 号
 */
inline val Bot.qqAccount: UInt get() = this.account.id
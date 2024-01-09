/*
 * Copyright 2019-2020 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AFFERO GENERAL PUBLIC LICENSE version 3 license that can be found via the following link.
 *
 * https://github.com/mamoe/mirai/blob/master/LICENSE
 */

@file:Suppress("NOTHING_TO_INLINE")

package test

import kotlinx.io.core.ByteReadPacket
import kotlinx.io.core.Input
import kotlinx.io.core.readAvailable
import kotlinx.io.core.use
import kotlinx.io.pool.useInstance
import net.mamoe.mirai.internal.utils.ByteArrayPool
import net.mamoe.mirai.internal.utils.toReadPacket
import net.mamoe.mirai.internal.utils.toUHexString
import net.mamoe.mirai.utils.DefaultLogger
import net.mamoe.mirai.utils.MiraiLoggerWithSwitch
import net.mamoe.mirai.utils.withSwitch
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract


val DebugLogger: MiraiLoggerWithSwitch = DefaultLogger("Packet Debug").withSwitch(true)

internal inline fun ByteArray.debugPrintThis(name: String): ByteArray {
    DebugLogger.debug(name + "=" + this.toUHexString())
    return this
}

internal inline fun <R> Input.debugIfFail(
    name: String = "",
    onFail: (ByteArray) -> ByteReadPacket = { it.toReadPacket() },
    block: ByteReadPacket.() -> R
): R {

    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
        callsInPlace(onFail, InvocationKind.UNKNOWN)
    }
    ByteArrayPool.useInstance {
        val count = this.readAvailable(it)
        try {
            return it.toReadPacket(0, count).use(block)
        } catch (e: Throwable) {
            onFail(it.take(count).toByteArray()).readAvailable(it)
            DebugLogger.debug("Error in ByteReadPacket $name=" + it.toUHexString(offset = 0, length = count))
            throw e
        }
    }
}
@file:Suppress("EXPERIMENTAL_UNSIGNED_LITERALS", "EXPERIMENTAL_API_USAGE", "FunctionName")

package net.mamoe.mirai.network.protocol.tim.packet.login

import kotlinx.io.core.ByteReadPacket
import kotlinx.io.core.discardExact
import net.mamoe.mirai.network.BotSession
import net.mamoe.mirai.network.protocol.tim.TIMProtocol
import net.mamoe.mirai.network.protocol.tim.packet.*
import net.mamoe.mirai.network.qqAccount
import net.mamoe.mirai.utils.io.*

fun BotSession.RequestSKeyPacket(): OutgoingPacket = RequestSKeyPacket(qqAccount, sessionKey)

/**
 * 请求 `SKey`
 * SKey 用于 http api
 */
@AnnotatedId(KnownPacketId.S_KEY)
object RequestSKeyPacket : OutgoingPacketBuilder {
    operator fun invoke(
        bot: UInt,
        sessionKey: ByteArray
    ): OutgoingPacket = buildOutgoingPacket {
        writeQQ(bot)
        writeHex(TIMProtocol.fixVer2)
        encryptAndWrite(sessionKey) {
            writeHex("33 00 05 00 08 74 2E 71 71 2E 63 6F 6D 00 0A 71 75 6E 2E 71 71 2E 63 6F 6D 00 0C 71 7A 6F 6E 65 2E 71 71 2E 63 6F 6D 00 0C 6A 75 62 61 6F 2E 71 71 2E 63 6F 6D 00 09 6B 65 2E 71 71 2E 63 6F 6D")
        }
    }

    @AnnotatedId(KnownPacketId.S_KEY)
    class Response(input: ByteReadPacket) : ResponsePacket(input) {
        lateinit var sKey: String

        override fun decode() = with(input) {
            discardExact(4)
            //debugDiscardExact(2)
            sKey = this.readString(10)//16??
            DebugLogger.warning("SKey=$sKey")
            DebugLogger.warning("SKey 包后面${this.readRemainingBytes().toUHexString()}")
        }
    }
}
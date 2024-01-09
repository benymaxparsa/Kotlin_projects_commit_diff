@file:Suppress("EXPERIMENTAL_API_USAGE", "EXPERIMENTAL_UNSIGNED_LITERALS")

package net.mamoe.mirai.network.protocol.tim.packet

import kotlinx.io.core.BytePacketBuilder
import kotlinx.io.core.writeFully
import net.mamoe.mirai.utils.*
import java.awt.image.BufferedImage

actual typealias PlatformImage = BufferedImage

actual typealias ClientTryGetImageIDPacket = ClientTryGetImageIDPacketJvm

/**
 * 请求上传图片. 将发送图片的 md5, size.
 * 服务器返回以下之一:
 * - 服务器已经存有这个图片 [ServerTryGetImageIDFailedPacket]
 * - 服务器未存有, 返回一个 key 用于客户端上传 [ServerTryGetImageIDSuccessPacket]
 *
 * @author Him188moe
 */
@PacketId("03 88")
class ClientTryGetImageIDPacketJvm(
        private val botNumber: Long,
        private val sessionKey: ByteArray,
        private val groupNumberOrQQNumber: Long,
        private val image: PlatformImage
) : ClientPacket() {
    override fun encode(builder: BytePacketBuilder) = with(builder) {
        this.writeRandom(2)

        this.writeQQ(botNumber)
        this.writeHex("04 00 00 00 01 01 01 00 00 68 20 00 00 00 00 00 00 00 00")

        val byteArray = image.toByteArray()
        this.encryptAndWrite(sessionKey) {
            writeZero(3)

            writeHex("07 00")

            writeZero(2)

            writeHex("5B")//原5E
            writeHex("08")
            writeHex("01 12 03 98 01 01 10 01")

            writeHex("1A")
            writeHex("57")//原5A

            writeHex("08")
            writeUVarInt(groupNumberOrQQNumber)//FB D2 D8 94

            writeByte(0x02)
            writeHex("10")
            writeUVarInt(botNumber)//A2 FF 8C F0

            writeHex("18 00")

            writeHex("22")
            writeHex("10")
            writeFully(md5(byteArray))

            writeHex("28")
            writeUVarInt(byteArray.size.toUInt())//E2 0D

            writeHex("32")
            writeHex("1A")
            //28 00 5A 00 53 00 41 00 58 00 40 00 57 00 4B 00 52 00 4A 00 5A 00 31 00 7E 00 38 01 48 01 50 38 58 34 60 04 6A 05 32 36 39 33 33 70 00 78 03 80 01 00


            writeHex("37 00 4D 00 32 00 25 00 4C 00 31 00 56 00 32 00 7B 00 39 00 30 00 29 00 52 00")

            writeHex("38 01")

            writeHex("48 01")

            writeHex("50")
            writeUVarInt(image.width.toUInt())
            writeHex("58")
            writeUVarInt(image.height.toUInt())

            writeHex("60 04")

            writeHex("6A")
            writeHex("05")
            writeHex("32 36 36 35 36")

            writeHex("70 00")

            writeHex("78 03")

            writeHex("80 01")

            writeHex("00")
        }
    }
}
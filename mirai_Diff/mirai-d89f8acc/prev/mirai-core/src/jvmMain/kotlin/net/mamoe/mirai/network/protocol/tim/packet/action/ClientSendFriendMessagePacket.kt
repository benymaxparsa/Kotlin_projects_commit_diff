package net.mamoe.mirai.network.protocol.tim.packet.action

import net.mamoe.mirai.message.MessageChain
import net.mamoe.mirai.message.internal.toByteArray
import net.mamoe.mirai.network.protocol.tim.TIMProtocol
import net.mamoe.mirai.network.protocol.tim.packet.*
import net.mamoe.mirai.utils.dataEncode
import java.io.DataInputStream

/**
 * @author Him188moe
 */
@PacketId("00 CD")
class ClientSendFriendMessagePacket(
        private val botQQ: Long,
        private val targetQQ: Long,
        private val sessionKey: ByteArray,
        private val message: MessageChain
) : ClientPacket() {
    override fun encode() {
        this.writeRandom(2)//part of packet id

        this.writeQQ(botQQ)
        this.writeHex(TIMProtocol.fixVer2)

        this.encryptAndWrite(sessionKey) {
            writeQQ(botQQ)
            writeQQ(targetQQ)
            writeHex("00 00 00 08 00 01 00 04 00 00 00 00")
            writeHex("37 0F")//TIM最新: 38 03
            writeQQ(botQQ)
            writeQQ(targetQQ)
            write(md5(dataEncode { md5Key -> md5Key.writeQQ(targetQQ); md5Key.write(sessionKey) }))
            writeHex("00 0B")
            writeRandom(2)
            writeTime()
            writeHex("00 00" +
                    "00 00 00 00")

            //消息过多要分包发送
            //如果只有一个
            writeByte(0x01)
            writeByte(0)//第几个包
            writeByte(0)
            //如果大于一个,
            //writeByte(0x02)//数量
            //writeByte(0)//第几个包
            //writeByte(0x91)//why?

            writeHex("00 01 4D 53 47 00 00 00 00 00")
            writeTime()
            writeRandom(4)
            writeHex("00 00 00 00 09 00 86")//TIM最新 0C 00 86
            writeHex(TIMProtocol.messageConst1)//... 85 E9 BB 91
            writeZero(2)

            write(message.toByteArray())

            /*
                //Plain text
                val bytes = message.toByteArray()
                it.writeByte(0x01)
                it.writeShort(bytes.size + 3)
                it.writeByte(0x01)
                it.writeShort(bytes.size)
                it.write(bytes)*/
        }
    }
}

@PacketId("00 CD")
class ServerSendFriendMessageResponsePacket(input: DataInputStream) : ServerPacket(input)
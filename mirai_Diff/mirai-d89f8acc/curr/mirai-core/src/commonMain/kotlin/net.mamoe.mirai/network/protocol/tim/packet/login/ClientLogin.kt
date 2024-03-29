package net.mamoe.mirai.network.protocol.tim.packet.login

import kotlinx.io.core.BytePacketBuilder
import kotlinx.io.core.IoBuffer
import kotlinx.io.core.writeFully
import net.mamoe.mirai.network.protocol.tim.TIMProtocol
import net.mamoe.mirai.network.protocol.tim.packet.ClientPacket
import net.mamoe.mirai.network.protocol.tim.packet.PacketId
import net.mamoe.mirai.utils.*


/**
 * Password submission (0836_622)
 */
@PacketId("08 36 31 03")
@Tested
class ClientPasswordSubmissionPacket(
        private val qq: Long,
        private val password: String,
        private val loginTime: Int,
        private val loginIP: String,
        private val privateKey: ByteArray,//16 random by client
        private val token0825: ByteArray,//56 from server
        private val randomDeviceName: Boolean
) : ClientPacket() {

    override fun encode(builder: BytePacketBuilder) = with(builder) {
        this.writeQQ(qq)
        this.writeHex(TIMProtocol.passwordSubmissionTLV1)

        this.writeShort(25)
        this.writeHex(TIMProtocol.publicKey)//25

        this.writeHex("00 00 00 10")
        this.writeHex(TIMProtocol.key0836)

        //TODO shareKey 极大可能为 publicKey, key0836 计算得到
        this.encryptAndWrite(TIMProtocol.shareKey.hexToBytes()) {
            writePart1(qq, password, loginTime, loginIP, privateKey, token0825, randomDeviceName)
            writePart2()
        }
    }
}

//实际上这些包性质都是一样的. 31 04 仅是一个序列 id, 可随机
//但为简化处理, 特固定这个 id

@PacketId("08 36 31 04")
class ClientLoginResendPacket3104(qq: Long, password: String, loginTime: Int, loginIP: String, privateKey: ByteArray, token0825: ByteArray, token00BA: ByteArray, randomDeviceName: Boolean, tlv0006: IoBuffer? = null) : ClientLoginResendPacket(qq, password, loginTime, loginIP, privateKey, token0825, token00BA, randomDeviceName, tlv0006)

@PacketId("08 36 31 05")
class ClientLoginResendPacket3105(qq: Long, password: String, loginTime: Int, loginIP: String, privateKey: ByteArray, token0825: ByteArray, token00BA: ByteArray, randomDeviceName: Boolean, tlv0006: IoBuffer? = null) : ClientLoginResendPacket(qq, password, loginTime, loginIP, privateKey, token0825, token00BA, randomDeviceName, tlv0006)

@PacketId("08 36 31 06")
class ClientLoginResendPacket3106(qq: Long, password: String, loginTime: Int, loginIP: String, privateKey: ByteArray, token0825: ByteArray, token00BA: ByteArray, randomDeviceName: Boolean, tlv0006: IoBuffer? = null) : ClientLoginResendPacket(qq, password, loginTime, loginIP, privateKey, token0825, token00BA, randomDeviceName, tlv0006)


open class ClientLoginResendPacket constructor(
        private val qq: Long,
        private val password: String,
        private val loginTime: Int,
        private val loginIP: String,
        private val privateKey: ByteArray,
        private val token0825: ByteArray,
        private val token00BA: ByteArray,
        private val randomDeviceName: Boolean = false,
        private val tlv0006: IoBuffer? = null
) : ClientPacket() {
    override fun encode(builder: BytePacketBuilder) = with(builder) {
        this.writeQQ(qq)
        this.writeHex(TIMProtocol.passwordSubmissionTLV1)

        this.writeShort(25)
        this.writeHex(TIMProtocol.publicKey)//25

        this.writeHex("00 00 00 10")//=16
        this.writeHex(TIMProtocol.key0836)//16

        this.encryptAndWrite(TIMProtocol.shareKey.hexToBytes()) {
            writePart1(qq, password, loginTime, loginIP, privateKey, token0825, randomDeviceName, tlv0006)

            writeHex("01 10")
            writeHex("00 3C")
            writeHex("00 01")

            writeHex("00 38")
            writeFully(token00BA)

            writePart2()
        }
    }
}


private fun BytePacketBuilder.writePart1(
        qq: Long,
        password: String,
        loginTime: Int,
        loginIP: String,
        privateKey: ByteArray,
        token0825: ByteArray,
        randomDeviceName: Boolean,
        tlv0006: IoBuffer? = null
) {

    //this.writeInt(System.currentTimeMillis().toInt())
    this.writeHex("01 12")//tag
    this.writeHex("00 38")//length
    this.writeFully(token0825)//length
    this.writeHex("03 0F")//tag
    this.writeDeviceName(randomDeviceName)

    this.writeHex("00 05 00 06 00 02")
    this.writeQQ(qq)
    this.writeHex("00 06")//tag
    this.writeHex("00 78")//length
    if (tlv0006 != null) {
        MiraiLogger.logDebug("tlv0006!=null")
        this.writeFully(tlv0006)
    } else {
        MiraiLogger.logDebug("tlv0006==null")
        this.writeTLV0006(qq, password, loginTime, loginIP, privateKey)
    }
    //fix
    this.writeHex(TIMProtocol.passwordSubmissionTLV2)
    this.writeHex("00 1A")//tag
    this.writeHex("00 40")//length
    this.writeFully(TEA.encrypt(TIMProtocol.passwordSubmissionTLV2.hexToBytes(), privateKey))
    this.writeHex(TIMProtocol.constantData1)
    this.writeHex(TIMProtocol.constantData2)
    this.writeQQ(qq)
    this.writeZero(4)

    this.writeHex("01 03")//tag
    this.writeHex("00 14")//length

    this.writeHex("00 01")//tag
    this.writeHex("00 10")//length
    this.writeHex("60 C9 5D A7 45 70 04 7F 21 7D 84 50 5C 66 A5 C6")//key
}


private fun BytePacketBuilder.writePart2() {

    this.writeHex("03 12")//tag
    this.writeHex("00 05")//length
    this.writeHex("01 00 00 00 01")//value

    this.writeHex("05 08")//tag
    this.writeHex("00 05")//length
    this.writeHex("01 00 00 00 00")//value

    this.writeHex("03 13")//tag
    this.writeHex("00 19")//length
    this.writeHex("01")//value

    this.writeHex("01 02")//tag
    this.writeHex("00 10")//length
    this.writeHex("04 EA 78 D1 A4 FF CD CC 7C B8 D4 12 7D BB 03 AA")//key
    this.writeZero(3)
    this.writeByte(0)//maybe 00, 0F, 1F

    this.writeHex("01 02")//tag
    this.writeHex("00 62")//length
    this.writeHex("00 01")//word?
    this.writeHex("04 EB B7 C1 86 F9 08 96 ED 56 84 AB 50 85 2E 48")//key
    this.writeHex("00 38")//length
    //value
    this.writeHex("E9 AA 2B 4D 26 4C 76 18 FE 59 D5 A9 82 6A 0C 04 B4 49 50 D7 9B B1 FE 5D 97 54 8D 82 F3 22 C2 48 B9 C9 22 69 CA 78 AD 3E 2D E9 C9 DF A8 9E 7D 8C 8D 6B DF 4C D7 34 D0 D3")

    this.writeHex("00 14")
    this.writeCRC32()
}



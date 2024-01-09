package net.mamoe.mirai.network.protocol.tim.packet

import kotlinx.io.core.ByteReadPacket
import kotlinx.io.core.IoBuffer
import net.mamoe.mirai.utils.decryptBy


/**
 * 会话密匙
 */
inline class SessionKey(override val value: ByteArray) : DecrypterByteArray {
    companion object Type : DecrypterType<SessionKey>
}

/**
 * [ByteArray] 解密器
 */
interface DecrypterByteArray : Decrypter {
    val value: ByteArray
    override fun decrypt(packet: ByteReadPacket): ByteReadPacket = packet.decryptBy(value)
}

/**
 * [IoBuffer] 解密器
 */
interface DecrypterIoBuffer : Decrypter {
    val value: IoBuffer
    override fun decrypt(packet: ByteReadPacket): ByteReadPacket = packet.decryptBy(value)
}

/**
 * 连接在一起的解密器
 */
inline class LinkedDecrypter(inline val block: (ByteReadPacket) -> ByteReadPacket) : Decrypter {
    override fun decrypt(packet: ByteReadPacket): ByteReadPacket = block(packet)
}

object NoDecrypter : Decrypter, DecrypterType<NoDecrypter> {
    override fun decrypt(packet: ByteReadPacket): ByteReadPacket = packet
}

/**
 * 解密器
 */
interface Decrypter {
    fun decrypt(packet: ByteReadPacket): ByteReadPacket
    /**
     * 连接后将会先用 this 解密, 再用 [another] 解密
     */
    operator fun plus(another: Decrypter): Decrypter = LinkedDecrypter { another.decrypt(this.decrypt(it)) }
}

interface DecrypterType<D : Decrypter>
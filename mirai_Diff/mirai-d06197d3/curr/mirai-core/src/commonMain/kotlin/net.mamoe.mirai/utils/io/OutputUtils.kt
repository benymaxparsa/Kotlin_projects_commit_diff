@file:Suppress("EXPERIMENTAL_API_USAGE")

package net.mamoe.mirai.utils

import kotlinx.io.core.*
import net.mamoe.mirai.network.protocol.tim.TIMProtocol
import kotlin.random.Random
import kotlin.random.nextInt


fun BytePacketBuilder.writeZero(count: Int) = repeat(count) { this.writeByte(0) }

fun BytePacketBuilder.writeRandom(length: Int) = repeat(length) { this.writeByte(Random.Default.nextInt(255).toByte()) }

fun BytePacketBuilder.writeQQ(qq: Long) = this.writeUInt(qq.toUInt())
fun BytePacketBuilder.writeQQ(qq: UInt) = this.writeUInt(qq)

fun BytePacketBuilder.writeGroup(groupIdOrGroupNumber: Long) = this.writeUInt(groupIdOrGroupNumber.toUInt())
fun BytePacketBuilder.writeGroup(groupIdOrGroupNumber: UInt) = this.writeUInt(groupIdOrGroupNumber)

fun BytePacketBuilder.writeShortLVByteArray(byteArray: ByteArray) {
    this.writeShort(byteArray.size.toShort())
    this.writeFully(byteArray)
}


private fun <N : Comparable<N>> N.coerceAtMostOrFail(maximumValue: N): N =
        if (this > maximumValue) error("value is greater than its expected maximum value $maximumValue")
        else this

fun BytePacketBuilder.writeShortLVPacket(tag: UByte? = null, lengthOffset: ((Long) -> Long)? = null, builder: BytePacketBuilder.() -> Unit) = with(BytePacketBuilder().apply(builder).build()) {
    if (tag != null) {
        writeUByte(tag)
    }
    writeUShort((lengthOffset?.invoke(remaining) ?: remaining).coerceAtMostOrFail(0xFFFFL).toUShort())
    writePacket(this)
    this.release()
}

fun BytePacketBuilder.writeUVarintLVPacket(tag: UByte? = null, lengthOffset: ((Long) -> Long)? = null, builder: BytePacketBuilder.() -> Unit) = with(BytePacketBuilder().apply(builder).build()) {
    if (tag != null) {
        writeUByte(tag)
    }
    writeUVarInt((lengthOffset?.invoke(remaining) ?: remaining).coerceAtMostOrFail(0xFFFFL))
    writePacket(this)
    this.release()
}

@Suppress("DEPRECATION")
fun BytePacketBuilder.writeShortLVString(str: String) = this.writeShortLVByteArray(str.toByteArray())

@Suppress("DEPRECATION")
fun BytePacketBuilder.writeLVHex(hex: String) = this.writeShortLVByteArray(hex.hexToBytes())

fun BytePacketBuilder.writeIP(ip: String) = writeFully(ip.trim().split(".").map { it.toUByte() }.toUByteArray())

fun BytePacketBuilder.writeTime() = this.writeInt(currentTime.toInt())

fun BytePacketBuilder.writeHex(uHex: String) = this.writeFully(uHex.hexToUBytes())

fun BytePacketBuilder.writeTLV(tag: UByte, values: UByteArray) {
    writeUByte(tag)
    writeVarInt(values.size)
    writeFully(values)
}

fun BytePacketBuilder.writeTLV(tag: UByte, values: ByteArray) {
    writeUByte(tag)
    writeVarInt(values.size)
    writeFully(values)
}

fun BytePacketBuilder.writeTHex(tag: UByte, uHex: String) {
    this.writeUByte(tag)
    this.writeFully(uHex.hexToUBytes())
}

fun BytePacketBuilder.writeTV(tagValue: UShort) = writeUShort(tagValue)

fun BytePacketBuilder.writeTUbyte(tag: UByte, value: UByte) {
    this.writeUByte(tag)
    this.writeUByte(value)
}

fun BytePacketBuilder.writeTUVarint(tag: UByte, value: UInt) {
    this.writeUByte(tag)
    this.writeUVarInt(value)
}

fun BytePacketBuilder.writeTByteArray(tag: UByte, value: ByteArray) {
    this.writeUByte(tag)
    this.writeFully(value)
}

fun BytePacketBuilder.encryptAndWrite(key: IoBuffer, encoder: BytePacketBuilder.() -> Unit) = encryptAndWrite(key.readBytes(), encoder)
fun BytePacketBuilder.encryptAndWrite(key: ByteArray, encoder: BytePacketBuilder.() -> Unit) = writeFully(TEA.encrypt(BytePacketBuilder().apply(encoder).use { it.build().readBytes() }, key))
fun BytePacketBuilder.encryptAndWrite(keyHex: String, encoder: BytePacketBuilder.() -> Unit) = encryptAndWrite(keyHex.hexToBytes(), encoder)

fun BytePacketBuilder.writeTLV0006(qq: UInt, password: String, loginTime: Int, loginIP: String, privateKey: ByteArray) {
    val firstMD5 = md5(password)
    val secondMD5 = md5(firstMD5 + byteArrayOf(0, 0, 0, 0) + qq.toUInt().toByteArray())

    this.encryptAndWrite(secondMD5) {
        writeRandom(4)
        writeHex("00 02")
        writeQQ(qq)
        writeHex(TIMProtocol.constantData2)
        writeHex("00 00 01")

        writeFully(firstMD5)
        writeInt(loginTime)
        writeByte(0)
        writeZero(4 * 3)
        writeIP(loginIP)
        writeZero(8)
        writeHex("00 10")//这两个hex是passwordSubmissionTLV2的末尾
        writeHex("15 74 C4 89 85 7A 19 F5 5E A9 C9 A3 5E 8A 5A 9B")//16
        writeFully(privateKey)
    }
}

@Tested
fun BytePacketBuilder.writeDeviceName(random: Boolean) {
    val deviceName: String = if (random) {
        "DESKTOP-" + String(ByteArray(7) {
            (if (Random.nextBoolean()) Random.nextInt('A'.toInt()..'Z'.toInt())
            else Random.nextInt('1'.toInt()..'9'.toInt())).toByte()
        })
    } else {
        deviceName
    }
    this.writeShort((deviceName.length + 2).toShort())
    this.writeShort(deviceName.length.toShort())
    this.writeStringUtf8(deviceName)//TODO TEST?
}
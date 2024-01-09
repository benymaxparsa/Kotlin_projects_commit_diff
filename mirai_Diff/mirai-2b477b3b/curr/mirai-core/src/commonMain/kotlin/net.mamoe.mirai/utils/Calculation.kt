package net.mamoe.mirai.utils

import kotlinx.io.core.BytePacketBuilder
import kotlinx.io.core.toByteArray
import kotlinx.io.core.writeFully
import net.mamoe.mirai.utils.io.getRandomByteArray

private const val GTK_BASE_VALUE: Int = 5381

@ExperimentalStdlibApi
internal fun getGTK(sKey: String): Int {
    var value = GTK_BASE_VALUE
    for (c in sKey.toCharArray()) {
        value += (value shl 5) + c.toInt()
    }

    value = value and Int.MAX_VALUE
    return value
}

@Tested
@PublishedApi
internal fun BytePacketBuilder.writeCRC32() = writeCRC32(getRandomByteArray(16))

@PublishedApi
internal fun BytePacketBuilder.writeCRC32(key: ByteArray) {
    writeFully(key)//key
    writeInt(crc32(key))
}

@PublishedApi
internal fun md5(str: String): ByteArray = md5(str.toByteArray())
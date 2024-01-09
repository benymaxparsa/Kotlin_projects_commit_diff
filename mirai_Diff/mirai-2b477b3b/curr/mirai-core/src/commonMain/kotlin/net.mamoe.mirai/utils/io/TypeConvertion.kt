@file:Suppress("EXPERIMENTAL_UNSIGNED_LITERALS", "EXPERIMENTAL_API_USAGE", "unused")

package net.mamoe.mirai.utils.io

import kotlinx.io.core.IoBuffer
import kotlinx.io.core.writeFully
import kotlinx.io.pool.ObjectPool
import kotlin.jvm.Synchronized
import kotlin.random.Random
import kotlin.random.nextInt


/*
 * 类型转换 Utils.
 * 这些函数为内部函数, 可能会改变
 */

/**
 * 255 -> 00 00 00 FF
 */
fun Int.toByteArray(): ByteArray = byteArrayOf(
    (shr(24) and 0xFF).toByte(),
    (shr(16) and 0xFF).toByte(),
    (shr(8) and 0xFF).toByte(),
    (shr(0) and 0xFF).toByte()
)

/**
 * 转 [ByteArray] 后再转 hex
 */
fun Int.toUHexString(separator: String = " "): String = this.toByteArray().toUHexString(separator)

/**
 * 255 -> 00 FF
 */
fun UShort.toByteArray(): ByteArray = with(toUInt()) {
    byteArrayOf(
        (shr(8) and 255u).toByte(),
        (shr(0) and 255u).toByte()
    )
}

/**
 * 转 [ByteArray] 后再转 hex
 */
fun UShort.toUHexString(separator: String = " "): String = this.toByteArray().toUHexString(separator)

/**
 * 255u -> 00 00 00 FF
 */
fun UInt.toByteArray(): ByteArray = byteArrayOf(
    (shr(24) and 255u).toByte(),
    (shr(16) and 255u).toByte(),
    (shr(8) and 255u).toByte(),
    (shr(0) and 255u).toByte()
)

/**
 * 转 [ByteArray] 后再转 hex
 */
fun UInt.toUHexString(separator: String = " "): String = this.toByteArray().toUHexString(separator)

/**
 * 转无符号十六进制表示, 并补充首位 `0`.
 * 转换结果示例: `FF`, `0E`
 */
fun Byte.toUHexString(): String = this.toUByte().toString(16).toUpperCase().let {
    if (it.length == 1) "0$it"
    else it
}

/**
 * 将无符号 Hex 转为 [ByteArray], 有根据 hex 的 [hashCode] 建立的缓存.
 */
fun String.hexToBytes(withCache: Boolean = true): ByteArray =
    if (withCache) HexCache.getCacheOrConvert(this)
    else this.split(" ")
        .filterNot { it.isEmpty() }
        .map { s -> s.toUByte(16).toByte() }
        .toByteArray()

/**
 * 将无符号 Hex 转为 [UByteArray], 有根据 hex 的 [hashCode] 建立的缓存.
 */
fun String.hexToUBytes(withCache: Boolean = true): UByteArray =
    if (withCache) HexCache.getUCacheOrConvert(this)
    else this.split(" ")
        .filterNot { it.isEmpty() }
        .map { s -> s.toUByte(16) }
        .toUByteArray()

/**
 * 生成长度为 [length], 元素为随机 `0..255` 的 [ByteArray]
 */
@PublishedApi
internal fun getRandomByteArray(length: Int): ByteArray = ByteArray(length) { Random.nextInt(0..255).toByte() }

/**
 * 随机生成长度为 [length] 的 [String].
 */
fun getRandomString(length: Int): String = getRandomString(length, 'a'..'z', 'A'..'Z', '0'..'9')

/**
 * 根据所给 [charRange] 随机生成长度为 [length] 的 [String].
 */
fun getRandomString(length: Int, charRange: CharRange): String = String(CharArray(length) { charRange.random() })

/**
 * 根据所给 [charRanges] 随机生成长度为 [length] 的 [String].
 */
fun getRandomString(length: Int, vararg charRanges: CharRange): String = String(CharArray(length) { charRanges[Random.Default.nextInt(0..charRanges.lastIndex)].random() })

/**
 * 将 [this] 前 4 个 [Byte] 的 bits 合并为一个 [Int]
 *
 * 详细解释:
 * 一个 [Byte] 有 8 bits
 * 一个 [Int] 有 32 bits
 * 本函数将 4 个 [Byte] 的 bits 连接得到 [Int]
 */
fun ByteArray.toUInt(): UInt = this[0].toUInt().and(255u).shl(24) + this[1].toUInt().and(255u).shl(16) + this[2].toUInt().and(255u).shl(8) + this[3].toUInt().and(255u).shl(0)

/**
 * 从 [IoBuffer.Pool] [borrow][ObjectPool.borrow] 一个 [IoBuffer] 然后将 [this] 写入.
 * 注意回收 ([ObjectPool.recycle])
 */
fun ByteArray.toIoBuffer(): IoBuffer = IoBuffer.Pool.borrow().let { it.writeFully(this); it }

/**
 * Hex 转换 [ByteArray] 和 [UByteArray] 缓存.
 * 为 [net.mamoe.mirai.network.protocol.tim.TIMProtocol] 的 hex 常量使用
 */
internal object HexCache {
    private val hexToByteArrayCacheMap: MutableMap<Int, ByteArray> = mutableMapOf()

    @Synchronized
    internal fun getCacheOrConvert(hex: String): ByteArray = hex.hashCode().let { id ->
        if (hexToByteArrayCacheMap.containsKey(id)) {
            return hexToByteArrayCacheMap[id]!!
        } else {
            hex.hexToBytes(withCache = false).let {
                hexToByteArrayCacheMap[id] = it
                return it
            }
        }
    }

    private val hexToUByteArrayCacheMap: MutableMap<Int, UByteArray> = mutableMapOf()

    @Synchronized
    internal fun getUCacheOrConvert(hex: String): UByteArray = hex.hashCode().let { id ->
        if (hexToUByteArrayCacheMap.containsKey(id)) {
            return hexToUByteArrayCacheMap[id]!!
        } else {
            hex.hexToUBytes(withCache = false).let {
                hexToUByteArrayCacheMap[id] = it
                return it
            }
        }
    }
}
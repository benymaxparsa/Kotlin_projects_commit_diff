package net.mamoe.mirai.utils

import kotlinx.io.core.ByteReadPacket
import kotlinx.io.core.IoBuffer
import kotlinx.io.pool.useInstance
import net.mamoe.mirai.network.protocol.timpc.packet.Decrypter
import net.mamoe.mirai.network.protocol.timpc.packet.DecrypterByteArray
import net.mamoe.mirai.utils.io.ByteArrayPool
import net.mamoe.mirai.utils.io.toByteArray
import net.mamoe.mirai.utils.io.toUHexString
import kotlin.experimental.and
import kotlin.experimental.xor
import kotlin.jvm.JvmStatic
import kotlin.random.Random


/**
 * 解密错误
 */
internal class DecryptionFailedException : Exception {
    constructor() : super()
    constructor(message: String?) : super(message)
}


// region encrypt

/**
 * 使用 [key] 解密 [this]
 *
 * @param key 长度至少为 16
 * @throws DecryptionFailedException 解密错误时
 */
internal fun ByteArray.encryptBy(key: ByteArray, length: Int = this.size): ByteArray = TEA.encrypt(this, key, sourceLength = length)

internal fun ByteArray.encryptBy(key: DecrypterByteArray, length: Int = this.size): ByteArray = TEA.encrypt(this, key.value, sourceLength = length)

/**
 * 在 [ByteArrayPool] 缓存 [this], 然后使用 [key] 加密.
 *
 * @param key 长度至少为 16
 * @consumer 由于缓存需要被回收, 需在方法内执行解密后明文的消耗过程
 * @throws DecryptionFailedException 解密错误时
 */
internal inline fun ByteReadPacket.encryptBy(key: ByteArray, offset: Int = 0, length: Int = remaining.toInt() - offset, consumer: (ByteArray) -> Unit) {
    ByteArrayPool.useInstance {
        this.readFully(it, offset, length)
        consumer(it.encryptBy(key, length = length))
    }
}

// endregion


// region decrypt

/**
 * 使用 [key] 解密 [this].
 *
 * @param key 固定长度 16
 * @throws DecryptionFailedException 解密错误时
 */
internal fun ByteArray.decryptBy(key: ByteArray, length: Int = this.size): ByteArray =
    TEA.decrypt(checkDataLengthAndReturnSelf(length), key, sourceLength = length)

/**
 * 使用 [key] 解密 [this].
 * [key] 将会被读取掉前 16 个字节
 * 将会使用 [ByteArrayPool] 来缓存 [key].
 *
 * @param key 长度至少为 16
 * @throws DecryptionFailedException 解密错误时
 */
internal fun ByteArray.decryptBy(key: IoBuffer, length: Int = this.size): ByteArray {
    checkDataLengthAndReturnSelf(length)
    return ByteArrayPool.useInstance { keyBuffer ->
        key.readFully(keyBuffer, 0, key.readRemaining)
        TEA.decrypt(this, keyBuffer, sourceLength = length)
    }
}

/**
 * 在 [ByteArrayPool] 缓存 [this], 然后使用 [key] 解密.
 *
 * @param key 长度至少为 16
 * @throws DecryptionFailedException 解密错误时
 */
internal fun IoBuffer.decryptBy(key: ByteArray, offset: Int = 0, length: Int = readRemaining - offset): ByteArray {
    return ByteArrayPool.useInstance {
        this.readFully(it, offset, length)
        it.checkDataLengthAndReturnSelf(length)
        TEA.decrypt(it, key, length)
    }
}

// endregion

// region ByteReadPacket extension

internal fun ByteReadPacket.decryptBy(key: ByteArray): ByteReadPacket = decryptAsByteArray(key) { data -> ByteReadPacket(data, 0) }

internal fun ByteReadPacket.decryptBy(key: IoBuffer): ByteReadPacket = decryptAsByteArray(key) { data -> ByteReadPacket(data, 0) }

internal fun ByteReadPacket.decryptBy(key: Decrypter): ByteReadPacket = key.decrypt(this)

internal inline fun <R> ByteReadPacket.decryptAsByteArray(key: ByteArray, consumer: (ByteArray) -> R): R =
    ByteArrayPool.useInstance {
        val length = remaining.toInt()
        readFully(it, 0, length)
        consumer(it.decryptBy(key, length))
    }.also { close() }

internal inline fun <R> ByteReadPacket.decryptAsByteArray(key: IoBuffer, consumer: (ByteArray) -> R): R =
    ByteArrayPool.useInstance {
        val length = remaining.toInt()
        readFully(it, 0, length)
        consumer(it.decryptBy(key, length))
    }.also { close() }

// endregion
private object TEA {
    private const val UINT32_MASK = 0xffffffffL

    private fun doOption(data: ByteArray, key: ByteArray, length: Int, encrypt: Boolean): ByteArray {
        lateinit var mOutput: ByteArray
        lateinit var mInBlock: ByteArray
        var mIndexPos: Int
        lateinit var mIV: ByteArray
        var mOutPos = 0
        var mPreOutPos = 0
        var isFirstBlock = true

        val mKey = LongArray(4)

        for (i in 0..3) {
            mKey[i] = key.pack(i * 4, 4)
        }

        fun rand(): Int = Random.Default.nextInt()


        fun encode(bytes: ByteArray): ByteArray {
            var v0 = bytes.pack(0, 4)
            var v1 = bytes.pack(4, 4)
            var sum: Long = 0
            val delta = 0x9e3779b9L
            for (i in 0..15) {
                sum = sum + delta and UINT32_MASK
                v0 += (v1 shl 4) + mKey[0] xor v1 + sum xor v1.ushr(5) + mKey[1]
                v0 = v0 and UINT32_MASK
                v1 += (v0 shl 4) + mKey[2] xor v0 + sum xor v0.ushr(5) + mKey[3]
                v1 = v1 and UINT32_MASK
            }

            return v0.toInt().toByteArray() + v1.toInt().toByteArray()
        }

        fun decode(bytes: ByteArray, offset: Int): ByteArray {
            var v0 = bytes.pack(offset, 4)
            var v1 = bytes.pack(offset + 4, 4)
            val delta = 0x9e3779b9L
            var sum = delta shl 4 and UINT32_MASK
            for (i in 0..15) {
                v1 -= (v0 shl 4) + mKey[2] xor v0 + sum xor v0.ushr(5) + mKey[3]
                v1 = v1 and UINT32_MASK
                v0 -= (v1 shl 4) + mKey[0] xor v1 + sum xor v1.ushr(5) + mKey[1]
                v0 = v0 and UINT32_MASK
                sum = sum - delta and UINT32_MASK
            }
            return v0.toInt().toByteArray() + v1.toInt().toByteArray()
        }

        fun encodeOneBlock() {
            mIndexPos = 0
            while (mIndexPos < 8) {
                mInBlock[mIndexPos] = if (isFirstBlock)
                    mInBlock[mIndexPos]
                else
                    (mInBlock[mIndexPos] xor mOutput[mPreOutPos + mIndexPos])
                mIndexPos++
            }

            encode(mInBlock).copyInto(mOutput, mOutPos, 0, 8)
            mIndexPos = 0
            while (mIndexPos < 8) {
                val outPos = mOutPos + mIndexPos
                mOutput[outPos] = (mOutput[outPos] xor mIV[mIndexPos])
                mIndexPos++
            }
            mInBlock.copyInto(mIV, 0, 0, 8)
            mPreOutPos = mOutPos
            mOutPos += 8
            mIndexPos = 0
            isFirstBlock = false
        }

        fun decodeOneBlock(ciphertext: ByteArray, offset: Int, len: Int): Boolean {
            mIndexPos = 0
            while (mIndexPos < 8) {
                if (mOutPos + mIndexPos < len) {
                    mIV[mIndexPos] = (mIV[mIndexPos] xor ciphertext[mOutPos + offset + mIndexPos])
                    mIndexPos++
                    continue
                }
                return true
            }

            mIV = decode(mIV, 0)
            mOutPos += 8
            mIndexPos = 0
            return true

        }

        @Suppress("NAME_SHADOWING")
        fun encrypt(plaintext: ByteArray, offset: Int, len: Int): ByteArray {
            var len = len
            var offset = offset
            mInBlock = ByteArray(8)
            mIV = ByteArray(8)
            mOutPos = 0
            mPreOutPos = 0
            isFirstBlock = true
            mIndexPos = (len + 10) % 8
            if (mIndexPos != 0) {
                mIndexPos = 8 - mIndexPos
            }
            mOutput = ByteArray(mIndexPos + len + 10)
            mInBlock[0] = (rand() and 0xf8 or mIndexPos).toByte()
            for (i in 1..mIndexPos) {
                mInBlock[i] = (rand() and 0xff).toByte()
            }
            ++mIndexPos
            for (i in 0..7) {
                mIV[i] = 0
            }

            var g = 0
            while (g < 2) {
                if (mIndexPos < 8) {
                    mInBlock[mIndexPos++] = (rand() and 0xff).toByte()
                    ++g
                }
                if (mIndexPos == 8) {
                    encodeOneBlock()
                }
            }

            while (len > 0) {
                if (mIndexPos < 8) {
                    mInBlock[mIndexPos++] = plaintext[offset++]
                }
                if (mIndexPos == 8) {
                    encodeOneBlock()
                }
                len--
            }
            g = 0
            while (g < 7) {
                if (mIndexPos < 8) {
                    mInBlock[mIndexPos++] = 0.toByte()
                }
                if (mIndexPos == 8) {
                    encodeOneBlock()
                }
                g++
            }
            return mOutput
        }

        fun decrypt(cipherText: ByteArray, offset: Int, len: Int): ByteArray {
            require(!(len % 8 != 0 || len < 16)) { "data must len % 8 == 0 && len >= 16 but given $len" }
            mIV = decode(cipherText, offset)
            mIndexPos = (mIV[0] and 7).toInt()
            var plen = len - mIndexPos - 10
            isFirstBlock = true
            if (plen < 0) {
                fail()
            }
            mOutput = ByteArray(plen)
            mPreOutPos = 0
            mOutPos = 8
            ++mIndexPos
            var g = 0
            while (g < 2) {
                if (mIndexPos < 8) {
                    ++mIndexPos
                    ++g
                }
                if (mIndexPos == 8) {
                    isFirstBlock = false
                    if (!decodeOneBlock(cipherText, offset, len)) {
                        fail()
                    }
                }
            }

            var outpos = 0
            while (plen != 0) {
                if (mIndexPos < 8) {
                    mOutput[outpos++] = if (isFirstBlock)
                        mIV[mIndexPos]
                    else
                        (cipherText[mPreOutPos + offset + mIndexPos] xor mIV[mIndexPos])
                    ++mIndexPos
                }
                if (mIndexPos == 8) {
                    mPreOutPos = mOutPos - 8
                    isFirstBlock = false
                    if (!decodeOneBlock(cipherText, offset, len)) {
                        fail()
                    }
                }
                plen--
            }
            g = 0
            while (g < 7) {
                if (mIndexPos < 8) {
                    if (cipherText[mPreOutPos + offset + mIndexPos].xor(mIV[mIndexPos]).toInt() != 0) {
                        fail()
                    } else {
                        ++mIndexPos
                    }
                }

                if (mIndexPos == 8) {
                    mPreOutPos = mOutPos
                    if (!decodeOneBlock(cipherText, offset, len)) {
                        fail()
                    }
                }
                g++
            }
            return mOutput
        }

        return if (encrypt) {
            encrypt(data, 0, length)
        } else {
            decrypt(data, 0, length)
        }
    }

    private fun fail(): Nothing = throw DecryptionFailedException()

    @PublishedApi
    @JvmStatic
    internal fun encrypt(source: ByteArray, key: ByteArray, sourceLength: Int = source.size): ByteArray = doOption(source, key, sourceLength, true)

    @PublishedApi
    @JvmStatic
    internal fun decrypt(source: ByteArray, key: ByteArray, sourceLength: Int = source.size): ByteArray = doOption(source, key, sourceLength, false)

    private fun ByteArray.pack(offset: Int, len: Int): Long {
        var result: Long = 0
        val maxOffset = if (len > 8) offset + 8 else offset + len
        for (index in offset until maxOffset) {
            result = result shl 8 or (this[index].toLong() and 0xffL)
        }
        return result shr 32 or (result and UINT32_MASK)
    }
}

@Suppress("NOTHING_TO_INLINE")
private inline fun ByteArray.checkDataLengthAndReturnSelf(length: Int = this.size): ByteArray {
    if (!(length % 8 == 0 && length >= 16)) {
        throw DecryptionFailedException("data must len % 8 == 0 && len >= 16 but given (length=$length) ${this.toUHexString()}")
    }
    return this
}
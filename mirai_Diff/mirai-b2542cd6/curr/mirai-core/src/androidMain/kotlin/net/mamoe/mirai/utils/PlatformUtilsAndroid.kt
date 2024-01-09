package net.mamoe.mirai.utils

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.io.pool.useInstance
import net.mamoe.mirai.utils.io.ByteArrayPool
import java.io.ByteArrayOutputStream
import java.io.DataInput
import java.io.EOFException
import java.io.InputStream
import java.net.InetAddress
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.zip.CRC32
import java.util.zip.Inflater


/**
 * 设备名
 */
actual val deviceName: String get() = InetAddress.getLocalHost().hostName

/**
 * Ktor HttpClient. 不同平台使用不同引擎.
 */
@KtorExperimentalAPI
actual val Http: HttpClient
    get() = HttpClient(CIO)

/**
 * Localhost 解析
 */
actual fun localIpAddress(): String = runCatching {
    InetAddress.getLocalHost().hostAddress
}.getOrElse { "192.168.1.123" }

/**
 * MD5 算法
 *
 * @return 16 bytes
 */
actual fun md5(byteArray: ByteArray): ByteArray = MessageDigest.getInstance("MD5").digest(byteArray)

fun InputStream.md5(): ByteArray {
    val digest = MessageDigest.getInstance("md5")
    digest.reset()
    this.readInSequence {
        digest.update(it.toByte())
    }
    return digest.digest()
}

fun DataInput.md5(): ByteArray {
    val digest = MessageDigest.getInstance("md5")
    digest.reset()
    val buffer = byteArrayOf(1)
    while (true) {
        try {
            this.readFully(buffer)
        } catch (e: EOFException) {
            break
        }
        digest.update(buffer[0])
    }
    return digest.digest()
}

private inline fun InputStream.readInSequence(block: (Int) -> Unit) {
    var read: Int
    while (this.read().also { read = it } != -1) {
        block(read)
    }
}

/**
 * CRC32 算法
 */
actual fun crc32(key: ByteArray): Int = CRC32().apply { update(key) }.value.toInt()

/**
 * hostname 解析 ipv4
 */
actual fun solveIpAddress(hostname: String): String = InetAddress.getByName(hostname).hostAddress

actual fun ByteArray.unzip(offset: Int, length: Int): ByteArray {
    this.checkOffsetAndLength(offset, length)
    if (length == 0) return ByteArray(0)

    val inflater = Inflater()
    inflater.reset()
    ByteArrayOutputStream().use { output ->
        inflater.setInput(this, offset, length)
        ByteArrayPool.useInstance {
            while (!inflater.finished()) {
                output.write(it, 0, inflater.inflate(it))
            }
        }

        inflater.end()
        return output.toByteArray()
    }
}

actual fun newCoroutineDispatcher(threadCount: Int): CoroutineDispatcher {
    return Executors.newFixedThreadPool(threadCount).asCoroutineDispatcher()
}
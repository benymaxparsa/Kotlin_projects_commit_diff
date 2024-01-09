/*
 * Copyright 2020 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/mamoe/mirai/blob/master/LICENSE
 */

@file:Suppress("EXPERIMENTAL_API_USAGE", "unused")

package net.mamoe.mirai.utils

import io.ktor.util.asStream
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import kotlinx.io.core.Input
import kotlinx.io.core.buildPacket
import kotlinx.io.core.copyTo
import kotlinx.io.errors.IOException
import kotlinx.io.streams.asInput
import kotlinx.io.streams.asOutput
import net.mamoe.mirai.utils.io.getRandomString
import java.awt.image.BufferedImage
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.URL
import java.security.MessageDigest
import javax.imageio.ImageIO

/*
 * 将各类型图片容器转为 [ExternalImage]
 */


/**
 * 读取 [BufferedImage] 的属性, 然后构造 [ExternalImage]
 */
@Throws(IOException::class)
fun BufferedImage.toExternalImage(formatName: String = "gif"): ExternalImage {
    val digest = MessageDigest.getInstance("md5")
    digest.reset()

    val buffer = buildPacket {
        ImageIO.write(this@toExternalImage, formatName, object : OutputStream() {
            override fun write(b: Int) {
                b.toByte().let {
                    this@buildPacket.writeByte(it)
                    digest.update(it)
                }
            }
        })
    }

    return ExternalImage(width, height, digest.digest(), formatName, buffer, getRandomString(16) + "." + formatName)
}

suspend inline fun BufferedImage.suspendToExternalImage(): ExternalImage = withContext(IO) { toExternalImage() }

/**
 * 读取文件头识别图片属性, 然后构造 [ExternalImage]
 */
@Throws(IOException::class)
fun File.toExternalImage(): ExternalImage {
    val input = ImageIO.createImageInputStream(this)
    checkNotNull(input) { "Unable to read file(path=${this.path}), no ImageInputStream found" }
    val image = ImageIO.getImageReaders(input).asSequence().firstOrNull()
        ?: error("Unable to read file(path=${this.path}), no ImageReader found")
    image.input = input

    val inputStream = this.inputStream()
    return ExternalImage(
        width = image.getWidth(0),
        height = image.getHeight(0),
        md5 = this.inputStream().md5(), // dont change
        imageFormat = image.formatName,
        input = inputStream.asInput(),
        inputSize = inputStream.available().toLong(),
        filename = this.name
    )
}

/**
 * 在 [IO] 中进行 [File.toExternalImage]
 */
suspend inline fun File.suspendToExternalImage(): ExternalImage = withContext(IO) { toExternalImage() }

/**
 * 下载文件到临时目录然后调用 [File.toExternalImage]
 */
@Throws(IOException::class)
fun URL.toExternalImage(): ExternalImage {
    val file = createTempFile().apply { deleteOnExit() }
    file.outputStream().asOutput().use { output ->
        openStream().asInput().use { input ->
            input.copyTo(output)
        }
    }
    return file.toExternalImage()
}

/**
 * 在 [IO] 中进行 [URL.toExternalImage]
 */
suspend inline fun URL.suspendToExternalImage(): ExternalImage = withContext(IO) { toExternalImage() }

/**
 * 保存为临时文件然后调用 [File.toExternalImage]
 */
@Throws(IOException::class)
fun InputStream.toExternalImage(): ExternalImage {
    val file = createTempFile().apply { deleteOnExit() }
    file.outputStream().use {
        this.copyTo(it)
    }
    this.close()
    return file.toExternalImage()
}

/**
 * 在 [IO] 中进行 [InputStream.toExternalImage]
 */
suspend inline fun InputStream.suspendToExternalImage(): ExternalImage = withContext(IO) { toExternalImage() }

/**
 * 保存为临时文件然后调用 [File.toExternalImage].
 *
 * 需要函数调用者 close [this]
 */
@Throws(IOException::class)
fun Input.toExternalImage(): ExternalImage {
    val file = createTempFile().apply { deleteOnExit() }
    file.outputStream().asOutput().use {
        this.asStream().asInput().copyTo(it)
    }
    return file.toExternalImage()
}

/**
 * 在 [IO] 中进行 [Input.toExternalImage]
 */
suspend inline fun Input.suspendToExternalImage(): ExternalImage = withContext(IO) { toExternalImage() }

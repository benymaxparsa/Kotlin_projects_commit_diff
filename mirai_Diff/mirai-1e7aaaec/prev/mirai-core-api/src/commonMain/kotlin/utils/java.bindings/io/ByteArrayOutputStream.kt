/*
 * Copyright 2019-2020 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AFFERO GENERAL PUBLIC LICENSE version 3 license that can be found via the following link.
 *
 * https://github.com/mamoe/mirai/blob/master/LICENSE
 */
@file:Suppress("unused", "NO_ACTUAL_FOR_EXPECT", "PackageDirectoryMismatch")

/**
 * Bindings for JDK.
 *
 * All the sources are copied from OpenJDK. Copyright OpenJDK authors.
 */

package java.io

public expect class ByteArrayOutputStream() : OutputStream {
    public constructor(size: Int)

    public override fun write(oneByte: Int)
    public fun toByteArray(): ByteArray
    public fun size(): Int
}
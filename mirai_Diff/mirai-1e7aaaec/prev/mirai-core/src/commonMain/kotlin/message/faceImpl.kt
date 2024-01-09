/*
 * Copyright 2019-2020 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AFFERO GENERAL PUBLIC LICENSE version 3 license that can be found via the following link.
 *
 * https://github.com/mamoe/mirai/blob/master/LICENSE
 */

package net.mamoe.mirai.internal.message

import net.mamoe.mirai.internal.network.protocol.data.proto.ImMsgBody
import net.mamoe.mirai.internal.utils.hexToBytes
import net.mamoe.mirai.internal.utils.toByteArray
import net.mamoe.mirai.message.data.Face

internal val FACE_BUF = "00 01 00 04 52 CC F5 D0".hexToBytes()

internal fun Face.toJceData(): ImMsgBody.Face {
    return ImMsgBody.Face(
        index = this.id,
        old = (0x1445 - 4 + this.id).toShort().toByteArray(),
        buf = FACE_BUF
    )
}

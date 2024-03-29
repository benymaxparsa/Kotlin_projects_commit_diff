package net.mamoe.mirai.qqandroid.network.protocol.packet.chat.data

import kotlinx.serialization.SerialId
import kotlinx.serialization.Serializable
import net.mamoe.mirai.qqandroid.network.protocol.packet.EMPTY_BYTE_ARRAY
import net.mamoe.mirai.qqandroid.network.protocol.protobuf.ProtoBuf

@Serializable
class MsgOnlinePush {
    @Serializable
    data class PbPushMsg(
        @SerialId(1) val msg: MsgComm.Msg? = null,
        @SerialId(2) val svrip: Int = 0,
        @SerialId(3) val pushToken: ByteArray = EMPTY_BYTE_ARRAY,
        @SerialId(4) val pingFlag: Int = 0,
        @SerialId(9) val generalFlag: Int = 0
    ) : ProtoBuf
}
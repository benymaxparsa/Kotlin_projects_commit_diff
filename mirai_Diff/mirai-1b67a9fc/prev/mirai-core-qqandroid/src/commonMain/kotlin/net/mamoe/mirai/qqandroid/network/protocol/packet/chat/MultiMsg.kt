/*
 * Copyright 2019-2020 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 with Mamoe Exceptions 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AFFERO GENERAL PUBLIC LICENSE version 3 with Mamoe Exceptions license that can be found via the following link.
 *
 * https://github.com/mamoe/mirai/blob/master/LICENSE
 */

@file:Suppress("EXPERIMENTAL_API_USAGE")

package net.mamoe.mirai.qqandroid.network.protocol.packet.chat

import kotlinx.io.core.ByteReadPacket
import net.mamoe.mirai.message.data.ForwardMessage
import net.mamoe.mirai.message.data.asMessageChain
import net.mamoe.mirai.qqandroid.QQAndroidBot
import net.mamoe.mirai.qqandroid.message.toRichTextElems
import net.mamoe.mirai.qqandroid.network.Packet
import net.mamoe.mirai.qqandroid.network.QQAndroidClient
import net.mamoe.mirai.qqandroid.network.protocol.data.proto.ImMsgBody
import net.mamoe.mirai.qqandroid.network.protocol.data.proto.MsgComm
import net.mamoe.mirai.qqandroid.network.protocol.data.proto.MsgTransmit
import net.mamoe.mirai.qqandroid.network.protocol.data.proto.MultiMsg
import net.mamoe.mirai.qqandroid.network.protocol.packet.OutgoingPacket
import net.mamoe.mirai.qqandroid.network.protocol.packet.OutgoingPacketFactory
import net.mamoe.mirai.qqandroid.network.protocol.packet.PacketLogger
import net.mamoe.mirai.qqandroid.network.protocol.packet.buildOutgoingUniPacket
import net.mamoe.mirai.qqandroid.utils.MiraiPlatformUtils
import net.mamoe.mirai.qqandroid.utils._miraiContentToString
import net.mamoe.mirai.qqandroid.utils.io.serialization.readProtoBuf
import net.mamoe.mirai.qqandroid.utils.io.serialization.toByteArray
import net.mamoe.mirai.qqandroid.utils.io.serialization.writeProtoBuf

internal class MessageValidationData(
    val data: ByteArray,
    val md5: ByteArray = MiraiPlatformUtils.md5(data)
) {
    override fun toString(): String {
        return "MessageValidationData(data=<size=${data.size}>, md5=${md5.contentToString()})"
    }
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun Int.toLongUnsigned(): Long = this.toLong().and(0xFFFF_FFFF)
internal fun Collection<ForwardMessage.INode>.calculateValidationDataForGroup(
    sequenceId: Int,
    random: Int,
    groupCode: Long
): MessageValidationData {
    val msgTransmit = MsgTransmit.PbMultiMsgTransmit(
        msg = this.map { chain ->
            MsgComm.Msg(
                msgHead = MsgComm.MsgHead(
                    fromUin = chain.senderId,
                    msgSeq = sequenceId,
                    msgTime = chain.time,
                    msgUid = 0x01000000000000000L or random.toLongUnsigned(),
                    mutiltransHead = MsgComm.MutilTransHead(
                        status = 0,
                        msgId = 1
                    ),
                    msgType = 82, // troop
                    groupInfo = MsgComm.GroupInfo(
                        groupCode = groupCode,
                        groupCard = chain.senderName // Cinnamon
                    ),
                    isSrcMsg = false
                ),
                msgBody = ImMsgBody.MsgBody(
                    richText = ImMsgBody.RichText(
                        elems = chain.message.asMessageChain()
                            .toRichTextElems(forGroup = true, withGeneralFlags = false).toMutableList()
                    )
                )
            )
        }
    )

    val bytes = msgTransmit.toByteArray(MsgTransmit.PbMultiMsgTransmit.serializer())

    return MessageValidationData(MiraiPlatformUtils.gzip(bytes))
}

internal class MultiMsg {

    object ApplyUp : OutgoingPacketFactory<ApplyUp.Response>("MultiMsg.ApplyUp") {
        sealed class Response : Packet {
            data class RequireUpload(
                val proto: MultiMsg.MultiMsgApplyUpRsp
            ) : Response() {
                override fun toString(): String {
                    if (PacketLogger.isEnabled) {
                        return _miraiContentToString()
                    }
                    return "MultiMsg.ApplyUp.Response.RequireUpload(proto=$proto)"
                }
            }

            object MessageTooLarge : Response()
        }

        // captured from group
        fun createForGroupLongMessage(
            buType: Int,
            client: QQAndroidClient,
            messageData: MessageValidationData,
            dstUin: Long // group uin
        ): OutgoingPacket = buildOutgoingUniPacket(client) {
            writeProtoBuf(
                MultiMsg.ReqBody.serializer(),
                MultiMsg.ReqBody(
                    buType = buType, // 1: long, 2: 合并转发
                    buildVer = "8.2.0.1296",
                    multimsgApplyupReq = listOf(
                        MultiMsg.MultiMsgApplyUpReq(
                            applyId = 0,
                            dstUin = dstUin,
                            msgMd5 = messageData.md5,
                            msgSize = messageData.data.size.toLong(),
                            msgType = 3 // TODO 3 for group?
                        )
                    ),
                    netType = 3, // wifi=3, wap=5
                    platformType = 9,
                    subcmd = 1,
                    termType = 5,
                    reqChannelType = 0
                )
            )
        }

        override suspend fun ByteReadPacket.decode(bot: QQAndroidBot): Response {
            val body = readProtoBuf(MultiMsg.RspBody.serializer())
            val response = body.multimsgApplyupRsp!!.first()
            return when (response.result) {
                0 -> Response.RequireUpload(response)
                193 -> Response.MessageTooLarge
                //1 -> Response.OK(resId = response.msgResid)
                else -> {
                    error(kotlin.run {
                        println(response._miraiContentToString())
                    }.let { "Protocol error: MultiMsg.ApplyUp failed with result ${response.result}" })
                }
            }
        }
    }
}
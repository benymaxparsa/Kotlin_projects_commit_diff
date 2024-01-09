package net.mamoe.mirai.qqandroid.network.protocol.packet.chat.receive

import kotlinx.io.core.ByteReadPacket
import kotlinx.io.core.discardExact
import net.mamoe.mirai.data.MultiPacket
import net.mamoe.mirai.message.FriendMessage
import net.mamoe.mirai.qqandroid.QQAndroidBot
import net.mamoe.mirai.qqandroid.io.readRemainingAsProtoBuf
import net.mamoe.mirai.qqandroid.io.serialization.loadAs
import net.mamoe.mirai.qqandroid.io.serialization.readRemainingAsJceStruct
import net.mamoe.mirai.qqandroid.io.writeProtoBuf
import net.mamoe.mirai.qqandroid.network.QQAndroidClient
import net.mamoe.mirai.qqandroid.network.protocol.packet.EMPTY_BYTE_ARRAY
import net.mamoe.mirai.qqandroid.network.protocol.packet.OutgoingPacket
import net.mamoe.mirai.qqandroid.network.protocol.packet.PacketFactory
import net.mamoe.mirai.qqandroid.network.protocol.packet.buildOutgoingUniPacket
import net.mamoe.mirai.qqandroid.network.protocol.packet.chat.data.MsgSvc
import net.mamoe.mirai.qqandroid.network.protocol.packet.chat.data.RequestPushNotify
import net.mamoe.mirai.qqandroid.network.protocol.packet.login.data.RequestDataVersion2
import net.mamoe.mirai.qqandroid.network.protocol.packet.login.data.RequestPacket
import net.mamoe.mirai.qqandroid.utils.toMessageChain
import net.mamoe.mirai.utils.firstValue
import net.mamoe.mirai.utils.io.hexToBytes
import net.mamoe.mirai.utils.io.toReadPacket

class MessageSvc {
    internal object PushNotify : PacketFactory<RequestPushNotify>("MessageSvc.PushNotify") {
        override suspend fun ByteReadPacket.decode(bot: QQAndroidBot): RequestPushNotify {
            discardExact(8)

            return readRemainingAsJceStruct(RequestPacket.serializer()).sBuffer
                .loadAs(RequestDataVersion2.serializer()).map.firstValue().firstValue()
                .toReadPacket().apply { discardExact(1) }
                .readRemainingAsJceStruct(RequestPushNotify.serializer())
        }

        override suspend fun QQAndroidBot.handle(packet: RequestPushNotify) {
            network.run {
                PbGetMsg(client, packet).sendAndExpect<MultiPacket<FriendMessage>>()
            }
        }
    }

    internal object PbGetMsg : PacketFactory<MultiPacket<FriendMessage>>("MessageSvc.PbGetMsg") {
        operator fun invoke(
            client: QQAndroidClient,
            from: RequestPushNotify
        ): OutgoingPacket = buildOutgoingUniPacket(
            client,
            extraData = "08 00 12 33 6D 6F 64 65 6C 3A 78 69 61 6F 6D 69 20 36 3B 6F 73 3A 32 32 3B 76 65 72 73 69 6F 6E 3A 76 32 6D 61 6E 3A 78 69 61 6F 6D 69 73 79 73 3A 4C 4D 59 34 38 5A 18 E4 E1 A4 FF FE 2D 20 E9 E1 A4 FF FE 2D 28 A8 E1 A4 FF FE 2D 30 99 E1 A4 FF FE 2D".hexToBytes().toReadPacket()
        ) {
            writeProtoBuf(
                MsgSvc.PbGetMsgReq.serializer(),
                MsgSvc.PbGetMsgReq(
                    msgReqType = from.ctype.toInt(),
                    contextFlag = 1,
                    rambleFlag = 0,
                    latestRambleNumber = 20,
                    otherRambleNumber = 3,
                    onlineSyncFlag = 1,
                    serverBuf = from.serverBuf ?: EMPTY_BYTE_ARRAY
                )
            )
        }

        override suspend fun ByteReadPacket.decode(bot: QQAndroidBot): MultiPacket<FriendMessage> {
            // 00 00 01 0F 08 00 12 00 1A 34 08 FF C1 C4 F1 05 10 FF C1 C4 F1 05 18 E6 ED B9 C3 02 20 89 FE BE A4 06 28 8A CA 91 D1 0C 48 9B A5 BD 9B 0A 58 DE 9D 99 F8 08 60 1D 68 FF C1 C4 F1 05 70 00 20 02 2A 9D 01 08 F3 C1 C4 F1 05 10 A2 FF 8C F0 03 18 01 22 8A 01 0A 2A 08 A2 FF 8C F0 03 10 DD F1 92 B7 07 18 A6 01 20 0B 28 AE F9 01 30 F4 C1 C4 F1 05 38 A7 E3 D8 D4 84 80 80 80 01 B8 01 CD B5 01 12 08 08 01 10 00 18 00 20 00 1A 52 0A 50 0A 27 08 00 10 F4 C1 C4 F1 05 18 A7 E3 D8 D4 04 20 00 28 0C 30 00 38 86 01 40 22 4A 0C E5 BE AE E8 BD AF E9 9B 85 E9 BB 91 12 08 0A 06 0A 04 4E 4D 53 4C 12 15 AA 02 12 9A 01 0F 80 01 01 C8 01 00 F0 01 00 F8 01 00 90 02 00 12 04 4A 02 08 00 30 01 2A 15 08 97 A2 C1 F1 05 10 95 A6 F5 E5 0C 18 01 30 01 40 01 48 81 01 2A 10 08 D3 F7 B5 F1 05 10 DD F1 92 B7 07 18 01 30 01 38 00 42 00 48 00
            discardExact(4)
            val resp = readRemainingAsProtoBuf(MsgSvc.PbGetMsgResp.serializer())
            //println(resp.contentToString())

            if (resp.uinPairMsgs == null) {
                return MultiPacket(emptyList())
            }
            return MultiPacket(resp.uinPairMsgs.asSequence().flatMap { it.msg.asSequence() }.mapNotNull {
                when (it.msgHead.msgType) {
                    166 -> {
                        FriendMessage(
                            bot,
                            false, // TODO: 2020/1/29 PREVIOUS??
                            bot.getQQ(it.msgHead.fromUin),
                            it.msgBody.richText.toMessageChain()
                        )
                    }
                    else -> null
                }
            }.toList())
        }
    }
}


/*
 * Copyright 2020 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/mamoe/mirai/blob/master/LICENSE
 */

@file:Suppress("EXPERIMENTAL_UNSIGNED_LITERALS")

package net.mamoe.mirai.qqandroid.network.protocol.packet.chat.receive

import kotlinx.io.core.ByteReadPacket
import kotlinx.io.core.readBytes
import kotlinx.io.core.readUInt
import net.mamoe.mirai.contact.MemberPermission
import net.mamoe.mirai.data.NoPacket
import net.mamoe.mirai.data.Packet
import net.mamoe.mirai.event.broadcast
import net.mamoe.mirai.event.events.*
import net.mamoe.mirai.message.GroupMessage
import net.mamoe.mirai.qqandroid.MemberImpl
import net.mamoe.mirai.qqandroid.QQAndroidBot
import net.mamoe.mirai.qqandroid.io.serialization.decodeUniPacket
import net.mamoe.mirai.qqandroid.io.serialization.loadAs
import net.mamoe.mirai.qqandroid.io.serialization.readProtoBuf
import net.mamoe.mirai.qqandroid.message.toMessageChain
import net.mamoe.mirai.qqandroid.network.protocol.data.jce.MsgInfo
import net.mamoe.mirai.qqandroid.network.protocol.data.jce.OnlinePushPack
import net.mamoe.mirai.qqandroid.network.protocol.data.proto.ImMsgBody
import net.mamoe.mirai.qqandroid.network.protocol.data.proto.MsgOnlinePush
import net.mamoe.mirai.qqandroid.network.protocol.data.proto.OnlinePushTrans
import net.mamoe.mirai.qqandroid.network.protocol.packet.IncomingPacketFactory
import net.mamoe.mirai.qqandroid.network.protocol.packet.OutgoingPacket
import net.mamoe.mirai.qqandroid.network.protocol.packet.buildResponseUniPacket
import net.mamoe.mirai.utils.cryptor.contentToString
import net.mamoe.mirai.utils.io.discardExact
import net.mamoe.mirai.utils.io.read
import net.mamoe.mirai.utils.io.readString
import net.mamoe.mirai.utils.io.toUHexString

internal inline class GroupMessageOrNull(val delegate: GroupMessage?) : Packet {
    override fun toString(): String {
        return delegate?.toString() ?: "<Receipt>"
    }
}

internal class OnlinePush {
    /**
     * 接受群消息
     */
    internal object PbPushGroupMsg : IncomingPacketFactory<GroupMessageOrNull>("OnlinePush.PbPushGroupMsg") {
        @UseExperimental(ExperimentalStdlibApi::class)
        override suspend fun ByteReadPacket.decode(bot: QQAndroidBot, sequenceId: Int): GroupMessageOrNull {
            // 00 00 02 E4 0A D5 05 0A 4F 08 A2 FF 8C F0 03 10 DD F1 92 B7 07 18 52 20 00 28 BC 3D 30 8C 82 AB F1 05 38 D2 80 E0 8C 80 80 80 80 02 4A 21 08 E7 C1 AD B8 02 10 01 18 BA 05 22 09 48 69 6D 31 38 38 6D 6F 65 30 06 38 02 42 05 4D 69 72 61 69 50 01 58 01 60 00 88 01 08 12 06 08 01 10 00 18 00 1A F9 04 0A F6 04 0A 26 08 00 10 87 82 AB F1 05 18 B7 B4 BF 30 20 00 28 0C 30 00 38 86 01 40 22 4A 0C E5 BE AE E8 BD AF E9 9B 85 E9 BB 91 12 E6 03 42 E3 03 12 2A 7B 34 45 31 38 35 38 32 32 2D 30 45 37 42 2D 46 38 30 46 2D 43 35 42 31 2D 33 34 34 38 38 33 37 34 44 33 39 43 7D 2E 6A 70 67 22 00 2A 04 03 00 00 00 32 60 15 36 20 39 36 6B 45 31 41 38 35 32 32 39 64 63 36 39 38 34 37 39 37 37 62 20 20 20 20 20 20 35 30 20 20 20 20 20 20 20 20 20 20 20 20 20 20 20 20 7B 34 45 31 38 35 38 32 32 2D 30 45 37 42 2D 46 38 30 46 2D 43 35 42 31 2D 33 34 34 38 38 33 37 34 44 33 39 43 7D 2E 6A 70 67 31 32 31 32 41 38 C6 BB 8A A9 08 40 FB AE 9E C2 09 48 50 50 41 5A 00 60 01 6A 10 4E 18 58 22 0E 7B F8 0F C5 B1 34 48 83 74 D3 9C 72 59 2F 67 63 68 61 74 70 69 63 5F 6E 65 77 2F 31 30 34 30 34 30 30 32 39 30 2F 36 35 35 30 35 37 31 32 37 2D 32 32 33 33 36 33 38 33 34 32 2D 34 45 31 38 35 38 32 32 30 45 37 42 46 38 30 46 43 35 42 31 33 34 34 38 38 33 37 34 44 33 39 43 2F 31 39 38 3F 74 65 72 6D 3D 32 82 01 57 2F 67 63 68 61 74 70 69 63 5F 6E 65 77 2F 31 30 34 30 34 30 30 32 39 30 2F 36 35 35 30 35 37 31 32 37 2D 32 32 33 33 36 33 38 33 34 32 2D 34 45 31 38 35 38 32 32 30 45 37 42 46 38 30 46 43 35 42 31 33 34 34 38 38 33 37 34 44 33 39 43 2F 30 3F 74 65 72 6D 3D 32 B0 01 4D B8 01 2E C8 01 FF 05 D8 01 4D E0 01 2E FA 01 59 2F 67 63 68 61 74 70 69 63 5F 6E 65 77 2F 31 30 34 30 34 30 30 32 39 30 2F 36 35 35 30 35 37 31 32 37 2D 32 32 33 33 36 33 38 33 34 32 2D 34 45 31 38 35 38 32 32 30 45 37 42 46 38 30 46 43 35 42 31 33 34 34 38 38 33 37 34 44 33 39 43 2F 34 30 30 3F 74 65 72 6D 3D 32 80 02 4D 88 02 2E 12 45 AA 02 42 50 03 60 00 68 00 9A 01 39 08 09 20 BF 50 80 01 01 C8 01 00 F0 01 00 F8 01 00 90 02 00 98 03 00 A0 03 20 B0 03 00 C0 03 00 D0 03 00 E8 03 00 8A 04 04 08 02 08 01 90 04 80 80 80 10 B8 04 00 C0 04 00 12 06 4A 04 08 00 40 01 12 14 82 01 11 0A 09 48 69 6D 31 38 38 6D 6F 65 18 06 20 08 28 03 10 8A CA 9D A1 07 1A 00
            if (!bot.firstLoginSucceed) return GroupMessageOrNull(null)
            val pbPushMsg = readProtoBuf(MsgOnlinePush.PbPushMsg.serializer())

            val extraInfo: ImMsgBody.ExtraInfo? = pbPushMsg.msg.msgBody.richText.elems.firstOrNull { it.extraInfo != null }?.extraInfo

            if (pbPushMsg.msg.msgHead.fromUin == bot.uin) {
                return GroupMessageOrNull(null)
            }

            val group = bot.getGroup(pbPushMsg.msg.msgHead.groupInfo!!.groupCode)

            // println(pbPushMsg.msg.msgBody.richText.contentToString())
            val flags = extraInfo?.flags ?: 0
            return GroupMessageOrNull(
                GroupMessage(
                    bot = bot,
                    group = group,
                    senderName = pbPushMsg.msg.msgHead.groupInfo.groupCard,
                    sender = group[pbPushMsg.msg.msgHead.fromUin],
                    message = pbPushMsg.msg.toMessageChain(),
                    permission = when {
                        flags and 16 != 0 -> MemberPermission.ADMINISTRATOR
                        flags and 8 != 0 -> MemberPermission.OWNER
                        flags == 0 -> MemberPermission.MEMBER
                        else -> {
                            bot.logger.warning("判断群员权限失败")
                            MemberPermission.MEMBER
                        }
                    }
                )
            )
        }

        override suspend fun QQAndroidBot.handle(packet: GroupMessageOrNull, sequenceId: Int): OutgoingPacket? {
            packet.delegate?.broadcast()
            return null
        }

    }

    internal object PbPushTransMsg : IncomingPacketFactory<Packet>("OnlinePush.PbPushTransMsg", "OnlinePush.RespPush") {

        @ExperimentalUnsignedTypes
        override suspend fun ByteReadPacket.decode(bot: QQAndroidBot, sequenceId: Int): Packet {
            val content = this.readProtoBuf(OnlinePushTrans.PbMsgInfo.serializer())
            content.msgData.read {
                when (content.msgType) {
                    44 -> {
                        this.discardExact(5)
                        val var4 = readByte().toInt()
                        var var5 = 0L
                        val target = readUInt().toLong()
                        if (var4 != 0 && var4 != 1) {
                            var5 = readUInt().toLong()
                        }
                        if (var5 == 0L && this.remaining == 1L) {//管理员变更
                            val groupUin = content.fromUin

                            val member = bot.getGroupByUin(groupUin)[target] as MemberImpl
                            val old = member.permission
                            return if (this.readByte().toInt() == 1) {
                                member.permission = MemberPermission.ADMINISTRATOR
                                MemberPermissionChangeEvent(member, old, MemberPermission.ADMINISTRATOR)
                            } else {
                                member.permission = MemberPermission.MEMBER
                                MemberPermissionChangeEvent(member, old, MemberPermission.ADMINISTRATOR)
                            }
                        }
                    }
                    34 -> {
                        readUInt().toLong() // uin or code ?
                        if (readByte().toInt() == 1) {
                            val target = readUInt().toLong()
                            val groupUin = content.fromUin

                            val member = bot.getGroupByUin(groupUin)[target] as MemberImpl

                            return MemberLeaveEvent.Kick(member, TODO("踢出时获取管理员"))
                        }

                    }
                }
            }
            return NoPacket
        }

        override suspend fun QQAndroidBot.handle(packet: Packet, sequenceId: Int): OutgoingPacket? {
            return buildResponseUniPacket(client, sequenceId = sequenceId) {}
        }

    }

    //0C 01 B1 89 BE 09 5E 3D 72 A6 00 01 73 68 FC 06 00 00 00 3C
    internal object ReqPush : IncomingPacketFactory<Packet>("OnlinePush.ReqPush", "OnlinePush.RespPush") {
        @ExperimentalUnsignedTypes
        @UseExperimental(ExperimentalStdlibApi::class)
        override suspend fun ByteReadPacket.decode(bot: QQAndroidBot, sequenceId: Int): Packet {
            val reqPushMsg = decodeUniPacket(OnlinePushPack.SvcReqPushMsg.serializer(), "req")
            reqPushMsg.vMsgInfos.forEach { msgInfo: MsgInfo ->
                msgInfo.vMsg!!.read {
                    when {
                        msgInfo.shMsgType.toInt() == 732 -> {
                            val group = bot.getGroup(this.readUInt().toLong())

                            when (val internalType = this.readShort().toInt()) {
                                3073 -> { // mute
                                    val operator = group[this.readUInt().toLong()]
                                    this.readUInt().toLong() // time
                                    this.discardExact(2)
                                    val target = this.readUInt().toLong()
                                    val time = this.readInt()

                                    return if (target == 0L) {
                                        if (time == 0) {
                                            GroupMuteAllEvent(origin = true, new = false, operator = operator, group = group)
                                        } else {
                                            GroupMuteAllEvent(origin = false, new = true, operator = operator, group = group)
                                        }
                                    } else {
                                        val member = group[target]
                                        if (time == 0) {
                                            MemberUnmuteEvent(operator = operator, member = member)
                                        } else {
                                            MemberMuteEvent(operator = operator, member = member, durationSeconds = time)
                                        }
                                    }
                                }
                                3585 -> { // 匿名
                                    val operator = group[this.readUInt().toLong()]
                                    return GroupAllowAnonymousChatEvent(
                                        origin = group.anonymousChat,
                                        new = this.readInt() == 0,
                                        operator = operator,
                                        group = group
                                    )
                                }
                                4096 -> {
                                    val dataBytes = this.readBytes(26)
                                    val message = this.readString(this.readByte().toInt())

                                    TODO("读取操作人")

                                    /*
                                    return if (dataBytes[0].toInt() != 59) {
                                        GroupNameChangeEvent(origin = group.name, new = )
                                    } else {
                                        println(message + ":" + dataBytes.toUHexString())
                                        when (message) {
                                            "管理员已关闭群聊坦白说" -> {
                                                GroupAllowConfessTalkEvent(group.confessTalk, false, ope)
                                            }
                                            "管理员已开启群聊坦白说" -> {

                                            }
                                            else -> {
                                                println("Unknown server messages $message")
                                            }
                                        }
                                    }
                                    */
                                }
                                4352 -> {
                                    println(msgInfo.contentToString())
                                    println(msgInfo.vMsg.toUHexString())
                                }
                                else -> {
                                    println("unknown group internal type $internalType , data: " + this.readBytes().toUHexString() + " ")
                                }
                            }
                        }
                        msgInfo.shMsgType.toInt() == 528 -> {
                            val content = msgInfo.vMsg.loadAs(OnlinePushPack.MsgType0x210.serializer())
                            println(content.contentToString())
                        }
                        else -> {
                            println("unknown shtype ${msgInfo.shMsgType.toInt()}")
                        }
                    }
                }
            }

            return NoPacket
        }


        override suspend fun QQAndroidBot.handle(packet: Packet, sequenceId: Int): OutgoingPacket? {
            return buildResponseUniPacket(client, sequenceId = sequenceId) {

            }
        }
    }
}
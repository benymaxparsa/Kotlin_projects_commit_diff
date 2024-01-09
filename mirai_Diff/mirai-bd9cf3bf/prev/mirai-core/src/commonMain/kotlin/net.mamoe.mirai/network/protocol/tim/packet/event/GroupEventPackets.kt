@file:Suppress("EXPERIMENTAL_API_USAGE", "EXPERIMENTAL_UNSIGNED_LITERALS")

package net.mamoe.mirai.network.protocol.tim.packet.event

import kotlinx.io.core.ByteReadPacket
import kotlinx.io.core.discardExact
import kotlinx.io.core.readUInt
import net.mamoe.mirai.event.events.FriendConversationInitializedEvent
import net.mamoe.mirai.event.events.MemberPermissionChangedEvent
import net.mamoe.mirai.network.protocol.tim.packet.CorrespondingEvent
import net.mamoe.mirai.network.protocol.tim.packet.PacketVersion
import net.mamoe.mirai.utils.io.readString
import kotlin.properties.Delegates


/**
 * 群文件上传
 */
class ServerGroupUploadFileEventPacket(input: ByteReadPacket, eventIdentity: EventPacketIdentity) :
    ServerEventPacket(input, eventIdentity) {
    private lateinit var xmlMessage: String

    override fun decode() {
        this.input.discardExact(60)
        val size = this.input.readShort().toInt()
        this.input.discardExact(3)
        xmlMessage = this.input.readString(size)
    }//todo test
}

class GroupMemberNickChangedEventPacket(input: ByteReadPacket, eventIdentity: EventPacketIdentity) :
    ServerEventPacket(input, eventIdentity) {
    private val groupId: UInt get() = eventIdentity.from
    private val group: UInt get() = eventIdentity.from

    override fun decode() {
        //                                     GroupId VarInt
        // 00 00 00 08 00 0A 00 04 01 00 00 00 22 96 29 7B 01 01 00 00 F3 66 00 00 00 05 00 00 00 EE 00 00 00 05
        // TODO ? 数据中没有哪个人的昵称改变了
    }
}

/**
 * 好友发起会话, 即在输入框输入了任意内容.
 */
@CorrespondingEvent(FriendConversationInitializedEvent::class)
@PacketVersion(date = "2019.11.2", timVersion = "2.3.2.21173")
class FriendConversationInitializedEventPacket(input: ByteReadPacket, eventIdentity: EventPacketIdentity) :
    ServerEventPacket(input, eventIdentity) {
    var qq: UInt by Delegates.notNull()

    // 00 00 00 00 3E 03 3F A2 00
    override fun decode() = with(input) {
        discardExact(4)// 00 00 00 00
        qq = readUInt()
    }
}

@CorrespondingEvent(MemberPermissionChangedEvent::class)
@PacketVersion(date = "2019.11.2", timVersion = "2.3.2.21173")
class GroupMemberPermissionChangedEventPacket internal constructor(
    input: ByteReadPacket,
    eventIdentity: EventPacketIdentity
) :
    ServerEventPacket(input, eventIdentity) {
    val groupId: UInt get() = eventIdentity.from
    var qq: UInt = 0u
    lateinit var kind: MemberPermissionChangedEvent.Kind

    override fun decode(): Unit = with(input) {
        // 群里一个人变成管理员:
        // 00 00 00 08 00 0A 00 04 01 00 00 00 22 96 29 7B 01 01 76 E4 B8 DD 01
        // 取消管理员
        // 00 00 00 08 00 0A 00 04 01 00 00 00 22 96 29 7B 01 00 76 E4 B8 DD 00
        discardExact(remaining - 5)
        qq = readUInt()
        kind = when (readByte().toInt()) {
            0x00 -> MemberPermissionChangedEvent.Kind.NO_LONGER_OPERATOR
            0x01 -> MemberPermissionChangedEvent.Kind.BECOME_OPERATOR
            else -> {
                error("Could not determine permission change kind")
            }
        }
    }
}

class ServerGroupUnknownChangedEventPacket(input: ByteReadPacket, eventIdentity: EventPacketIdentity) :
    ServerEventPacket(input, eventIdentity) {

    override fun decode() = with(input) {
        //00 00 00 08 00 0A 00 04 01 00 00 00 22 96 29 7B 01 01 00 00 F3 55 00 00 00 05 00 00 00 E9 00 00 00 05
        //00 00 00 08 00 0A 00 04 01 00 00 00 22 96 29 7B 01 01 00 00 F3 56 00 00 00 05 00 00 00 EA 00 00 00 05
    }
}
@file:Suppress("EXPERIMENTAL_UNSIGNED_LITERALS", "EXPERIMENTAL_API_USAGE")

package net.mamoe.mirai.network.protocol.tim.packet.event

import kotlinx.io.core.ByteReadPacket
import kotlinx.io.core.discardExact
import kotlinx.io.core.readUInt
import net.mamoe.mirai.Bot
import net.mamoe.mirai.network.protocol.tim.packet.Packet
import net.mamoe.mirai.network.protocol.tim.packet.PacketVersion


data class MemberPermissionChangePacket(
    val groupId: UInt,
    val qq: UInt,
    val kind: Kind
) : Packet {
    enum class Kind {
        /**
         * 变成管理员
         */
        BECOME_OPERATOR,
        /**
         * 不再是管理员
         */
        NO_LONGER_OPERATOR,
    } // TODO: 2019/11/2 变成群主的情况
}

@PacketVersion(date = "2019.11.1", timVersion = "2.3.2.21173")
object GroupMemberPermissionChangedEventFactory : KnownEventParserAndHandler<MemberPermissionChangePacket>(0x002Cu) {
    override suspend fun ByteReadPacket.parse(bot: Bot, identity: EventPacketIdentity): MemberPermissionChangePacket {
        // 群里一个人变成管理员:
        // 00 00 00 08 00 0A 00 04 01 00 00 00 22 96 29 7B 01 01 76 E4 B8 DD 01
        // 取消管理员
        // 00 00 00 08 00 0A 00 04 01 00 00 00 22 96 29 7B 01 00 76 E4 B8 DD 00
        discardExact(remaining - 5)
        val qq = readUInt()
        val kind = when (readByte().toInt()) {
            0x00 -> MemberPermissionChangePacket.Kind.NO_LONGER_OPERATOR
            0x01 -> MemberPermissionChangePacket.Kind.BECOME_OPERATOR
            else -> error("Could not determine permission change kind")
        }
        return MemberPermissionChangePacket(identity.from, qq, kind)
    }
}

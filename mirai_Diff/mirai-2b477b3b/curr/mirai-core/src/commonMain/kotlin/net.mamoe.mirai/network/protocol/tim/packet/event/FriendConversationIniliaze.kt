@file:Suppress("EXPERIMENTAL_API_USAGE", "EXPERIMENTAL_UNSIGNED_LITERALS")

package net.mamoe.mirai.network.protocol.tim.packet.event

import kotlinx.io.core.ByteReadPacket
import kotlinx.io.core.discardExact
import kotlinx.io.core.readUInt
import net.mamoe.mirai.Bot
import net.mamoe.mirai.network.protocol.tim.packet.PacketVersion


@PacketVersion(date = "2019.11.2", timVersion = "2.3.2.21173")
data class FriendConversationInitialize(
    val qq: UInt
) : EventPacket

object FriendConversationInitializedEventParserAndHandler : KnownEventParserAndHandler<FriendConversationInitialize>(0x0079u) {
    override suspend fun ByteReadPacket.parse(bot: Bot, identity: EventPacketIdentity): FriendConversationInitialize {
        discardExact(4)// 00 00 00 00
        return FriendConversationInitialize(readUInt())
    }

}

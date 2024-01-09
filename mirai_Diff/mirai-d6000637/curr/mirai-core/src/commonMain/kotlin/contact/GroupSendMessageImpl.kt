/*
 * Copyright 2020 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/mamoe/mirai/blob/master/LICENSE
 */

package net.mamoe.mirai.internal.contact

import net.mamoe.mirai.contact.Contact
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.MessageTooLargeException
import net.mamoe.mirai.event.broadcast
import net.mamoe.mirai.event.events.EventCancelledException
import net.mamoe.mirai.event.events.GroupMessagePostSendEvent
import net.mamoe.mirai.event.events.GroupMessagePreSendEvent
import net.mamoe.mirai.internal.MiraiImpl
import net.mamoe.mirai.internal.forwardMessage
import net.mamoe.mirai.internal.longMessage
import net.mamoe.mirai.internal.message.*
import net.mamoe.mirai.internal.network.protocol.packet.chat.image.ImgStore
import net.mamoe.mirai.internal.network.protocol.packet.chat.receive.MessageSvcPbSendMsg
import net.mamoe.mirai.internal.network.protocol.packet.chat.receive.createToGroup
import net.mamoe.mirai.message.MessageReceipt
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.utils.currentTimeSeconds

/**
 * Might be recalled with [transformedMessage] `is` [LongMessageInternal] if length estimation failed ([sendMessagePacket])
 */
internal suspend fun GroupImpl.sendMessageImpl(
    originalMessage: Message,
    transformedMessage: Message,
    forceAsLongMessage: Boolean,
): MessageReceipt<Group> {
    val chain = transformedMessage
        .transformSpecialMessages(this)
        .convertToLongMessageIfNeeded(originalMessage, forceAsLongMessage, this)

    chain.findIsInstance<QuoteReply>()?.source?.ensureSequenceIdAvailable()

    chain.asSequence().filterIsInstance<FriendImage>().forEach { image ->
        updateFriendImageForGroupMessage(image)
    }

    return sendMessagePacket(
        originalMessage,
        chain,
        allowResendAsLongMessage = transformedMessage.takeSingleContent<LongMessageInternal>() == null
    )
}


/**
 * Called only in 'public' apis.
 */
internal suspend fun GroupImpl.broadcastGroupMessagePreSendEvent(message: Message): MessageChain {
    return kotlin.runCatching {
        GroupMessagePreSendEvent(this, message).broadcast()
    }.onSuccess {
        check(!it.isCancelled) {
            throw EventCancelledException("cancelled by GroupMessagePreSendEvent")
        }
    }.getOrElse {
        throw EventCancelledException("exception thrown when broadcasting GroupMessagePreSendEvent", it)
    }.message.toMessageChain()
}


/**
 * - [ForwardMessage] -> [ForwardMessageInternal] (by uploading through highway)
 * - ... any others for future
 */
private suspend fun Message.transformSpecialMessages(contact: Contact): MessageChain {
    return takeSingleContent<ForwardMessage>()?.let { forward ->
        check(forward.nodeList.size <= 200) {
            throw MessageTooLargeException(
                contact, forward, forward,
                "ForwardMessage allows up to 200 nodes, but found ${forward.nodeList.size}"
            )
        }

        val resId = MiraiImpl.uploadGroupMessageHighway(contact.bot, contact.id, forward.nodeList, false)
        RichMessage.forwardMessage(
            resId = resId,
            timeSeconds = currentTimeSeconds(),
            forwardMessage = forward,
        )
    }?.toMessageChain() ?: toMessageChain()
}

/**
 * Final process
 */
private suspend fun GroupImpl.sendMessagePacket(
    originalMessage: Message,
    finalMessage: MessageChain,
    allowResendAsLongMessage: Boolean,
): MessageReceipt<Group> {
    val group = this

    val result = bot.network.runCatching sendMsg@{
        val source: OnlineMessageSourceToGroupImpl
        MessageSvcPbSendMsg.createToGroup(bot.client, group, finalMessage) {
            source = it
        }.sendAndExpect<MessageSvcPbSendMsg.Response>().let { resp ->
            if (resp is MessageSvcPbSendMsg.Response.MessageTooLarge) {
                if (allowResendAsLongMessage) {
                    return@sendMsg sendMessageImpl(originalMessage, finalMessage, true)
                } else throw MessageTooLargeException(
                    group,
                    originalMessage,
                    finalMessage,
                    "Message '${finalMessage.content.take(10)}' is too large."
                )
            }
            check(resp is MessageSvcPbSendMsg.Response.SUCCESS) {
                "Send group message failed: $resp"
            }
        }

        try {
            source.ensureSequenceIdAvailable()
        } catch (e: Exception) {
            bot.network.logger.warning(
                "Timeout awaiting sequenceId for group message(${finalMessage.content.take(10)}). Some features may not work properly",
                e

            )
        }

        MessageReceipt(source, group)
    }

    GroupMessagePostSendEvent(this, finalMessage, result.exceptionOrNull(), result.getOrNull()).broadcast()

    return result.getOrThrow()
}

private suspend fun GroupImpl.uploadGroupLongMessageHighway(
    chain: MessageChain
) = MiraiImpl.uploadGroupMessageHighway(
    bot, this.id,
    listOf(
        ForwardMessage.Node(
            senderId = bot.id,
            time = currentTimeSeconds().toInt(),
            messageChain = chain,
            senderName = bot.nick
        )
    ),
    true
)

private suspend fun MessageChain.convertToLongMessageIfNeeded(
    originalMessage: Message,
    forceAsLongMessage: Boolean,
    groupImpl: GroupImpl,
): MessageChain {
    if (forceAsLongMessage || this.shouldSendAsLongMessage(originalMessage, groupImpl)) {
        val resId = groupImpl.uploadGroupLongMessageHighway(this)

        return this + RichMessage.longMessage(
            brief = takeContent(27),
            resId = resId,
            timeSeconds = currentTimeSeconds()
        ) // LongMessageInternal replaces all contents and preserves metadata
    }

    return this
}

/**
 * Ensures server holds the cache
 */
private suspend fun GroupImpl.updateFriendImageForGroupMessage(image: FriendImage) {
    bot.network.run {
        ImgStore.GroupPicUp(
            bot.client,
            uin = bot.id,
            groupCode = id,
            md5 = image.md5,
            size = if (image is OnlineFriendImageImpl) image.delegate.fileLen else 0
        ).sendAndExpect<ImgStore.GroupPicUp.Response>()
    }
}

private fun MessageChain.shouldSendAsLongMessage(originalMessage: Message, target: Contact): Boolean {
    val length = verityLength(originalMessage, target)

    return length > 700 || countImages() > 1
}
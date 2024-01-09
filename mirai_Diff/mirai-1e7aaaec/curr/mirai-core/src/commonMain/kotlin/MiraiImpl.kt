/*
 * Copyright 2019-2020 Mamoe Technologies and contributors.
 *
 *  此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 *  Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 *  https://github.com/mamoe/mirai/blob/master/LICENSE
 */

package net.mamoe.mirai.internal

import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.mamoe.mirai.*
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.data.*
import net.mamoe.mirai.event.broadcast
import net.mamoe.mirai.event.events.BotInvitedJoinGroupRequestEvent
import net.mamoe.mirai.event.events.MemberJoinRequestEvent
import net.mamoe.mirai.event.events.MessageRecallEvent
import net.mamoe.mirai.event.events.NewFriendRequestEvent
import net.mamoe.mirai.internal.contact.FriendImpl
import net.mamoe.mirai.internal.contact.GroupImpl
import net.mamoe.mirai.internal.contact.MemberInfoImpl
import net.mamoe.mirai.internal.contact.checkIsGroupImpl
import net.mamoe.mirai.internal.message.*
import net.mamoe.mirai.internal.network.highway.HighwayHelper
import net.mamoe.mirai.internal.network.protocol.data.proto.ImMsgBody
import net.mamoe.mirai.internal.network.protocol.data.proto.LongMsg
import net.mamoe.mirai.internal.network.protocol.packet.chat.*
import net.mamoe.mirai.internal.network.protocol.packet.chat.voice.PttStore
import net.mamoe.mirai.internal.network.protocol.packet.list.FriendList
import net.mamoe.mirai.internal.utils.MiraiPlatformUtils
import net.mamoe.mirai.internal.utils.encodeToString
import net.mamoe.mirai.internal.utils.io.serialization.toByteArray
import net.mamoe.mirai.message.MessageReceipt
import net.mamoe.mirai.message.action.Nudge
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.utils.BotConfiguration
import net.mamoe.mirai.utils.MiraiExperimentalApi
import net.mamoe.mirai.utils.currentTimeSeconds
import kotlin.jvm.JvmSynthetic
import kotlin.math.absoluteValue
import kotlin.random.Random

@OptIn(LowLevelApi::class)
internal object MiraiImpl : Mirai, LowLevelApiAccessor {
    override val BotFactory: BotFactory
        get() = BotFactoryImpl

    @OptIn(LowLevelApi::class)
    override suspend fun acceptNewFriendRequest(event: NewFriendRequestEvent) {
        @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
        check(event.responded.compareAndSet(expect = false, update = true)) {
            "the request $this has already been responded"
        }

        check(!event.bot.friends.contains(event.fromId)) {
            "the request $event is outdated: You had already responded it on another device."
        }

        _lowLevelSolveNewFriendRequestEvent(
            event.bot,
            eventId = event.eventId,
            fromId = event.fromId,
            fromNick = event.fromNick,
            accept = true,
            blackList = false
        )
    }

    override suspend fun rejectNewFriendRequest(event: NewFriendRequestEvent, blackList: Boolean) {
        @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
        check(event.responded.compareAndSet(false, true)) {
            "the request $event has already been responded"
        }

        check(!event.bot.friends.contains(event.fromId)) {
            "the request $event is outdated: You had already responded it on another device."
        }

        _lowLevelSolveNewFriendRequestEvent(
            event.bot,
            eventId = event.eventId,
            fromId = event.fromId,
            fromNick = event.fromNick,
            accept = false,
            blackList = blackList
        )
    }

    override suspend fun acceptMemberJoinRequest(event: MemberJoinRequestEvent) {
        @Suppress("DuplicatedCode")
        checkGroupPermission(event.bot, event.group) { event::class.simpleName ?: "<anonymous class>" }
        @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
        check(event.responded.compareAndSet(expect = false, update = true)) {
            "the request $this has already been responded"
        }

        check(!event.group.members.contains(event.fromId)) {
            "the request $this is outdated: Another operator has already responded it."
        }

        _lowLevelSolveMemberJoinRequestEvent(
            bot = event.bot,
            eventId = event.eventId,
            fromId = event.fromId,
            fromNick = event.fromNick,
            groupId = event.groupId,
            accept = true,
            blackList = false
        )
    }

    @Suppress("DuplicatedCode")
    override suspend fun rejectMemberJoinRequest(event: MemberJoinRequestEvent, blackList: Boolean, message: String) {
        checkGroupPermission(event.bot, event.group) { event::class.simpleName ?: "<anonymous class>" }
        @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
        check(event.responded.compareAndSet(false, true)) {
            "the request $this has already been responded"
        }

        check(!event.group.members.contains(event.fromId)) {
            "the request $this is outdated: Another operator has already responded it."
        }

        _lowLevelSolveMemberJoinRequestEvent(
            bot = event.bot,
            eventId = event.eventId,
            fromId = event.fromId,
            fromNick = event.fromNick,
            groupId = event.groupId,
            accept = false,
            blackList = blackList,
            message = message
        )
    }

    private inline fun checkGroupPermission(eventBot: Bot, eventGroup: Group, eventName: () -> String) {
        val group = eventBot.getGroupOrNull(eventGroup.id)
            ?: kotlin.run {
                error(
                    "A ${eventName()} is outdated. Group ${eventGroup.id} not found for bot ${eventBot.id}. " +
                            "This is because bot isn't in the group anymore"
                )

            }

        group.checkBotPermission(MemberPermission.ADMINISTRATOR)
    }

    override suspend fun ignoreMemberJoinRequest(event: MemberJoinRequestEvent, blackList: Boolean) {
        checkGroupPermission(event.bot, event.group) { event::class.simpleName ?: "<anonymous class>" }
        @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
        check(event.responded.compareAndSet(expect = false, update = true)) {
            "the request $this has already been responded"
        }

        _lowLevelSolveMemberJoinRequestEvent(
            bot = event.bot,
            eventId = event.eventId,
            fromId = event.fromId,
            fromNick = event.fromNick,
            groupId = event.groupId,
            accept = null,
            blackList = blackList
        )
    }

    override suspend fun acceptInvitedJoinGroupRequest(event: BotInvitedJoinGroupRequestEvent) =
        solveInvitedJoinGroupRequest(event, accept = true)

    override suspend fun ignoreInvitedJoinGroupRequest(event: BotInvitedJoinGroupRequestEvent) =
        solveInvitedJoinGroupRequest(event, accept = false)


    private suspend fun solveInvitedJoinGroupRequest(event: BotInvitedJoinGroupRequestEvent, accept: Boolean) {
        @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
        check(event.responded.compareAndSet(false, true)) {
            "the request $this has already been responded"
        }

        check(!event.bot.groups.contains(event.groupId)) {
            "the request $this is outdated: Bot has been already in the group."
        }

        _lowLevelSolveBotInvitedJoinGroupRequestEvent(
            bot = event.bot,
            eventId = event.eventId,
            invitorId = event.invitorId,
            groupId = event.groupId,
            accept = accept
        )
    }

    @LowLevelApi
    override fun _lowLevelNewFriend(bot: Bot, friendInfo: FriendInfo): Friend {
        return FriendImpl(
            bot.asQQAndroidBot(),
            bot.coroutineContext + SupervisorJob(bot.supervisorJob),
            friendInfo.uin,
            friendInfo
        )
    }


    @OptIn(LowLevelApi::class)
    override suspend fun _lowLevelQueryGroupList(bot: Bot): Sequence<Long> {
        bot.asQQAndroidBot()
        return bot.network.run {
            FriendList.GetTroopListSimplify(bot.client)
                .sendAndExpect<FriendList.GetTroopListSimplify.Response>(retry = 2)
        }.groups.asSequence().map { it.groupUin.shl(32) and it.groupCode }
    }

    @OptIn(LowLevelApi::class)
    override suspend fun _lowLevelQueryGroupMemberList(
        bot: Bot,
        groupUin: Long,
        groupCode: Long,
        ownerId: Long
    ): Sequence<MemberInfo> =
        bot.asQQAndroidBot().network.run {
            var nextUin = 0L
            var sequence = sequenceOf<MemberInfoImpl>()
            while (true) {
                val data = FriendList.GetTroopMemberList(
                    client = bot.client,
                    targetGroupUin = groupUin,
                    targetGroupCode = groupCode,
                    nextUin = nextUin
                ).sendAndExpect<FriendList.GetTroopMemberList.Response>(retry = 3)
                sequence += data.members.asSequence().map { troopMemberInfo ->
                    MemberInfoImpl(troopMemberInfo, ownerId)
                }
                nextUin = data.nextUin
                if (nextUin == 0L) {
                    break
                }
            }
            return sequence
        }

    @Suppress("RemoveExplicitTypeArguments") // false positive
    override suspend fun recall(bot: Bot, source: MessageSource) = bot.asQQAndroidBot().run {
        check(source is MessageSourceInternal)

        source.ensureSequenceIdAvailable()

        @Suppress("BooleanLiteralArgument", "INVISIBLE_REFERENCE", "INVISIBLE_MEMBER") // false positive
        check(!source.isRecalledOrPlanned.value && source.isRecalledOrPlanned.compareAndSet(false, true)) {
            "$source had already been recalled."
        }

        val response: PbMessageSvc.PbMsgWithDraw.Response = when (source) {
            is MessageSourceToGroupImpl,
            is MessageSourceFromGroupImpl
            -> {
                val group = when (source) {
                    is MessageSourceToGroupImpl -> source.target
                    is MessageSourceFromGroupImpl -> source.group
                    else -> error("stub")
                }
                if (bot.id != source.fromId) {
                    group.checkBotPermission(MemberPermission.ADMINISTRATOR)
                }
                @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
                MessageRecallEvent.GroupRecall(
                    bot,
                    source.fromId,
                    source.id,
                    source.internalId,
                    source.time,
                    null,
                    group
                ).broadcast()

                network.run {
                    PbMessageSvc.PbMsgWithDraw.createForGroupMessage(
                        bot.asQQAndroidBot().client,
                        group.id,
                        source.sequenceId,
                        source.internalId
                    ).sendAndExpect<PbMessageSvc.PbMsgWithDraw.Response>()
                }
            }
            is MessageSourceFromFriendImpl,
            is MessageSourceToFriendImpl
            -> network.run {
                check(source.fromId == bot.id) {
                    "can only recall a message sent by bot"
                }
                PbMessageSvc.PbMsgWithDraw.createForFriendMessage(
                    bot.client,
                    source.targetId,
                    source.sequenceId,
                    source.internalId,
                    source.time
                ).sendAndExpect<PbMessageSvc.PbMsgWithDraw.Response>()
            }
            is MessageSourceFromTempImpl,
            is MessageSourceToTempImpl
            -> network.run {
                check(source.fromId == bot.id) {
                    "can only recall a message sent by bot"
                }
                source as MessageSourceToTempImpl
                PbMessageSvc.PbMsgWithDraw.createForTempMessage(
                    bot.client,
                    (source.target.group as GroupImpl).uin,
                    source.targetId,
                    source.sequenceId,
                    source.internalId,
                    source.time
                ).sendAndExpect<PbMessageSvc.PbMsgWithDraw.Response>()
            }
            is OfflineMessageSource -> network.run {
                when (source.kind) {
                    OfflineMessageSource.Kind.FRIEND -> {
                        check(source.fromId == bot.id) {
                            "can only recall a message sent by bot"
                        }
                        PbMessageSvc.PbMsgWithDraw.createForFriendMessage(
                            bot.client,
                            source.targetId,
                            source.sequenceId,
                            source.internalId,
                            source.time
                        ).sendAndExpect<PbMessageSvc.PbMsgWithDraw.Response>()
                    }
                    OfflineMessageSource.Kind.TEMP -> {
                        check(source.fromId == bot.id) {
                            "can only recall a message sent by bot"
                        }
                        PbMessageSvc.PbMsgWithDraw.createForTempMessage(
                            bot.client,
                            source.targetId, // groupUin
                            source.targetId, // memberUin
                            source.sequenceId,
                            source.internalId,
                            source.time
                        ).sendAndExpect<PbMessageSvc.PbMsgWithDraw.Response>()
                    }
                    OfflineMessageSource.Kind.GROUP -> {
                        PbMessageSvc.PbMsgWithDraw.createForGroupMessage(
                            bot.client,
                            source.targetId,
                            source.sequenceId,
                            source.internalId
                        ).sendAndExpect<PbMessageSvc.PbMsgWithDraw.Response>()
                    }
                }
            }
            else -> error("stub!")
        }


        // 1001: No message meets the requirements (实际上是没权限, 管理员在尝试撤回群主的消息)
        // 154: timeout
        // 3: <no message>
        check(response is PbMessageSvc.PbMsgWithDraw.Response.Success) { "Failed to recall message #${source.id}: $response" }
    }

    @LowLevelApi
    @MiraiExperimentalApi
    override suspend fun _lowLevelGetAnnouncements(
        bot: Bot,
        groupId: Long,
        page: Int,
        amount: Int
    ): GroupAnnouncementList = bot.asQQAndroidBot().run {
        val rep = bot.asQQAndroidBot().network.run {
            MiraiPlatformUtils.Http.post<String> {
                url("https://web.qun.qq.com/cgi-bin/announce/list_announce")
                body = MultiPartFormDataContent(formData {
                    append("qid", groupId)
                    append("bkn", bot.bkn)
                    append("ft", 23)  //好像是一个用来识别应用的参数
                    append("s", if (page == 1) 0 else -(page * amount + 1))  // 第一页这里的参数应该是-1
                    append("n", amount)
                    append("ni", if (page == 1) 1 else 0)
                    append("format", "json")
                })
                headers {
                    append(
                        "cookie",
                        "uin=o${id}; skey=${client.wLoginSigInfo.sKey.data.encodeToString()}; p_uin=o${id};"
                    )
                }
            }
        }
//        bot.network.logger.error(rep)
        return bot.json.decodeFromString(GroupAnnouncementList.serializer(), rep)
    }

    @LowLevelApi
    @MiraiExperimentalApi
    override suspend fun _lowLevelSendAnnouncement(bot: Bot, groupId: Long, announcement: GroupAnnouncement): String =
        bot.asQQAndroidBot().run {
            val rep = withContext(network.coroutineContext) {
                MiraiPlatformUtils.Http.post<String> {
                    url("https://web.qun.qq.com/cgi-bin/announce/add_qun_notice")
                    body = MultiPartFormDataContent(formData {
                        append("qid", groupId)
                        append("bkn", bkn)
                        append("text", announcement.msg.text)
                        append("pinned", announcement.pinned)
                        append(
                            "settings",
                            json.encodeToString(
                                GroupAnnouncementSettings.serializer(),
                                announcement.settings ?: GroupAnnouncementSettings()
                            )
                        )
                        append("format", "json")
                    })
                    headers {
                        append(
                            "cookie",
                            "uin=o${id};" +
                                    " skey=${client.wLoginSigInfo.sKey.data.encodeToString()};" +
                                    " p_uin=o${id};" +
                                    " p_skey=${client.wLoginSigInfo.psKeyMap["qun.qq.com"]?.data?.encodeToString()}; "
                        )
                    }
                }
            }
            val jsonObj = json.parseToJsonElement(rep)
            return jsonObj.jsonObject["new_fid"]?.jsonPrimitive?.content
                ?: throw throw IllegalStateException("Send Announcement fail group:$groupId msg:${jsonObj.jsonObject["em"]} content:${announcement.msg.text}")
        }

    @LowLevelApi
    @MiraiExperimentalApi
    override suspend fun _lowLevelDeleteAnnouncement(bot: Bot, groupId: Long, fid: String) = bot.asQQAndroidBot().run {
        val data = withContext(network.coroutineContext) {
            MiraiPlatformUtils.Http.post<String> {
                url("https://web.qun.qq.com/cgi-bin/announce/del_feed")
                body = MultiPartFormDataContent(formData {
                    append("qid", groupId)
                    append("bkn", bkn)
                    append("fid", fid)
                    append("format", "json")
                })
                headers {
                    append(
                        "cookie",
                        "uin=o${id};" +
                                " skey=${client.wLoginSigInfo.sKey.data.encodeToString()};" +
                                " p_uin=o${id};" +
                                " p_skey=${client.wLoginSigInfo.psKeyMap["qun.qq.com"]?.data?.encodeToString()}; "
                    )
                }
            }
        }
        val jsonObj = json.parseToJsonElement(data)
        if (jsonObj.jsonObject["ec"]?.jsonPrimitive?.int ?: 1 != 0) {
            throw throw IllegalStateException("delete Announcement fail group:$groupId msg:${jsonObj.jsonObject["em"]} fid:$fid")
        }
    }

    @LowLevelApi
    @MiraiExperimentalApi
    override suspend fun _lowLevelGetAnnouncement(bot: Bot, groupId: Long, fid: String): GroupAnnouncement =
        bot.asQQAndroidBot().run {
            val rep = network.run {
                MiraiPlatformUtils.Http.post<String> {
                    url("https://web.qun.qq.com/cgi-bin/announce/get_feed")
                    body = MultiPartFormDataContent(formData {
                        append("qid", groupId)
                        append("bkn", bkn)
                        append("fid", fid)
                        append("format", "json")
                    })
                    headers {
                        append(
                            "cookie",
                            "uin=o${id}; skey=${client.wLoginSigInfo.sKey.data.encodeToString()}; p_uin=o${id};"
                        )
                    }
                }
            }

//        bot.network.logger.error(rep)
            return json.decodeFromString(GroupAnnouncement.serializer(), rep)

        }

    @LowLevelApi
    @MiraiExperimentalApi
    override suspend fun _lowLevelGetGroupActiveData(bot: Bot, groupId: Long, page: Int): GroupActiveData =
        bot.asQQAndroidBot().run {
            val rep = network.run {
                MiraiPlatformUtils.Http.get<String> {
                    url("https://qqweb.qq.com/c/activedata/get_mygroup_data")
                    parameter("bkn", bkn)
                    parameter("gc", groupId)
                    if (page != -1) {
                        parameter("page", page)
                    }
                    headers {
                        append(
                            "cookie",
                            "uin=o${id}; skey=${client.wLoginSigInfo.sKey.data.encodeToString()}; p_uin=o${id};"
                        )
                    }
                }
            }
            return json.decodeFromString(GroupActiveData.serializer(), rep)
        }

    @LowLevelApi
    @MiraiExperimentalApi
    override suspend fun _lowLevelGetGroupHonorListData(
        bot: Bot,
        groupId: Long,
        type: GroupHonorType
    ): GroupHonorListData? = bot.asQQAndroidBot().run {
        val rep = network.run {
            MiraiPlatformUtils.Http.get<String> {
                url("https://qun.qq.com/interactive/honorlist")
                parameter("gc", groupId)
                parameter("type", type.value)
                headers {
                    append(
                        "cookie",
                        "uin=o${id};" +
                                " skey=${client.wLoginSigInfo.sKey.data.encodeToString()};" +
                                " p_uin=o${id};" +
                                " p_skey=${client.wLoginSigInfo.psKeyMap["qun.qq.com"]?.data?.encodeToString()}; "
                    )
                }
            }
        }
        val jsonText = Regex("""window.__INITIAL_STATE__=(.+?)</script>""").find(rep)?.groupValues?.get(1)
        return jsonText?.let { json.decodeFromString(GroupHonorListData.serializer(), it) }
    }

    @JvmSynthetic
    @LowLevelApi
    @MiraiExperimentalApi
    @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
    internal suspend fun lowLevelSendGroupLongOrForwardMessage(
        bot: Bot,
        groupCode: Long,
        message: Collection<ForwardMessage.INode>,
        isLong: Boolean,
        forwardMessage: ForwardMessage?
    ): MessageReceipt<Group> = bot.asQQAndroidBot().run {
        message.forEach {
            it.message.ensureSequenceIdAvailable()
        }

        val group = getGroup(groupCode)

        val time = currentTimeSeconds
        val sequenceId = client.atomicNextMessageSequenceId()

        network.run {
            val data = message.calculateValidationDataForGroup(
                sequenceId = sequenceId,
                random = Random.nextInt().absoluteValue,
                groupCode = groupCode
            )

            val response =
                MultiMsg.ApplyUp.createForGroupLongMessage(
                    buType = if (isLong) 1 else 2,
                    client = bot.client,
                    messageData = data,
                    dstUin = Group.calculateGroupUinByGroupCode(groupCode)
                ).sendAndExpect<MultiMsg.ApplyUp.Response>()

            val resId: String
            when (response) {
                is MultiMsg.ApplyUp.Response.MessageTooLarge ->
                    error(
                        "Internal error: message is too large, but this should be handled before sending. "
                    )
                is MultiMsg.ApplyUp.Response.RequireUpload -> {
                    resId = response.proto.msgResid

                    val body = LongMsg.ReqBody(
                        subcmd = 1,
                        platformType = 9,
                        termType = 5,
                        msgUpReq = listOf(
                            LongMsg.MsgUpReq(
                                msgType = 3, // group
                                dstUin = Group.calculateGroupUinByGroupCode(groupCode),
                                msgId = 0,
                                msgUkey = response.proto.msgUkey,
                                needCache = 0,
                                storeType = 2,
                                msgContent = data.data
                            )
                        )
                    ).toByteArray(LongMsg.ReqBody.serializer())

                    HighwayHelper.uploadImageToServers(
                        bot,
                        response.proto.uint32UpIp.zip(response.proto.uint32UpPort),
                        response.proto.msgSig,
                        MiraiPlatformUtils.md5(body),
                        net.mamoe.mirai.utils.internal.asReusableInput0(body), // don't use toLongUnsigned: Overload resolution ambiguity
                        "group long message",
                        27
                    )
                }
            }

            return if (isLong) {
                group.sendMessage(
                    RichMessage.longMessage(
                        brief = message.joinToString(limit = 27) { it.message.contentToString() },
                        resId = resId,
                        timeSeconds = time
                    )
                )
            } else {
                checkNotNull(forwardMessage) { "Internal error: forwardMessage is null when sending forward" }
                group.sendMessage(
                    RichMessage.forwardMessage(
                        resId = resId,
                        timeSeconds = time,
                        //  preview = message.take(5).joinToString {
                        //      """
                        //          <title size="26" color="#777777" maxLines="2" lineSpace="12">${it.message.asMessageChain().joinToString(limit = 10)}</title>
                        //      """.trimIndent()
                        //  },
                        preview = forwardMessage.displayStrategy.generatePreview(forwardMessage).take(4)
                            .map {
                                """<title size="26" color="#777777" maxLines="2" lineSpace="12">$it</title>"""
                            }.joinToString(""),
                        title = forwardMessage.displayStrategy.generateTitle(forwardMessage),
                        brief = forwardMessage.displayStrategy.generateBrief(forwardMessage),
                        source = forwardMessage.displayStrategy.generateSource(forwardMessage),
                        summary = forwardMessage.displayStrategy.generateSummary(forwardMessage)
                    )
                )
            }
        }
    }


    @LowLevelApi
    @MiraiExperimentalApi
    override suspend fun _lowLevelSolveNewFriendRequestEvent(
        bot: Bot,
        eventId: Long,
        fromId: Long,
        fromNick: String,
        accept: Boolean,
        blackList: Boolean
    ): Unit = bot.asQQAndroidBot().run {
        network.apply {
            NewContact.SystemMsgNewFriend.Action(
                bot.client,
                eventId = eventId,
                fromId = fromId,
                accept = accept,
                blackList = blackList
            ).sendWithoutExpect()
            @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
            bot.friends.delegate.addLast(_lowLevelNewFriend(bot, object : FriendInfo {
                override val uin: Long get() = fromId
                override val nick: String get() = fromNick
                override val remark: String get() = ""
            }))
        }
    }

    @LowLevelApi
    @MiraiExperimentalApi
    override suspend fun _lowLevelSolveBotInvitedJoinGroupRequestEvent(
        bot: Bot,
        eventId: Long,
        invitorId: Long,
        groupId: Long,
        accept: Boolean
    ) = bot.asQQAndroidBot().run {
        network.run {
            NewContact.SystemMsgNewGroup.Action(
                bot.client,
                eventId = eventId,
                fromId = invitorId,
                groupId = groupId,
                isInvited = true,
                accept = accept
            ).sendWithoutExpect()
        }
    }

    @LowLevelApi
    @MiraiExperimentalApi
    override suspend fun _lowLevelSolveMemberJoinRequestEvent(
        bot: Bot,
        eventId: Long,
        fromId: Long,
        fromNick: String,
        groupId: Long,
        accept: Boolean?,
        blackList: Boolean,
        message: String
    ) = bot.asQQAndroidBot().run {
        network.run {
            NewContact.SystemMsgNewGroup.Action(
                bot.client,
                eventId = eventId,
                fromId = fromId,
                groupId = groupId,
                isInvited = false,
                accept = accept,
                blackList = blackList,
                message = message
            ).sendWithoutExpect()
        }

        if (accept ?: return@run)
            groups[groupId].apply {
                @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
                members.delegate.addLast(newMember(object : MemberInfo {
                    override val nameCard: String get() = ""
                    override val permission: MemberPermission get() = MemberPermission.MEMBER
                    override val specialTitle: String get() = ""
                    override val muteTimestamp: Int get() = 0
                    override val uin: Long get() = fromId
                    override val nick: String get() = fromNick
                    override val remark: String
                        get() = ""
                }))
            }
    }

    @OptIn(ExperimentalStdlibApi::class)
    @LowLevelApi
    override suspend fun _lowLevelQueryGroupVoiceDownloadUrl(
        bot: Bot,
        md5: ByteArray,
        groupId: Long,
        dstUin: Long
    ): String {
        bot.asQQAndroidBot().network.run {
            val response: PttStore.GroupPttDown.Response.DownLoadInfo =
                PttStore.GroupPttDown(bot.client, groupId, dstUin, md5).sendAndExpect()
            return "http://${response.strDomain}${response.downPara.encodeToString()}"
        }
    }

    @LowLevelApi
    override suspend fun _lowLevelUploadVoice(bot: Bot, md5: ByteArray, groupId: Long) {

    }

    override fun createImage(imageId: String): Image {
        return when {
            imageId matches FRIEND_IMAGE_ID_REGEX_1 -> OfflineFriendImage(imageId)
            imageId matches FRIEND_IMAGE_ID_REGEX_2 -> OfflineFriendImage(imageId)
            imageId matches GROUP_IMAGE_ID_REGEX -> OfflineGroupImage(imageId)
            else ->
                @Suppress("INVISIBLE_MEMBER")
                throw IllegalArgumentException("Illegal imageId: $imageId. $ILLEGAL_IMAGE_ID_EXCEPTION_MESSAGE")
        }
    }

    @Suppress("DEPRECATION", "OverridingDeprecatedMember")
    override suspend fun queryImageUrl(bot: Bot, image: Image): String = when (image) {
        is ConstOriginUrlAware -> image.originUrl
        is DeferredOriginUrlAware -> image.getUrl(bot)
        is SuspendDeferredOriginUrlAware -> image.getUrl(bot)
        else -> error("Internal error: unsupported image class: ${image::class.simpleName}")
    }

    @Suppress("CANNOT_OVERRIDE_INVISIBLE_MEMBER")
    override suspend fun sendNudge(bot: Bot, nudge: Nudge, receiver: Contact): Boolean {
        if (bot.configuration.protocol != BotConfiguration.MiraiProtocol.ANDROID_PHONE) {
            throw UnsupportedOperationException("nudge is supported only with protocol ANDROID_PHONE")
        }
        bot.asQQAndroidBot()

        bot.network.run {
            return if (receiver is Group) {
                receiver.checkIsGroupImpl()
                NudgePacket.troopInvoke(
                    client = bot.client,
                    messageReceiverGroupCode = receiver.id,
                    nudgeTargetId = nudge.target.id,
                ).sendAndExpect<NudgePacket.Response>().success
            } else {
                NudgePacket.friendInvoke(
                    client = bot.client,
                    messageReceiverUin = receiver.id,
                    nudgeTargetId = nudge.target.id,
                ).sendAndExpect<NudgePacket.Response>().success
            }
        }
    }

    override fun constructMessageSource(
        bot: Bot,
        kind: OfflineMessageSource.Kind,
        fromUin: Long,
        targetUin: Long,
        id: Int,
        time: Int,
        internalId: Int,
        originalMessage: MessageChain
    ): OfflineMessageSource {
        return object : OfflineMessageSource(), MessageSourceInternal {
            override val kind: Kind get() = kind
            override val id: Int get() = id
            override val bot: Bot get() = bot
            override val time: Int get() = time
            override val fromId: Long get() = fromUin
            override val targetId: Long get() = targetUin
            override val originalMessage: MessageChain get() = originalMessage
            override val sequenceId: Int = id
            override val internalId: Int = internalId

            @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
            override var isRecalledOrPlanned: net.mamoe.mirai.event.internal.MiraiAtomicBoolean =
                net.mamoe.mirai.event.internal.MiraiAtomicBoolean(false)

            override fun toJceData(): ImMsgBody.SourceMsg {
                return ImMsgBody.SourceMsg(
                    origSeqs = listOf(sequenceId),
                    senderUin = fromUin,
                    toUin = 0,
                    flag = 1,
                    elems = originalMessage.toRichTextElems(forGroup = kind == Kind.GROUP, withGeneralFlags = false),
                    type = 0,
                    time = time,
                    pbReserve = EMPTY_BYTE_ARRAY,
                    srcMsg = EMPTY_BYTE_ARRAY
                )
            }
        }
    }

}
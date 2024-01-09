package net.mamoe.mirai.qqandroid

import net.mamoe.mirai.BotAccount
import net.mamoe.mirai.BotImpl
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.data.AddFriendResult
import net.mamoe.mirai.data.ImageLink
import net.mamoe.mirai.message.data.Image
import net.mamoe.mirai.qqandroid.network.QQAndroidBotNetworkHandler
import net.mamoe.mirai.qqandroid.network.QQAndroidClient
import net.mamoe.mirai.qqandroid.utils.Context
import net.mamoe.mirai.qqandroid.utils.ImageIdQQA
import net.mamoe.mirai.utils.BotConfiguration
import net.mamoe.mirai.utils.LockFreeLinkedList
import net.mamoe.mirai.utils.MiraiInternalAPI
import kotlin.coroutines.CoroutineContext

@UseExperimental(MiraiInternalAPI::class)
internal expect class QQAndroidBot constructor(
    context: Context,
    account: BotAccount,
    configuration: BotConfiguration
) : QQAndroidBotBase

@UseExperimental(MiraiInternalAPI::class)
internal abstract class QQAndroidBotBase constructor(
    context: Context,
    account: BotAccount,
    configuration: BotConfiguration
) : BotImpl<QQAndroidBotNetworkHandler>(account, configuration) {
    val client: QQAndroidClient = QQAndroidClient(context, account, bot = @Suppress("LeakingThis") this as QQAndroidBot)
    override val uin: Long get() = client.uin
    override val qqs: ContactList<QQ> = ContactList(LockFreeLinkedList())

    override fun getQQ(id: Long): QQ {
        return qqs.delegate.filteringGetOrAdd({ it.id == id }, { QQImpl(this, coroutineContext, id) })
    }

    override fun createNetworkHandler(coroutineContext: CoroutineContext): QQAndroidBotNetworkHandler {
        return QQAndroidBotNetworkHandler(this as QQAndroidBot)
    }

    override val groups: ContactList<Group> = ContactList(LockFreeLinkedList())

    override suspend fun getGroup(id: GroupId): Group {
        return groups.delegate.filteringGetOrAdd({ it.id == id.value }, { GroupImpl(this, coroutineContext, id.value) })
    }

    override suspend fun getGroup(internalId: GroupInternalId): Group {
        internalId.toId().value.let { id ->
            return groups.delegate.filteringGetOrAdd({ it.id == id }, { GroupImpl(this, coroutineContext, id) })
        }
    }

    override suspend fun getGroup(id: Long): Group {
        return groups.delegate.filteringGetOrAdd({ it.id == id }, { GroupImpl(this, coroutineContext, id) })
    }

    override suspend fun Image.getLink(): ImageLink {
        require(this.id is ImageIdQQA) { "image.id must be ImageIdQQA" }
        return (this.id as ImageIdQQA).link
    }

    override suspend fun addFriend(id: Long, message: String?, remark: String?): AddFriendResult {
        TODO("not implemented")
    }

    override suspend fun approveFriendAddRequest(id: Long, remark: String?) {
        TODO("not implemented")
    }
}
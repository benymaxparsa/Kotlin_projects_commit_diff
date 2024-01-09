package net.mamoe.mirai.console

import net.mamoe.mirai.Bot

/**
 * 只需要实现一个这个 传入MiraiConsole 就可以绑定UI层与Console层
 * 注意线程
 */

interface MiraiConsoleUI {
    /**
     * 让UI层展示一条log
     *
     * identity：log所属的screen, Main=0; Bot=Bot.uin
     */
    fun pushLog(
        identity: Long,
        message: String
    )

    /**
     * 让UI层准备接受新增的一个BOT
     */
    fun prePushBot(
        identity: Long
    )

    /**
     * 让UI层接受一个新的bot
     * */
    fun pushBot(
        bot: Bot
    )


    fun pushVersion(
        consoleVersion: String,
        consoleBuild: String,
        coreVersion: String
    )

    /**
     * 让UI层提供一个Input
     * 这个Input 不 等于 Command
     *
     */
    suspend fun requestInput(
        question: String
    ): String


    /**
     * 让UI层更新BOT管理员的数据
     */
    fun pushBotAdminStatus(
        identity: Long,
        admins: List<Long>
    )

}
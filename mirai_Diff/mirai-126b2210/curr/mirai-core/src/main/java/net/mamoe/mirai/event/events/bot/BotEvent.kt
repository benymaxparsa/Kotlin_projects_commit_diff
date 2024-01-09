package net.mamoe.mirai.event.events.bot

import net.mamoe.mirai.Bot
import net.mamoe.mirai.event.Event
import kotlin.reflect.KClass

/**
 * @author Him188moe
 */
abstract class BotEvent(val bot: Bot) : Event()

class BotLoginSucceedEvent(bot: Bot) : BotEvent(bot) {
    companion object : KClass<BotLoginSucceedEvent> by BotLoginSucceedEvent::class
}
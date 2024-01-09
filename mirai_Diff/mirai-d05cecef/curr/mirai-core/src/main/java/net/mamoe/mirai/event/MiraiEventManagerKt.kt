@file:JvmName("MiraiEventManagerKt")

package net.mamoe.mirai.event

import kotlin.reflect.KClass

/**
 * [MiraiEventManager] 的 kotlin 简易化实现.
 * 若要 hook 一个事件, 你可以:
 * FriendMessageEvent::class.hookOnce {}
 * FriendMessageEvent::class.hookAlways {}
 *
 * @author Him188moe
 */

object EventManager : MiraiEventManager()

/**
 * 每次事件触发时都会调用 hook
 */
fun <C : Class<E>, E : MiraiEvent> C.hookAlways(hook: (E) -> Unit) {
    MiraiEventManager.getInstance().hookAlways(MiraiEventHook<E>(this, hook))
}

/**
 * 当下一次事件触发时调用 hook
 */
fun <C : Class<E>, E : MiraiEvent> C.hookOnce(hook: (E) -> Unit) {
    MiraiEventManager.getInstance().hookOnce(MiraiEventHook<E>(this, hook))
}

/**
 * 每次事件触发时都会调用 hook, 直到 hook 返回 false 时停止 hook
 */
fun <C : Class<E>, E : MiraiEvent> C.hookWhile(hook: (E) -> Boolean) {
    MiraiEventManager.getInstance().hookAlways(MiraiEventHookSimple(this, hook))
}


/**
 * 每次事件触发时都会调用 hook
 */
fun <C : KClass<E>, E : MiraiEvent> C.hookAlways(hook: (E) -> Unit) {
    this.java.hookAlways(hook)
}

/**
 * 当下一次事件触发时调用 hook
 */
fun <C : KClass<E>, E : MiraiEvent> C.hookOnce(hook: (E) -> Unit) {
    this.java.hookOnce(hook)
}

/**
 * 每次事件触发时都会调用 hook, 直到 hook 返回 false 时停止 hook
 */
fun <C : KClass<E>, E : MiraiEvent> C.hookWhile(hook: (E) -> Boolean) {
    this.java.hookWhile(hook)
}


private class MiraiEventHookSimple<E : MiraiEvent>(clazz: Class<E>, val hook: (E) -> Boolean) : MiraiEventHook<E>(clazz) {
    override fun accept(event: MiraiEvent?): Boolean {
        @Suppress("UNCHECKED_CAST")
        return !hook.invoke(event as E)
    }
}
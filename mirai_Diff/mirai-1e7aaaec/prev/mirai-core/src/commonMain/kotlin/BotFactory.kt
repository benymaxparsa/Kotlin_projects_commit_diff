/*
 * Copyright 2019-2020 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AFFERO GENERAL PUBLIC LICENSE version 3 license that can be found via the following link.
 *
 * https://github.com/mamoe/mirai/blob/master/LICENSE
 */
@file:Suppress(
    "FunctionName", "INAPPLICABLE_JVM_NAME", "DEPRECATION_ERROR", "DeprecatedCallableAddReplaceWith",
    "OverridingDeprecatedMember"
)

package net.mamoe.mirai.internal

import net.mamoe.mirai.Bot
import net.mamoe.mirai.BotFactory
import net.mamoe.mirai.internal.QQAndroid.Bot
import net.mamoe.mirai.utils.BotConfiguration
import net.mamoe.mirai.utils.Context
import kotlin.jvm.JvmName

/**
 * QQ for Android
 */
@Suppress("INAPPLICABLE_JVM_NAME")
expect object QQAndroid : BotFactory {

    /**
     * 使用指定的 [配置][configuration] 构造 [Bot] 实例
     */
    @JvmName("newBot")
    override fun Bot(context: Context, qq: Long, password: String, configuration: BotConfiguration): Bot

    /**
     * 使用指定的 [配置][configuration] 构造 [Bot] 实例
     */
    @JvmName("newBot")
    override fun Bot(
        context: Context,
        qq: Long,
        passwordMd5: ByteArray,
        configuration: BotConfiguration
    ): Bot
}
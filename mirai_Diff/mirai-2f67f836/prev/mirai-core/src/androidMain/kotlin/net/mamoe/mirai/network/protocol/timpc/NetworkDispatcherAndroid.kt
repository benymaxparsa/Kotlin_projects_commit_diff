package net.mamoe.mirai.network.protocol.timpc

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

/**
 * 包处理协程调度器.
 *
 * JVM: 独立的 4 thread 调度器
 */
internal actual val NetworkDispatcher: CoroutineDispatcher = Executors.newFixedThreadPool(4).asCoroutineDispatcher()
/*******************************************************************************
 *                                                                             *
 *  Copyright (C) 2019 by Max Lv <max.c.lv@gmail.com>                          *
 *  Copyright (C) 2019 by Mygod Studio <contact-shadowsocks-android@mygod.be>  *
 *                                                                             *
 *  This program is free software: you can redistribute it and/or modify       *
 *  it under the terms of the GNU General Public License as published by       *
 *  the Free Software Foundation, either version 3 of the License, or          *
 *  (at your option) any later version.                                        *
 *                                                                             *
 *  This program is distributed in the hope that it will be useful,            *
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 *  GNU General Public License for more details.                               *
 *                                                                             *
 *  You should have received a copy of the GNU General Public License          *
 *  along with this program. If not, see <http://www.gnu.org/licenses/>.       *
 *                                                                             *
 *******************************************************************************/

package com.github.shadowsocks.net

import com.github.shadowsocks.utils.printLog
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.sendBlocking
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.*

class ChannelMonitor : Thread("ChannelMonitor") {
    private data class Registration(val channel: SelectableChannel,
                                    val ops: Int,
                                    val listener: (SelectionKey) -> Unit) {
        val result = CompletableDeferred<SelectionKey>()
    }

    private val selector = Selector.open()
    private val registrationPipe = Pipe.open()
    private val pendingRegistrations = Channel<Registration>(Channel.UNLIMITED)
    private val closeChannel = Channel<Unit>(1)
    @Volatile
    private var running = true

    private fun registerInternal(channel: SelectableChannel, ops: Int, block: (SelectionKey) -> Unit) =
            channel.register(selector, ops, block)

    init {
        registrationPipe.source().apply {
            configureBlocking(false)
            registerInternal(this, SelectionKey.OP_READ) {
                val junk = ByteBuffer.allocateDirect(1)
                while (read(junk) > 0) {
                    pendingRegistrations.poll()!!.apply {
                        try {
                            result.complete(registerInternal(channel, ops, listener))
                        } catch (e: ClosedChannelException) {
                            result.completeExceptionally(e)
                        }
                    }
                    junk.clear()
                }
            }
        }
        start()
    }

    suspend fun register(channel: SelectableChannel, ops: Int, block: (SelectionKey) -> Unit): SelectionKey {
        val registration = Registration(channel, ops, block)
        pendingRegistrations.send(registration)
        ByteBuffer.allocateDirect(1).also { junk ->
            loop@ while (running) when (registrationPipe.sink().write(junk)) {
                0 -> kotlinx.coroutines.yield()
                1 -> break@loop
                else -> throw IOException("Failed to register in the channel")
            }
        }
        if (!running) throw ClosedChannelException()
        return registration.result.await()
    }

    suspend fun wait(channel: SelectableChannel, ops: Int) = CompletableDeferred<SelectionKey>().run {
        register(channel, ops) {
            if (it.isValid) it.interestOps(0)       // stop listening
            complete(it)
        }
        await()
    }

    override fun run() {
        while (running) {
            val num = try {
                selector.select()
            } catch (e: IOException) {
                printLog(e)
                continue
            }
            if (num <= 0) continue
            val iterator = selector.selectedKeys().iterator()
            while (iterator.hasNext()) {
                val key = iterator.next()
                iterator.remove()
                (key.attachment() as (SelectionKey) -> Unit)(key)
            }
        }
        closeChannel.sendBlocking(Unit)
    }

    fun close(scope: CoroutineScope) {
        running = false
        selector.wakeup()
        scope.launch {
            closeChannel.receive()
            selector.keys().forEach { it.channel().close() }
            selector.close()
        }
    }
}

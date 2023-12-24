/*******************************************************************************
 *                                                                             *
 *  Copyright (C) 2017 by Max Lv <max.c.lv@gmail.com>                          *
 *  Copyright (C) 2017 by Mygod Studio <contact-shadowsocks-android@mygod.be>  *
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

package com.github.shadowsocks.bg

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.*
import android.util.Log
import androidx.core.content.getSystemService
import androidx.core.os.bundleOf
import com.crashlytics.android.Crashlytics
import com.github.shadowsocks.Core
import com.github.shadowsocks.Core.app
import com.github.shadowsocks.aidl.IShadowsocksService
import com.github.shadowsocks.aidl.IShadowsocksServiceCallback
import com.github.shadowsocks.aidl.TrafficStats
import com.github.shadowsocks.core.R
import com.github.shadowsocks.plugin.PluginManager
import com.github.shadowsocks.preference.DataStore
import com.github.shadowsocks.utils.*
import com.google.firebase.analytics.FirebaseAnalytics
import java.io.File
import java.net.UnknownHostException
import java.util.*

/**
 * This object uses WeakMap to simulate the effects of multi-inheritance.
 */
object BaseService {
    /**
     * IDLE state is only used by UI and will never be returned by BaseService.
     */
    const val IDLE = 0
    const val CONNECTING = 1
    const val CONNECTED = 2
    const val STOPPING = 3
    const val STOPPED = 4

    const val CONFIG_FILE = "shadowsocks.conf"
    const val CONFIG_FILE_UDP = "shadowsocks-udp.conf"

    class Data internal constructor(private val service: Interface) {
        @Volatile var state = STOPPED
        val processes = GuardedProcessPool()
        @Volatile var proxy: ProxyInstance? = null
        @Volatile var udpFallback: ProxyInstance? = null

        val callbacks = RemoteCallbackList<IShadowsocksServiceCallback>()
        val bandwidthListeners = HashSet<IBinder>() // the binder is the real identifier

        var notification: ServiceNotification? = null
        val closeReceiver = broadcastReceiver { _, intent ->
            when (intent.action) {
                Action.RELOAD -> service.forceLoad()
                else -> service.stopRunner(true)
            }
        }
        var closeReceiverRegistered = false

        val binder = object : IShadowsocksService.Stub() {
            override fun getState(): Int = this@Data.state
            override fun getProfileName(): String = proxy?.profile?.name ?: "Idle"

            override fun registerCallback(cb: IShadowsocksServiceCallback) {
                callbacks.register(cb)
            }

            private fun registerTimeout() =
                    Core.handler.postAtTime(this::onTimeout, this, SystemClock.uptimeMillis() + 1000)
            private fun onTimeout() {
                val proxies = listOfNotNull(proxy, udpFallback)
                val stats = proxies
                        .map { Pair(it.profile.id, it.trafficMonitor?.requestUpdate()) }
                        .filter { it.second != null }
                        .map { Triple(it.first, it.second!!.first, it.second!!.second) }
                if (stats.any { it.third } && state == CONNECTED && bandwidthListeners.isNotEmpty()) {
                    val sum = stats.fold(TrafficStats()) { a, b -> a + b.second }
                    val n = callbacks.beginBroadcast()
                    for (i in 0 until n) try {
                        val item = callbacks.getBroadcastItem(i)
                        if (bandwidthListeners.contains(item.asBinder())) {
                            stats.forEach { (id, stats) -> item.trafficUpdated(id, stats) }
                            item.trafficUpdated(0, sum)
                        }
                    } catch (e: Exception) {
                        printLog(e)
                    }
                    callbacks.finishBroadcast()
                }
                registerTimeout()
            }

            override fun startListeningForBandwidth(cb: IShadowsocksServiceCallback) {
                val wasEmpty = bandwidthListeners.isEmpty()
                if (bandwidthListeners.add(cb.asBinder())) {
                    if (wasEmpty) registerTimeout()
                    if (state != CONNECTED) return
                    var sum = TrafficStats()
                    val proxy = proxy ?: return
                    proxy.trafficMonitor?.out.also { stats ->
                        cb.trafficUpdated(proxy.profile.id, if (stats == null) sum else {
                            sum += stats
                            stats
                        })
                    }
                    udpFallback?.also { udpFallback ->
                        udpFallback.trafficMonitor?.out.also { stats ->
                            cb.trafficUpdated(udpFallback.profile.id, if (stats == null) TrafficStats() else {
                                sum += stats
                                stats
                            })
                        }
                    }
                    cb.trafficUpdated(0, sum)
                }
            }

            override fun stopListeningForBandwidth(cb: IShadowsocksServiceCallback) {
                if (bandwidthListeners.remove(cb.asBinder()) && bandwidthListeners.isEmpty()) {
                    Core.handler.removeCallbacksAndMessages(this)
                }
            }

            override fun unregisterCallback(cb: IShadowsocksServiceCallback) {
                stopListeningForBandwidth(cb)   // saves an RPC, and safer
                callbacks.unregister(cb)
            }
        }

        fun changeState(s: Int, msg: String? = null) {
            if (state == s && msg == null) return
            if (callbacks.registeredCallbackCount > 0) Core.handler.post {
                val n = callbacks.beginBroadcast()
                for (i in 0 until n) try {
                    callbacks.getBroadcastItem(i).stateChanged(s, binder.profileName, msg)
                } catch (e: Exception) {
                    printLog(e)
                }
                callbacks.finishBroadcast()
            }
            state = s
        }
    }
    interface Interface {
        val tag: String

        fun onBind(intent: Intent): IBinder? = if (intent.action == Action.SERVICE) data.binder else null

        fun forceLoad() {
            val (profile, fallback) = Core.currentProfile
                    ?: return stopRunner(true, (this as Context).getString(R.string.profile_empty))
            if (profile.host.isEmpty() || profile.password.isEmpty() ||
                    fallback != null && (fallback.host.isEmpty() || fallback.password.isEmpty())) {
                stopRunner(true, (this as Context).getString(R.string.proxy_empty))
                return
            }
            val s = data.state
            when (s) {
                STOPPED -> startRunner()
                CONNECTED -> {
                    stopRunner(false)
                    startRunner()
                }
                else -> Crashlytics.log(Log.WARN, tag, "Illegal state when invoking use: $s")
            }
        }

        fun buildAdditionalArguments(cmd: ArrayList<String>): ArrayList<String> = cmd

        fun startNativeProcesses() {
            val configRoot = (if (Build.VERSION.SDK_INT < 24 || app.getSystemService<UserManager>()
                            ?.isUserUnlocked != false) app else Core.deviceStorage).noBackupFilesDir
            val udpFallback = data.udpFallback
            data.proxy!!.start(this,
                    File(Core.deviceStorage.noBackupFilesDir, "stat_main"),
                    File(configRoot, CONFIG_FILE),
                    if (udpFallback == null) "-u" else null)
            check(udpFallback?.pluginPath == null)
            udpFallback?.start(this,
                    File(Core.deviceStorage.noBackupFilesDir, "stat_udp"),
                    File(configRoot, CONFIG_FILE_UDP),
                    "-U")
        }

        fun createNotification(profileName: String): ServiceNotification

        fun startRunner() {
            this as Context
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(Intent(this, javaClass))
            else startService(Intent(this, javaClass))
        }

        fun killProcesses() = data.processes.killAll()

        fun stopRunner(stopService: Boolean, msg: String? = null) {
            // channge the state
            val data = data
            data.changeState(STOPPING)

            Core.analytics.logEvent("stop", bundleOf(Pair(FirebaseAnalytics.Param.METHOD, tag)))

            killProcesses()

            // clean up recevier
            this as Service
            if (data.closeReceiverRegistered) {
                unregisterReceiver(data.closeReceiver)
                data.closeReceiverRegistered = false
            }

            data.notification?.destroy()
            data.notification = null

            val ids = listOfNotNull(data.proxy, data.udpFallback).map {
                it.close()
                it.profile.id
            }
            data.proxy = null
            if (ids.isNotEmpty()) Core.handler.post {
                if (data.bandwidthListeners.isNotEmpty()) {
                    val n = data.callbacks.beginBroadcast()
                    for (i in 0 until n) {
                        try {
                            val item = data.callbacks.getBroadcastItem(i)
                            if (data.bandwidthListeners.contains(item.asBinder())) ids.forEach(item::trafficPersisted)
                        } catch (e: Exception) {
                            printLog(e)
                        }
                    }
                    data.callbacks.finishBroadcast()
                }
            }

            // change the state
            data.changeState(STOPPED, msg)

            // stop the service if nothing has bound to it
            if (stopService) stopSelf()
        }

        val data: Data get() = instances[this]!!

        fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
            val data = data
            if (data.state != STOPPED) return Service.START_NOT_STICKY
            val profilePair = Core.currentProfile
            this as Context
            if (profilePair == null) {
                // gracefully shutdown: https://stackoverflow.com/q/47337857/2245107
                data.notification = createNotification("")
                stopRunner(true, getString(R.string.profile_empty))
                return Service.START_NOT_STICKY
            }
            val (profile, fallback) = profilePair
            profile.name = profile.formattedName    // save name for later queries
            val proxy = ProxyInstance(profile)
            data.proxy = proxy
            if (fallback != null) data.udpFallback = ProxyInstance(fallback, profile.route)

            if (!data.closeReceiverRegistered) {
                // register close receiver
                val filter = IntentFilter()
                filter.addAction(Action.RELOAD)
                filter.addAction(Intent.ACTION_SHUTDOWN)
                filter.addAction(Action.CLOSE)
                registerReceiver(data.closeReceiver, filter)
                data.closeReceiverRegistered = true
            }

            data.notification = createNotification(profile.formattedName)
            Core.analytics.logEvent("start", bundleOf(Pair(FirebaseAnalytics.Param.METHOD, tag)))

            data.changeState(CONNECTING)

            thread("$tag-Connecting") {
                try {
                    proxy.init()
                    data.udpFallback?.init()
                    // Clean up
                    killProcesses()

                    startNativeProcesses()

                    proxy.scheduleUpdate()
                    data.udpFallback?.scheduleUpdate()
                    RemoteConfig.fetch()

                    data.changeState(CONNECTED)
                } catch (_: UnknownHostException) {
                    stopRunner(true, getString(R.string.invalid_server))
                } catch (exc: Throwable) {
                    if (exc !is PluginManager.PluginNotFoundException && exc !is VpnService.NullConnectionException) {
                        printLog(exc)
                    }
                    stopRunner(true, "${getString(R.string.service_failed)}: ${exc.localizedMessage}")
                }
            }
            return Service.START_NOT_STICKY
        }
    }

    private val instances = WeakHashMap<Interface, Data>()
    internal fun register(instance: Interface) {
        instances[instance] = Data(instance)
        RemoteConfig.fetch()
    }

    val usingVpnMode: Boolean get() = DataStore.serviceMode == Key.modeVpn
    val serviceClass get() = when (DataStore.serviceMode) {
        Key.modeProxy -> ProxyService::class
        Key.modeVpn -> VpnService::class
        Key.modeTransproxy -> TransproxyService::class
        else -> throw UnknownError()
    }
}

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
import android.content.Intent
import com.github.shadowsocks.Core
import com.github.shadowsocks.preference.DataStore
import java.io.File

class TransproxyService : Service(), LocalDnsService.Interface {
    init {
        BaseService.register(this)
    }

    override val tag: String get() = "ShadowsocksTransproxyService"
    override fun createNotification(profileName: String): ServiceNotification =
            ServiceNotification(this, profileName, "service-transproxy", true)

    override fun onBind(intent: Intent) = super.onBind(intent)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
            super<LocalDnsService.Interface>.onStartCommand(intent, flags, startId)

    private fun startDNSTunnel() {
        val proxy = data.proxy!!
        val cmd = arrayListOf(File(applicationInfo.nativeLibraryDir, Executable.SS_TUNNEL).absolutePath,
                "-t", "10",
                "-b", DataStore.listenAddress,
                "-u",
                "-l", DataStore.portLocalDns.toString(),    // ss-tunnel listens on the same port as overture
                "-L", proxy.profile.remoteDns.split(",").first().trim() + ":53",
                "-c", proxy.configFile!!.absolutePath)      // config is already built by BaseService.Interface
        if (DataStore.tcpFastOpen) cmd += "--fast-open"
        data.processes.start(cmd)
    }

    private fun startRedsocksDaemon() {
        File(Core.deviceStorage.noBackupFilesDir, "redsocks.conf").writeText("""base {
 log_debug = off;
 log_info = off;
 log = stderr;
 daemon = off;
 redirector = iptables;
}
redsocks {
 local_ip = ${DataStore.listenAddress};
 local_port = ${DataStore.portTransproxy};
 ip = 127.0.0.1;
 port = ${DataStore.portProxy};
 type = socks5;
}
""")
        data.processes.start(listOf(
                File(applicationInfo.nativeLibraryDir, Executable.REDSOCKS).absolutePath, "-c", "redsocks.conf"))
    }

    override fun startNativeProcesses() {
        startRedsocksDaemon()
        super.startNativeProcesses()
        if (data.proxy!!.profile.udpdns) startDNSTunnel()
    }

    override fun onDestroy() {
        super<Service>.onDestroy()
        super<LocalDnsService.Interface>.onDestroy()
    }
}

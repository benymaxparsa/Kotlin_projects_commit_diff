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

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.support.annotation.RequiresApi
import com.github.shadowsocks.App.Companion.app
import com.github.shadowsocks.R
import com.github.shadowsocks.ShadowsocksConnection
import com.github.shadowsocks.aidl.IShadowsocksService
import com.github.shadowsocks.aidl.IShadowsocksServiceCallback
import android.service.quicksettings.TileService as BaseTileService

@RequiresApi(Build.VERSION_CODES.N)
class TileService : BaseTileService(), ShadowsocksConnection.Interface {
    private val iconIdle by lazy { Icon.createWithResource(this, R.drawable.ic_start_idle).setTint(0x79ffffff) }
    private val iconBusy by lazy { Icon.createWithResource(this, R.drawable.ic_start_busy) }
    private val iconConnected by lazy { Icon.createWithResource(this, R.drawable.ic_start_connected) }

    override val serviceCallback: IShadowsocksServiceCallback.Stub by lazy {
        @RequiresApi(Build.VERSION_CODES.N)
        object : IShadowsocksServiceCallback.Stub() {
            override fun stateChanged(state: Int, profileName: String?, msg: String?) {
                val tile = qsTile ?: return
                when (state) {
                    BaseService.STOPPED -> {
                        tile.icon = iconIdle
                        tile.label = getString(R.string.app_name)
                        tile.state = Tile.STATE_INACTIVE
                    }
                    BaseService.CONNECTED -> {
                        tile.icon = iconConnected
                        tile.label = profileName ?: getString(R.string.app_name)
                        tile.state = Tile.STATE_ACTIVE
                    }
                    else -> {
                        tile.icon = iconBusy
                        tile.label = getString(R.string.app_name)
                        tile.state = Tile.STATE_UNAVAILABLE
                    }
                }
                tile.updateTile()
            }
            override fun trafficUpdated(profileId: Int, txRate: Long, rxRate: Long, txTotal: Long, rxTotal: Long) { }
            override fun trafficPersisted(profileId: Int) { }
        }
    }

    override fun onServiceConnected(service: IShadowsocksService) =
            serviceCallback.stateChanged(service.state, service.profileName, null)

    override fun onStartListening() {
        super.onStartListening()
        connection.connect()
    }
    override fun onStopListening() {
        super.onStopListening()
        connection.disconnect()
    }

    override fun onClick() {
        if (isLocked) unlockAndRun(this::toggle) else toggle()
    }

    private fun toggle() {
        val service = connection.service ?: return
        when (service.state) {
            BaseService.STOPPED -> app.startService()
            BaseService.CONNECTED -> app.stopService()
        }
    }
}

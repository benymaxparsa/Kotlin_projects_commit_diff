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
import android.os.IBinder

/**
 * Shadowsocks service at its minimum.
 */
class ProxyService : Service(), BaseService.Interface {
    init {
        BaseService.register(this)
    }

    override val tag: String get() = "ShadowsocksProxyService"
    override fun createNotification(): ServiceNotification =
            ServiceNotification(this, data.profile!!.formattedName, "service-proxy", true)

    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
            super<BaseService.Interface>.onStartCommand(intent, flags, startId)
}

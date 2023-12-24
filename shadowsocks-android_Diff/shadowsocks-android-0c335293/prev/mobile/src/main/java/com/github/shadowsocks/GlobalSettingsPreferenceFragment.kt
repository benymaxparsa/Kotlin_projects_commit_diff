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

package com.github.shadowsocks

import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v14.preference.SwitchPreference
import android.support.v7.preference.Preference
import com.github.shadowsocks.bg.BaseService
import com.github.shadowsocks.preference.DataStore
import com.github.shadowsocks.utils.Key
import com.github.shadowsocks.utils.TcpFastOpen
import com.takisoft.fix.support.v7.preference.PreferenceFragmentCompatDividers

class GlobalSettingsPreferenceFragment : PreferenceFragmentCompatDividers() {
    override fun onCreatePreferencesFix(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.preferenceDataStore = DataStore
        DataStore.initGlobal()
        addPreferencesFromResource(R.xml.pref_global)
        val switch = findPreference(Key.isAutoConnect) as SwitchPreference
        switch.setOnPreferenceChangeListener { _, value ->
            BootReceiver.enabled = value as Boolean
            true
        }
        switch.isChecked = BootReceiver.enabled

        val tfo = findPreference(Key.tfo) as SwitchPreference
        tfo.isChecked = TcpFastOpen.sendEnabled
        tfo.setOnPreferenceChangeListener { _, value ->
            val result = TcpFastOpen.enabled(value as Boolean)
            if (result != null && result != "Success.")
                Snackbar.make(activity!!.findViewById(R.id.snackbar), result, Snackbar.LENGTH_LONG).show()
            value == TcpFastOpen.sendEnabled
        }
        if (!TcpFastOpen.supported) {
            tfo.isEnabled = false
            tfo.summary = getString(R.string.tcp_fastopen_summary_unsupported, System.getProperty("os.version"))
        }

        val serviceMode = findPreference(Key.serviceMode)
        val portProxy = findPreference(Key.portProxy)
        val portLocalDns = findPreference(Key.portLocalDns)
        val portTransproxy = findPreference(Key.portTransproxy)
        val onServiceModeChange = Preference.OnPreferenceChangeListener { _, newValue ->
            val (enabledLocalDns, enabledTransproxy) = when (newValue as String?) {
                Key.modeProxy -> Pair(false, false)
                Key.modeVpn -> Pair(true, false)
                Key.modeTransproxy -> Pair(true, true)
                else -> throw IllegalArgumentException()
            }
            portLocalDns.isEnabled = enabledLocalDns
            portTransproxy.isEnabled = enabledTransproxy
            true
        }
        val listener: (Int) -> Unit = {
            when (it) {
                BaseService.IDLE, BaseService.STOPPED -> {
                    serviceMode.isEnabled = true
                    portProxy.isEnabled = true
                    onServiceModeChange.onPreferenceChange(null, DataStore.serviceMode)
                }
                else -> {
                    serviceMode.isEnabled = false
                    portProxy.isEnabled = false
                    portLocalDns.isEnabled = false
                    portTransproxy.isEnabled = false
                }
            }
        }
        listener((activity as MainActivity).state)
        MainActivity.stateListener = listener
        serviceMode.onPreferenceChangeListener = onServiceModeChange
    }

    override fun onDestroy() {
        MainActivity.stateListener = null
        super.onDestroy()
    }
}

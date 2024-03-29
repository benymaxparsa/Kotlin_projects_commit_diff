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

package com.github.shadowsocks.acl

import android.support.v7.util.SortedList
import android.util.Log
import com.github.shadowsocks.App.Companion.app
import com.github.shadowsocks.utils.Subnet
import com.github.shadowsocks.utils.asIterable
import com.j256.ormlite.field.DatabaseField
import java.io.File
import java.io.FileNotFoundException
import java.io.Reader
import java.net.URL

class Acl {
    companion object {
        const val TAG = "Acl"
        const val ALL = "all"
        const val BYPASS_LAN = "bypass-lan"
        const val BYPASS_CHN = "bypass-china"
        const val BYPASS_LAN_CHN = "bypass-lan-china"
        const val GFWLIST = "gfwlist"
        const val CHINALIST = "china-list"
        const val CUSTOM_RULES = "custom-rules"
        const val CUSTOM_RULES_FLATTENED = "custom-rules-flattened"

        val networkAclParser = "^IMPORT_URL\\s*<(.+)>\\s*$".toRegex()

        fun getFile(id: String) = File(app.filesDir, id + ".acl")
        val customRules: Acl get() {
            val acl = Acl()
            try {
                acl.fromId(CUSTOM_RULES)
            } catch (_: FileNotFoundException) { }
            acl.bypass = true
            acl.bypassHostnames.clear() // everything is bypassed
            return acl
        }
        fun save(id: String, acl: Acl): Unit = getFile(id).bufferedWriter().use { it.write(acl.toString()) }
    }

    private abstract class BaseSorter<T> : SortedList.Callback<T>() {
        override fun onInserted(position: Int, count: Int) { }
        override fun areContentsTheSame(oldItem: T?, newItem: T?): Boolean = oldItem == newItem
        override fun onMoved(fromPosition: Int, toPosition: Int) { }
        override fun onChanged(position: Int, count: Int) { }
        override fun onRemoved(position: Int, count: Int) { }
        override fun areItemsTheSame(item1: T?, item2: T?): Boolean = item1 == item2
        override fun compare(o1: T?, o2: T?): Int =
                if (o1 == null) if (o2 == null) 0 else 1 else if (o2 == null) -1 else compareNonNull(o1, o2)
        abstract fun compareNonNull(o1: T, o2: T): Int
    }
    private open class DefaultSorter<T : Comparable<T>> : BaseSorter<T>() {
        override fun compareNonNull(o1: T, o2: T): Int = o1.compareTo(o2)
    }
    private object StringSorter : DefaultSorter<String>()
    private object SubnetSorter : DefaultSorter<Subnet>()
    private object URLSorter : BaseSorter<URL>() {
        private val ordering = compareBy<URL>({ it.host }, { it.port }, { it.file }, { it.protocol })
        override fun compareNonNull(o1: URL, o2: URL): Int = ordering.compare(o1, o2)
    }

    @DatabaseField(generatedId = true)
    var id = 0
    val bypassHostnames = SortedList(String::class.java, StringSorter)
    val proxyHostnames = SortedList(String::class.java, StringSorter)
    val subnets = SortedList(Subnet::class.java, SubnetSorter)
    val urls = SortedList(URL::class.java, URLSorter)

    @DatabaseField
    var bypass = false

    fun fromAcl(other: Acl): Acl {
        bypassHostnames.clear()
        for (item in other.bypassHostnames.asIterable()) bypassHostnames.add(item)
        proxyHostnames.clear()
        for (item in other.proxyHostnames.asIterable()) proxyHostnames.add(item)
        subnets.clear()
        for (item in other.subnets.asIterable()) subnets.add(item)
        urls.clear()
        for (item in other.urls.asIterable()) urls.add(item)
        bypass = other.bypass
        return this
    }
    fun fromReader(reader: Reader, defaultBypass: Boolean = false): Acl {
        bypassHostnames.clear()
        proxyHostnames.clear()
        subnets.clear()
        urls.clear()
        bypass = defaultBypass
        val bypassSubnets by lazy { SortedList(Subnet::class.java, SubnetSorter) }
        val proxySubnets by lazy { SortedList(Subnet::class.java, SubnetSorter) }
        var hostnames: SortedList<String>? = if (defaultBypass) proxyHostnames else bypassHostnames
        var subnets: SortedList<Subnet>? = if (defaultBypass) proxySubnets else bypassSubnets
        reader.useLines {
            for (line in it) {
                val blocks = line.split('#', limit = 2)
                val url = networkAclParser.matchEntire(blocks.getOrElse(1, {""}))?.groupValues?.getOrNull(1)
                if (url != null) urls.add(URL(url))
                val input = blocks[0].trim()
                when (input) {
                    "[outbound_block_list]" -> {
                        hostnames = null
                        subnets = null
                    }
                    "[black_list]", "[bypass_list]" -> {
                        hostnames = bypassHostnames
                        subnets = bypassSubnets
                    }
                    "[white_list]", "[proxy_list]" -> {
                        hostnames = proxyHostnames
                        subnets = proxySubnets
                    }
                    "[reject_all]", "[bypass_all]" -> bypass = true
                    "[accept_all]", "[proxy_all]" -> bypass = false
                    else -> if (subnets != null && input.isNotEmpty()) try {
                        subnets!!.add(Subnet.fromString(input))
                    } catch (_: IllegalArgumentException) {
                        hostnames!!.add(input)
                    }
                }
            }
        }
        for (item in (if (bypass) proxySubnets else bypassSubnets).asIterable()) this.subnets.add(item)
        return this
    }
    fun fromId(id: String): Acl = fromReader(Acl.getFile(id).bufferedReader())

    fun flatten(depth: Int): Acl {
        if (depth > 0) for (url in urls.asIterable()) {
            val child = Acl().fromReader(url.openStream().bufferedReader(), bypass).flatten(depth - 1)
            if (bypass != child.bypass) {
                Log.w(TAG, "Imported network ACL has a conflicting mode set. " +
                        "This will probably not work as intended. URL: $url")
                child.subnets.clear() // subnets for the different mode are discarded
                child.bypass = bypass
            }
            for (item in child.bypassHostnames.asIterable()) bypassHostnames.add(item)
            for (item in child.proxyHostnames.asIterable()) proxyHostnames.add(item)
            for (item in child.subnets.asIterable()) subnets.add(item)
        }
        urls.clear()
        return this
    }

    override fun toString(): String {
        val result = StringBuilder()
        result.append(if (bypass) "[bypass_all]\n" else "[proxy_all]\n")
        val bypassList = (if (bypass) bypassHostnames.asIterable().asSequence() else
            subnets.asIterable().asSequence().map(Subnet::toString) + proxyHostnames.asIterable().asSequence()).toList()
        val proxyList = (if (bypass) subnets.asIterable().asSequence().map(Subnet::toString) +
                proxyHostnames.asIterable().asSequence() else bypassHostnames.asIterable().asSequence()).toList()
        if (bypassList.isNotEmpty()) {
            result.append("[bypass_list]\n")
            result.append(bypassList.joinToString("\n"))
            result.append('\n')
        }
        if (proxyList.isNotEmpty()) {
            result.append("[proxy_list]\n")
            result.append(proxyList.joinToString("\n"))
            result.append('\n')
        }
        result.append(urls.asIterable().joinToString("") { "#IMPORT_URL <$it>\n" })
        return result.toString()
    }
}

/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.kde.kdeconnect.pingora

/** Bounded in-memory retries. Versions protect replacements from delayed acknowledgements. */
class NoticeOutbox<T>(private val clock: () -> Long, private val limit: Int = 128) {
    private data class Entry<T>(val version: String, val value: T, val created: Long)
    private val entries=LinkedHashMap<String,Entry<T>>()
    @Synchronized fun put(source: String, version: String, value: T) {
        prune();entries.remove(source)
        while(entries.size>=limit) entries.remove(entries.keys.first())
        entries[source]=Entry(version,value,clock())
    }
    @Synchronized fun acknowledge(source: String, version: String) {
        if(entries[source]?.version==version) entries.remove(source)
    }
    @Synchronized fun remove(source: String) {entries.remove(source)}
    @Synchronized fun pending(): List<T> {prune();return entries.values.map {it.value}}
    private fun prune() {val now=clock();entries.entries.removeAll {now-it.value.created !in 0..120_000}}
}

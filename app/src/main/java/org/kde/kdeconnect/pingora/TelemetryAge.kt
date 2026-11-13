/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.kde.kdeconnect.pingora

object TelemetryAge {
    fun label(savedAt: Long, now: Long): String {
        if(savedAt<=0) return "Not synced yet"
        val age=now-savedAt
        if(age<0) return "Saved reading · check phone clock"
        val minutes=age/60_000
        return when {
            minutes<1 -> "Updated just now"
            minutes<60 -> "Updated $minutes min ago"
            minutes<1440 -> "Updated ${minutes/60} hours ago"
            else -> "Updated ${minutes/1440} days ago"
        }
    }
}

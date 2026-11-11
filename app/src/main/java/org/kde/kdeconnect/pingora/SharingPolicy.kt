/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.kde.kdeconnect.pingora

import android.content.Context
import androidx.core.content.edit

object SharingPolicy {
    val features = listOf("notifications", "clipboard", "files", "folders", "battery", "ring", "slumber")
    fun preferences(context: Context) = context.getSharedPreferences("pingora_sharing", Context.MODE_PRIVATE)
    fun allows(context: Context, peer: String, feature: String): Boolean {
        val p = preferences(context)
        return !p.getBoolean("$peer.paused", false) && p.getBoolean("$peer.$feature", false)
    }
    fun set(context: Context, peer: String, feature: String, enabled: Boolean) {
        require(feature in features || feature == "paused")
        preferences(context).edit { putBoolean("$peer.$feature", enabled) }
    }
    fun apps(context: Context): Set<String> = preferences(context).getStringSet("phone_apps", emptySet())?.toSet() ?: emptySet()
}

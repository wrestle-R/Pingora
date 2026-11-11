/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.kde.kdeconnect.pingora

import android.content.Context
import androidx.core.content.edit

object SharingPolicy {
    val features = listOf("notifications", "clipboard", "files", "folders", "battery", "ring", "slumber")
    fun preferences(context: Context) = context.getSharedPreferences("pingora_sharing", Context.MODE_PRIVATE)
    fun allows(context: Context, peer: String, feature: String): Boolean {
        val p = preferences(context)
        return !p.getBoolean("$peer.paused", false) && p.getBoolean("$peer.$feature", feature in features)
    }
    fun set(context: Context, peer: String, feature: String, enabled: Boolean) {
        require(feature in features || feature == "paused")
        preferences(context).edit { putBoolean("$peer.$feature", enabled) }
    }
    fun revokeFolder(context: Context, root: String) {
        val p=preferences(context)
        val roots=try {org.json.JSONObject(p.getString("folder_roots","{}").orEmpty())} catch (_:Exception) {return}
        val uri=roots.optString(root)
        if(uri.isEmpty()) return
        roots.remove(root)
        p.edit {putString("folder_roots",roots.toString())}
        if(roots.keys().asSequence().none {roots.optString(it)==uri}) {
            try {context.contentResolver.releasePersistableUriPermission(android.net.Uri.parse(uri),android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)}catch(_:SecurityException){}
        }
    }
    fun apps(context: Context): Set<String> = preferences(context).getStringSet("phone_apps", setOf("com.whatsapp", "com.whatsapp.w4b", "com.instagram.android", "com.google.android.gm", android.provider.Telephony.Sms.getDefaultSmsPackage(context) ?: "").filter { it.isNotEmpty() }.toSet())?.toSet() ?: emptySet()
}

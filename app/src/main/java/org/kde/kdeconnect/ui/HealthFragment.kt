/* SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL */
package org.kde.kdeconnect.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import org.kde.kdeconnect.KdeConnect

/** Local observations only. No permission grants, feature activation or private payloads. */
class HealthFragment : Fragment() {
    private var content: LinearLayout? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val context = requireContext()
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        content = layout
        return ScrollView(context).apply { addView(layout) }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val context = requireContext()
        val layout = content ?: return
        layout.removeAllViews()
        fun text(value: String) {
            layout.addView(TextView(context).apply {
                text = value
                textSize = 17f
                setPadding(0, 16, 0, 16)
            })
        }
        fun action(label: String, callback: () -> Unit) {
            layout.addView(Button(context).apply {
                text = label
                setOnClickListener { callback() }
            })
        }
        fun settings(action: String, packageUri: Boolean = false) {
            val intent = Intent(action)
            if (packageUri) intent.data = Uri.parse("package:${context.packageName}")
            if (action == Settings.ACTION_APP_NOTIFICATION_SETTINGS) {
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
            try { startActivity(intent) } catch (_: android.content.ActivityNotFoundException) {
                text("This settings screen is unavailable. Open Android Settings, then Apps, then Pingora.")
            }
        }
        text("Connection health")
        val devices = KdeConnect.getInstance().devices.values.toList()
        val paired = devices.filter { it.isPaired }
        text("Paired devices: ${paired.size}. Reachable paired devices: ${paired.count { it.isReachable }}.")
        text("Pairing grants trust to a device. Sharing features remain unavailable in this development build.")
        val notifications = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        text(if (notifications) "App notifications: allowed." else "App notifications: blocked. Allow them to see connection and pairing notices.")
        action("Notification settings") { settings(Settings.ACTION_APP_NOTIFICATION_SETTINGS) }
        if (Build.VERSION.SDK_INT >= 37) {
            val localNetwork = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED
            text(if (localNetwork) "Local network permission: allowed." else "Local network permission: blocked. Allow nearby network access in app permissions to discover your laptop.")
        }
        action("App permissions") { settings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, true) }
        val power = context.getSystemService(PowerManager::class.java)
        text(if (power.isIgnoringBatteryOptimizations(context.packageName)) "Battery optimization: unrestricted." else "Battery optimization: active. Android may interrupt background connections. Review battery settings if disconnects occur.")
        action("Battery optimization settings") { settings(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) }
        text("Discovery: keep both devices on the same trusted local network. Review Trusted networks in Pingora settings if discovery is restricted.")
        text("Notification mirroring, clipboard, files, shared folders, calls and phone Slumber controls: not implemented. App notification permission does not grant access to other apps’ notifications.")
        action("Refresh health") { render() }
    }

    override fun onDestroyView() {
        content = null
        super.onDestroyView()
    }
}

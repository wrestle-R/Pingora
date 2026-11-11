/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.kde.kdeconnect.pingora

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect_tp.BuildConfig

object DiagnosticReport {
    fun create(context:Context):String {
        val devices=KdeConnect.getInstance().devices.values.toList()
        return JSONObject().put("schema",1).put("platform","android").put("app_version",BuildConfig.VERSION_NAME).put("android_api",Build.VERSION.SDK_INT)
            .put("paired_devices",devices.count {it.isPaired}).put("reachable_paired_devices",devices.count {it.isPaired&&it.isReachable})
            .put("notification_access",NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName))
            .put("app_notifications",NotificationManagerCompat.from(context).areNotificationsEnabled())
            .put("local_network_permission",Build.VERSION.SDK_INT<37||ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_LOCAL_NETWORK)==PackageManager.PERMISSION_GRANTED)
            .put("battery_unrestricted",context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName))
            .put("inbox_storage_healthy",LocalInbox.storageHealthy(context)).put("private_payloads_included",false).toString(2)
    }
}

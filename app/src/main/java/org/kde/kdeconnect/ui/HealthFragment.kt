/* SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL */
package org.kde.kdeconnect.ui

import com.google.android.material.button.MaterialButton as Button

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
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import org.kde.kdeconnect.KdeConnect

/** Local observations only. No permission grants, feature activation or private payloads. */
class HealthFragment : Fragment() {
    private val exportReport=registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")) {uri->
        if(uri!=null) try {requireContext().contentResolver.openOutputStream(uri)?.use {it.write(org.kde.kdeconnect.pingora.DiagnosticReport.create(requireContext()).toByteArray(Charsets.UTF_8))}}catch(_:Exception){}
    }
    private val updateImport = org.kde.kdeconnect.pingora.UpdateImportCoordinator<org.kde.kdeconnect.pingora.SignedUpdate.Staged> { it.file.delete() }
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var updateMessage="No signed update imported."
    private val importUpdate=registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) {uri->
        if (uri != null) {
            val ticket = updateImport.begin() ?: return@registerForActivityResult
            updateMessage = "Verifying signed update…"
            render()
            val context = requireContext().applicationContext
            Thread {
                val candidate = try {
                    org.kde.kdeconnect.pingora.SignedUpdate.stage(context, uri)
                } catch (_: Exception) { null }
                mainHandler.post {
                    if (updateImport.complete(ticket, candidate)) {
                        updateMessage = if (candidate != null) "Verified version ${candidate.version}. Installation needs approval and Android confirmation."
                            else "Update rejected. Check signing identity, bundle integrity and version."
                        if (isAdded) render()
                    }
                }
            }.start()
        }
    }

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
        fun action(label: String, enabled: Boolean = true, callback: () -> Unit) {
            layout.addView(Button(context).apply {
                text = label
                isAllCaps = false
                minimumHeight = (52 * resources.displayMetrics.density).toInt()
                isEnabled = enabled
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
        text("Pairing grants trust to a device. Sharing features start enabled for new pairings. Review them in Home → Settings.")
        val notifications = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        text(if (notifications) "App notifications: allowed." else "App notifications: blocked. Allow them to see connection and pairing notices.")
        text(if(org.kde.kdeconnect.pingora.PingoraNotificationListener.instance!=null) "Notification listener: connected." else "Notification listener: not connected. Check notification access.")
        if(Build.VERSION.SDK_INT>=24) action("Reconnect notification listener") {
            val listener=org.kde.kdeconnect.pingora.PingoraNotificationListener.instance
            if(listener!=null) listener.requestUnbind() else org.kde.kdeconnect.pingora.PingoraNotificationListener.ensureConnected(context)
            text("Reconnecting notification listener. Refresh connection to check status.")
        }
        action("Copy harmless clipboard test") {
            context.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText("Pingora test","Pingora clipboard delivery check"))
            android.widget.Toast.makeText(context,"Test text copied. Tap the clipboard widget to send it.",android.widget.Toast.LENGTH_LONG).show()
        }
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
        text("Notification sharing needs separate notification access and selected phone apps in Sharing controls. Clipboard uses an explicit foreground send. Enable files and read-only folders per device in Sharing controls. Slumber requires the matching laptop integration. Optional calls are not part of this testing pass. App notification permission does not grant access to other apps’ notifications.")
        action("Preview sanitized diagnostics") {text(org.kde.kdeconnect.pingora.DiagnosticReport.create(context))}
        action("Export sanitized diagnostics") {exportReport.launch("pingora-diagnostics.json")}
        text("Signed updates")
        text("Download the signed update bundle from Pingora’s GitHub account for private releases. Download the Android update ZIP, then import it here. The signature, APK hash, package, signer and version are checked before the system installer opens.")
        action("Open GitHub releases") {startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/wrestle-R/Pingora/releases")))}
        action("Import signed Android update", !updateImport.isVerifying) {importUpdate.launch(arrayOf("application/zip","application/octet-stream"))}
        text(updateMessage)
        val approval=android.widget.CheckBox(context).apply{text="I approve installing this verified update"};layout.addView(approval)
        action("Install approved update", !updateImport.isVerifying && updateImport.candidate != null) {
            try {
                val candidate=updateImport.candidate ?: error("Import a signed update first")
                require(approval.isChecked){"Approve the update first"}
                if(ContextCompat.checkSelfPermission(context,Manifest.permission.READ_PHONE_STATE)!=PackageManager.PERMISSION_GRANTED){requestPermissions(arrayOf(Manifest.permission.READ_PHONE_STATE),73);error("Phone-state permission is needed to check for an active call. Retry after choosing.")}
                @Suppress("DEPRECATION") val inCall=context.getSystemService(android.telephony.TelephonyManager::class.java).callState!=android.telephony.TelephonyManager.CALL_STATE_IDLE
                val busy=inCall || KdeConnect.getInstance().devices.values.any{it.getPlugin<org.kde.kdeconnect.pingora.BridgePlugin>()?.files?.busy()==true}
                val opened=org.kde.kdeconnect.pingora.SignedUpdate.install(context,candidate,true,busy)
                if (opened) updateImport.handedToInstaller()
                updateMessage=if(opened)"Android installer opened. Confirm there to finish." else "Allow Pingora to request installations, then return and retry."
            }catch(e:Exception){updateMessage=e.message ?: "Update could not be installed"}
            render()
        }
        action("Refresh connection") { org.kde.kdeconnect.BackgroundService.ForceRefreshConnections(context); render() }
    }

    override fun onDestroy() {
        updateImport.dispose()
        super.onDestroy()
    }

    override fun onDestroyView() {
        content = null
        super.onDestroyView()
    }
}

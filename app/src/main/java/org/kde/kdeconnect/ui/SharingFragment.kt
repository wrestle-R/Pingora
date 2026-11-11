/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.kde.kdeconnect.ui

import android.app.KeyguardManager
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.provider.Telephony
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.button.MaterialButton as Button
import com.google.android.material.materialswitch.MaterialSwitch as CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import org.json.JSONObject
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.pingora.BridgePlugin
import org.kde.kdeconnect.pingora.SharingPolicy

class SharingFragment : Fragment() {
    private var selectedPeer: String? = null
    private val filePicker=registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) {uri->
        if(uri!=null) selectedPeer?.let {id->KdeConnect.getInstance().devices[id]?.getPlugin<BridgePlugin>()?.let {bridge->Thread {bridge.files.offer(uri)}.start()}}
    }
    private val folderPicker=registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()) {uri->
        if(uri!=null) {
            val context=requireContext()
            try {
                val roots=JSONObject(SharingPolicy.preferences(context).getString("folder_roots","{}").orEmpty())
                val existing=roots.keys().asSequence().firstOrNull {roots.optString(it)==uri.toString()}
                if(existing==null && roots.length()>=32) {
                    android.widget.Toast.makeText(context,"Revoke a shared folder before adding another (32 maximum).",android.widget.Toast.LENGTH_LONG).show()
                    return@registerForActivityResult
                }
                context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)
                roots.put(existing ?: java.util.UUID.randomUUID().toString(),uri.toString())
                SharingPolicy.preferences(context).edit {putString("folder_roots",roots.toString())}
            }catch(_:Exception){}
            render()
        }
    }

    private var content: LinearLayout? = null
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val context=requireContext()
        content=LinearLayout(context).apply { orientation=LinearLayout.VERTICAL; val p=(24*resources.displayMetrics.density).toInt(); setPadding(p,p,p,p) }
        return ScrollView(context).apply { addView(content) }
    }
    override fun onResume() { super.onResume(); render() }
    private fun render() {
        val context=requireContext(); val layout=content ?: return; layout.removeAllViews()
        fun text(value:String) { layout.addView(TextView(context).apply { text=value; textSize=if(value=="Sharing controls") 30f else 16f; setPadding(0,16,0,16) }) }
        fun button(label:String, action:()->Unit) { layout.addView(Button(context).apply { text=label; isAllCaps=false; minimumHeight=(56*resources.displayMetrics.density).toInt(); setOnClickListener {action()} }) }
        text("Sharing controls")
        text("Choose each feature on both devices. Pause rejects new sharing; pairing and saved history remain. Notification access is optional and can be revoked in Android Settings.")
        button("Notification access settings") { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        val apps=linkedMapOf("com.whatsapp" to "WhatsApp", "com.whatsapp.w4b" to "WhatsApp Business", "com.instagram.android" to "Instagram", "com.google.android.gm" to "Gmail")
        Telephony.Sms.getDefaultSmsPackage(context)?.let { apps[it]="Default SMS app notifications" }
        text("Phone apps to share")
        apps.forEach { (pkg,label)-> layout.addView(CheckBox(context).apply {
            text=label; isChecked=pkg in SharingPolicy.apps(context)
            setOnCheckedChangeListener { _,enabled-> val chosen=SharingPolicy.apps(context).toMutableSet(); if(enabled) chosen.add(pkg) else chosen.remove(pkg); SharingPolicy.preferences(context).edit {putStringSet("phone_apps",chosen)}; KdeConnect.getInstance().devices.values.forEach {it.getPlugin<BridgePlugin>()?.policyChanged()} }
        }) }
        button("Choose a read-only shared folder") {folderPicker.launch(null)}
        val selectedRoots=try {JSONObject(SharingPolicy.preferences(context).getString("folder_roots","{}").orEmpty())}catch(_:Exception){JSONObject()}
        selectedRoots.keys().asSequence().toList().forEach {root->
            val name=try {androidx.documentfile.provider.DocumentFile.fromTreeUri(context,android.net.Uri.parse(selectedRoots.getString(root)))?.name}catch(_:Exception){null}
            button("Revoke folder: ${name ?: "Shared folder"}") {
                SharingPolicy.revokeFolder(context,root)
                KdeConnect.getInstance().devices.values.forEach {it.getPlugin<BridgePlugin>()?.files?.prune()}
                render()
            }
        }
        button("Revoke all shared folders") {
            val preferences=SharingPolicy.preferences(context)
            try {val roots=JSONObject(preferences.getString("folder_roots","{}").orEmpty());roots.keys().forEach {key->try {context.contentResolver.releasePersistableUriPermission(android.net.Uri.parse(roots.getString(key)),Intent.FLAG_GRANT_READ_URI_PERMISSION)}catch(_:Exception){}}}catch(_:Exception){}
            preferences.edit {putString("folder_roots","{}")}
            KdeConnect.getInstance().devices.values.forEach {it.getPlugin<BridgePlugin>()?.files?.prune()}
            text("Shared folder access revoked.")
        }
        val devices=KdeConnect.getInstance().devices.values.filter {it.isPaired}.toList()
        if(devices.isEmpty()) text("Pair your laptop first, then return here to choose sharing features.")
        for(device in devices) {
            text("${device.name} · ${if(device.isReachable) "reachable" else "offline"}")
            val preferences=SharingPolicy.preferences(context)
            for(feature in listOf("paused","notifications","clipboard","files","folders","battery","ring","slumber")) {
                layout.addView(CheckBox(context).apply {
                    text=if(feature=="paused") "Pause sharing" else "Allow $feature"
                    isChecked=preferences.getBoolean("${device.deviceId}.$feature",feature != "paused")
                    setOnCheckedChangeListener {_,enabled-> SharingPolicy.set(context,device.deviceId,feature,enabled); device.getPlugin<BridgePlugin>()?.policyChanged() }
                })
            }
            button("Send clipboard to ${device.name}") {
                val bridge=device.getPlugin<BridgePlugin>()
                if(bridge?.canSend("clipboard") != true) {text("Enable clipboard on both devices and wait for a connection."); return@button}
                if(context.getSystemService(KeyguardManager::class.java).isDeviceLocked) {text("Unlock your phone to send clipboard text.");return@button}
                val clip=context.getSystemService(ClipboardManager::class.java).primaryClip
                val value=clip?.takeIf {it.itemCount>0}?.getItemAt(0)?.text?.toString()
                if(value==null) text("The clipboard has no text.")
                else if(value.toByteArray(Charsets.UTF_8).size>16384) text("Clipboard text is too large (16 KiB maximum).")
                else text(if(bridge.send("clipboard",JSONObject().put("text",value))) "Clipboard queued for this connection." else "Could not queue clipboard. Reconnect and try again.")
            }
            button("Choose a file to send to ${device.name}") { selectedPeer=device.deviceId;filePicker.launch(arrayOf("*/*")) }
            val bridge=device.getPlugin<BridgePlugin>()
            bridge?.files?.snapshot()?.forEach {offer->
                text("${offer.name} · ${offer.received}/${offer.size} bytes ${if(offer.direction=="send") "sent" else "received"} · ${offer.state}")
                if(offer.state=="approval_required") button("Accept ${offer.name}") {Thread {bridge.files.accept(offer.id)}.start();text("Transfer accepted. Refresh to check progress.")}
                if(offer.state=="saved") button("Open saved ${offer.name}") {
                    try {bridge.files.savedUri(offer.id)?.let {uri->startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,java.net.URLConnection.guessContentTypeFromName(offer.name) ?: "application/octet-stream").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))}}catch(_:Exception){text("No app can open this file. It remains saved in Pingora.")}
                }
                if(offer.state !in setOf("saved","sent")) button("Reject or cancel ${offer.name}") {bridge.files.reject(offer.id);render()}
            }
            bridge?.receivedLinks()?.forEach {value->
                text(value)
                button("Open received link") {try {if(BridgePlugin.validUrl(value)) startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse(value)))}catch(_:Exception){text("No app could open this link.")}}
            }
            val link=android.widget.EditText(context).apply {hint="HTTP or HTTPS link";inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI;filters=arrayOf(android.text.InputFilter.LengthFilter(4096))}
            layout.addView(link)
            button("Send link to ${device.name}") {val value=link.text.toString();if(bridge?.canSend("files")==true && BridgePlugin.validUrl(value)) {bridge.send("url",JSONObject().put("url",value));link.text.clear()}else text("Enable files on both devices and enter an HTTP or HTTPS link.")}
            bridge?.files?.status?.takeIf {it.isNotEmpty()}?.let {text(it)}
            text(bridge?.laptopBattery ?: "Laptop battery unavailable")
            button("Refresh laptop battery") {if(bridge?.canSend("battery")==true)bridge.send("battery_request",JSONObject())}
            button("Ring laptop (30 seconds)") {if(bridge?.canSend("ring")==true)bridge.send("ring",JSONObject())}
            button("Stop laptop ringing") {bridge?.send("ring_stop",JSONObject())}
            text(bridge?.slumberStatus ?: "Slumber connection unavailable")
            button("Refresh laptop Slumber status") {if(bridge?.canSend("slumber")==true) bridge.send("slumber_status",JSONObject()) else text("Enable Slumber on both devices first.")}
            for(mode in listOf("sleep","slumber")) button("On laptop lid close: $mode") {if(bridge?.canSend("slumber")==true) {bridge.send("slumber_lid",JSONObject().put("value",mode));text("Preference requested. Refresh status to verify; the laptop needs the narrow Slumber IPC adapter.")}else text("Enable Slumber on both devices first.")}
            button("Activate Slumber on laptop") {if(bridge?.canSend("slumber")==true) {bridge.send("slumber_activate",JSONObject());text("Activation requested. Refresh status to check the laptop’s secure state.")} else text("Enable Slumber on both devices first.")}
            button("Stop phone ringing") {device.getPlugin<BridgePlugin>()?.stopLocalRing()}
        }
        button("Refresh connection") {render()}
    }
    override fun onDestroyView() { content=null; super.onDestroyView() }
}

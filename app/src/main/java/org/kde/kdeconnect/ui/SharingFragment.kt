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
    private var page = 0
    private var advanced = false
    private val linkDrafts = mutableMapOf<String, String>()
    private var snackbar: com.google.android.material.snackbar.Snackbar? = null
    private var lastLiveState = ""
    private var connectionLabel: TextView? = null
    private var batteryLabel: TextView? = null
    private var slumberLabel: TextView? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val refreshStatus = object : Runnable {
        override fun run() {
            val bridge = selectedPeer?.let { KdeConnect.getInstance().devices[it]?.getPlugin<BridgePlugin>() }
            val peer=selectedPeer?.let {KdeConnect.getInstance().devices[it]}
            connectionLabel?.text=when {
                peer==null -> "Pair with Pingora on your laptop to start sharing."
                peer?.isReachable!=true -> "Offline · Open Pingora on your laptop and use the same network."
                SharingPolicy.preferences(requireContext()).getBoolean("${peer.deviceId}.paused",false) -> "Sharing paused · Resume in Settings."
                else -> "Connected · Sharing over your local network"
            }
            val liveState=liveState()
            if(page!=3 && lastLiveState!=liveState) render()
            batteryLabel?.text = bridge?.laptopBattery ?: "Battery status unavailable"
            slumberLabel?.text = bridge?.slumberStatus ?: "Slumber is not connected"
            if (content != null) handler.postDelayed(this, 2000)
        }
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        selectedPeer = state?.getString("peer") ?: arguments?.getString("peer")
        page = state?.getInt("page") ?: 0
        state?.getBundle("drafts")?.let { b -> b.keySet().forEach { linkDrafts[it] = b.getString(it).orEmpty() } }
    }
    override fun onSaveInstanceState(state: Bundle) {
        super.onSaveInstanceState(state)
        state.putString("peer", selectedPeer)
        state.putInt("page", page)
        state.putBundle("drafts", Bundle().apply { linkDrafts.forEach { (id, text) -> putString(id, text) } })
    }
    private fun liveState(): String {
        val peer=selectedPeer?.let {KdeConnect.getInstance().devices[it]}
        val bridge=peer?.getPlugin<BridgePlugin>()
        return "${peer?.deviceId}:${peer?.isReachable}:" + SharingPolicy.features.joinToString {bridge?.canSend(it).toString()} +
            if(page==1) bridge?.files?.snapshot()?.joinToString {"${it.id}:${it.state}:${it.received}"}.orEmpty()+bridge?.receivedLinks()?.size else ""
    }
    private fun message(value: String) {
        val layout = content ?: return
        snackbar?.dismiss()
        snackbar = com.google.android.material.snackbar.Snackbar.make(layout, value, com.google.android.material.snackbar.Snackbar.LENGTH_LONG).also { it.show() }
    }
    private val filePicker=registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) {uri->
        if(uri!=null) selectedPeer?.let {id->KdeConnect.getInstance().devices[id]?.getPlugin<BridgePlugin>()?.let {bridge->
            message("Preparing file. Track it in Activity.")
            Thread {bridge.files.offer(uri);handler.post {if(content!=null) message(bridge.files.status.ifEmpty {"File prepared. Open Activity for progress."})}}.start()
        }}
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
            }catch(_:Exception){message("Folder access was not granted. Choose the folder again.")}
            render()
        }
    }

    private var content: LinearLayout? = null
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val context=requireContext()
        content=LinearLayout(context).apply { orientation=LinearLayout.VERTICAL; val p=(24*resources.displayMetrics.density).toInt(); setPadding(p,p,p,p) }
        return ScrollView(context).apply { addView(content) }
    }
    override fun onResume() { super.onResume(); render(); handler.removeCallbacks(refreshStatus); handler.post(refreshStatus) }
    override fun onPause() { handler.removeCallbacks(refreshStatus); super.onPause() }
    private fun render() {
        val context=requireContext(); val layout=content ?: return; layout.removeAllViews(); batteryLabel=null; slumberLabel=null
        fun text(value:String, heading:Boolean=false): TextView {
            return TextView(context).apply {
                text=value; textSize=if(heading) 26f else 15f
                if(heading) setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, (12*resources.displayMetrics.density).toInt(), 0, (8*resources.displayMetrics.density).toInt())
                layout.addView(this)
            }
        }
        fun button(label:String, enabled:Boolean=true, action:()->Unit) {
            layout.addView(Button(context).apply {
                text=label; isAllCaps=false; isEnabled=enabled; alpha=if(enabled) 1f else 0.45f; strokeWidth=if(label=="Choose a file") 0 else (resources.displayMetrics.density).toInt().coerceAtLeast(1);
                if(label!="Choose a file") {backgroundTintList=android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT);setTextColor(com.google.android.material.color.MaterialColors.getColor(this,androidx.appcompat.R.attr.colorPrimary));strokeColor=android.content.res.ColorStateList.valueOf(com.google.android.material.color.MaterialColors.getColor(this,com.google.android.material.R.attr.colorOutline))}; cornerRadius=(16*resources.displayMetrics.density).toInt()
                minimumHeight=(56*resources.displayMetrics.density).toInt()
                layoutParams=LinearLayout.LayoutParams(-1,-2).apply { topMargin=(8*resources.displayMetrics.density).toInt() }
                setOnClickListener {action()}
            })
        }
        val devices=KdeConnect.getInstance().devices.values.filter {it.isPaired}.toList()
        val current=devices.firstOrNull { it.deviceId==selectedPeer } ?: devices.firstOrNull()
        selectedPeer=current?.deviceId
        text(current?.name ?: "Connect your laptop", true)
        connectionLabel=text(when {
            current==null -> "Pair with Pingora on your laptop to start sharing."
            !current.isReachable -> "Offline · Open Pingora on your laptop and use the same network."
            SharingPolicy.preferences(context).getBoolean("${current.deviceId}.paused",false) -> "Sharing paused · Resume in Settings."
            else -> "Connected · Sharing over your local network"
        })
        if(devices.size>1) button("Switch laptop") {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(context).setTitle("Choose a laptop")
                .setItems(devices.map {it.name}.toTypedArray()) {_,index -> selectedPeer=devices[index].deviceId; render()}.show()
        }
        val tabs=com.google.android.material.tabs.TabLayout(context).apply {
            tabMode=com.google.android.material.tabs.TabLayout.MODE_SCROLLABLE
            tabGravity=com.google.android.material.tabs.TabLayout.GRAVITY_START
            listOf("Share","Activity","Controls","Settings").forEach {addTab(newTab().setText(it))}
            getTabAt(page)?.select()
            addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {page=tab.position; render()}
                override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
                override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
            })
        }
        layout.addView(tabs)
        lastLiveState=liveState()
        if(page==3) {
        button("Connection & Android permissions") {(activity as? MainActivity)?.openPingoraPage("health")}
        text("Permissions & folders", true)
        text("Sharing features start enabled. Android still needs your approval for notification access and folders.")
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
        }
        if(current==null) {
            button("Find devices") { (activity as? MainActivity)?.openPingoraPage("devices") }
            return
        }
        for(device in listOf(current)) {
            val bridge=device.getPlugin<BridgePlugin>()
            if(page==3) {
            val preferences=SharingPolicy.preferences(context)
            for(feature in listOf("paused","notifications","clipboard","files","folders","battery","ring","slumber")) {
                layout.addView(CheckBox(context).apply {
                    text=if(feature=="paused") "Pause sharing" else "Allow $feature"
                    isChecked=preferences.getBoolean("${device.deviceId}.$feature",feature != "paused")
                    setOnCheckedChangeListener {_,enabled-> SharingPolicy.set(context,device.deviceId,feature,enabled); device.getPlugin<BridgePlugin>()?.policyChanged() }
                })
            }
            }
            if(page==0) {
            text("Send something", true)
            text("Pick a file, copy text, or send a link to your laptop.")
            if(bridge?.canSend("files")!=true || bridge.canSend("clipboard")!=true)
                text("Some actions are unavailable. Check the connection and feature switches in Settings on both devices.")
            button("Send clipboard", bridge?.canSend("clipboard")==true) {
                val bridge=device.getPlugin<BridgePlugin>()
                if(bridge?.canSend("clipboard") != true) {message("Enable clipboard on both devices and wait for a connection."); return@button}
                if(context.getSystemService(KeyguardManager::class.java).isDeviceLocked) {message("Unlock your phone to send clipboard text.");return@button}
                val clip=context.getSystemService(ClipboardManager::class.java).primaryClip
                val value=clip?.takeIf {it.itemCount>0}?.getItemAt(0)?.text?.toString()
                if(value==null) message("The clipboard has no text.")
                else if(value.toByteArray(Charsets.UTF_8).size>16384) message("Clipboard text is too large (16 KiB maximum).")
                else message(if(bridge.send("clipboard",JSONObject().put("text",value))) "Clipboard queued for this connection." else "Could not queue clipboard. Reconnect and try again.")
            }
            button("Choose a file", bridge?.canSend("files")==true) { selectedPeer=device.deviceId;filePicker.launch(arrayOf("*/*")) }
            }
            if(page==1) {
            button("Open notification inbox") {(activity as? MainActivity)?.openPingoraPage("inbox")}
            text("Recent activity", true)
            if(bridge?.files?.snapshot().isNullOrEmpty() && bridge?.receivedLinks().isNullOrEmpty())
                text("Nothing received yet. Incoming files and links will appear here. Files ask for your approval before downloading.")
            bridge?.files?.snapshot()?.forEach {offer->
                text(offer.name)
                val stateLabel=when(offer.state) {
                    "approval_required" -> "Waiting for your approval"
                    "waiting_for_approval" -> "Waiting for your laptop to accept"
                    "sending" -> "Sending"
                    "receiving" -> "Receiving"
                    "saved" -> "Saved on this phone"
                    "sent" -> "Sent to your laptop"
                    else -> "Transfer stopped"
                }
                text("$stateLabel · ${android.text.format.Formatter.formatShortFileSize(context,offer.received)} / ${android.text.format.Formatter.formatShortFileSize(context,offer.size)}")
                if(offer.state=="approval_required") button("Accept ${offer.name}") {Thread {bridge.files.accept(offer.id)}.start();message("Transfer accepted. Refresh to check progress.")}
                if(offer.state=="saved") button("Open saved ${offer.name}") {
                    try {bridge.files.savedUri(offer.id)?.let {uri->startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,java.net.URLConnection.guessContentTypeFromName(offer.name) ?: "application/octet-stream").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))}}catch(_:Exception){message("No app can open this file. It remains saved in Pingora.")}
                }
                if(offer.state in setOf("approval_required","waiting_for_approval","sending","receiving")) button("Reject or cancel ${offer.name}") {bridge.files.reject(offer.id);render()}
            }
            bridge?.receivedLinks()?.forEach {value->
                text(value)
                button("Open received link") {try {if(BridgePlugin.validUrl(value)) startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse(value)))}catch(_:Exception){message("No app could open this link.")}}
            }
            }
            if(page==0) {
            text("Send a link")
            val link=com.google.android.material.textfield.TextInputEditText(context).apply {hint="https://example.com";setText(linkDrafts[device.deviceId].orEmpty());inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI;filters=arrayOf(android.text.InputFilter.LengthFilter(4096))}
            link.addTextChangedListener(object: android.text.TextWatcher {
                override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
                override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) {linkDrafts[device.deviceId]=s.toString()}
                override fun afterTextChanged(s:android.text.Editable?) {}
            })
            layout.addView(com.google.android.material.textfield.TextInputLayout(context).apply {
                hint="Link to send";boxBackgroundMode=com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE
                layoutParams=LinearLayout.LayoutParams(-1,-2).apply {topMargin=(12*resources.displayMetrics.density).toInt()}
                addView(link)
            })
            button("Send link", bridge?.canSend("files")==true) {
                val value=link.text.toString().trim()
                if(!BridgePlugin.validUrl(value)) {link.error="Enter a complete http:// or https:// link";return@button}
                if(bridge?.canSend("files")==true && bridge.send("url",JSONObject().put("url",value))) {
                    link.text?.clear();message("Link sent to ${device.name}.")
                } else message("Link was not sent. Check the connection and try again.")
            }
            bridge?.files?.status?.takeIf {it.isNotEmpty()}?.let {text(it)}
            }
            if(page==2) {
            text("Laptop controls", true)
            batteryLabel=text(bridge?.laptopBattery ?: "Battery status unavailable")
            button("Refresh battery", bridge?.canSend("battery")==true) {if(bridge?.canSend("battery")==true)bridge.send("battery_request",JSONObject())}
            button("Ring laptop · 30 seconds", bridge?.canSend("ring")==true) {
                message(if(bridge?.canSend("ring")==true && bridge.send("ring",JSONObject())) "Ring requested for 30 seconds." else "Ring request could not be sent.")
            }
            button("Stop laptop ringing") {bridge?.send("ring_stop",JSONObject())}
            text("Slumber", true)
            text("Optional controls for your laptop’s Slumber setup.")
            button(if(advanced) "Hide Slumber controls" else "Show Slumber controls") {advanced=!advanced;render()}
            if(advanced) {
            slumberLabel=text(bridge?.slumberStatus ?: "Slumber connection unavailable")
            button("Refresh Slumber status", bridge?.canSend("slumber")==true) {if(bridge?.canSend("slumber")==true) bridge.send("slumber_status",JSONObject()) else message("Enable Slumber on both devices first.")}
            for(mode in listOf("sleep","slumber")) button("On lid close: $mode", bridge?.canSend("slumber")==true) {if(bridge?.canSend("slumber")==true) {bridge.send("slumber_lid",JSONObject().put("value",mode));message("Lid preference requested. Waiting for laptop status.")}else message("Enable Slumber on both devices first.")}
            button("Activate Slumber", bridge?.canSend("slumber")==true) {if(bridge?.canSend("slumber")==true) {bridge.send("slumber_activate",JSONObject());message("Slumber activation requested. Check the status above.")} else message("Enable Slumber on both devices first.")}
            }
            button("Stop phone ringing") {device.getPlugin<BridgePlugin>()?.stopLocalRing()}
        }
            }
        if(current!=null) button("Refresh connection") {render()}
        if(current!=null && !current.isReachable) button("Connection help") {(activity as? MainActivity)?.openPingoraPage("health")}
    }
    override fun onDestroyView() { handler.removeCallbacks(refreshStatus); snackbar?.dismiss(); snackbar=null; connectionLabel=null; batteryLabel=null; slumberLabel=null; content=null; super.onDestroyView() }
}

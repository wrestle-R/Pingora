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
    private var showActivity = false
    private val back = object : androidx.activity.OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {page=0;advanced=false;showActivity=false;render()}
    }
    private val linkDrafts = mutableMapOf<String, String>()
    private var snackbar: com.google.android.material.snackbar.Snackbar? = null
    private var lastLiveState = ""
    private var connectionLabel: TextView? = null
    private var batteryLabel: TextView? = null
    private var slumberLabel: TextView? = null
    private var telemetryLabel: TextView? = null
    private var syncLabel: TextView? = null
    private var profileLabel: TextView? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val refreshStatus = object : Runnable {
        override fun run() {
            val bridge = selectedPeer?.let { KdeConnect.getInstance().devices[it]?.getPlugin<BridgePlugin>() }
            val peer=selectedPeer?.let {KdeConnect.getInstance().devices[it]}
            connectionLabel?.text=when {
                peer==null -> "No laptop paired"
                peer?.isReachable!=true -> "Offline · Showing saved readings"
                SharingPolicy.preferences(requireContext()).getBoolean("${peer.deviceId}.paused",false) -> "Sharing paused · Resume in Settings."
                bridge?.isReady != true -> "Connecting to laptop…"
                else -> "Connected"
            }
            val liveState=liveState()
            if(page!=3 && lastLiveState!=liveState) render()
            selectedPeer?.let {id ->
                val saved=org.kde.kdeconnect.pingora.LaptopTelemetry.read(requireContext(),id)
                val level=saved.data.optJSONObject("battery")?.optInt("level",-1) ?: -1
                batteryLabel?.text=if(level in 0..100) "$level%" else "—"
                telemetryLabel?.text=org.kde.kdeconnect.pingora.LaptopWidget.metrics(saved.data).replace(" · ","\n")
                syncLabel?.text=saved.age()+if(peer?.isReachable==true && bridge?.isReady==true && bridge.laptopStatusAt>0 && android.os.SystemClock.elapsedRealtime()-bridge.laptopStatusAt in 0..90_000) " · Live reading" else " · Saved reading"
                profileLabel?.text=org.kde.kdeconnect.pingora.LaptopWidget.profileLabel(saved.data.optString("power_profile"))
            }
            slumberLabel?.text = bridge?.slumberStatus ?: "Slumber is not connected"
            if (content != null) handler.postDelayed(this, 2000)
        }
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        selectedPeer = state?.getString("peer") ?: arguments?.getString("peer")
        page = state?.getInt("page") ?: arguments?.getInt("page", 0) ?: 0
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
            if(page==0 && showActivity) bridge?.files?.snapshot()?.joinToString {"${it.id}:${it.state}:${it.received}"}.orEmpty()+bridge?.receivedLinks()?.size else ""
    }
    private fun message(value: String) {
        val layout = content ?: return
        snackbar?.dismiss()
        snackbar = com.google.android.material.snackbar.Snackbar.make(layout, value, com.google.android.material.snackbar.Snackbar.LENGTH_LONG).also { it.show() }
    }
    private val filePicker=registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) {uri->
        if(uri!=null) selectedPeer?.let {id->KdeConnect.getInstance().devices[id]?.getPlugin<BridgePlugin>()?.let {bridge->
            message("Preparing file. See Transfers & received links.")
            Thread {bridge.files.offer(uri);handler.post {if(content!=null) message(bridge.files.status.ifEmpty {"File prepared. See Transfers & received links."})}}.start()
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
                val rootId=existing ?: java.util.UUID.randomUUID().toString()
                roots.put(rootId,uri.toString())
                val name=androidx.documentfile.provider.DocumentFile.fromTreeUri(context,uri)?.name ?: "Shared folder"
                SharingPolicy.preferences(context).edit {putString("folder_roots",roots.toString());putString("folder_name.$rootId",name)}
            }catch(_:Exception){message("Folder access was not granted. Choose the folder again.")}
            render()
        }
    }

    private fun Int.dp() = (this*resources.displayMetrics.density).toInt()
    private var content: LinearLayout? = null
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val context=requireContext()
        content=LinearLayout(context).apply { orientation=LinearLayout.VERTICAL; val p=(24*resources.displayMetrics.density).toInt(); setPadding(p,p,p,p) }
        return ScrollView(context).apply { addView(content) }
    }
    override fun onViewCreated(view: View, state: Bundle?) {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, back)
    }
    override fun onResume() { super.onResume(); org.kde.kdeconnect.pingora.PingoraNotificationListener.ensureConnected(requireContext()); render(); selectedPeer?.let {KdeConnect.getInstance().devices[it]?.getPlugin<BridgePlugin>()?.apply {if(canSend("battery")) send("battery_request",JSONObject());requestLaptopStatus()}}; handler.removeCallbacks(refreshStatus); handler.post(refreshStatus) }
    override fun onPause() { handler.removeCallbacks(refreshStatus); super.onPause() }
    private fun render() {
        val context=requireContext(); val layout=content ?: return; back.isEnabled=page==2 || showActivity; layout.removeAllViews(); batteryLabel=null; slumberLabel=null;telemetryLabel=null;syncLabel=null;profileLabel=null
        fun text(value:String, heading:Boolean=false): TextView {
            return TextView(context).apply {
                text=value; textSize=if(heading) 20f else 15f
                if(heading) setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, (12*resources.displayMetrics.density).toInt(), 0, (8*resources.displayMetrics.density).toInt())
                layout.addView(this)
            }
        }
        fun button(label:String, enabled:Boolean=true, action:()->Unit) {
            layout.addView(Button(context).apply {
                text=label; isAllCaps=false; isEnabled=enabled; alpha=if(enabled) 1f else 0.45f; strokeWidth=0;
                if(label!="Send file" && label!="Send clipboard") {backgroundTintList=android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT);setTextColor(com.google.android.material.color.MaterialColors.getColor(this,com.google.android.material.R.attr.colorOnSurface))}; cornerRadius=(16*resources.displayMetrics.density).toInt()
                minimumHeight=(56*resources.displayMetrics.density).toInt()
                layoutParams=LinearLayout.LayoutParams(-1,-2).apply { topMargin=(8*resources.displayMetrics.density).toInt() }
                setOnClickListener {action()}
            })
        }
        val devices=KdeConnect.getInstance().devices.values.filter {it.isPaired}.toList()
        val current=devices.firstOrNull { it.deviceId==selectedPeer } ?: devices.firstOrNull {it.isReachable} ?: devices.firstOrNull()
        selectedPeer=current?.deviceId
        if((page==0 && !showActivity) || page==2) {renderDashboard(layout,current,page==2);lastLiveState=liveState();return}
        if(page==0 || page==2) {
            val card=com.google.android.material.card.MaterialCardView(context).apply {
                radius=24*resources.displayMetrics.density
                strokeWidth=0
                setCardBackgroundColor(com.google.android.material.color.MaterialColors.getColor(layout,com.google.android.material.R.attr.colorSurfaceContainer))
                layoutParams=LinearLayout.LayoutParams(-1,-2).apply {bottomMargin=(24*resources.displayMetrics.density).toInt()}
            }
            val cardContent=LinearLayout(context).apply {orientation=LinearLayout.VERTICAL;setPadding(24.dp(),16.dp(),24.dp(),20.dp())}
            layout.addView(card)
            card.addView(cardContent)
            val heading=TextView(context).apply {text=current?.name ?: "Connect your laptop";textSize=24f;setTypeface(typeface,android.graphics.Typeface.BOLD)}
            cardContent.addView(heading)
            batteryLabel=TextView(context).apply {
                text=current?.getPlugin<BridgePlugin>()?.laptopBattery?.removePrefix("Laptop battery: ") ?: "Battery unavailable"
                textSize=34f;setTypeface(typeface,android.graphics.Typeface.BOLD)
                setTextColor(com.google.android.material.color.MaterialColors.getColor(this,androidx.appcompat.R.attr.colorPrimary))
                setPadding(0,16.dp(),0,8.dp())
            }
            cardContent.addView(batteryLabel)
        connectionLabel=text(when {
            current==null -> "No laptop paired"
            !current.isReachable -> "Offline · Showing saved readings"
            SharingPolicy.preferences(context).getBoolean("${current.deviceId}.paused",false) -> "Sharing paused · Resume in Settings."
            current.getPlugin<BridgePlugin>()?.isReady != true -> "Connecting to laptop…"
            else -> "Connected"
        })
            layout.removeView(connectionLabel)
            cardContent.addView(connectionLabel)
        } else text("Settings", true)
        val visibleDevices=devices.filter {it.isReachable || devices.none {other->other.name==it.name && other.isReachable}}
        if(visibleDevices.size>1) button("Switch laptop") {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(context).setTitle("Choose a laptop")
                .setItems(visibleDevices.map {"${it.name} · ${if(it.isReachable) "Connected" else "Saved offline"} · ${it.deviceId.takeLast(6)}"}.toTypedArray()) {_,index -> selectedPeer=visibleDevices[index].deviceId; render()}.show()
        }
        if(page==2) button("Back to Home") {page=0;render()}
        if(page==0 && current!=null) button("Laptop details") {page=2;render()}
        lastLiveState=liveState()
        if(page==3) {
        val savedDevices=devices.filter {!it.isReachable}
        if(savedDevices.isNotEmpty()) button("Saved laptops (${savedDevices.size})") {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(context).setTitle("Saved offline laptops")
                .setItems(savedDevices.map {"${it.name} · ${it.deviceId.takeLast(6)}"}.toTypedArray()) {_,index ->
                    val saved=savedDevices[index]
                    com.google.android.material.dialog.MaterialAlertDialogBuilder(context).setTitle("Forget ${saved.name}?")
                        .setMessage("Remove only the offline identity ending ${saved.deviceId.takeLast(6)}. Your connected laptop is kept.")
                        .setPositiveButton("Forget") {_,_->saved.unpair();selectedPeer=null;render()}.setNegativeButton("Cancel",null).show()
                }.show()
        }
        text("Home screen widgets",true)
        if(android.os.Build.VERSION.SDK_INT>=26) {
            button("Add laptop status widget") { android.appwidget.AppWidgetManager.getInstance(context).requestPinAppWidget(android.content.ComponentName(context,org.kde.kdeconnect.pingora.LaptopWidget::class.java),null,null) }
            button("Add clipboard widget") { android.appwidget.AppWidgetManager.getInstance(context).requestPinAppWidget(android.content.ComponentName(context,org.kde.kdeconnect.pingora.ClipboardWidget::class.java),null,null) }
        }
        button("Connection & Android permissions") {(activity as? MainActivity)?.openPingoraPage("health")}
        text("Permissions & folders", true)
        text("Sharing features start enabled. Android still needs your approval for notification access and folders.")
        button("Test notification forwarding") { android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({org.kde.kdeconnect.pingora.PingoraNotificationListener.postProbe(context.applicationContext)},5000);message("Test in 5 seconds. You can return to the home screen.") }
        button("Notification access settings") { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        val apps=linkedMapOf("com.whatsapp" to "WhatsApp", "com.whatsapp.w4b" to "WhatsApp Business", "com.instagram.android" to "Instagram", "com.google.android.gm" to "Gmail")
        Telephony.Sms.getDefaultSmsPackage(context)?.let { apps[it]="Default SMS app notifications" }
        text("Phone apps to share")
        apps.forEach { (pkg,label)-> layout.addView(CheckBox(context).apply {
            text=label; isChecked=pkg in SharingPolicy.apps(context)
            setOnCheckedChangeListener { _,enabled-> val chosen=SharingPolicy.apps(context).toMutableSet(); if(enabled) chosen.add(pkg) else chosen.remove(pkg); SharingPolicy.preferences(context).edit {putStringSet("phone_apps",chosen)}; KdeConnect.getInstance().devices.values.forEach {it.getPlugin<BridgePlugin>()?.policyChanged()} }
        }) }
        if(android.os.Build.VERSION.SDK_INT>=30) {
            text(if(android.os.Environment.isExternalStorageManager()) "Phone storage browsing is available (read-only)." else "Allow storage access to browse phone folders from your laptop.")
            button("Phone storage access") {startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,android.net.Uri.parse("package:${context.packageName}")))}
        }
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
            for(feature in listOf("paused","notifications","clipboard","files","folders","battery","ring","slumber","power")) {
                layout.addView(CheckBox(context).apply {
                    text=if(feature=="paused") "Pause sharing" else "Allow $feature"
                    isChecked=preferences.getBoolean("${device.deviceId}.$feature",feature != "paused")
                    setOnCheckedChangeListener {_,enabled-> SharingPolicy.set(context,device.deviceId,feature,enabled); device.getPlugin<BridgePlugin>()?.policyChanged() }
                })
            }
            }
            if(page==0) {
            text("Send to laptop", true)
            text("Send something. Keep going on your laptop.")
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
            button("Send file", bridge?.canSend("files")==true) { selectedPeer=device.deviceId;filePicker.launch(arrayOf("*/*")) }
            }
            if(page==0 && showActivity) {

            text("Transfers & links", true)
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
        if(page==0 && current!=null) button(if(showActivity) "Hide transfers & links" else "Transfers & received links") {showActivity=!showActivity;render()}
        if(page==2) button("Reconnect") {org.kde.kdeconnect.BackgroundService.ForceRefreshConnections(context);render()}
        if(current!=null && !current.isReachable) button("Connection help") {(activity as? MainActivity)?.openPingoraPage("health")}
    }
    override fun onDestroyView() { handler.removeCallbacks(refreshStatus); snackbar?.dismiss(); snackbar=null; connectionLabel=null; batteryLabel=null; slumberLabel=null;telemetryLabel=null;syncLabel=null;profileLabel=null; content=null; super.onDestroyView() }
    private fun renderDashboard(root: LinearLayout, peer: org.kde.kdeconnect.Device?, details: Boolean) {
        val context=requireContext()
        val bridge=peer?.getPlugin<BridgePlugin>()
        val saved=peer?.let {org.kde.kdeconnect.pingora.LaptopTelemetry.read(context,it.deviceId)}
            ?: org.kde.kdeconnect.pingora.LaptopTelemetry.Snapshot(JSONObject(),0)
        fun label(parent: LinearLayout,value: String,size: Float=15f,bold: Boolean=false): TextView = TextView(context).apply {
            text=value;textSize=size;if(bold)setTypeface(typeface,android.graphics.Typeface.BOLD)
            setPadding(0,6.dp(),0,6.dp());parent.addView(this)
        }
        fun action(parent: LinearLayout,title: String,enabled: Boolean=true,primary: Boolean=false,run:()->Unit) {
            parent.addView(Button(context).apply {
                text=title;isAllCaps=false;isEnabled=enabled;minimumHeight=48.dp();cornerRadius=14.dp()
                if(!primary) {backgroundTintList=android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT);setTextColor(com.google.android.material.color.MaterialColors.getColor(this,androidx.appcompat.R.attr.colorPrimary))}
                layoutParams=LinearLayout.LayoutParams(-1,-2).apply {topMargin=4.dp()};setOnClickListener {run()}
            })
        }
        label(root,if(details) "Laptop controls" else "Your workspace",24f,true)
        val card=com.google.android.material.card.MaterialCardView(context).apply {
            radius=20.dp().toFloat();strokeWidth=0;cardElevation=0f
            setCardBackgroundColor(com.google.android.material.color.MaterialColors.getColor(root,com.google.android.material.R.attr.colorSurfaceContainer))
            layoutParams=LinearLayout.LayoutParams(-1,-2).apply {topMargin=12.dp();bottomMargin=12.dp()}
        }
        val inner=LinearLayout(context).apply {orientation=LinearLayout.VERTICAL;setPadding(20.dp(),16.dp(),20.dp(),16.dp())}
        root.addView(card);card.addView(inner)
        label(inner,peer?.name ?: "Connect your laptop",20f,true)
        connectionLabel=label(inner,if(peer?.isReachable==true && bridge?.isReady==true) "Connected" else "Offline · Last saved status",13f)
        val level=saved.data.optJSONObject("battery")?.optInt("level",-1) ?: -1
        batteryLabel=label(inner,if(level in 0..100) "$level%" else "—",42f,true).apply {
            setTextColor(com.google.android.material.color.MaterialColors.getColor(this,androidx.appcompat.R.attr.colorPrimary))
        }
        val metricsRow=LinearLayout(context).apply {orientation=LinearLayout.HORIZONTAL;gravity=android.view.Gravity.CENTER_VERTICAL}
        inner.removeView(batteryLabel)
        metricsRow.addView(batteryLabel,LinearLayout.LayoutParams(0,-2,1f))
        telemetryLabel=TextView(context).apply {
            text=org.kde.kdeconnect.pingora.LaptopWidget.metrics(saved.data).replace(" · ","\n")
            textSize=16f;setTypeface(typeface,android.graphics.Typeface.BOLD)
            metricsRow.addView(this,LinearLayout.LayoutParams(0,-2,1f))
        }
        inner.addView(metricsRow)
        label(inner,"Laptop battery · System usage",12f)
        syncLabel=label(inner,saved.age()+if(peer?.isReachable==true) "" else " · Saved reading",12f)
        profileLabel=label(inner,org.kde.kdeconnect.pingora.LaptopWidget.profileLabel(saved.data.optString("power_profile")),14f)
        action(root,"Refresh status",peer?.isReachable==true) {bridge?.requestLaptopStatus();message("Requesting a fresh laptop reading…")}
        if(peer==null) {action(root,"Pair a laptop",primary=true) {(activity as? MainActivity)?.openPingoraPage("devices")};return}
        if(!details) {
            label(root,"Send to ${peer.name}",17f,true)
            val sendActions=LinearLayout(context).apply {orientation=LinearLayout.HORIZONTAL;root.addView(this)}
            action(sendActions,"Send clipboard",bridge?.canSend("clipboard")==true && peer.isReachable,true) {
                startActivity(Intent(context,org.kde.kdeconnect.pingora.ClipboardSendActivity::class.java))
            }
            action(sendActions,"Send file",bridge?.canSend("files")==true && peer.isReachable,true) {filePicker.launch(arrayOf("*/*"))}
            for(index in 0 until sendActions.childCount) sendActions.getChildAt(index).layoutParams=LinearLayout.LayoutParams(0,60.dp(),1f).apply {setMargins(if(index==0)0 else 4.dp(),0,if(index==0)4.dp() else 0,0)}
            action(root,"Transfers & received links") {showActivity=true;render()}
            action(root,"Power & lid controls") {page=2;render()}
            action(root,"Connection help") {(activity as? MainActivity)?.openPingoraPage("health")}
        } else {
            action(root,"Back to Home") {page=0;render()}
            label(root,"Power profile",18f,true)
            label(root,"Applies to the laptop. The profile icon appears on your widget.",13f)
            val fresh=bridge?.isReady==true && bridge.laptopStatusAt>0 && android.os.SystemClock.elapsedRealtime()-bridge.laptopStatusAt in 0..90_000
            val profiles=saved.data.optJSONArray("power_profiles")
            val available=(0 until (profiles?.length() ?: 0)).map {profiles!!.optString(it)}
            for(value in listOf("power-saver","balanced","performance")) {
                action(root,org.kde.kdeconnect.pingora.LaptopWidget.profileLabel(value),fresh && bridge?.canSend("power")==true && value in available) {
                    if(bridge?.send("power_profile",JSONObject().put("value",value))==true) message("Requested profile. Waiting for the laptop to confirm.")
                }
            }
            label(root,"Device actions",18f,true)
            action(root,"Ring laptop",bridge?.canSend("ring")==true && peer.isReachable) {bridge?.send("ring",JSONObject())}
            action(root,"Stop ringing") {bridge?.send("ring_stop",JSONObject())}
            action(root,"Activate Slumber",fresh && bridge?.canSend("slumber")==true) {
                com.google.android.material.dialog.MaterialAlertDialogBuilder(context).setTitle("Activate Slumber?")
                    .setMessage("This locks your laptop and switches its displays off.")
                    .setPositiveButton("Activate") {_,_->bridge?.send("slumber_activate",JSONObject())}.setNegativeButton("Cancel",null).show()
            }
            label(root,"When the lid closes",18f,true)
            label(root,"Change the lid preference. This does not sleep or lock the laptop now.",13f)
            for(value in listOf("sleep","slumber")) action(root,value.replaceFirstChar {it.uppercase()},fresh && bridge?.canSend("slumber")==true && saved.data.optJSONObject("slumber")?.optBoolean("lid_control")==true) {
                bridge?.send("slumber_lid",JSONObject().put("value",value));message("Requested lid mode: $value")
            }
        }
    }

}

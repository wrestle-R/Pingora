/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.kde.kdeconnect.pingora

import android.app.Notification
import android.app.RemoteInput
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONObject
import org.kde.kdeconnect.KdeConnect
import java.security.MessageDigest

class PingoraNotificationListener : NotificationListenerService() {
    private val versions = LinkedHashMap<String, String>()
    private val pending = java.util.concurrent.ConcurrentHashMap<String,NoticeOutbox<JSONObject>>()
    fun acknowledge(peer: String, source: String, version: String) {pending[peer]?.acknowledge(source,version)}
    fun hasPending(peer: String): Boolean = pending[peer]?.pending()?.isNotEmpty()==true
    fun retry(bridge: BridgePlugin, peer: String) {
        if(bridge.canSend("notifications")) pending[peer]?.pending()?.forEach {bridge.send("notice",it)}
    }
    private fun bridges(): List<BridgePlugin> = KdeConnect.getInstance().devices.values.mapNotNull { it.getPlugin<BridgePlugin>() }

    override fun onListenerConnected() {
        instance = this
        lastRebind=0
        bridges().forEach { sync(it) }
    }
    override fun onListenerDisconnected() {
        instance = null; synchronized(versions) { versions.clear() }
        ensureConnected(this)
    }
    override fun onDestroy() { if (instance === this) instance = null; super.onDestroy() }

    private fun isProbe(sbn: StatusBarNotification): Boolean = sbn.packageName == packageName && sbn.tag == PROBE_TAG && sbn.id == PROBE_ID
    private fun allowed(sbn: StatusBarNotification): Boolean = (isProbe(sbn) || (sbn.packageName != packageName && sbn.packageName in SharingPolicy.apps(this))) && sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0

    @Suppress("DEPRECATION")
    private fun record(sbn: StatusBarNotification): JSONObject? {
        if (!allowed(sbn)) return null
        val n = sbn.notification
        val extras = n.extras ?: Bundle.EMPTY
        val messages = if(Build.VERSION.SDK_INT >= 24) Notification.MessagingStyle.Message.getMessagesFromBundleArray(extras.getParcelableArray(Notification.EXTRA_MESSAGES)) else emptyList()
        val message = messages.lastOrNull()
        val sender = if (Build.VERSION.SDK_INT >= 28) message?.senderPerson?.name?.toString() else message?.sender?.toString()
        val title = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString() ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = message?.text?.toString() ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (body.isEmpty() && title.isEmpty()) return null
        val actions = n.actions.orEmpty()
        val reply = actions.any { action -> action.remoteInputs?.any { it.allowFreeFormInput } == true && action.actionIntent != null }
        val read = Build.VERSION.SDK_INT >= 28 && actions.any { it.semanticAction == Notification.Action.SEMANTIC_ACTION_MARK_AS_READ && it.actionIntent != null }
        val item=NotificationNormalizer.normalize(NotificationFields(
            app=sbn.packageName,source=sbn.key,posted=sbn.postTime,
            shortcut=if(Build.VERSION.SDK_INT>=26)n.shortcutId else null,
            conversationTitle=extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString(),
            title=extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            text=extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
            bigText=extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            messages=messages.map {MessageFields(if(Build.VERSION.SDK_INT>=28)it.senderPerson?.name?.toString().orEmpty() else it.sender?.toString().orEmpty(),it.text?.toString().orEmpty())},
            reply=reply,read=read
        ),if(isProbe(sbn)) setOf(packageName) else SharingPolicy.apps(this),if(isProbe(sbn)) "" else packageName) ?: return null
        return JSONObject().put("peer", "").put("source",item.source).put("version",item.version)
            .put("app",item.app).put("conversation",item.conversation).put("sender",item.sender).put("title",item.title).put("body",item.body)
            .put("received",0).put("active",true).put("reply",item.reply).put("read",item.read)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val item = record(sbn) ?: return
        synchronized(versions) {
            if (versions.size >= 500 && sbn.key !in versions) versions.remove(versions.keys.first())
            versions[sbn.key] = item.getString("version")
        }
        KdeConnect.getInstance().devices.values.filter {it.isPaired}.forEach {peer ->
            val bridge=peer.getPlugin<BridgePlugin>() ?: return@forEach
            if(bridge.canSend("notifications")) {
                if(pending.size<32 || pending.containsKey(peer.deviceId))
                    pending.getOrPut(peer.deviceId) {NoticeOutbox({android.os.SystemClock.elapsedRealtime()})}.put(sbn.key,item.getString("version"),item)
                bridge.send("notice",item)
            }
        }
    }
    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        pending.values.forEach {it.remove(sbn.key)}
        val version = synchronized(versions) { versions.remove(sbn.key) } ?: return
        val data = JSONObject().put("source",sbn.key).put("version",version)
        bridges().filter { it.canSend("notifications") }.forEach { it.send("notice_removed", data) }
    }
    fun sync(bridge: BridgePlugin) {
        if (!bridge.canSend("notifications")) return
        try {
            activeNotifications.takeLast(500).forEach { sbn ->
                val item = record(sbn) ?: return@forEach
                synchronized(versions) { if (versions.size >= 500 && sbn.key !in versions) versions.remove(versions.keys.first()); versions[sbn.key] = item.getString("version") }
                bridge.send("notice",item.put("snapshot",true))
            }
        } catch (_: SecurityException) { /* Access was revoked; no cached content is sent. */ }
    }
    fun action(data: JSONObject):String {
        val key=data.optString("source");val expected=data.optString("version")
        if(key.isEmpty()||key.length>512||expected.length!=64)return "unavailable"
        try {
            val sbn=getActiveNotifications(arrayOf(key)).firstOrNull() ?: return "unavailable"
            val current=record(sbn) ?: return "unavailable"
            if(current.optString("version")!=expected)return "stale"
            val actions=sbn.notification.actions.orEmpty()
            when(data.optString("action")) {
                "dismiss" -> cancelNotification(key)
                "read" -> {
                    if(Build.VERSION.SDK_INT<28)return "unavailable"
                    val action=actions.firstOrNull {it.semanticAction==Notification.Action.SEMANTIC_ACTION_MARK_AS_READ}?.actionIntent ?: return "unavailable"
                    action.send()
                }
                "reply" -> {
                    val text=data.optString("text");if(text.isEmpty()||text.toByteArray(Charsets.UTF_8).size>4096)return "unavailable"
                    val action=actions.firstOrNull {it.remoteInputs?.any {input->input.allowFreeFormInput}==true} ?: return "unavailable"
                    val input=action.remoteInputs.first {it.allowFreeFormInput};val intent=Intent()
                    RemoteInput.addResultsToIntent(arrayOf(input),intent,Bundle().apply {putCharSequence(input.resultKey,text)})
                    val pending=action.actionIntent ?: return "unavailable";pending.send(this,0,intent)
                }
                else -> return "unavailable"
            }
            return "requested"
        }catch(_:Exception){return "unavailable"}
    }
    companion object {
        private const val PROBE_TAG = "pingora-forwarding-probe"
        private const val PROBE_ID = 7151
        @Volatile private var lastRebind=0L
        @Synchronized fun ensureConnected(context: android.content.Context) {
            if(Build.VERSION.SDK_INT<24 || instance!=null) return
            val now=android.os.SystemClock.elapsedRealtime()
            if(lastRebind!=0L && now-lastRebind<60_000) return
            if(androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)) {
                lastRebind=now
                runCatching {requestRebind(android.content.ComponentName(context,PingoraNotificationListener::class.java))}
            }
        }
        @Volatile var instance: PingoraNotificationListener? = null; private set
        fun postProbe(context: android.content.Context) {
            val manager = context.getSystemService(android.app.NotificationManager::class.java)
            val channel = "pingora_forwarding_test"
            if(Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(android.app.NotificationChannel(channel,"Forwarding test",android.app.NotificationManager.IMPORTANCE_DEFAULT))
            val notification = androidx.core.app.NotificationCompat.Builder(context,channel)
                .setSmallIcon(org.kde.kdeconnect_tp.R.drawable.pingora_icon)
                .setContentTitle("Pingora delivery check")
                .setContentText("Harmless test notification. No message or clipboard content.")
                .setTimeoutAfter(120_000).build()
            manager.notify(PROBE_TAG,PROBE_ID,notification)
        }
    }
}

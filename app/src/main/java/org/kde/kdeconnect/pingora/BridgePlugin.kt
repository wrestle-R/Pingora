/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.kde.kdeconnect.pingora

import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.IntentFilter
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.PluginFactory.LoadablePlugin
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

@LoadablePlugin
class BridgePlugin : Plugin() {
    override val displayName = "Pingora connection"
    override val description = "Authenticated feature channel. Choose each sharing feature in Sharing controls."
    override val supportedPacketTypes = arrayOf(TYPE)
    override val outgoingPacketTypes = arrayOf(TYPE)
    override val settingsScope = SettingsScope.PER_DEVICE
    override fun getUiButtons(): List<PluginUiButton> = listOf(
        PluginUiButton("Sharing controls", org.kde.kdeconnect_tp.R.drawable.pingora_icon) { activity ->
            activity.startActivity(Intent(context, org.kde.kdeconnect.ui.MainActivity::class.java).putExtra("pingora_sharing", true))
        },
        PluginUiButton("Inbox", org.kde.kdeconnect_tp.R.drawable.pingora_icon) { activity ->
            activity.startActivity(Intent(context, org.kde.kdeconnect.ui.MainActivity::class.java).putExtra("pingora_inbox", true))
        }
    )

    val files by lazy { FileTransfers(context,this) }
    @Volatile var laptopBattery:String="Laptop battery unavailable";private set
    @Volatile var slumberStatus: String = "Status unavailable"; private set
    @Volatile private var local = ""
    @Volatile private var remote = ""
    private val outboundSequence=java.util.concurrent.atomic.AtomicLong(1)
    private var lastSequence=0L
    private val links=ArrayDeque<String>()
    @Synchronized fun receivedLinks():List<String> = links.toList()
    private val seen = ArrayDeque<String>()
    private val queue = ArrayBlockingQueue<NetworkPacket>(128)
    private val running = AtomicBoolean(false)
    private var worker: Thread? = null
    private val handler = Handler(Looper.getMainLooper())
    private var ringtone: Ringtone? = null
    private val stopRing = Runnable { ringtone?.stop(); ringtone = null }
    @Volatile private var remotePolicy = JSONObject()

    override fun onCreate() {
        local = ByteArray(24).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        remote = ""; lastSequence=0; outboundSequence.set(1); seen.clear(); queue.clear(); running.set(true)
        worker = Thread({
            try {
                while (running.get()) {
                    val packet = queue.poll(30, java.util.concurrent.TimeUnit.SECONDS)
                    if (packet == null) { battery(); LocalInbox.flush(context,device.deviceId,this); continue }
                    if (device.isPaired && device.isReachable) device.sendPacketBlocking(packet)
                }
            } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        }, "pingora-outbound").apply { isDaemon = true; start() }
        enqueue("hello", JSONObject().put("token", local), "")
    }

    override fun onDestroy() {
        running.set(false); worker?.interrupt(); worker = null; queue.clear()
        synchronized(this) { local = ""; remote = ""; seen.clear(); remotePolicy = JSONObject() }
        files.close()
        handler.removeCallbacks(stopRing); handler.post(stopRing)
    }

    private fun enqueue(kind: String, data: JSONObject, session: String): Boolean {
        if (!device.isPaired || !running.get()) return false
        val json = JSONObject().put("seq",outboundSequence.getAndIncrement()).put("id", UUID.randomUUID().toString()).put("kind", kind).put("session", session).put("data", data).toString()
        if (json.toByteArray(Charsets.UTF_8).size > 32768) return false
        return queue.offer(NetworkPacket(TYPE).apply { set("json", json) })
    }

    @Synchronized
    fun send(kind: String, data: JSONObject): Boolean = remote.isNotEmpty() && enqueue(kind, data, remote)

    fun allows(feature: String): Boolean = device.isPaired && SharingPolicy.allows(context, device.deviceId, feature)
    @Synchronized
    fun canSend(feature: String): Boolean = allows(feature) && !remotePolicy.optBoolean("paused", true) && remotePolicy.optJSONObject("enabled")?.optBoolean(feature, false) == true

    fun stopLocalRing() { handler.post(stopRing) }

    fun policyChanged() {
        val enabled = JSONObject()
        SharingPolicy.features.forEach { enabled.put(it, allows(it)) }
        send("policy", JSONObject().put("enabled", enabled).put("paused", SharingPolicy.preferences(context).getBoolean("${device.deviceId}.paused", false)))
        if (!allows("ring")) handler.post(stopRing)
        if (!allows("files")) files.close()
        battery()
        LocalInbox.flush(context,device.deviceId,this)
        if (canSend("notifications")) PingoraNotificationListener.instance?.sync(this)
    }

    override fun onPacketReceived(np: NetworkPacket): Boolean {
        if (!device.isPaired || np.type != TYPE || np.payload != null) { np.payload?.close(); return false }
        val raw = np.getString("json")
        if (raw.toByteArray(Charsets.UTF_8).size > 32768) return false
        val packet = try { JSONObject(raw) } catch (_: Exception) { return false }
        val kind = packet.optString("kind")
        val data = packet.optJSONObject("data") ?: JSONObject()
        if (kind == "hello") {
            val token = data.optString("token")
            if (!token.matches(Regex("[0-9a-f]{48}"))) return false
            synchronized(this) {
                if(remote.isNotEmpty() && remote!=token) {
                    local=ByteArray(24).also {SecureRandom().nextBytes(it)}.joinToString("") {"%02x".format(it)}
                    lastSequence=0;seen.clear();queue.clear()
                }
                remote=token
            }
            enqueue("hello_ack", JSONObject().put("token", local), remote)
            policyChanged()
            return true
        }
        val id = packet.optString("id")
        val sequence=packet.optLong("seq",0)
        val accepted = synchronized(this) {
            if (sequence <= lastSequence || local.isEmpty() || packet.optString("session") != local || id.isEmpty() || id.length > 128 || id in seen) false
            else { lastSequence=sequence; if (seen.size >= 1024) seen.removeFirst(); seen.addLast(id); true }
        }
        if (!accepted) return false
        if (kind == "hello_ack") {
            val token = data.optString("token")
            if (!token.matches(Regex("[0-9a-f]{48}"))) return false
            remote = token; policyChanged(); return true
        }
        when (kind) {
            "notice" -> if(allows("notifications"))LocalInbox.receive(context,device.deviceId,data)
            "notice_removed" -> if(allows("notifications"))LocalInbox.removed(context,device.deviceId,data.optString("source"),data.optString("version"))
            "action_result" -> LocalInbox.acknowledge(context,device.deviceId,data.optString("action_id"))
            "url" -> if(allows("files")) {
                val value=data.optString("url")
                if(validUrl(value)) synchronized(this) {if(links.size>=20)links.removeFirst();links.addLast(value)}
            }
            "file_offer", "file_next", "file_chunk", "file_done", "file_cancel" -> files.handle(kind,data)
            "folder_list", "folder_read" -> files.folders(kind,data)
            "slumber" -> if (allows("slumber")) { slumberStatus=if(!data.optBoolean("available")) "Slumber unavailable on laptop" else if(data.optBoolean("active")) "Active · secure: ${data.optBoolean("secure")} · lid: ${data.optString("lid_action", "unknown")}" else "Available · inactive · lid: ${data.optString("lid_action", "unknown")}" }

            "policy" -> { remotePolicy = data; if(data.optBoolean("paused",true)||data.optJSONObject("enabled")?.optBoolean("files",false)!=true) files.close(); if (canSend("notifications")) PingoraNotificationListener.instance?.sync(this) }
            "sync" -> { if (canSend("notifications")) PingoraNotificationListener.instance?.sync(this); battery() }
            "battery_request" -> battery()
            "battery" -> if(allows("battery")) {val level=data.optInt("level",-1);if(level in 0..100) laptopBattery="Laptop battery: $level%${if(data.optBoolean("charging")) " · charging" else ""}"}
            "notification_action" -> if (allows("notifications") && unlocked()) {
                val result=PingoraNotificationListener.instance?.action(data) ?: "unavailable"
                send("action_result",JSONObject().put("action_id",data.optString("action_id")).put("result",result))
            }
            "clipboard" -> if (allows("clipboard") && unlocked()) {
                val text = data.optString("text")
                if (text.toByteArray(Charsets.UTF_8).size <= 16384) handler.post {
                    if (allows("clipboard") && unlocked()) context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Pingora", text))
                }
            }
            "ring" -> if (allows("ring")) handler.post {
                if (allows("ring")) {
                    stopRing.run()
                    ringtone = RingtoneManager.getRingtone(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                    ringtone?.play(); handler.removeCallbacks(stopRing); handler.postDelayed(stopRing, 30_000)
                }
            }
            "ring_stop" -> if (allows("ring")) handler.post(stopRing)
        }
        return true
    }

    override fun onDeviceUnpaired(context: android.content.Context, deviceId: String) {
        val editor = SharingPolicy.preferences(context).edit()
        (SharingPolicy.features + "paused").forEach { editor.remove("$deviceId.$it") }
        editor.apply()
        LocalInbox.forget(context,deviceId)
    }

    private fun unlocked(): Boolean = !context.getSystemService(KeyguardManager::class.java).isDeviceLocked
    fun battery() {
        if (!canSend("battery")) return
        val state = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return
        val level = state.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = state.getIntExtra(BatteryManager.EXTRA_SCALE, 0)
        if (level < 0 || scale <= 0) return
        val status = state.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        send("battery", JSONObject().put("level", (level * 100 / scale).coerceIn(0,100)).put("charging", status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL))
    }
    companion object {
        fun validUrl(value:String):Boolean {val uri=android.net.Uri.parse(value);return value.length<=4096 && value.none {it.isISOControl()} && uri.scheme in listOf("http","https") && !uri.host.isNullOrEmpty()}
        const val TYPE = "pingora.envelope" }
}

/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.kde.kdeconnect.pingora

import android.app.KeyguardManager
import android.content.ClipboardManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONObject
import org.kde.kdeconnect.KdeConnect

/** A focused, user-invoked send surface: Android does not grant widgets clipboard focus. */
class ClipboardSendActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private var started = false
    private var attempts = 0
    private fun done(message: String) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); finish() }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(android.widget.ProgressBar(this))
        title = "Sending clipboard"
    }
    override fun onResume() {
        super.onResume()
        org.kde.kdeconnect.BackgroundService.ForceRefreshConnections(this)
    }
    override fun onWindowFocusChanged(focus: Boolean) {
        super.onWindowFocusChanged(focus)
        if(focus && !started) { started=true; handler.post(::send) }
    }
    private fun send() {
        if(isFinishing || !hasWindowFocus()) return
        if(getSystemService(KeyguardManager::class.java).isDeviceLocked) {done("Unlock your phone to send clipboard text.");return}
        val peers=KdeConnect.getInstance().devices.values.filter {it.isPaired && it.isReachable && it.getPlugin<BridgePlugin>()?.canSend("clipboard")==true}
        if(peers.isEmpty() && attempts++<75) {handler.postDelayed(::send,200);return}
        if(peers.isEmpty()) {done("No laptop ready. Check your connection and clipboard sharing.");return}
        val clip=getSystemService(ClipboardManager::class.java).primaryClip
        val text=clip?.takeIf {it.itemCount>0}?.getItemAt(0)?.text?.toString()
        if(text.isNullOrEmpty()) {done("The clipboard has no readable text.");return}
        if(text.toByteArray(Charsets.UTF_8).size>16384) {done("Clipboard text is too large (16 KiB maximum).");return}
        fun transmit(index: Int) {
            val peer=peers[index]
            val bridge=peer.getPlugin<BridgePlugin>()
            val sent=bridge?.canSend("clipboard")==true && bridge.send("clipboard",JSONObject().put("text",text))
            done(if(sent) "Clipboard queued for ${peer.name}." else "Connection changed. Try sending again.")
        }
        if(peers.size==1) transmit(0)
        else MaterialAlertDialogBuilder(this).setTitle("Send clipboard to")
            .setItems(peers.map {it.name}.toTypedArray()) {_,i->transmit(i)}.setOnCancelListener {finish()}.show()
    }
    override fun onDestroy() {handler.removeCallbacksAndMessages(null);super.onDestroy()}
}

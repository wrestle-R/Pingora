/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.kde.kdeconnect.pingora

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import org.json.JSONObject
import org.kde.kdeconnect.KdeConnect

/** Explicit destination choice; receiving an exported share intent never sends by itself. */
class ShareActivity : AppCompatActivity() {
    override fun onCreate(state:Bundle?) {
        super.onCreate(state)
        val layout=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(32,48,32,32)}
        setContentView(layout)
        val status=TextView(this).apply {text="Choose a paired device. Enable sharing on both devices first."};layout.addView(status)
        if(intent.action!=Intent.ACTION_SEND){status.text="Only single-item shares are supported.";return}
        val stream=IntentCompat.getParcelableExtra(intent,Intent.EXTRA_STREAM,android.net.Uri::class.java)
        val text=intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        for(device in KdeConnect.getInstance().devices.values.filter {it.isPaired}) {
            layout.addView(Button(this).apply {
                this.text=device.name
                setOnClickListener {
                    val bridge=device.getPlugin<BridgePlugin>()
                    if(bridge==null){status.text="Device offline. Open Pingora on both devices.";return@setOnClickListener}
                    if(stream!=null) {Thread {bridge.files.offer(stream);runOnUiThread {status.text=bridge.files.status}}.start()}
                    else if(text!=null && BridgePlugin.validUrl(text) && bridge.canSend("files")) {bridge.send("url",JSONObject().put("url",text));status.text="Link queued for this connection."}
                    else if(text!=null && text.toByteArray(Charsets.UTF_8).size<=16384 && bridge.canSend("clipboard")) {bridge.send("clipboard",JSONObject().put("text",text));status.text="Text queued for clipboard sharing."}
                    else status.text="This share is unavailable. Check feature controls and content size."
                }
            })
        }
        layout.addView(Button(this).apply {this.text="Close";setOnClickListener {finish()}})
    }
}

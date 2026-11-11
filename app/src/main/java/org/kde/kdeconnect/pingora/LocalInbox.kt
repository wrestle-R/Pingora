/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.kde.kdeconnect.pingora

import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AtomicFile
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.json.JSONArray
import org.json.JSONObject
import org.kde.kdeconnect.helpers.NotificationHelper
import org.kde.kdeconnect.ui.MainActivity
import org.kde.kdeconnect_tp.R
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object LocalInbox {
    private val items=ArrayList<JSONObject>()
    private val pending=ArrayList<JSONObject>()
    private var file:AtomicFile?=null
    private var context:Context?=null
    private var healthy=true
    private var dirty=false
    private var scheduled=false
    private val writer=Executors.newSingleThreadScheduledExecutor {r->Thread(r,"pingora-inbox-storage").apply {isDaemon=true}}
    @Synchronized private fun init(context:Context) {
        if(file!=null)return
        this.context=context.applicationContext
        val target=File(context.filesDir,"pingora-inbox.json");file=AtomicFile(target)
        if(target.exists()) try {
            if(target.length()>12*1024*1024)throw IllegalStateException()
            val root=JSONObject(file!!.openRead().use {it.readBytes().toString(Charsets.UTF_8)})
            val stored=root.optJSONArray("items") ?: JSONArray();for(i in maxOf(0,stored.length()-500) until stored.length())items.add(stored.getJSONObject(i))
            val actions=root.optJSONArray("pending") ?: JSONArray();for(i in 0 until minOf(128,actions.length()))pending.add(actions.getJSONObject(i))
            prune();schedule()
        }catch(_:Exception){healthy=false}
    }
    private fun schedule() {
        dirty=true;if(scheduled||!healthy)return;scheduled=true
        writer.schedule({
            val data=synchronized(this) {dirty=false;JSONObject().put("items",JSONArray(items)).put("pending",JSONArray(pending)).toString()}
            var stream:java.io.FileOutputStream?=null
            try {stream=file!!.startWrite();stream.write(data.toByteArray(Charsets.UTF_8));file!!.finishWrite(stream)}catch(_:Exception){file?.failWrite(stream);synchronized(this){healthy=false}}
            synchronized(this){scheduled=false;if(dirty)schedule()}
        },250,TimeUnit.MILLISECONDS)
    }
    private fun tag(item:JSONObject)="pingora:${item.optString("peer")}:${item.optString("app")}:${item.optString("conversation")}"
    private fun cancelMirror(item:JSONObject) {
        // A conversation slot may now contain a newer source version.
        val current=items.lastOrNull {it.optBoolean("active") && tag(it)==tag(item)}
        if(current!=null && current!==item) return
        context?.let {NotificationManagerCompat.from(it).cancel(tag(item),1)}
    }
    private fun prune(){
        val now=System.currentTimeMillis();val expired=items.filter {now-it.optLong("received")>=DAY}
        expired.forEach {cancelMirror(it)};items.removeAll(expired.toSet())
        while(items.size>500) {val old=items.removeAt(0);cancelMirror(old)}
        pending.removeAll {now-it.optLong("created")>=DAY}
        if(expired.isNotEmpty())schedule()
    }
    @Synchronized fun receive(context:Context,peer:String,data:JSONObject) {
        init(context);if(!healthy)return;prune()
        val source=data.optString("source");val version=data.optString("version");val app=data.optString("app")
        if(source.isEmpty()||source.length>512||version.isEmpty()||version.length>128||app.isEmpty()||app.length>256)return
        if(items.any {it.optString("peer")==peer&&it.optString("source")==source&&it.optString("version")==version})return
        val item=JSONObject(data.toString()).put("peer",peer).put("received",System.currentTimeMillis()).put("active",true)
        val conversation=item.optString("conversation").ifEmpty {source};if(conversation.length>512)return;item.put("conversation",conversation)
        fun bound(field:String,limit:Int){item.put(field,TextLimits.utf8(item.optString(field),limit))}
        bound("sender",512);bound("title",1024);bound("body",16384)
        items.filter {it.optString("peer")==peer&&it.optString("app")==app&&it.optString("conversation")==conversation}.forEach {it.put("active",false)}
        items.add(item);prune();schedule()
        if(!data.optBoolean("snapshot",false))show(item)
    }
    private fun show(item:JSONObject) {
        val context=context ?: return
        val peer=item.optString("peer")
        val reveal=!context.getSystemService(KeyguardManager::class.java).isDeviceLocked || SharingPolicy.preferences(context).getBoolean("$peer.show_locked",false)
        val title=if(reveal)item.optString("title").ifEmpty {item.optString("app")} else "New laptop notification"
        val body=if(reveal)item.optString("body") else "Open Pingora to view private content."
        val open=PendingIntent.getActivity(context,519,Intent(context,MainActivity::class.java).putExtra("pingora_inbox",true),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val identity=MessageDigest.getInstance("SHA-256").digest((peer+item.optString("source")+item.optString("version")).toByteArray()).joinToString("") {"%02x".format(it)}
        val dismiss=Intent(context,MirrorDismissReceiver::class.java).setData(Uri.parse("pingora://dismiss/$identity")).putExtra("peer",peer).putExtra("source",item.optString("source")).putExtra("version",item.optString("version"))
        val delete=PendingIntent.getBroadcast(context,0,dismiss,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification=NotificationCompat.Builder(context,NotificationHelper.Channels.DEFAULT).setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body)).setContentIntent(open).setDeleteIntent(delete).setOnlyAlertOnce(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
        try {NotificationManagerCompat.from(context).notify(tag(item),1,notification)}catch(_:SecurityException){}
    }
    @Synchronized fun removed(context:Context,peer:String,source:String,version:String) {
        init(context);items.filter {it.optString("peer")==peer&&it.optString("source")==source&&it.optString("version")==version}.forEach {it.put("active",false);cancelMirror(it)};schedule()
    }
    @Synchronized fun search(context:Context,query:String):List<JSONObject> {
        init(context);prune()
        val locked=context.getSystemService(KeyguardManager::class.java).isDeviceLocked
        return items.asReversed().filter {(!locked||SharingPolicy.preferences(context).getBoolean("${it.optString("peer")}.show_locked",false))&&(query.isEmpty()||listOf("title","sender","body","app").any {field->it.optString(field).contains(query,ignoreCase=true)})}.map {JSONObject(it.toString())}
    }
    @Synchronized fun clear(context:Context) {init(context);items.clear();healthy=true;schedule()}
    @Synchronized fun storageHealthy(context:Context):Boolean{init(context);return healthy}
    @Synchronized fun dismiss(context:Context,peer:String,source:String,version:String) {
        init(context);if(!healthy||!SharingPolicy.allows(context,peer,"notifications")||context.getSystemService(KeyguardManager::class.java).isDeviceLocked)return
        val item=items.lastOrNull {it.optBoolean("active")&&it.optString("peer")==peer&&it.optString("source")==source&&it.optString("version")==version} ?: return
        if(pending.size>=128)return
        if(pending.none {it.optString("peer")==peer&&it.optString("source")==source&&it.optString("version")==version})pending.add(JSONObject().put("peer",peer).put("source",source).put("version",version).put("action","dismiss").put("action_id",UUID.randomUUID().toString()).put("created",System.currentTimeMillis()).put("last_sent",0))
        item.put("active",false);NotificationManagerCompat.from(context).cancel(tag(item),1);schedule()
    }
    @Synchronized fun flush(context:Context,peer:String,bridge:BridgePlugin) {
        init(context);prune();if(!bridge.canSend("notifications"))return
        pending.filter {it.optString("peer")==peer && System.currentTimeMillis()-it.optLong("last_sent")>10_000}.forEach {if(bridge.send("notification_action",it))it.put("last_sent",System.currentTimeMillis())}
    }
    @Synchronized fun acknowledge(context:Context,peer:String,id:String) {init(context);pending.removeAll {it.optString("peer")==peer&&it.optString("action_id")==id};schedule()}
    @Synchronized fun forget(context:Context,peer:String) {init(context);pending.removeAll {it.optString("peer")==peer};items.filter {it.optString("peer")==peer}.forEach {it.put("active",false);cancelMirror(it)};schedule()}
    private const val DAY=86_400_000L
}

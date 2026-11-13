/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.kde.kdeconnect.pingora

import android.content.Context
import org.json.JSONObject

/** One last successful snapshot per paired identity; survives transport/process death. */
object LaptopTelemetry {
    data class Snapshot(val data: JSONObject, val receivedAt: Long) {
        fun age(now: Long=System.currentTimeMillis())=TelemetryAge.label(receivedAt,now)
    }
    private fun prefs(context: Context)=context.getSharedPreferences("pingora_laptop_snapshots",Context.MODE_PRIVATE)
    @Synchronized fun read(context: Context, peer: String): Snapshot {
        val raw=prefs(context).getString(peer,null)
        if(raw==null || raw.length>32768) return Snapshot(JSONObject(),0)
        return runCatching {val record=JSONObject(raw);Snapshot(record.optJSONObject("data") ?: JSONObject(),record.optLong("received_at",0))}.getOrElse {Snapshot(JSONObject(),0)}
    }
    @Synchronized fun save(context: Context, peer: String, data: JSONObject) {
        if(data.toString().toByteArray().size>24576) return
        val p=prefs(context)
        if(peer !in p.all && p.all.size>=32) return
        p.edit().putString(peer,JSONObject().put("received_at",System.currentTimeMillis()).put("data",data).toString()).apply()
    }
    @Synchronized fun updateSlumber(context: Context,peer: String, slumber: JSONObject) {
        val snapshot=read(context,peer)
        if(snapshot.receivedAt<=0) return
        snapshot.data.put("slumber",slumber)
        prefs(context).edit().putString(peer,JSONObject().put("received_at",snapshot.receivedAt).put("data",snapshot.data).toString()).apply()
    }
    fun forget(context: Context, peer: String) {prefs(context).edit().remove(peer).apply()}
}

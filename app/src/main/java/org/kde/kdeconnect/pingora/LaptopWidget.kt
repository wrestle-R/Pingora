/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.kde.kdeconnect.pingora

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import android.widget.Toast
import org.json.JSONObject
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect_tp.R

/** Controls use explicit broadcasts, never navigate to an activity. Each widget pins its device. */
class LaptopWidget : AppWidgetProvider() {
    companion object {
        private const val ACTION = "io.github.wrestle_r.pingora.LAPTOP_WIDGET"
        fun hasWidgets(context: Context) = AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context,LaptopWidget::class.java)).isNotEmpty()
        fun refresh(context: Context) {
            val manager=AppWidgetManager.getInstance(context)
            val ids=manager.getAppWidgetIds(ComponentName(context,LaptopWidget::class.java))
            if(ids.isNotEmpty()) LaptopWidget().onUpdate(context,manager,ids)
        }
    }
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val preferences=context.getSharedPreferences("pingora_widgets",Context.MODE_PRIVATE)
        val peers=KdeConnect.getInstance().devices.values.filter {it.isPaired}
        for(id in ids) {
            var target=preferences.getString("peer.$id",null)
            if(target==null) {
                val candidates=peers.filter {it.isReachable}
                target=(candidates.singleOrNull() ?: peers.singleOrNull())?.deviceId
                if(target!=null) preferences.edit().putString("peer.$id",target).apply()
            }
            val peer=peers.firstOrNull {it.deviceId==target}
            val bridge=peer?.getPlugin<BridgePlugin>()
            val age=bridge?.let {SystemClock.elapsedRealtime()-it.laptopStatusAt} ?: Long.MAX_VALUE
            val fresh=peer?.isReachable==true && bridge?.isReady==true && bridge.laptopStatusAt>0 && age in 0..90_000
            val status=bridge?.laptopStatus ?: JSONObject()
            val battery=status.optJSONObject("battery")
            val level=battery?.optInt("level",-1) ?: -1
            val slumber=status.optJSONObject("slumber")
            val view=RemoteViews(context.packageName,R.layout.pingora_laptop_widget)
            view.setTextViewText(R.id.laptop_widget_name,peer?.name ?: "Connect your laptop")
            view.setTextViewText(R.id.laptop_widget_battery,if(fresh && level in 0..100) "$level%${if(battery?.optBoolean("charging")==true) " · Charging" else ""}" else "—")
            view.setTextViewText(R.id.laptop_widget_connection,when {peer==null->"Pair a laptop in Pingora";peer.isReachable!=true->"Offline · Tap refresh";!fresh->"Status out of date · Refresh";else->"Last report: connected"})
            view.setChronometer(R.id.laptop_widget_age,bridge?.laptopStatusAt?.takeIf {it>0} ?: SystemClock.elapsedRealtime(),"Updated %s ago",bridge?.laptopStatusAt?.let {it>0}==true)
            view.setTextViewText(R.id.laptop_widget_internet,if(!fresh) "Internet —" else when(status.optString("internet")) {"full"->"Internet online";"portal"->"Sign-in needed";"limited"->"Internet limited";"none"->"No internet";else->"Internet unknown"})
            val profile=status.optString("power_profile")
            view.setTextViewCompoundDrawables(R.id.laptop_widget_power,when(profile) {"power-saver"->R.drawable.ic_profile_saver;"performance"->R.drawable.ic_profile_performance;else->R.drawable.ic_profile_balanced},0,0,0)
            view.setTextViewCompoundDrawables(R.id.laptop_widget_lid,R.drawable.ic_lid_mode,0,0,0)
            view.setTextViewText(R.id.laptop_widget_power,when { !fresh->"Power —";profile=="power-saver"->"Power saver";profile=="balanced"->"Balanced";profile=="performance"->"Performance";else->"Power unavailable"})
            view.setContentDescription(R.id.laptop_widget_power,"Power profile: $profile. Tap to cycle available profiles.")
            view.setContentDescription(R.id.laptop_widget_lid,"Lid-close mode. Tap to switch Sleep or Slumber; this does not sleep the laptop now.")
            view.setTextViewText(R.id.laptop_widget_lid,if(fresh && slumber?.optBoolean("available")==true) when(slumber.optString("lid_action")) {"sleep"->"Lid: Sleep";"slumber"->"Lid: Slumber";else->"Lid: Unknown"} else "Lid unavailable")
            for((control,command) in listOf(R.id.laptop_widget_refresh to "refresh",R.id.laptop_widget_power to "power",R.id.laptop_widget_lid to "lid")) {
                val enabled=command=="refresh" || (fresh && bridge?.canSend(if(command=="power") "power" else "slumber")==true && (command!="lid" || slumber?.optBoolean("lid_control")==true) && (command!="power" || (status.optJSONArray("power_profiles")?.length() ?: 0)>0))
                view.setBoolean(control,"setEnabled",enabled)
                val intent=Intent(context,LaptopWidget::class.java).setAction(ACTION).putExtra("widget",id).putExtra("command",command)
                view.setOnClickPendingIntent(control,PendingIntent.getBroadcast(context,id*4+listOf("refresh","power","lid").indexOf(command),intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            }
            manager.updateAppWidget(id,view)
        }
    }
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context,intent)
        if(intent.action!=ACTION) return
        val id=intent.getIntExtra("widget",-1)
        val target=context.getSharedPreferences("pingora_widgets",Context.MODE_PRIVATE).getString("peer.$id",null)
        val bridge=target?.let {KdeConnect.getInstance().devices[it]?.getPlugin<BridgePlugin>()}
        org.kde.kdeconnect.BackgroundService.ForceRefreshConnections(context)
        val command=intent.getStringExtra("command")
        val fresh=bridge!=null && bridge.isReady && bridge.laptopStatusAt>0 && SystemClock.elapsedRealtime()-bridge.laptopStatusAt in 0..90_000
        if(command=="refresh") { bridge?.requestLaptopStatus();Toast.makeText(context,"Refreshing laptop status",Toast.LENGTH_SHORT).show() }
        else if(fresh && bridge!=null) {
            val status=bridge.laptopStatus
            if(command=="power" && bridge.canSend("power")) {
                val available=status.optJSONArray("power_profiles")
                val profiles=(0 until (available?.length() ?: 0)).map {available!!.optString(it)}.filter {it in listOf("power-saver","balanced","performance")}
                if(profiles.isNotEmpty()) {
                    val next=profiles[(profiles.indexOf(status.optString("power_profile"))+1).mod(profiles.size)]
                    if(bridge.send("power_profile",JSONObject().put("value",next))) Toast.makeText(context,"Requested $next · waiting for laptop",Toast.LENGTH_SHORT).show()
                }
            }
            if(command=="lid" && bridge.canSend("slumber") && status.optJSONObject("slumber")?.optBoolean("lid_control")==true) {
                val next=if(status.optJSONObject("slumber")?.optString("lid_action")=="sleep") "slumber" else "sleep"
                if(bridge.send("slumber_lid",JSONObject().put("value",next))) Toast.makeText(context,"Requested lid mode: $next",Toast.LENGTH_SHORT).show()
            }
        } else Toast.makeText(context,"Laptop unavailable. Refresh before changing controls.",Toast.LENGTH_SHORT).show()
        refresh(context)
    }
    override fun onDeleted(context: Context, ids: IntArray) {
        val editor=context.getSharedPreferences("pingora_widgets",Context.MODE_PRIVATE).edit()
        ids.forEach {editor.remove("peer.$it")};editor.apply()
    }
}

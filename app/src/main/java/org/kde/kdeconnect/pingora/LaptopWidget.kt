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
        fun profileLabel(value: String)=when(value) {"power-saver"->"Battery saving";"balanced"->"Efficiency";"performance"->"Powerful";else->"Unknown"}
        fun metrics(data: JSONObject): String {
            val cpu=data.optDouble("cpu_percent",Double.NaN)
            val used=data.optDouble("ram_used_bytes",Double.NaN);val total=data.optDouble("ram_total_bytes",Double.NaN)
            val cpuText=if(cpu.isFinite() && cpu in 0.0..100.0) "${cpu.toInt()}%" else "—"
            val ramText=if(used.isFinite() && total>0 && used in 0.0..total) "${(used/total*100).toInt()}%" else "—"
            return "CPU $cpuText · RAM $ramText"
        }
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
            val saved=target?.let {LaptopTelemetry.read(context,it)} ?: LaptopTelemetry.Snapshot(JSONObject(),0)
            val status=saved.data
            val battery=status.optJSONObject("battery")
            val level=battery?.optInt("level",-1) ?: -1
            val slumber=status.optJSONObject("slumber")
            val view=RemoteViews(context.packageName,R.layout.pingora_laptop_widget)
            view.setTextViewText(R.id.laptop_widget_name,peer?.name ?: "Connect your laptop")
            view.setTextViewText(R.id.laptop_widget_battery,if(level in 0..100) "$level%" else "—")
            view.setTextViewText(R.id.laptop_widget_connection,when {peer==null->"Pair a laptop in Pingora";peer.isReachable!=true->"Offline · Saved reading";!fresh->"Cached · Refresh to update";else->"Last report: connected"})
            view.setTextViewText(R.id.laptop_widget_age,saved.age())
            view.setTextViewText(R.id.laptop_widget_internet,(if(battery?.optBoolean("charging")==true) "Charging · " else "")+when(status.optString("internet")) {"full"->"Internet online";"portal"->"Sign-in needed";"limited"->"Internet limited";"none"->"No internet";else->"Internet unknown"})
            val profile=status.optString("power_profile")
            view.setImageViewResource(R.id.laptop_widget_power,when(profile) {"power-saver"->R.drawable.ic_profile_saver;"performance"->R.drawable.ic_profile_performance;else->R.drawable.ic_profile_balanced})
            view.setViewVisibility(R.id.laptop_widget_power,if(profile in listOf("power-saver","balanced","performance")) android.view.View.VISIBLE else android.view.View.INVISIBLE)
            view.setContentDescription(R.id.laptop_widget_power,"Last reported profile: ${profileLabel(profile)}")
            view.setTextViewText(R.id.laptop_widget_lid,if(slumber?.optBoolean("available")==true) "Lid mode · ${slumber.optString("lid_action","unknown")}" else "Lid mode unavailable")
            view.setTextViewText(R.id.laptop_widget_metrics,metrics(status).replace(" · ","\n"))
            view.setTextViewText(R.id.laptop_widget_age,if(saved.receivedAt>0) "Last sync · ${android.text.format.DateFormat.format("d MMM, HH:mm",saved.receivedAt)}" else "Not synced yet")
            val hasAge=saved.receivedAt>0 && System.currentTimeMillis()>=saved.receivedAt
            view.setViewVisibility(R.id.laptop_widget_elapsed,if(hasAge) android.view.View.VISIBLE else android.view.View.GONE)
            view.setChronometer(R.id.laptop_widget_elapsed,SystemClock.elapsedRealtime()-(System.currentTimeMillis()-saved.receivedAt).coerceAtLeast(0),"%s since last sync",hasAge)
            val intent=Intent(context,LaptopWidget::class.java).setAction(ACTION).putExtra("widget",id).putExtra("command","refresh")
            view.setOnClickPendingIntent(R.id.laptop_widget_refresh,PendingIntent.getBroadcast(context,id*4,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            val height=manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,240)
            view.setViewVisibility(R.id.laptop_widget_lid,if(height<220) android.view.View.GONE else android.view.View.VISIBLE)
            view.setViewVisibility(R.id.laptop_widget_internet,if(height<200) android.view.View.GONE else android.view.View.VISIBLE)
            if(height<190) view.setViewVisibility(R.id.laptop_widget_elapsed,android.view.View.GONE)
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
        if(command=="refresh") { bridge?.requestLaptopStatus();Toast.makeText(context,"Refreshing laptop status",Toast.LENGTH_SHORT).show() }
        refresh(context)
    }
    override fun onDeleted(context: Context, ids: IntArray) {
        val editor=context.getSharedPreferences("pingora_widgets",Context.MODE_PRIVATE).edit()
        ids.forEach {editor.remove("peer.$it")};editor.apply()
    }
}

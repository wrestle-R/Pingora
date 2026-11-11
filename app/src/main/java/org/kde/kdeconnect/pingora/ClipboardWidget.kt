/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.kde.kdeconnect.pingora

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect_tp.R

/** One tap sends through a focused send surface without navigating the main app. */
class ClipboardWidget:AppWidgetProvider() {
    companion object {
        fun refresh(context: Context) {
            val manager=AppWidgetManager.getInstance(context)
            val ids=manager.getAppWidgetIds(android.content.ComponentName(context,ClipboardWidget::class.java))
            if(ids.isNotEmpty()) ClipboardWidget().onUpdate(context,manager,ids)
        }
    }
    override fun onUpdate(context:Context,manager:AppWidgetManager,ids:IntArray) {
        val intent=Intent(context,ClipboardSendActivity::class.java)
        val pending=PendingIntent.getActivity(context,517,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        for(id in ids){val view=RemoteViews(context.packageName,R.layout.pingora_clipboard_widget);view.setTextViewText(R.id.pingora_widget_status, KdeConnect.getInstance().devices.values.firstOrNull {it.isPaired && it.isReachable}?.let {"${it.name} · Connected"} ?: "Laptop offline");view.setOnClickPendingIntent(R.id.pingora_widget_send,pending);manager.updateAppWidget(id,view)}
    }
}

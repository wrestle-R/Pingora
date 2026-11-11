/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.kde.kdeconnect.pingora

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import org.kde.kdeconnect.ui.MainActivity
import org.kde.kdeconnect_tp.R

/** Opens a foreground send action; never reads the clipboard in a background widget. */
class ClipboardWidget:AppWidgetProvider() {
    override fun onUpdate(context:Context,manager:AppWidgetManager,ids:IntArray) {
        val intent=Intent(context,MainActivity::class.java).putExtra("pingora_sharing",true)
        val pending=PendingIntent.getActivity(context,517,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        for(id in ids){val view=RemoteViews(context.packageName,R.layout.pingora_clipboard_widget);view.setOnClickPendingIntent(R.id.pingora_widget_send,pending);manager.updateAppWidget(id,view)}
    }
}

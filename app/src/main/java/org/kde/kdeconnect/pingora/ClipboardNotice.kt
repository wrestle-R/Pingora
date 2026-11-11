/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.kde.kdeconnect.pingora

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.kde.kdeconnect.helpers.NotificationHelper
import org.kde.kdeconnect.ui.MainActivity
import org.kde.kdeconnect_tp.R

object ClipboardNotice {
    fun received(context: Context, copied: Boolean = true) {
        val open = PendingIntent.getActivity(context, 520, Intent(context, MainActivity::class.java).putExtra("pingora_sharing", true), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notice = NotificationCompat.Builder(context, NotificationHelper.Channels.DEFAULT)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle("Clipboard received")
            .setContentText(if(copied) "Ready to paste on this phone." else "Could not update this phone’s clipboard. Try sending again.").setContentIntent(open)
            .setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
        try { NotificationManagerCompat.from(context).notify("pingora-clipboard", 1, notice) } catch (_: SecurityException) { }
    }
}

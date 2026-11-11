/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.kde.kdeconnect.pingora

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.kde.kdeconnect.KdeConnect

class MirrorDismissReceiver:BroadcastReceiver(){
    override fun onReceive(context:Context,intent:Intent){
        val peer=intent.getStringExtra("peer") ?: return
        LocalInbox.dismiss(context,peer,intent.getStringExtra("source") ?: return,intent.getStringExtra("version") ?: return)
        KdeConnect.getInstance().devices[peer]?.getPlugin<BridgePlugin>()?.let {LocalInbox.flush(context,peer,it)}
    }
}

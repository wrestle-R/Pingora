/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.kde.kdeconnect.pingora

import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest=Config.NONE,application=Application::class,sdk=[23])
class LocalInboxTest {
    @Test fun staleRemovalCannotCloseNewerConversationAndUnlockedContentIsReadable(){
        val context=ApplicationProvider.getApplicationContext<Application>()
        val manager=context.getSystemService(NotificationManager::class.java)
        LocalInbox.clear(context)
        fun notice(version:String)=JSONObject().put("source","synthetic-source").put("version",version).put("app","synthetic-app").put("conversation","synthetic-thread").put("title","Synthetic title").put("body","Synthetic body $version")
        LocalInbox.receive(context,"synthetic-peer",notice("one"))
        LocalInbox.receive(context,"synthetic-peer",notice("two"))
        assertEquals(1,manager.activeNotifications.size)
        LocalInbox.removed(context,"synthetic-peer","synthetic-source","one")
        assertEquals(1,manager.activeNotifications.size)
        assertEquals("Synthetic body two",manager.activeNotifications.single().notification.extras.getCharSequence("android.text").toString())
        LocalInbox.removed(context,"synthetic-peer","synthetic-source","two")
        assertEquals(0,manager.activeNotifications.size)
        LocalInbox.clear(context)
    }
}

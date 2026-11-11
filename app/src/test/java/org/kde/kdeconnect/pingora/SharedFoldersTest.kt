/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.kde.kdeconnect.pingora

import android.app.Application
import android.content.ContentResolver
import android.content.UriPermission
import android.database.MatrixCursor
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import io.mockk.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest=Config.NONE,application=Application::class,sdk=[23])
class SharedFoldersTest {
    @Test fun browsingUsesOpaqueTokensAndRevocationStopsCachedNodes(){
        val context=spyk(ApplicationProvider.getApplicationContext<Application>())
        val resolver=mockk<ContentResolver>()
        every {context.contentResolver} returns resolver
        val root=DocumentsContract.buildTreeDocumentUri("synthetic.documents","root")
        val permission=mockk<UriPermission>()
        every {permission.uri} returns root
        every {permission.isReadPermission} returns true
        every {resolver.persistedUriPermissions} returns listOf(permission)
        SharingPolicy.preferences(context).edit().putString("folder_roots",JSONObject().put("chosen",root.toString()).toString()).commit()
        val replies=mutableListOf<JSONObject>()
        val bridge=mockk<BridgePlugin>()
        every {bridge.allows("folders")} returns true
        every {bridge.send("folder_entries",any())} answers {replies.add(JSONObject(secondArg<JSONObject>().toString()));true}
        val transfers=FileTransfers(context,bridge)
        transfers.folders("folder_list",JSONObject())
        val token=replies.single().getJSONArray("entries").getJSONObject(0).getString("id")
        assertNotEquals("chosen",token);assertFalse(token.contains("root"))
        every {resolver.query(any<Uri>(),any(),null,null,null)} answers {
            MatrixCursor(arrayOf("document_id","display_name","mime_type","size")).apply {addRow(arrayOf<Any>("root/file","Synthetic file","text/plain",3))}
        }
        transfers.folders("folder_list",JSONObject().put("node",token))
        val fileToken=replies.last().getJSONArray("entries").getJSONObject(0).getString("id")
        assertEquals("Synthetic file",replies.last().getJSONArray("entries").getJSONObject(0).getString("name"))
        // Remote input cannot supply arbitrary provider URIs or traversal paths.
        transfers.folders("folder_list",JSONObject().put("node","../root"))
        every {resolver.persistedUriPermissions} returns emptyList()
        transfers.folders("folder_read",JSONObject().put("node",fileToken))
        transfers.folders("folder_list",JSONObject().put("node",token))
        verify(exactly=1) {resolver.query(any<Uri>(),any(),null,null,null)}
        verify(exactly=0) {resolver.openInputStream(any())}
        transfers.close();unmockkAll()
    }
}

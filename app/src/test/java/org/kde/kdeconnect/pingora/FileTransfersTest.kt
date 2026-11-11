/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.kde.kdeconnect.pingora

import android.content.Context
import android.content.ContentResolver
import android.net.Uri
import android.util.Base64
import io.mockk.*
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.file.Files

class FileTransfersTest {
    private val directory=Files.createTempDirectory("pingora-transfer-test").toFile()
    private val bridge=mockk<BridgePlugin>(relaxed=true)
    private val resolver=mockk<ContentResolver>(relaxed=true)
    private val context=mockk<Context>()
    private val uri=mockk<Uri>()
    private fun transfers(bytes:ByteArray):FileTransfers {
        every {context.filesDir} returns directory
        every {context.contentResolver} returns resolver
        every {resolver.query(uri,any(),null,null,null)} returns null
        every {resolver.openInputStream(uri)} answers {ByteArrayInputStream(bytes)}
        every {bridge.canSend("files")} returns true
        every {bridge.allows("files")} returns true
        every {bridge.send(any(),any())} returns true
        mockkStatic(Base64::class)
        every {Base64.encodeToString(any(),any())} answers {java.util.Base64.getEncoder().encodeToString(firstArg())}
        return FileTransfers(context,bridge)
    }
    @After fun cleanup(){unmockkAll();directory.deleteRecursively()}
    @Test fun outgoingTransferIsVisibleCancellableAndNeedsCompleteAcknowledgement(){
        val transfers=transfers(ByteArray(12*1024+3){7})
        transfers.offer(uri)
        val pending=transfers.snapshot().single()
        assertEquals("send",pending.direction);assertEquals("waiting_for_approval",pending.state)
        transfers.handle("file_done",JSONObject().put("id",pending.id))
        assertTrue(transfers.busy())
        transfers.handle("file_next",JSONObject().put("id",pending.id).put("offset",0))
        assertEquals(12*1024L,transfers.snapshot().single().received)
        transfers.handle("file_done",JSONObject().put("id",pending.id))
        assertTrue(transfers.busy())
        transfers.handle("file_next",JSONObject().put("id",pending.id).put("offset",12*1024))
        transfers.handle("file_done",JSONObject().put("id",pending.id))
        assertEquals("sent",transfers.snapshot().single().state);assertFalse(transfers.busy())
        transfers.offer(uri);val next=transfers.snapshot().first {it.state=="waiting_for_approval"}
        transfers.reject(next.id);assertFalse(transfers.busy())
        verify {bridge.send("file_cancel",match {it.optString("id")==next.id})}
    }
    @Test fun failedOfferDoesNotStayBusyAndEmptyFilesNeedApproval(){
        val transfers=transfers(byteArrayOf())
        every {bridge.send("file_offer",any())} returns false
        transfers.offer(uri);assertFalse(transfers.busy());assertTrue(transfers.snapshot().isEmpty())
        every {bridge.send("file_offer",any())} returns true
        transfers.offer(uri);val id=transfers.snapshot().single().id
        transfers.handle("file_done",JSONObject().put("id",id));assertTrue(transfers.busy())
        transfers.handle("file_next",JSONObject().put("id",id).put("offset",0))
        transfers.handle("file_done",JSONObject().put("id",id));assertFalse(transfers.busy())
        assertEquals("sent",transfers.snapshot().single().state)
    }
}

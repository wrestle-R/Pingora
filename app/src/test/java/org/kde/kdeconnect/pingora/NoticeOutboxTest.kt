package org.kde.kdeconnect.pingora
import org.junit.Assert.*
import org.junit.Test
class NoticeOutboxTest {
    @Test fun droppedSendIsRetriedUntilMatchingAckAndStaleAckKeepsReplacement() {
        val queue=NoticeOutbox<String>({0})
        queue.put("source","v1","first")
        assertEquals(listOf("first"),queue.pending()) // dropped send
        assertEquals(listOf("first"),queue.pending()) // next drain retries
        queue.put("source","v2","new")
        queue.acknowledge("source","v1")
        assertEquals(listOf("new"),queue.pending())
        queue.acknowledge("source","v2")
        assertTrue(queue.pending().isEmpty())
    }
    @Test fun memoryAndAgeAreBoundedAndRemovedNotificationsNeverReplay() {
        var now=0L;val queue=NoticeOutbox<String>({now},2)
        for(i in 1..3) queue.put("$i","v","$i")
        assertEquals(listOf("2","3"),queue.pending())
        queue.remove("2");assertEquals(listOf("3"),queue.pending())
        now=120_001;assertTrue(queue.pending().isEmpty())
    }
}

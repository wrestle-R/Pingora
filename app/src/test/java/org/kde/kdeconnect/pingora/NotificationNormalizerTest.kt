/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.kde.kdeconnect.pingora

import org.junit.Assert.*
import org.junit.Test
class NotificationNormalizerTest {
    private val allowed=setOf("com.whatsapp")
    private fun normalize(input:NotificationFields)=NotificationNormalizer.normalize(input,allowed,"pingora")
    @Test fun structuredMessagesPreserveGroupSenderAndUnicode(){
        val result=normalize(NotificationFields("com.whatsapp","source",10,shortcut="group1",conversationTitle="Friends",title="Wrong fallback",text="Unreadable fallback",messages=listOf(MessageFields("A","old"),MessageFields("B 🌈","new\nline"))))!!
        assertEquals("Friends",result.title);assertEquals("B 🌈",result.sender);assertEquals("new\nline",result.body);assertEquals("group1",result.conversation)
    }
    @Test fun summariesAndUnselectedAppsAreNeverForwarded(){
        assertNull(normalize(NotificationFields("com.whatsapp","id",1,summary=true,text="summary")))
        assertNull(normalize(NotificationFields("other","id",1,text="private")))
        assertNull(NotificationNormalizer.normalize(NotificationFields("pingora","id",1,text="mirror"),setOf("pingora"),"pingora"))
    }
    @Test fun unknownThreadsRemainSeparateAndStaleVersionsChange(){
        val first=normalize(NotificationFields("com.whatsapp","one",1,text="code 123456"))!!
        val other=normalize(NotificationFields("com.whatsapp","two",1,text="code 123456"))!!
        val updated=normalize(NotificationFields("com.whatsapp","one",2,text="new"))!!
        assertNotEquals(first.conversation,other.conversation);assertNotEquals(first.version,updated.version)
    }
    @Test fun bodyLimitsDoNotSplitUnicodeOrInventRedactedText(){
        val input=NotificationFields("com.whatsapp","one",1,bigText="🌈".repeat(6000))
        val result=normalize(input)!!;assertEquals(16384,result.body.toByteArray().size);assertFalse(result.body.endsWith("\uD83C"))
        assertNull(normalize(NotificationFields("com.whatsapp","empty",1)))
        assertEquals("",normalize(NotificationFields("com.whatsapp","redacted",1,title="New message"))!!.body)
        assertEquals("",TextLimits.utf8("🌈",3));assertEquals("🌈",TextLimits.utf8("🌈🌈",4))
    }
}

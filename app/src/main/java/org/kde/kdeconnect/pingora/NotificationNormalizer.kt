/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.kde.kdeconnect.pingora

import java.security.MessageDigest

object TextLimits {
    fun utf8(value:String,limit:Int):String {
        var end=minOf(value.length,limit)
        if(end>0&&value[end-1].isHighSurrogate())end--
        var result=value.substring(0,end)
        while(result.toByteArray(Charsets.UTF_8).size>limit){
            val remove=if(result.length>=2&&result.last().isLowSurrogate()&&result[result.lastIndex-1].isHighSurrogate())2 else 1
            result=result.dropLast(remove)
        }
        return result
    }
}
data class MessageFields(val sender:String,val body:String)
data class NotificationFields(val app:String,val source:String,val posted:Long,val summary:Boolean=false,val shortcut:String?=null,val conversationTitle:String?=null,val title:String="",val text:String="",val bigText:String?=null,val messages:List<MessageFields> = emptyList(),val reply:Boolean=false,val read:Boolean=false)
data class NormalizedNotice(val source:String,val version:String,val app:String,val conversation:String,val sender:String,val title:String,val body:String,val reply:Boolean,val read:Boolean)
object NotificationNormalizer {
    fun normalize(input:NotificationFields,allowed:Set<String>,ownPackage:String):NormalizedNotice? {
        if(input.summary||input.app==ownPackage||input.app !in allowed||input.app.length>256||input.source.isEmpty()||input.source.length>512)return null
        val message=input.messages.lastOrNull()
        val sender=message?.sender.orEmpty()
        val title=input.conversationTitle ?: input.title
        val body=message?.body ?: input.bigText ?: input.text
        if(title.isEmpty()&&body.isEmpty())return null
        val thread=input.shortcut?.takeIf {it.isNotEmpty()} ?: input.source
        if(thread.length>512)return null
        val fields=listOf(input.source,input.posted.toString(),title,body,sender,input.reply.toString(),input.read.toString())
        val version=MessageDigest.getInstance("SHA-256").digest(fields.joinToString("") {"${it.length}:$it"}.toByteArray(Charsets.UTF_8)).joinToString("") {"%02x".format(it)}
        return NormalizedNotice(input.source,version,input.app,thread,TextLimits.utf8(sender,512),TextLimits.utf8(title,1024),TextLimits.utf8(body,16384),input.reply,input.read)
    }
}

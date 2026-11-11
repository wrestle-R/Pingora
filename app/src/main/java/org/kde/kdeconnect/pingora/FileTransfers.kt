/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.kde.kdeconnect.pingora

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID

class FileTransfers(private val context: Context, private val bridge: BridgePlugin) {
    data class Offer(val id: String, val name: String, val size: Long, var received: Long = 0, var state: String = "approval_required", val created: Long = System.currentTimeMillis(), val direction: String = "receive")
    private data class Incoming(val offer: Offer, val hash: String, var file: File? = null, var stream: OutputStream? = null, val digest: MessageDigest = MessageDigest.getInstance("SHA-256"))
    private data class Outgoing(val stream: InputStream, val size: Long, val name: String, var started: Boolean = false, var offset: Long = 0, val root: String? = null, val created: Long = System.currentTimeMillis())
    private data class Node(val root: String, val uri: Uri)
    private val incoming = LinkedHashMap<String, Incoming>()
    private val outgoing = LinkedHashMap<String, Outgoing>()
    private val nodes = LinkedHashMap<String, Node>()
    private val savedFiles = LinkedHashMap<String,File>()
    private val completed = ArrayDeque<Offer>()
    var status = ""; private set
    private val directory: File get() = File(context.filesDir, "Pingora/downloads")
    private fun safe(name: String) = name.isNotEmpty() && name.toByteArray().size <= 240 && !name.startsWith('.') && name.none { it.isISOControl() || it in "/\\:" }
    private fun identity(id: String) = id.isNotEmpty() && id.length <= 64 && id.all {it.isLetterOrDigit() && it.code < 128 || it == '-'}
    @Synchronized fun busy(): Boolean = incoming.isNotEmpty() || outgoing.isNotEmpty()
    @Synchronized fun snapshot(): List<Offer> { prune(); return incoming.values.map {it.offer.copy()} + outgoing.map {(id,item)->Offer(id,item.name,item.size,item.offset,if(item.started) "sending" else "waiting_for_approval",item.created,"send")} + completed.toList() }
    private fun roots(): JSONObject = try {JSONObject(SharingPolicy.preferences(context).getString("folder_roots", "{}").orEmpty())} catch (_: Exception) {JSONObject()}
    private fun allowedRoot(id: String): Boolean {
        if(id=="shared-storage") return android.os.Build.VERSION.SDK_INT>=30 && Environment.isExternalStorageManager()
        val value = roots().optString(id)
        if (value.isEmpty()) return false
        return context.contentResolver.persistedUriPermissions.any { it.uri.toString() == value && it.isReadPermission }
    }
    @Synchronized fun savedUri(id: String): Uri? = savedFiles[id]?.let { androidx.core.content.FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",it) }
    @Synchronized fun cancel(id: String) {
        incoming.remove(id)?.let { try {it.stream?.close()} catch (_: Exception) {}; it.file?.delete() }
        outgoing.remove(id)?.let { try {it.stream.close()} catch (_: Exception) {} }
    }
    @Synchronized fun close() { (incoming.keys + outgoing.keys).toList().forEach {cancel(it)}; nodes.clear() }
    @Synchronized fun prune() {
        val now=System.currentTimeMillis()
        incoming.values.filter {now-it.offer.created > if(it.offer.state=="approval_required") 120_000 else 600_000}.map {it.offer.id}.forEach {cancel(it)}
        outgoing.filter {(_,v)->now-v.created>600_000 || v.root?.let {!allowedRoot(it)} == true}.keys.toList().forEach {cancel(it)}
    }
    @Synchronized fun accept(id: String) {
        if (!bridge.allows("files")) return
        val item=incoming[id] ?: return
        if(item.offer.state!="approval_required" || System.currentTimeMillis()-item.offer.created>120_000) {cancel(id);return}
        try {
            directory.mkdirs()
            val file=File(directory,".pingora-$id.part")
            if(!file.createNewFile()) {status="Could not reserve transfer storage.";return}
            item.file=file;item.stream=file.outputStream();item.offer.state="receiving"
            if(!bridge.send("file_next",JSONObject().put("id",id).put("offset",0))) {cancel(id);status="Connection unavailable."}
        } catch (_: Exception) {cancel(id);status="Could not create transfer storage. Check free space."}
    }
    @Synchronized fun reject(id: String) {cancel(id);bridge.send("file_cancel",JSONObject().put("id",id))}
    @Synchronized fun offer(uri: Uri, root: String? = null) {
        if(!bridge.canSend("files") || outgoing.size>=4 || root?.let {!allowedRoot(it)} == true) {status="Enable files on both devices and reconnect.";return}
        var opened: InputStream? = null
        try {
            if(root=="shared-storage" && !sharedFile(uri).isFile) throw IllegalArgumentException()
            val name=if(uri.scheme=="file") sharedFile(uri).name else context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use {c->if(c.moveToFirst()) c.getString(0) else null} ?: "shared-file"
            if(!safe(name)) {status="Unsupported filename.";return}
            val digest=MessageDigest.getInstance("SHA-256");var total=0L;val buffer=ByteArray(CHUNK)
            context.contentResolver.openInputStream(uri)?.use {stream->
                while(true) {val n=stream.read(buffer);if(n<0) break;if(n==0)continue;total+=n;if(total>MAX)throw IllegalArgumentException();digest.update(buffer,0,n)}
            } ?: throw IllegalArgumentException()
            val hash=digest.digest().joinToString("") {"%02x".format(it)}
            val id=UUID.randomUUID().toString();opened=context.contentResolver.openInputStream(uri) ?: throw IllegalArgumentException()
            outgoing[id]=Outgoing(opened,total,name,root=root);opened=null
            if(!bridge.send("file_offer",JSONObject().put("id",id).put("name",name).put("size",total).put("sha256",hash))) {cancel(id);status="Connection unavailable."} else status="File offered. Waiting for approval on the laptop."
        } catch (_: Exception) {try {opened?.close()}catch(_:Exception){};status="Could not read selected file, or it exceeds 100 MiB."}
    }
    @Synchronized fun handle(kind: String, data: JSONObject) {
        prune();val id=data.optString("id")
        if(kind=="file_cancel") {cancel(id);status="Transfer cancelled.";return}
        if(kind=="file_done") {
            val item=outgoing[id] ?: return
            if(!item.started || item.offset!=item.size) return
            completed.addLast(Offer(id,item.name,item.size,item.offset,"sent",item.created,"send"))
            if(completed.size>32) completed.removeFirst()
            cancel(id);status="Transfer completed.";return
        }
        if(!bridge.allows("files") || !identity(id)) return
        try {
            when(kind) {
                "file_offer" -> {
                    val name=data.optString("name");val size=data.optLong("size",-1);val hash=data.optString("sha256")
                    if(!safe(name)||size !in 0..MAX||!hash.matches(Regex("[a-f0-9]{64}"))||id in incoming||completed.any {it.id==id}||incoming.size>=8) return
                    incoming[id]=Incoming(Offer(id,name,size),hash)
                }
                "file_next" -> {
                    val item=outgoing[id] ?: return
                    if(item.root?.let {!allowedRoot(it)}==true) {reject(id);return}
                    if(data.optLong("offset",-1)!=item.offset) {reject(id);return}
                    val bytes=ByteArray(minOf(CHUNK.toLong(),item.size-item.offset).toInt());var filled=0
                    while(filled<bytes.size){val n=item.stream.read(bytes,filled,bytes.size-filled);if(n<=0)throw IllegalStateException();filled+=n}
                    val offset=item.offset;item.started=true;item.offset+=bytes.size
                    if(!bridge.send("file_chunk",JSONObject().put("id",id).put("offset",offset).put("bytes",Base64.encodeToString(bytes,Base64.NO_WRAP)))) {reject(id)}
                }
                "file_chunk" -> {
                    val item=incoming[id] ?: return
                    if(item.offer.state!="receiving" || data.optLong("offset",-1)!=item.offer.received) {reject(id);return}
                    val encoded=data.optString("bytes");if(encoded.length>CHUNK*4/3+4){reject(id);return}
                    val bytes=Base64.decode(encoded,Base64.NO_WRAP)
                    if(bytes.size>CHUNK||item.offer.received+bytes.size>item.offer.size||bytes.isEmpty()&&item.offer.received!=item.offer.size){reject(id);return}
                    item.stream?.write(bytes) ?: throw IllegalStateException();item.digest.update(bytes);item.offer.received+=bytes.size
                    if(item.offer.received==item.offer.size){
                        val hash=item.digest.digest().joinToString("") {"%02x".format(it)}
                        if(hash!=item.hash) {reject(id);status="Transfer checksum mismatch.";return}
                        (item.stream as? java.io.FileOutputStream)?.fd?.sync()
                        item.stream?.close();item.stream=null
                        var committed=false
                        var saved:File?=null
                        for(index in 0..999){
                            val candidate=File(directory,if(index==0)item.offer.name else "$index-${item.offer.name}")
                            try { android.system.Os.link(item.file!!.absolutePath,candidate.absolutePath);committed=true;saved=candidate;break }
                            catch(e:android.system.ErrnoException){if(e.errno!=android.system.OsConstants.EEXIST) throw e}
                        }
                        if(!committed) throw IllegalStateException()
                        item.file?.delete()
                        saved?.let {savedFiles[id]=it};if(savedFiles.size>32)savedFiles.remove(savedFiles.keys.first())
                        item.file=null;item.offer.state="saved";completed.addLast(item.offer.copy());if(completed.size>32)completed.removeFirst();incoming.remove(id)
                        bridge.send("file_done",JSONObject().put("id",id));status="File saved in Pingora downloads. Nothing was opened."
                    } else bridge.send("file_next",JSONObject().put("id",id).put("offset",item.offer.received))
                }
            }
        } catch (_: Exception) {reject(id);status="Transfer stopped. Check connection, available storage and file access."}
    }
    private fun sharedFile(uri: Uri): File {
        require(uri.scheme=="file" && allowedRoot("shared-storage"))
        return SharedStorageBoundary.resolve(Environment.getExternalStorageDirectory(),File(requireNotNull(uri.path)))
    }
    @Synchronized fun folders(kind: String, data: JSONObject) {
        if(!bridge.allows("folders")) return
        val nodeId=data.optString("node")
        if(kind=="folder_read") {val node=nodes[nodeId] ?: return;if(allowedRoot(node.root)) offer(node.uri,node.root);return}
        if(kind!="folder_list") return
        val entries=JSONArray()
        val offset=data.optInt("offset",0).coerceIn(0,1_000_000)
        var nextOffset: Int? = null
        if(nodes.size>700) {val parent=nodes[nodeId];nodes.clear();if(parent!=null) nodes[nodeId]=parent}
        try {
            if(nodeId.isEmpty()) {
                nodes.clear();val selected=roots()
                if(allowedRoot("shared-storage")) {
                    val token=UUID.randomUUID().toString();nodes[token]=Node("shared-storage",Uri.fromFile(Environment.getExternalStorageDirectory()))
                    entries.put(JSONObject().put("id",token).put("name","Phone storage").put("directory",true).put("size",0))
                }
                selected.keys().asSequence().take(32).forEach {root->if(allowedRoot(root)) {val uri=Uri.parse(selected.getString(root));val token=UUID.randomUUID().toString();nodes[token]=Node(root,uri);entries.put(JSONObject().put("id",token).put("name",SharingPolicy.preferences(context).getString("folder_name.$root","Shared folder")).put("directory",true).put("size",0))}}
            } else {
                val parent=nodes[nodeId] ?: return;if(!allowedRoot(parent.root)) return
                if(parent.root=="shared-storage") {
                    val directory=sharedFile(parent.uri)
                    require(directory.isDirectory)
                    // No remote paths: only bounded opaque node tokens leave the phone.
                    val children=java.nio.file.Files.newDirectoryStream(directory.toPath()).use {stream->
                        stream.iterator().asSequence().drop(offset).take(257).map {it.toFile()}.toList()
                    }
                    if(children.size>256) nextOffset=offset+256
                    children.take(256).sortedWith(compareBy<File> {!it.isDirectory}.thenBy {it.name.lowercase()}).forEach {file->
                        try {
                            val child=sharedFile(Uri.fromFile(file));if(nodes.size>=1024)return@forEach
                            val token=UUID.randomUUID().toString();nodes[token]=Node(parent.root,Uri.fromFile(child))
                            entries.put(JSONObject().put("id",token).put("name",child.name.take(240)).put("directory",child.isDirectory).put("size",if(child.isFile)child.length() else 0))
                        } catch (_: IllegalArgumentException) { }
                    }
                } else {
                val documentId=if(DocumentsContract.isTreeUri(parent.uri)&&parent.uri.pathSegments.size==2) DocumentsContract.getTreeDocumentId(parent.uri) else DocumentsContract.getDocumentId(parent.uri)
                val childUri=DocumentsContract.buildChildDocumentsUriUsingTree(parent.uri,documentId)
                context.contentResolver.query(childUri,arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE,DocumentsContract.Document.COLUMN_SIZE),null,null,null)?.use {cursor->
                    cursor.moveToPosition(offset-1)
                    while(entries.length()<256 && nodes.size<1024 && cursor.moveToNext()) {
                        val child=DocumentsContract.buildDocumentUriUsingTree(parent.uri,cursor.getString(0));val token=UUID.randomUUID().toString();nodes[token]=Node(parent.root,child)
                        entries.put(JSONObject().put("id",token).put("name",cursor.getString(1).take(240)).put("directory",cursor.getString(2)==DocumentsContract.Document.MIME_TYPE_DIR).put("size",cursor.getLong(3).coerceAtLeast(0)))
                    }
                    if(cursor.moveToNext()) nextOffset=offset+entries.length()
                }
                }
            }
            bridge.send("folder_entries",JSONObject().put("entries",entries).put("node",nodeId).put("next_offset",nextOffset))
        } catch (_: Exception) {status="Shared folder access was revoked or unavailable.";bridge.send("folder_entries",JSONObject().put("entries",JSONArray()).put("error","access_unavailable"))}
    }
    companion object {private const val CHUNK=12*1024;private const val MAX=100L*1024*1024}
}

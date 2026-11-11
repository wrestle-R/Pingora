package org.kde.kdeconnect.pingora

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/** Explicit import of a signed update downloaded using the recipient's own GitHub session. */
object SignedUpdate {
    data class Staged(val file:File,val version:String,val code:Long,val digest:String)
    private fun hash(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
    fun stage(context:Context,uri:Uri):Staged {
        val directory=File(context.cacheDir,"updates").apply{mkdirs()}
        val file=File.createTempFile("verified-",".apk",directory)
        try {
            context.contentResolver.openInputStream(uri)?.use {input->ZipInputStream(input).use {zip->
                fun read(name:String,limit:Int):ByteArray {
                    val entry=zip.nextEntry ?: error("Update bundle is incomplete")
                    require(!entry.isDirectory && entry.name==name){"Unexpected update bundle entry"}
                    val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                    while(true){val n=zip.read(buffer);if(n<0)break;require(output.size()+n<=limit){"Update metadata exceeded its limit"};output.write(buffer,0,n)}
                    zip.closeEntry();return output.toByteArray()
                }
                val metadata=read("pingora-update.json",65536)
                val signature=android.util.Base64.decode(read("pingora-update.sig",256),android.util.Base64.DEFAULT)
                val key=context.resources.openRawResource(org.kde.kdeconnect_tp.R.raw.pingora_update_key).bufferedReader().use{it.readText().trim()}
                require(key.matches(Regex("[0-9a-fA-F]{64}"))){"Invalid update trust anchor"}
                val raw=key.chunked(2).map{it.toInt(16).toByte()}.toByteArray()
                val verifier=Ed25519Signer();verifier.init(false,Ed25519PublicKeyParameters(raw,0));verifier.update(metadata,0,metadata.size)
                require(verifier.verifySignature(signature)){"Update signature is invalid"}
                val manifest=JSONObject(String(metadata,Charsets.UTF_8));require(manifest.getInt("schema")==1){"Unsupported update format"}
                val target=manifest.getJSONObject("targets").getJSONObject("android")
                require(target.getString("package_name")==context.packageName){"This update is for another application"}
                val code=manifest.getLong("version_code")
                val installed=context.packageManager.getPackageInfo(context.packageName,0)
                val current=if(Build.VERSION.SDK_INT>=28)installed.longVersionCode else @Suppress("DEPRECATION") installed.versionCode.toLong()
                require(code>current){"No newer update in this bundle"}
                val name=target.getString("name");require(name.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,159}\\.apk"))){"Unsafe update filename"}
                val size=target.getLong("size");require(size in 1..536870912){"Invalid update size"}
                val entry=zip.nextEntry ?: error("Update APK is missing");require(entry.name==name && !entry.isDirectory){"Unexpected APK entry"}
                val digest=MessageDigest.getInstance("SHA-256");var count=0L;val buffer=ByteArray(65536)
                file.outputStream().use{out->while(true){val n=zip.read(buffer);if(n<0)break;count+=n;require(count<=size){"APK exceeds signed size"};digest.update(buffer,0,n);out.write(buffer,0,n)}}
                val actual=digest.digest().joinToString(""){"%02x".format(it)}
                require(count==size && actual.equals(target.getString("sha256"),true)){"Update APK integrity verification failed"}
                zip.closeEntry();require(zip.nextEntry==null){"Unexpected extra bundle entry"}
                @Suppress("DEPRECATION") val flags=if(Build.VERSION.SDK_INT>=28)PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
                val archive=context.packageManager.getPackageArchiveInfo(file.path,flags) ?: error("Invalid APK")
                @Suppress("DEPRECATION") val apkCode=if(Build.VERSION.SDK_INT>=28)archive.longVersionCode else archive.versionCode.toLong()
                require(archive.packageName==context.packageName && apkCode==code){"APK identity or version does not match signed metadata"}
                @Suppress("DEPRECATION") val signatures=if(Build.VERSION.SDK_INT>=28)archive.signingInfo?.apkContentsSigners else archive.signatures
                val existing=context.packageManager.getPackageInfo(context.packageName,flags)
                @Suppress("DEPRECATION") val previous=if(Build.VERSION.SDK_INT>=28)existing.signingInfo?.apkContentsSigners else existing.signatures
                val expected=target.getString("signing_cert_sha256")
                require(signatures?.size==1 && hash(signatures[0].toByteArray()).equals(expected,true) && previous?.any{hash(it.toByteArray()).equals(expected,true)}==true){"APK signing identity does not match this installation"}
                return Staged(file,manifest.getString("version"),code,actual)
            }} ?: error("Could not open update bundle")
        } catch(e:Exception){file.delete();throw IllegalArgumentException("Update verification failed. Check the bundle, version and signing identity.",e)}
    }
    fun install(context:Context,staged:Staged,approved:Boolean,busy:Boolean):Boolean {
        require(approved){"Approve installation first"};require(!busy){"Finish active calls or transfers first"}
        val digest=MessageDigest.getInstance("SHA-256");staged.file.inputStream().use{input->val b=ByteArray(65536);while(true){val n=input.read(b);if(n<0)break;digest.update(b,0,n)}}
        require(digest.digest().joinToString(""){"%02x".format(it)}==staged.digest){"Staged update changed"}
        if(Build.VERSION.SDK_INT>=26 && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:${context.packageName}")));return false
        }
        val uri=FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",staged.file)
        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));return true
    }
}

package dev.pianopocket.update

import java.io.File
import java.io.InterruptedIOException
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.net.URI

data class UpdateManifest(val versionCode: Int,val versionName: String,val minSdk: Int,val bytes: Long,val sha256: String,val notes: String) {
    init {
        require(versionCode>0 && versionName.matches(Regex("[0-9]{1,4}\\.[0-9]{1,4}\\.[0-9]{1,4}(?:-alpha)?"))) {"更新バージョンが不正です"}
        require(minSdk in 29..100 && bytes in 1024..MAX_APK_BYTES && sha256.matches(Regex("[a-f0-9]{64}")) && notes.length<=8000){"更新情報が不正です"}
    }
    val fileName get()="PianoPocket-${versionName.removeSuffix("-alpha")}-arm64.apk"
    val apkUrl get()="$BASE/$fileName"
    val releaseUrl get()="https://github.com/tstyr/PianoPocket/releases/tag/v$versionName"
    fun available(current: Int,sdk: Int,abis: List<String>)=versionCode>current && sdk>=minSdk && "arm64-v8a" in abis
    companion object {
        const val BASE="https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts"
        const val LATEST="$BASE/latest.json"
        const val MAX_APK_BYTES=128L*1024*1024
        fun allowed(value: String): URI {
            val uri=URI(value)
            require(uri.scheme=="https" && uri.host=="raw.githubusercontent.com" && uri.port==-1 && uri.userInfo==null && uri.rawQuery==null && uri.rawFragment==null)
            require(value==LATEST || value.matches(Regex(Regex.escape(BASE)+"/PianoPocket-[0-9]{1,4}\\.[0-9]{1,4}\\.[0-9]{1,4}-arm64\\.apk"))) {"更新先が不正です"}
            return uri
        }
    }
}

interface UpdateResponse: AutoCloseable {val status: Int;val offset: Long?;val total: Long?;val input: java.io.InputStream}
fun interface UpdateSource {fun open(url: String,offset: Long): UpdateResponse}

/** Resumable APK transfer. Network and platform signature verification are separate. */
class UpdateTransfer(private val source: UpdateSource) {
    fun receive(info: UpdateManifest,home: File,cancel:()->Boolean={false},progress:(Long)->Unit={}): File {
        check(home.isDirectory || home.mkdirs())
        val complete=File(home,info.fileName);val pending=File(home,"${info.versionCode}-${info.sha256.take(12)}.part")
        fun checkCancel(){if(cancel())throw InterruptedIOException("ダウンロードをキャンセルしました")}
        checkCancel()
        if(complete.isFile && complete.length()==info.bytes && digest(complete,cancel)==info.sha256){progress(info.bytes);return complete}
        complete.delete()
        if(pending.length()>info.bytes)pending.delete()
        if(pending.length()!=info.bytes){
            var position=pending.length()
            source.open(info.apkUrl,position).use {response->
                if(response.status==200)position=0 else require(response.status==206 && response.offset==position && response.total==info.bytes){"再開位置が一致しません"}
                require(response.total==null || response.total==info.bytes){"APKサイズが更新情報と一致しません"}
                RandomAccessFile(pending,"rw").use {file->
                    file.setLength(position);file.seek(position);val block=ByteArray(65536)
                    while(true){checkCancel();val n=response.input.read(block);if(n<0)break;if(n==0)continue;require(position+n<=info.bytes){"APKサイズが上限を超えました"};file.write(block,0,n);position+=n;progress(position)}
                    file.fd.sync()
                }
            }
            checkCancel();require(position==info.bytes){"通信が途中で終了しました。再試行で再開できます"}
        }
        if(digest(pending,cancel)!=info.sha256){pending.delete();error("APKのSHA-256が一致しません")}
        checkCancel();check(pending.renameTo(complete)){"APKを保存できません"};return complete
    }
    companion object {
        fun digest(file: File,cancel:()->Boolean={false}): String {
            val hash=MessageDigest.getInstance("SHA-256");val block=ByteArray(65536)
            file.inputStream().use {input->while(true){if(cancel())throw InterruptedIOException("キャンセルしました");val n=input.read(block);if(n<0)break;hash.update(block,0,n)}}
            return hash.digest().joinToString(""){"%02x".format(it)}
        }
    }
}

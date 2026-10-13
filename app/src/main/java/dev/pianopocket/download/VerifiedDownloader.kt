package dev.pianopocket.download

import java.io.*
import java.net.HttpURLConnection
import java.security.MessageDigest

interface ChunkResponse: Closeable {
    val status: Int
    val offset: Long?
    val total: Long?
    val input: InputStream
}
fun interface ChunkSource {fun open(part: DownloadPart,offset: Long): ChunkResponse}

class HttpsChunkSource: ChunkSource {
    @Volatile private var active: HttpURLConnection?=null
    fun abort() {active?.disconnect()}
    override fun open(part: DownloadPart,offset: Long): ChunkResponse {
        var url=allowedDownloadUrl(part.url).toURL()
        repeat(5) {
            val connection=url.openConnection() as HttpURLConnection;active=connection
            connection.connectTimeout=15000;connection.readTimeout=10000;connection.instanceFollowRedirects=false
            connection.setRequestProperty("User-Agent","PianoPocket")
            connection.setRequestProperty("Accept-Encoding","identity")
            if(offset>0)connection.setRequestProperty("Range","bytes=$offset-")
            try {
                val status=connection.responseCode
                if(status in listOf(301,302,303,307,308)) {
                    val location=connection.getHeaderField("Location") ?: error("Redirect without destination")
                    url=allowedDownloadUrl(url.toURI().resolve(location).toString()).toURL();connection.disconnect()
                }else {
                    require(status==200 || status==206) {"ダウンロード先が応答しました: HTTP $status"}
                    val range=connection.getHeaderField("Content-Range")?.let {Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(it)}
                    val stream=connection.inputStream
                    return object:ChunkResponse {
                        override val status=status
                        override val offset=range?.groupValues?.get(1)?.toLong()
                        override val total=range?.groupValues?.get(3)?.toLong()
                        override val input=stream
                        override fun close(){try{stream.close()}finally{connection.disconnect();if(active===connection)active=null}}
                    }
                }
            }catch(e:Throwable){connection.disconnect();if(active===connection)active=null;throw e}
        }
        error("ダウンロード先の転送回数が上限を超えました")
    }
}

/** Bounded, resumable transfer. Only fully verified chunks are passed to the ZIP importer. */
class VerifiedDownloader(private val source: ChunkSource) {
    fun receive(pack: DownloadPack,directory: File,canceled: ()->Boolean,progress: (Long)->Unit): List<File> {
        directory.mkdirs();var finished=0L;val result=mutableListOf<File>()
        fun check(){if(canceled())throw InterruptedIOException("ダウンロードをキャンセルしました")}
        for(part in pack.parts){
            check();val file=File(directory,part.name);val pending=File(directory,part.name+".pending")
            if(file.isFile && file.length()==part.bytes && digest(file,canceled)==part.sha256){finished+=part.bytes;result+=file;progress(finished);continue}
            file.delete()
            if(pending.length()>part.bytes)pending.delete()
            if(pending.length()==part.bytes){if(digest(pending,canceled)==part.sha256){require(pending.renameTo(file));finished+=part.bytes;result+=file;progress(finished);continue}else pending.delete()}
            var position=pending.length()
            source.open(part,position).use {response->
                if(response.status==200)position=0
                else require(response.status==206 && response.offset==position && response.total==part.bytes) {"再開位置の応答が一致しません"}
                FileOutputStream(pending,position>0).use {out->
                    val buffer=ByteArray(65536)
                    while(true){check();val n=response.input.read(buffer);if(n<0)break;if(n==0)continue
                        require(position+n<=part.bytes) {"ダウンロードサイズが上限を超えました"}
                        out.write(buffer,0,n);position+=n;progress(finished+position)
                    }
                    out.fd.sync()
                }
            }
            check();require(position==part.bytes) {"通信が途中で終了しました。再試行で再開できます"}
            if(digest(pending,canceled)!=part.sha256){pending.delete();error("ダウンロードしたファイルのSHA-256が一致しません")}
            require(pending.renameTo(file)) {"Download commit failed"};finished+=part.bytes;result+=file
        }
        val combined=MessageDigest.getInstance("SHA-256");val buffer=ByteArray(65536)
        for(file in result)file.inputStream().use {input->while(true){check();val n=input.read(buffer);if(n<0)break;combined.update(buffer,0,n)}}
        require(hex(combined.digest())==pack.sha256) {"音源パッケージのSHA-256が一致しません"}
        return result
    }
    companion object {
        fun digest(file: File,canceled: ()->Boolean = {false}): String {
            val hash=MessageDigest.getInstance("SHA-256");val bytes=ByteArray(65536)
            file.inputStream().use {stream->while(true){if(canceled())throw InterruptedIOException("キャンセルしました");val n=stream.read(bytes);if(n<0)break;hash.update(bytes,0,n)}}
            return hex(hash.digest())
        }
        fun hex(bytes: ByteArray)=bytes.joinToString(""){"%02x".format(it)}
    }
}

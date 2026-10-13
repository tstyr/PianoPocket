package dev.pianopocket.library

import dev.pianopocket.audio.NativeAudio
import java.io.File
import java.security.MessageDigest

/** Imported source files are immutable. Decode once, on the import/audio preparation worker. */
class DecodedSamples(private val home: File) {
    fun resolve(source: File, instrument: String): File {
        if(source.extension.lowercase() !in setOf("flac","ogg"))return source
        require(source.length()<=512L*1024*1024) { "Compressed sample too large" }
        val signature=MessageDigest.getInstance("SHA-256").digest("${source.canonicalPath}:${source.length()}:${source.lastModified()}".toByteArray()).joinToString(""){"%02x".format(it)}
        val dir=File(home,instrument).apply {mkdirs()};val result=File(dir,"$signature.wav")
        if(result.isFile)return result
        require(dir.usableSpace>source.length()*2+128L*1024*1024) { "圧縮サンプルの展開に必要な空き容量がありません" }
        val temp=File(dir,"$signature.pending")
        try { NativeAudio.decodeSample(source.path,temp.path);require(temp.length()>44 && temp.renameTo(result)) { "Decoded sample commit failed" } }
        catch(e:Throwable){temp.delete();throw e}
        return result
    }
    fun delete(instrument: String) {File(home,instrument).deleteRecursively()}
}

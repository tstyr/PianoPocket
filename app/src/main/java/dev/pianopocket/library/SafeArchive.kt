package dev.pianopocket.library

import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/** Pure-JVM archive boundary, shared by Android import and adversarial tests. */
object SafeArchive {
    data class Limits(val files: Int=20000,val perFile: Long=512L*1024*1024,val total: Long=8L*1024*1024*1024,val ratio: Long=1000,val reserve: Long=128L*1024*1024)
    fun extract(input: InputStream, directory: File, limits: Limits=Limits(), progress: (String)->Unit = {}) {
        ZipInputStream(input).use { zip ->
            val seen=hashSetOf<String>();var count=0;var total=0L
            while(true) {
                val entry=zip.nextEntry ?: break
                require(++count<=limits.files) { "Archive file count exceeded" }
                val path=DspresetParser.safeRelative(entry.name.trimEnd('/','\\'))
                val target=File(directory,path).canonicalFile
                require(target.path.startsWith(directory.canonicalPath+File.separator)) { "ZIP traversal" }
                require(seen.add(target.path)) { "Duplicate archive path: $path" }
                if(entry.isDirectory)require(target.mkdirs() || target.isDirectory) { "Cannot create archive directory" } else {
                    target.parentFile!!.mkdirs();progress("展開 $count: ${target.name}")
                    var size=0L;val buffer=ByteArray(65536)
                    target.outputStream().use { out ->
                        while(true) { val n=zip.read(buffer);if(n<0)break;size+=n;total+=n
                            require(size<=limits.perFile && total<=limits.total) { "Archive expansion size limit exceeded" }
                            require(directory.usableSpace>limits.reserve) { "Storage space exhausted" }
                            out.write(buffer,0,n)
                        }
                    }
                    if(entry.compressedSize>0)require(size<=maxOf(1024L*1024,entry.compressedSize*limits.ratio)) { "ZIP compression ratio exceeded" }
                };zip.closeEntry()
            }
            require(count>0) { "Not a ZIP-based archive. Encrypted/proprietary .dslibrary unsupported." }
        }
    }
}

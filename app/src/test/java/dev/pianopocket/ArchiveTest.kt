package dev.pianopocket
import dev.pianopocket.library.SafeArchive
import dev.pianopocket.library.LibraryFilePolicy
import org.junit.Test
import org.junit.Assert.*
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry

class ArchiveTest {
    @Test fun macosSidecarsAreNotInstruments(){assertTrue(LibraryFilePolicy.isPreset("Piano.dspreset"));assertFalse(LibraryFilePolicy.isPreset("__MACOSX/._Piano.dspreset"));assertFalse(LibraryFilePolicy.isPreset("Nested/._Piano.dspreset"))}
    private fun archive(vararg entries: Pair<String,ByteArray>): ByteArray {
        val out=ByteArrayOutputStream();ZipOutputStream(out).use { zip->entries.forEach { (name,data)->zip.putNextEntry(ZipEntry(name));zip.write(data);zip.closeEntry() } };return out.toByteArray()
    }
    private fun extract(bytes: ByteArray,limits: SafeArchive.Limits=SafeArchive.Limits(reserve=0),check:(java.io.File)->Unit={}) {
        val dir=Files.createTempDirectory("pocket-zip-").toFile();try{SafeArchive.extract(bytes.inputStream(),dir,limits);check(dir)}finally{dir.deleteRecursively()}
    }
    @Test fun validArchive(){extract(archive("Samples/C4.wav" to byteArrayOf(1,2,3))) { dir->assertArrayEquals(byteArrayOf(1,2,3),java.io.File(dir,"Samples/C4.wav").readBytes()) } }
    @Test(expected=IllegalArgumentException::class) fun traversal(){extract(archive("../escape.wav" to byteArrayOf(1)))}
    @Test(expected=IllegalArgumentException::class) fun absolutePath(){extract(archive("/escape.wav" to byteArrayOf(1)))}
    @Test(expected=IllegalArgumentException::class) fun canonicalDuplicate(){extract(archive("a.wav" to byteArrayOf(1),"./a.wav" to byteArrayOf(2)))}
    @Test(expected=IllegalArgumentException::class) fun expandedSize(){extract(archive("large.wav" to ByteArray(100)),SafeArchive.Limits(perFile=50,reserve=0))}
    @Test(expected=IllegalArgumentException::class) fun totalSize(){extract(archive("a" to ByteArray(40),"b" to ByteArray(40)),SafeArchive.Limits(total=60,reserve=0))}
    @Test(expected=IllegalArgumentException::class) fun fileCount(){extract(archive("a" to byteArrayOf(1),"b" to byteArrayOf(1)),SafeArchive.Limits(files=1,reserve=0))}
    @Test(expected=IllegalArgumentException::class) fun compressionBomb(){extract(archive("bomb" to ByteArray(2*1024*1024)),SafeArchive.Limits(ratio=1,reserve=0))}
}

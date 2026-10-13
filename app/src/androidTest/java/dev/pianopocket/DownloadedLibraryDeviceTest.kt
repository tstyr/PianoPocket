package dev.pianopocket

import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import dev.pianopocket.library.LibraryRepository
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DownloadedLibraryDeviceTest {
    @Test fun verifiedPackageImportSurvivesAnotherRepositoryAndDecodesFlac() {
        val instrumentation=InstrumentationRegistry.getInstrumentation();val app=instrumentation.targetContext
        val dir=File(app.cacheDir,"download-import-${UUID.randomUUID()}").apply {mkdirs()}
        val context=object:ContextWrapper(app){override fun getFilesDir():File=dir}
        try {
            val audio=instrumentation.context.assets.open("compressed-samples/tone.flac").use {it.readBytes()}
            val entries=mapOf("download-id.txt" to "test-piano-v1".toByteArray(),"Test.sfz" to "<region> key=60 sample=Samples/tone.flac".toByteArray(),"Samples/tone.flac" to audio)
            val zip=ByteArrayOutputStream();ZipOutputStream(zip).use {out->entries.forEach {(name,bytes)->out.putNextEntry(ZipEntry(name));out.write(bytes);out.closeEntry()}}
            val repository=LibraryRepository(context)
            val entry=repository.importDownloaded(zip.toByteArray().inputStream(),"test-piano-v1",entries.values.sumOf {it.size.toLong()}) {LibraryRepository(context).entries()}.single()
            val restarted=LibraryRepository(context);assertEquals(entry.id,restarted.findDownloaded("test-piano-v1")?.id)
            val preset=restarted.parse(entry);val decoded=restarted.resolve(entry,preset.regions.single().path)
            assertTrue(decoded.name.endsWith(".wav"));assertTrue(restarted.entries().single().decodedBytes>0)
            restarted.delete(entry);assertFalse(decoded.exists());assertNull(restarted.findDownloaded("test-piano-v1"))
        }finally{dir.deleteRecursively()}
    }
}

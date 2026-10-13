package dev.pianopocket

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import dev.pianopocket.library.LibraryRepository
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class SfzLibraryDeviceTest {
    @Test fun bundledBankInstallsAndSurvivesRepositoryRestart() {
        val app=InstrumentationRegistry.getInstrumentation().targetContext
        val token=UUID.randomUUID().toString()
        val folder=File(app.cacheDir,"sfz-test-$token").apply {mkdirs()}
        val context=object:ContextWrapper(app) {
            override fun getFilesDir():File=folder
            override fun getSharedPreferences(name:String,mode:Int):SharedPreferences=app.getSharedPreferences("sfz-test-$token-$name",mode)
        }
        try {
            val repository=LibraryRepository(context)
            val entry=repository.ensureBundled().single()
            assertTrue(entry.preset.endsWith(".sfz"))
            val preset=repository.parse(entry)
            assertEquals(36,preset.regions.size);assertTrue(preset.warnings.isEmpty())
            preset.regions.forEach { assertTrue(repository.resolve(entry,it.path).length()>44) }
            repository.used(entry)
            val restarted=LibraryRepository(context)
            assertEquals(entry.id,restarted.ensureBundled().single().id)
            assertTrue(restarted.entries().single().lastUsed>0)
            restarted.delete(entry)
            assertTrue(restarted.entries().isEmpty())
            assertEquals(36,restarted.parse(restarted.installBundled().single()).regions.size)
        } finally { folder.deleteRecursively();app.deleteSharedPreferences("sfz-test-$token-library") }
    }
}

package dev.pianopocket

import androidx.test.platform.app.InstrumentationRegistry
import dev.pianopocket.audio.NativeAudio
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class CompressedSampleDeviceTest {
    @Test fun jniDecodesFlacAndVorbisToPlayableWaveHeaders() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val dir=File(instrumentation.targetContext.cacheDir,"codec-device-test").apply {mkdirs()}
        try {
            for(extension in listOf("flac","ogg")) {
                val source=File(dir,"tone.$extension");val result=File(dir,"tone-$extension.wav")
                instrumentation.context.assets.open("compressed-samples/tone.$extension").use {input->source.outputStream().use {input.copyTo(it)}}
                NativeAudio.decodeSample(source.path,result.path)
                val header=result.inputStream().use { input->ByteArray(44).also {assertEquals(44,input.read(it))} }
                assertEquals("RIFF",String(header,0,4));assertEquals("WAVE",String(header,8,4));assertEquals(2,header[22].toInt());assertTrue(result.length()>44100*4)
            }
        } finally {dir.deleteRecursively()}
    }
}

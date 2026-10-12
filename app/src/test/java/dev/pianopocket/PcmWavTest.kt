package dev.pianopocket
import dev.pianopocket.sequencer.PcmWavInput
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.ByteArrayOutputStream
class PcmWavTest {
    private fun wav(bits: Int=16,truncated: Boolean=false): ByteArray {
        val out=ByteArrayOutputStream()
        fun tag(s: String)=out.write(s.toByteArray())
        fun number(n: Int,width: Int){repeat(width){out.write(n ushr (8*it))}}
        tag("RIFF");number(46+16,4);tag("WAVEJUNK");number(1,4);out.write(42);out.write(0)
        tag("fmt ");number(16,4);number(1,2);number(2,2);number(48000,4);number(192000,4);number(4,2);number(bits,2)
        tag("data");number(16,4);repeat(if(truncated)12 else 16){out.write(it)}
        return out.toByteArray()
    }
    private fun withFile(bytes: ByteArray,block:(File)->Unit){val f=File.createTempFile("pocket-pcm-",".wav");try{f.writeBytes(bytes);block(f)}finally{f.delete()}}
    @Test fun findsPcmThroughOddMetadataAndReadsOnlyWholeStereoFrames()=withFile(wav()){f->PcmWavInput(f).use { pcm->assertEquals(48000,pcm.rate);assertEquals(4L,pcm.frames);val buffer=ByteArray(10);assertEquals(8,pcm.read(buffer));assertArrayEquals(ByteArray(8){it.toByte()},buffer.copyOf(8));assertEquals(8,pcm.read(buffer));assertEquals(-1,pcm.read(buffer)) }}
    @Test fun rejectsTruncatedOrNonPcm16Input(){for(bytes in listOf(wav(truncated=true),wav(bits=24))){withFile(bytes){f->assertTrue(runCatching { PcmWavInput(f).close() }.isFailure)}}}
}

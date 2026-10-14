package dev.pianopocket
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.pianopocket.audio.*
import dev.pianopocket.library.SfzParser
import dev.pianopocket.sequencer.TrackRouting
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
@RunWith(AndroidJUnit4::class)
class MultiBankDeviceTest {
    @Test fun fourColumnJniTimelineKeepsSameChannelTracksAndBanksIndependent(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val tone=File(context.cacheDir,"track-original.wav");val rendered=File(context.cacheDir,"track-export.wav")
        val latch=CountDownLatch(1);var error:Throwable?=null
        AudioWorkQueue.executor.execute {
            try {
                val bytes=ByteBuffer.allocate(44+48000*2).order(ByteOrder.LITTLE_ENDIAN)
                bytes.put("RIFF".toByteArray()).putInt(36+48000*2).put("WAVEfmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(48000).putInt(96000).putShort(2).putShort(16).put("data".toByteArray()).putInt(96000)
                repeat(48000){i->bytes.putShort((kotlin.math.sin(i*2*Math.PI*440/48000)*9000).toInt().toShort())};tone.writeBytes(bytes.array())
                val main=SfzParser().parse("<region> sample=tone.wav key=60 pan=-100 ampeg_release=0.005","A").regions.single()
                val other=TrackRouting.region(SfzParser().parse("<region> sample=tone.wav key=60 pan=100 ampeg_release=0.005","B").regions.single(),1,1)
                NativeAudio.suspendPerformance();NativeAudio.exportBegin(32,48000)
                NativeAudio.region(tone.path,main.ints,main.floats);NativeAudio.region(tone.path,other.ints,other.floats);NativeAudio.trackRoute(1,1)
                NativeAudio.exportControl(4,0,0,.3f)
                NativeAudio.exportWav(rendered.path,longArrayOf(0,0,200000,400000),intArrayOf(0x90,60,100,0,0x90,60,100,1,0x80,60,0,1,0x80,60,0,0),500000,16,0)
                val pcm=ByteBuffer.wrap(rendered.readBytes()).order(ByteOrder.LITTLE_ENDIAN);var left=0.0;var right=0.0
                for(frame in 12000..16000){val l=pcm.getShort(44+frame*4)/32768.0;val r=pcm.getShort(46+frame*4)/32768.0;left+=l*l;right+=r*r}
                assertTrue(left>1);assertEquals(0.0,right,0.0)
            }catch(failure:Throwable){error=failure}finally{NativeAudio.abort();tone.delete();rendered.delete();latch.countDown()}
        }
        assertTrue(latch.await(60,TimeUnit.SECONDS));error?.let {throw AssertionError("JNI multi-bank export",it)}
    }
}

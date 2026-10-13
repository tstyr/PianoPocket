package dev.pianopocket

import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.pianopocket.audio.AudioWorkQueue
import dev.pianopocket.sequencer.*
import dev.pianopocket.video.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class ExportServiceDeviceTest {
    private fun tone(file: File) {
        val rate=48000;val bytes=ByteBuffer.allocate(44+rate*4).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray()).putInt(36+rate*4).put("WAVEfmt ".toByteArray()).putInt(16).putShort(1).putShort(2).putInt(rate).putInt(rate*4).putShort(4).putShort(16).put("data".toByteArray()).putInt(rate*4)
        repeat(rate){i->bytes.putShort((sin(i*2*PI*440/rate)*10000).toInt().toShort());bytes.putShort((sin(i*2*PI*660/rate)*8000).toInt().toShort())};file.writeBytes(bytes.array())
    }
    private fun request(file: File): ExportRequest {
        val song=MidiSong.recorded(listOf(SongMessage(0,0,0,0x90,60,100),SongMessage(0,500000,0,0x80,60,0)),1000000)
        val ints=intArrayOf(0,60,60,60,1,127,0,0,-1,-1,-1,-1,0,127,-1,-1,-1,-1,1)
        val floats=floatArrayOf(1f,0f,0f,.003f,0f,1f,.01f,1f,1f)
        return ExportRequest(null,ExportFormat.MP4,48000,1,192,VideoOptions(),song,"Original service fixture",32,.7f,512,listOf(ExportRegion(file.path,ints,floats)),emptyList(),emptyMap(),listOf(1f),listOf(1f),true,KeyboardViewport(),5f,true)
    }
    private fun waitFor(predicate:()->Boolean) {val deadline=SystemClock.uptimeMillis()+60000;while(!predicate() && SystemClock.uptimeMillis()<deadline)Thread.sleep(20);assertTrue("Service state reached within 60 seconds",predicate())}
    private fun exercise(cancel: Boolean) {
        assumeTrue(VideoExporter.supports(VideoOptions()))
        val context=InstrumentationRegistry.getInstrumentation().targetContext;val wav=File(context.cacheDir,"service-original.wav")
        val gate=CountDownLatch(1);val entered=CountDownLatch(1);var scenario: ActivityScenario<MainActivity>?=null
        try {
            tone(wav);scenario=ActivityScenario.launch(MainActivity::class.java)
            AudioWorkQueue.executor.execute {entered.countDown();gate.await(30,TimeUnit.SECONDS)}
            assertTrue(entered.await(30,TimeUnit.SECONDS))
            ExportCoordinator.submit(context,request(wav));waitFor {ExportCoordinator.status.value.phase=="音源準備"}
            assertTrue(ExportCoordinator.running)
            // Destroy the Activity/ViewModel, not just move it into the background.
            scenario.close();scenario=null
            if(cancel)ExportCoordinator.cancel()
            gate.countDown();waitFor {!ExportCoordinator.running}
            val result=ExportCoordinator.status.value
            if(cancel){assertNull(result.previewPath);assertTrue(result.message.contains("キャンセル"));assertTrue(result.progress<1f)}
            else {
                assertEquals(1f,result.progress);val output=File(checkNotNull(result.previewPath));assertTrue(output.length()>1024)
                val extractor=MediaExtractor();try{extractor.setDataSource(output.path);assertEquals(2,extractor.trackCount);assertTrue((0 until 2).any {extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)=="video/avc"});assertTrue((0 until 2).any {extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)=="audio/mp4a-latm"})}finally{extractor.release()}
            }
        }finally{gate.countDown();scenario?.close();if(ExportCoordinator.running){ExportCoordinator.cancel();waitFor {!ExportCoordinator.running}};ExportCoordinator.clearPreview();wav.delete()}
    }
    @Test fun realVideoJobSurvivesActivityDestruction(){exercise(false)}
    @Test fun cancelledQueuedJobDoesNotReportSuccessOrLeavePreview(){exercise(true)}
}

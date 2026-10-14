package dev.pianopocket

import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.pianopocket.audio.AudioWorkQueue
import dev.pianopocket.audio.NativeAudio
import dev.pianopocket.playback.PerformanceSession
import dev.pianopocket.playback.PlaybackService
import dev.pianopocket.sequencer.*
import dev.pianopocket.ui.PocketViewModel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class BackgroundPlaybackDeviceTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private fun main(action:()->Unit)=instrumentation.runOnMainSync(action)
    private fun waitFor(condition:()->Boolean){
        val deadline=SystemClock.uptimeMillis()+60000
        while(!condition() && SystemClock.uptimeMillis()<deadline)Thread.sleep(20)
        assertTrue("Background session state reached within 60 seconds",condition())
    }
    private fun enable(model:PocketViewModel){
        main {model.backgroundPlayback(true)}
        waitFor {context.getSystemService(android.app.ActivityManager::class.java).getRunningServices(30).any {it.service.className==PlaybackService::class.java.name && it.foreground}}
        // Barrier: allow the service to finish focus acquisition and attach ownership.
        instrumentation.waitForIdleSync()
    }
    @Test fun playbackSurvivesActivityDestructionAndReturnsToTheSameSession(){
        var scenario:ActivityScenario<MainActivity>?=ActivityScenario.launch(MainActivity::class.java)
        var model:PocketViewModel?=null;var entry:MidiEntry?=null
        try {
            main {model=PerformanceSession.current()};val owned=checkNotNull(model)
            waitFor {owned.state.value.ready && !owned.state.value.busy}
            val latch=CountDownLatch(1)
            AudioWorkQueue.executor.execute {
                val notes=(0..59).flatMap {i->listOf(SongMessage(0,i*1000000L,0,0x90,60+i%12,70),SongMessage(0,i*1000000L+500000,0,0x80,60+i%12,0))}
                entry=MidiLibrary(context).save("Background test",MidiSong.recorded(notes,60000000));latch.countDown()
            }
            assertTrue(latch.await(30,TimeUnit.SECONDS));main {owned.selectMidi(checkNotNull(entry))}
            waitFor {owned.state.value.sequence.selected==entry!!.id}
            enable(owned);main {owned.play()};waitFor {NativeAudio.transport()[1]>0}
            scenario!!.close();scenario=null
            val before=NativeAudio.transport()[0];Thread.sleep(300)
            assertTrue(NativeAudio.transport()[0]>before+.15)
            scenario=ActivityScenario.launch(MainActivity::class.java)
            main {assertSame(owned,PerformanceSession.current())}
            assertTrue(NativeAudio.transport()[1]>0)
            main {context.startService(Intent(context,PlaybackService::class.java).setAction("pause"))}
            waitFor {NativeAudio.transport()[1]==0.0}
        }finally{
            main {model?.backgroundPlayback(false)};scenario?.close()
            entry?.let {MidiLibrary(context).delete(it)}
        }
    }
    @Test fun backgroundInputIsRecordedAndStopPersistsTheTakeAfterTheActivityCloses(){
        var scenario:ActivityScenario<MainActivity>?=ActivityScenario.launch(MainActivity::class.java)
        var model:PocketViewModel?=null
        val before=MidiLibrary(context).entries().map {it.id}.toSet()
        try {
            main {model=PerformanceSession.current()};val owned=checkNotNull(model)
            waitFor {owned.state.value.ready && !owned.state.value.busy};enable(owned)
            main {owned.input("screen");owned.startRecording()}
            scenario!!.close();scenario=null
            main {owned.key(60,80)};Thread.sleep(150);main {owned.key(60,0)}
            assertTrue(owned.state.value.sequence.recording)
            main {context.startService(Intent(context,PlaybackService::class.java).setAction("stop"))}
            waitFor {MidiLibrary(context).entries().any {it.id !in before}}
            val recorded=MidiLibrary(context).entries().first {it.id !in before}
            assertEquals(60,MidiLibrary(context).load(recorded).notes.single().key)
        }finally{
            main {model?.backgroundPlayback(false)};scenario?.close()
            MidiLibrary(context).entries().filter {it.id !in before}.forEach {MidiLibrary(context).delete(it)}
        }
    }
}

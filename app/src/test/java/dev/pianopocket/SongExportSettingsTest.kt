package dev.pianopocket
import dev.pianopocket.sequencer.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
class SongExportSettingsTest {
    private fun song():MidiSong {
        val source=MidiSong.recorded(listOf(SongMessage(0,0,0,0xb0,64,127),SongMessage(0,0,0,0xc0,5,0),
            SongMessage(0,100000,0,0x90,60,90),SongMessage(0,400000,0,0x80,60,0),
            SongMessage(0,200000,1,0x91,67,100),SongMessage(0,800000,1,0x81,67,0),SongMessage(0,900000,0,0xb0,64,0)),1000000).copy(tracks=2)
        return source
    }
    @Test fun tempoSpeedMuteAndTrailingRestsRoundTripForBothMidiFormats(){
        val source=song();val result=SongExportSettings.apply(source,.5f,setOf(1))
        assertEquals(2000000L,result.duration);assertEquals(60.0,result.bpm,0.0);assertEquals(listOf(60),result.notes.map {it.key})
        assertEquals(200000L,result.notes.single().start);assertEquals(800000L,result.notes.single().keyEnd);assertEquals(1800000L,result.notes.single().end)
        for(format in 0..1){val out=ByteArrayOutputStream();MidiFileCodec.write(result,out,format);assertEquals(result.duration,MidiFileCodec.read(out.toByteArray().inputStream()).duration)}
        assertEquals(1000000L,source.duration);assertEquals(2,source.notes.size)
    }
    @Test fun cropRebuildsPedalAndHeldNotesAndStopsAtBoundary(){
        val result=SongExportSettings.apply(song(),1f,emptySet(),500000,850000)
        assertEquals(350000L,result.duration);assertEquals(2,result.notes.size)
        val sustained=result.notes.first {it.key==60};assertEquals(0L,sustained.start);assertEquals(0L,sustained.keyEnd);assertEquals(result.duration,sustained.end)
        assertEquals(300000L,result.notes.first {it.key==67}.keyEnd)
        assertEquals(5,result.messages.first {it.status==0xc0}.a)
        assertFalse(result.sustainIndex.active(result.duration))
    }
    @Test fun cropIgnoresMutedChannelsAndMaintainsTempoChanges(){
        val s=song();val tempo=MidiRecord(480,0,0xff,byteArrayOf(0x51,0x0f,0x42,0x40))
        val bytes=ByteArrayOutputStream();MidiFileCodec.write(s.copy(records=s.records+tempo,endTick=960),bytes,1);val changed=MidiFileCodec.read(bytes.toByteArray().inputStream())
        val result=SongExportSettings.apply(changed,2f,setOf(0))
        assertEquals(listOf(67),result.notes.map {it.key});assertTrue(result.messages.none {it.status==0xb0 || it.status==0xc0})
        assertEquals(750000L,result.duration)
    }
    @Test fun cropPreservesLastKeyswitchBeforeStartingNotes(){
        val original=MidiSong.recorded(listOf(SongMessage(0,0,0,0x90,24,90),SongMessage(0,10000,0,0x80,24,0),SongMessage(0,200000,0,0x90,60,90),SongMessage(0,900000,0,0x80,60,0)),1000000)
        val result=SongExportSettings.apply(original,1f,emptySet(),500000,800000,mapOf(-1 to setOf(24)))
        assertEquals(listOf(24,60),result.notes.map {it.key});assertEquals(0L,result.notes.first {it.key==24}.keyEnd)
    }
    @Test fun invalidOrEmptyIntervalsFailBeforeCreatingAnOutput(){
        assertTrue(runCatching {SongExportSettings.apply(song(),1f,emptySet(),700000,600000)}.isFailure)
        assertTrue(runCatching {SongExportSettings.apply(song(),Float.NaN)}.isFailure)
    }
}

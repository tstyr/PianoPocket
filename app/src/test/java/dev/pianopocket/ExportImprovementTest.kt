package dev.pianopocket
import dev.pianopocket.sequencer.*
import dev.pianopocket.video.ExportEstimate
import org.junit.Assert.*
import org.junit.Test
class ExportImprovementTest {
    @Test fun estimatorHasWarmupAndResetsBetweenPhasesOrOnRegression() {
        val e=ExportEstimate();assertNull(e.update(0,"WAV",0f));assertNull(e.update(500,"WAV",.1f));assertEquals(3L,e.update(2000,"WAV",.4f));assertNull(e.update(2100,"AVC",.1f));assertNull(e.update(2500,"AVC",.12f));assertNull(e.update(2600,"AVC",.05f));assertNull(e.update(2700,"AVC",Float.NaN));assertNull(e.update(3000,"AVC",1f))
    }
    @Test fun previewRestoresPedalSustainedNotesAndClosesAtFiveSeconds() {
        val events=listOf(SongMessage(0,0,0,0xb0,64,127),SongMessage(0,0,0,0x90,60,100),SongMessage(0,1000000,0,0x80,60,0),SongMessage(0,9000000,0,0xb0,64,0))
        val song=MidiSong.recorded(events,10000000);val clip=PreviewSong.slice(song,2000000)
        assertEquals(5000000L,clip.duration);assertTrue(clip.messages.any {it.micros==0L && it.status==0x90 && it.a==60});assertTrue(clip.messages.any {it.micros==0L && it.status==0x80 && it.a==60});assertTrue(clip.sustainIndex.active(0));assertFalse(clip.sustainIndex.active(clip.duration));assertEquals(5000000L,clip.notes.single().end)
    }
    @Test fun previewAtEndRestartsAndKeepsOriginalTrackNumbers() {
        val song=MidiSong(480,3,emptyList(),listOf(SongMessage(0,0,2,0x92,72,100),SongMessage(0,1000000,2,0x82,72,0)),2000000,120.0,emptyList())
        val clip=PreviewSong.slice(song,song.duration);assertEquals(2,clip.notes.single().track);assertEquals(3,clip.tracks);assertEquals(2000000L,clip.duration);assertTrue(runCatching {PreviewSong.slice(song,0,60)}.isFailure)
    }
}

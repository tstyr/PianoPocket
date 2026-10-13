package dev.pianopocket
import dev.pianopocket.sequencer.*
import org.junit.Assert.*
import org.junit.Test
class PracticeTest {
    @Test fun mutesBothNoteEdgesWhileKeepingSharedPedalAndOtherTracks() {
        val events=listOf(SongMessage(0,0,0,0x90,60,100),SongMessage(0,10,1,0x90,60,80),SongMessage(0,20,1,0xb0,64,127),SongMessage(0,30,1,0x80,60,0),SongMessage(0,40,0,0x80,60,0))
        val song=MidiSong(480,2,emptyList(),events,100,120.0,emptyList());val filtered=Practice.messages(song,setOf(1))
        assertEquals(listOf(events[0],events[2],events[4]),filtered);assertEquals(1,MidiSong(480,2,emptyList(),filtered,100,120.0,emptyList()).notes.size)
    }
    @Test fun readsBoundedTrackNameWithFallback() {
        val title="右手ピアノ";val song=MidiSong(480,2,listOf(MidiRecord(0,0,0xff,byteArrayOf(3)+title.toByteArray())),emptyList(),100,120.0,emptyList())
        assertEquals(title,Practice.trackName(song,0));assertEquals("Track 2",Practice.trackName(song,1));assertEquals(listOf(.25f,.5f,.75f,1f,1.25f,1.5f,2f),Practice.speeds)
    }
}

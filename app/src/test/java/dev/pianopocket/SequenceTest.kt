package dev.pianopocket

import dev.pianopocket.sequencer.*
import dev.pianopocket.ui.KeyLayout
import dev.pianopocket.ui.solfege
import dev.pianopocket.ui.noteLabelLines
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

class SequenceTest {
    private fun file(format: Int=1,division: Int=480,vararg tracks: ByteArray): ByteArray {
        val b=ByteArrayOutputStream();val d=DataOutputStream(b);d.writeBytes("MThd");d.writeInt(6);d.writeShort(format);d.writeShort(tracks.size);d.writeShort(division)
        tracks.forEach { d.writeBytes("MTrk");d.writeInt(it.size);d.write(it) };return b.toByteArray()
    }
    private fun bytes(vararg values: Int)=values.map { it.toByte() }.toByteArray()
    private fun tempoSong()=MidiFileCodec.read(file(1,480,
        bytes(0,255,81,3,7,161,32,131,96,255,81,3,3,208,144,131,96,255,47,0),
        bytes(0,144,60,100,131,96,128,60,64,0,144,62,80,131,96,128,62,0,0,255,47,0)
    ).inputStream())
    @Test fun mergesTempoAcrossTracks() {
        val song=tempoSong();assertEquals(750000L,song.duration);assertEquals(2,song.notes.size)
        assertEquals(500000L,song.notes[0].end);assertEquals(500000L,song.notes[1].start);assertEquals(750000L,song.notes[1].end)
        assertEquals(120.0,song.bpm,0.001)
    }
    @Test fun exportsBothSmfTypesWithoutLosingTempoOrRests() {
        val song=tempoSong()
        for(type in 0..1){val out=ByteArrayOutputStream();MidiFileCodec.write(song,out,type);val round=MidiFileCodec.read(out.toByteArray().inputStream());assertEquals(song.duration,round.duration);assertEquals(song.notes,round.notes.map { if(type==0)it.copy(track=1)else it });assertEquals(if(type==0)1 else 2,round.tracks)}
    }
    @Test fun retainsTrailingEndOfTrackTime() {
        val song=MidiFileCodec.read(file(0,480,bytes(0,144,60,99,10,128,60,0,131,96,255,47,0)).inputStream())
        val out=ByteArrayOutputStream();MidiFileCodec.write(song,out,0);assertEquals(song.duration,MidiFileCodec.read(out.toByteArray().inputStream()).duration)
    }
    @Test fun handlesRunningStatusAndZeroVelocityOff() {
        val song=MidiFileCodec.read(file(0,480,bytes(0,144,60,127,10,64,90,10,60,0,10,64,0,0,255,47,0)).inputStream());assertEquals(2,song.notes.size);assertEquals(20833L,song.notes[0].end)
    }
    @Test fun pedalExtendsNotesAcrossTracksAndChannelsStaySeparate() {
        val m=listOf(SongMessage(0,0,0,0x90,60,90),SongMessage(0,10,1,0xb0,64,127),SongMessage(0,20,0,0x80,60,0),SongMessage(0,30,0,0x91,65,90),SongMessage(0,40,0,0x81,65,0),SongMessage(0,100,1,0xb0,64,0))
        val song=MidiSong.recorded(m.map { it.copy(track=0) },100);assertEquals(20L,song.notes[0].keyEnd);assertEquals(100L,song.notes[0].end);assertEquals(40L,song.notes[1].end)
    }
    @Test fun sameNoteUsesFifoPairing() {
        val song=MidiSong.recorded(listOf(SongMessage(0,0,0,0x90,60,30),SongMessage(0,10,0,0x90,60,90),SongMessage(0,20,0,0x80,60,0),SongMessage(0,30,0,0x80,60,0)),40)
        assertEquals(listOf(20L,30L),song.notes.map { it.end });assertEquals(listOf(30,90),song.notes.map { it.velocity })
    }
    @Test fun rejectsUnsupportedAndMalformedFiles() {
        val cases=listOf(file(2,480,bytes(0,255,47,0)),file(0,0xe728,bytes(0,255,47,0)),file(0,480,bytes(128,128,128,128,0)),file(0,480,bytes(0,60,90)),file(0,480,bytes(0,255,81,3,0,0,0)),file(0,480,bytes(0,240,10,1)),file(0,480,bytes(0,144,255,40)))
        cases.forEach { data->assertTrue(runCatching { MidiFileCodec.read(data.inputStream()) }.isFailure) }
    }
    @Test fun preservesSysExAndWarnsAboutUnsupportedSynthesis() {
        val song=MidiFileCodec.read(file(0,480,bytes(0,240,3,1,2,247,0,192,4,0,255,47,0)).inputStream());assertEquals(2,song.warnings.size)
        val out=ByteArrayOutputStream();MidiFileCodec.write(song,out,0);val read=MidiFileCodec.read(out.toByteArray().inputStream());assertArrayEquals(song.records[0].data,read.records[0].data)
    }
    @Test fun recorderUsesTimestampsAndClosesHeldKeysAndPedal() {
        var clock=1000000000L;val r=MidiRecorder { clock };r.start();clock+=10000000;r.capture(0x92,60,127,clock-1000000);clock+=10000000;r.capture(0xb2,64,127);clock+=10000000
        val song=r.stop().toSong();assertFalse(r.isRecording());assertEquals(9000L,song.notes.single().start);assertEquals(30000L,song.notes.single().end);assertTrue(song.messages.any { it.status==0xb2 && it.a==64 && it.b==0 })
        val out=ByteArrayOutputStream();MidiFileCodec.write(song,out,1);assertEquals(1,MidiFileCodec.read(out.toByteArray().inputStream()).notes.size)
    }
    @Test fun liveViewShowsHeldAndPedalReleaseHistory() {
        var now=0L;val live=LiveNotes { now };live.event(0x90,60,90);now=100000000;live.event(0xb0,64,127);live.event(0x80,60,0)
        assertEquals(setOf(60),live.snapshot().sustained);assertTrue(live.snapshot().held.isEmpty());now=200000000;live.event(0xb0,64,0)
        assertEquals(100000L,live.snapshot().notes.single().keyEnd);assertEquals(200000L,live.snapshot().notes.single().end)
    }
    @Test fun intervalQueryIncludesLongHeldNotes() {
        val notes=listOf(RollNote(60,90,0,0,0,10000000,10000000),RollNote(64,90,0,0,1000,2000,2000),RollNote(67,90,0,0,8000000,9000000,9000000))
        val found=mutableListOf<Int>();NoteIndex(notes).visible(8500000,8600000){found+=it.key};assertEquals(listOf(60,67),found)
    }
    @Test fun originalStudyFixtureHasTwoTemposAndNineteenNotes() {
        val candidates=listOf(java.io.File("../tests/fixtures/PocketStudy.mid"),java.io.File("tests/fixtures/PocketStudy.mid"))
        val song=candidates.first { it.isFile }.inputStream().use(MidiFileCodec::read)
        assertEquals(19,song.notes.size);assertEquals(5200000L,song.duration);assertTrue(song.notes.any { it.end>it.keyEnd })
    }
    @Test fun recorderStopsAtDurationLimit() {
        var clock=0L;val r=MidiRecorder { clock };r.start();r.capture(0x90,60,90);clock=MidiFileCodec.MAX_MICROS*1000;r.capture(0x90,62,90)
        assertFalse(r.isRecording());assertTrue(r.limitReached());assertEquals(MidiFileCodec.MAX_MICROS,r.stop().duration)
    }
    @Test fun keyboardGeometryAndJapaneseNames() {
        val all=KeyLayout(21,108);assertEquals(52,all.whites.size);assertEquals(88,all.shapes.size)
        assertEquals(21,all.hit(.001f,.9f));assertEquals(108,all.hit(.999f,.9f));assertNull(all.hit(-.1f,.5f))
        val two=KeyLayout(60,83);assertEquals(60,two.hit(.01f,.9f));assertEquals(61,two.hit(1/14f,.1f));assertEquals("ファ♯",solfege(66))
    }
    @Test fun rollStopsAtKeyOffWhileAudioDurationKeepsPedalTail() {
        val song=MidiSong.recorded(listOf(SongMessage(0,0,0,0x90,60,90),SongMessage(0,5,0,0xb0,64,127),SongMessage(0,20,0,0x80,60,0),SongMessage(0,100,0,0xb0,64,0)),110)
        val held=mutableListOf<Int>();val sounding=mutableListOf<Int>()
        song.keyIndex.visible(21,99){held+=it.key};song.noteIndex.visible(21,99){sounding+=it.key}
        assertTrue(held.isEmpty());assertEquals(listOf(60),sounding);assertEquals(20L,song.notes.single().keyEnd);assertEquals(100L,song.notes.single().end)
    }
    @Test fun sustainDisplayTracksEmptyPedalAndOverlappingChannelsAndSeek() {
        val song=MidiSong.recorded(listOf(SongMessage(0,10,0,0xb0,64,127),SongMessage(0,20,0,0xb1,64,127),SongMessage(0,30,0,0xb0,64,0),SongMessage(0,40,0,0xb1,121,0)),100)
        assertFalse(song.sustainIndex.active(9));assertTrue(song.sustainIndex.active(10));assertTrue(song.sustainIndex.active(35));assertFalse(song.sustainIndex.active(40));assertTrue(song.sustainIndex.active(25));assertFalse(song.sustainIndex.active(0))
        var clock=0L;val live=LiveNotes { clock };live.event(0xb2,64,127);assertTrue(live.snapshot().pedalDown);assertTrue(live.snapshot().held.isEmpty());clock=1000000;live.event(0xb2,64,0);assertFalse(live.snapshot().pedalDown)
    }
    @Test fun multiCharacterKatakanaStacksInNarrowKeys() {
        assertEquals(listOf("フ","ァ"),noteLabelLines(65));assertEquals(listOf("フ","ァ","♯"),noteLabelLines(66));assertEquals(listOf("ド"),noteLabelLines(60))
    }

}

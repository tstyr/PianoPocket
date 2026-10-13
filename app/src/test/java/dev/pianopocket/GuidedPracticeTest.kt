package dev.pianopocket

import dev.pianopocket.sequencer.*
import org.junit.Assert.*
import org.junit.Test

class GuidedPracticeTest {
    private fun song():MidiSong {
        val messages=listOf(SongMessage(0,0,0,0x90,48,90),SongMessage(0,0,0,0xb0,64,127),SongMessage(0,0,1,0x91,72,90),SongMessage(0,5000,1,0x91,76,90),SongMessage(0,100000,0,0x80,48,0),SongMessage(0,100000,1,0x81,72,0),SongMessage(0,100000,1,0x81,76,0),SongMessage(0,200000,0,0xb0,64,0))
        return MidiSong(480,2,emptyList(),messages,500000,120.0,emptyList(),480)
    }
    @Test fun selectedHandIsManualAndOtherHandIsAccompaniment() {
        val s=song();val options=GuidedPractice(true,true,PracticeHand.LEFT)
        val messages=Practice.messages(s,emptySet(),options)
        assertFalse(messages.any {it.a==48 && it.status and 0xf0 in listOf(0x80,0x90)})
        assertEquals(4,messages.count {it.track==1});assertEquals(2,messages.count {it.status and 0xf0==0xb0})
        assertEquals(setOf(48),Practice.gates(s,emptySet(),options).single().keys)
    }
    @Test fun humanizedChordGroupsAndTrackChoice() {
        val s=song();val gates=Practice.gates(s,emptySet(),GuidedPractice(true,true,PracticeHand.RIGHT))
        assertEquals(setOf(72,76),gates.single().keys)
        assertEquals(setOf(48),Practice.gates(s,emptySet(),GuidedPractice(true,true,tracks=setOf(0))).single().keys)
        assertTrue(Practice.gates(s,setOf(0,1),GuidedPractice(true)).isEmpty())
        assertTrue(Practice.gates(s,emptySet(),GuidedPractice(true,false)).isEmpty())
    }
    @Test fun metronomeFollowsTempoAndMeterChanges() {
        val records=listOf(MidiRecord(0,0,0xff,byteArrayOf(0x51,7,0xa1.toByte(),0x20)),MidiRecord(480,0,0xff,byteArrayOf(0x51,15,0x42,0x40)),MidiRecord(480,0,0xff,byteArrayOf(0x58,3,2)))
        val s=MidiSong(480,1,records,emptyList(),3000000,120.0,emptyList(),1440)
        assertEquals(listOf(0L,500000L,1500000L,2500000L),Practice.beats(s).map {it.micros})
        assertEquals(listOf(true,true,false,false),Practice.beats(s).map {it.accent})
    }
    @Test fun eighthNoteMeterHasRealBarAccents() {
        val s=MidiSong(480,1,listOf(MidiRecord(0,0,0xff,byteArrayOf(0x58,7,3))),emptyList(),4000000,120.0,emptyList(),1920)
        val beats=Practice.beats(s);assertEquals(250000L,beats[1].micros);assertTrue(beats[0].accent);assertTrue(beats[7].accent);assertFalse(beats[6].accent)
    }
}

package dev.pianopocket

import dev.pianopocket.sequencer.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream

class SongTransposeTest {
    private fun song() = MidiSong.recorded(listOf(
        SongMessage(0,0,0,0x90,60,100), SongMessage(0,10000,0,0xa0,60,20),
        SongMessage(0,20000,0,0xb0,64,127), SongMessage(0,30000,0,0x90,60,0),
        SongMessage(0,40000,0,0xb0,64,0), SongMessage(0,50000,0,0x99,36,80),
        SongMessage(0,60000,0,0x89,36,0), SongMessage(0,70000,0,0xe0,0,64)),100000)
    @Test fun notesPressureAndSustainStayTogetherButDrumsDoNotMove() {
        val source=song();val changed=SongTranspose.apply(source,3)
        assertEquals(listOf(63,63,64,63,64,36,36,0),changed.messages.map {it.a})
        assertEquals(source.messages.map {it.micros},changed.messages.map {it.micros})
        assertEquals(source.messages.map {it.b},changed.messages.map {it.b})
        val note=changed.notes.first();assertEquals(63,note.key);assertEquals(30000L,note.keyEnd);assertEquals(40000L,note.end)
        assertEquals(60,source.notes.first().key);assertEquals(source.duration,changed.duration)
    }
    @Test fun bothMidiFormatsExportTheTransposedNotesAndPreserveTheOriginal() {
        val source=song();val changed=SongTranspose.apply(source,-12)
        for(format in 0..1) {
            val out=ByteArrayOutputStream();MidiFileCodec.write(changed,out,format)
            val loaded=MidiFileCodec.read(out.toByteArray().inputStream())
            assertEquals(listOf(48,36),loaded.notes.map {it.key})
            assertEquals(changed.messages.map {it.status to it.a},loaded.messages.map {it.status to it.a})
            assertEquals(source.endTick,loaded.endTick)
        }
        assertEquals(60,source.records.first {it.status==0x90}.data[0].toInt())
    }
    @Test fun outOfRangePairsAreRemovedWithoutClampingOtherNotes() {
        val source=MidiSong.recorded(listOf(SongMessage(0,0,0,0x90,120,90),SongMessage(0,1,0,0x90,110,90),SongMessage(0,2,0,0x80,120,0),SongMessage(0,3,0,0x80,110,0)),100)
        val changed=SongTranspose.apply(source,12)
        assertEquals(listOf(122,122),changed.messages.map {it.a})
        assertEquals(1,changed.notes.size);assertTrue(changed.warnings.any {"1音" in it})
        assertEquals(2,changed.records.count {it.status in 0x80..0xef})
        assertEquals(source.duration,changed.duration)
    }
    @Test fun zeroAndRepeatedChangesUseTheUnchangedSource() {
        val source=song();assertSame(source,SongTranspose.apply(source,0))
        assertEquals(72,SongTranspose.apply(source,12).notes.first().key)
        assertEquals(62,SongTranspose.apply(source,2).notes.first().key)
        assertEquals(36,SongTranspose.apply(source,-24).notes.first().key)
    }
    @Test fun keySignaturesMoveWithoutChangingMajorMinorOrTempo() {
        val source=song().copy(records=song().records+MidiRecord(0,0,0xff,byteArrayOf(0x59,0,1)))
        val changed=SongTranspose.apply(source,2)
        assertArrayEquals(byteArrayOf(0x59,2,1),changed.records.last().data)
        assertArrayEquals(source.records.first().data,changed.records.first().data)
        assertEquals(source.bpm,changed.bpm,0.0)
    }
    @Test fun practiceAndRollIndicesUseTheTransposedKeys() {
        val changed=SongTranspose.apply(song(),-2)
        val gates=Practice.gates(changed,emptySet(),GuidedPractice(enabled=true))
        assertEquals(setOf(58),gates.first().keys)
        val keys=mutableListOf<Int>();changed.keyIndex.visible(0,40000){keys+=it.key}
        assertEquals(listOf(58),keys)
    }
    @Test fun octaveChangesPreserveTheOriginalEnharmonicKeySignature() {
        for(fifths in -7..7)for(minor in 0..1)for(shift in listOf(-24,-12,12,24)) {
            val signature=byteArrayOf(0x59,fifths.toByte(),minor.toByte())
            val source=song().copy(records=song().records+MidiRecord(0,0,0xff,signature))
            assertArrayEquals(signature,SongTranspose.apply(source,shift).records.last().data)
        }
    }
    @Test(expected=IllegalArgumentException::class) fun limitsAreChecked() {SongTranspose.apply(song(),25)}
}

package dev.pianopocket

import dev.pianopocket.editor.*
import dev.pianopocket.sequencer.*
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test

class MidiEditorTest {
    private fun fixture(): MidiSong {
        fun r(t: Long,track: Int,status: Int,vararg b: Int)=MidiRecord(t,track,status,b.map {it.toByte()}.toByteArray())
        val records=listOf(r(0,0,0xff,0x51,7,0xa1,0x20),r(0,0,0xff,3,80),r(0,0,0xc0,5),r(0,0,0xf0,1,2,3),
            r(0,0,0x90,60,80),r(120,1,0x90,60,100),r(200,0,0xb0,64,127),r(240,1,0x80,60,42),r(480,0,0x80,60,20),r(960,0,0xb0,64,0))
        val song=MidiSong(480,2,records,emptyList(),0,120.0,emptyList(),1200)
        val b=ByteArrayOutputStream();MidiFileCodec.write(song,b,1);return MidiFileCodec.read(b.toByteArray().inputStream())
    }
    @Test fun pairingUsesTrackAndEndsAtKeyReleaseNotPedal(){
        val doc=MidiEditDocument(fixture());assertEquals(2,doc.notes.size)
        assertEquals(480L,doc.notes.first {it.track==0}.end);assertEquals(240L,doc.notes.first {it.track==1}.end)
        assertEquals(1000000L,doc.source.notes.first {it.track==1}.end)
        assertEquals(500000L,doc.source.notes.first {it.track==0}.keyEnd)
        assertEquals(250000L,doc.source.notes.first {it.track==1}.keyEnd)
    }
    @Test fun editsRoundTripAndKeepOtherEventsAndTrailingRest(){
        val doc=MidiEditDocument(fixture());val n=doc.notes.first {it.track==1}
        doc.update(n.copy(key=62,velocity=55,start=600,end=720))
        val song=doc.song();val edited=song.notes.single {it.track==1}
        assertEquals(62,edited.key);assertEquals(55,edited.velocity);assertEquals(1250000L,song.duration)
        assertEquals(600L,song.records.first {it.track==1 && it.status==0x90}.tick)
        assertEquals(42,song.records.first {it.track==1 && it.status==0x80}.data[1].toInt())
        doc.source.records.filter {it.status !in listOf(0x80,0x90)}.forEach {r->assertTrue(song.records.any {it.tick==r.tick && it.track==r.track && it.status==r.status && it.data.contentEquals(r.data)})}
        assertEquals(1200L,song.endTick)
    }
    @Test fun deleteUndoRedoAndUndoBackToOriginal(){
        val doc=MidiEditDocument(fixture());val n=doc.notes[0];doc.delete(n.id)
        assertEquals(1,doc.song().notes.size);doc.undo();assertFalse(doc.dirty);assertEquals(2,doc.song().notes.size)
        doc.redo();assertTrue(doc.dirty);assertNull(doc.note(n.id));doc.undo();doc.move(n.id,120,1)
        assertFalse(doc.canRedo);doc.undo();assertFalse(doc.dirty)
    }
    @Test fun snapAndNudgesRespectLimitsAndUndoIsBounded(){
        val doc=MidiEditDocument(fixture());val n=doc.notes.first {it.track==1}
        doc.quantize(n.id,1);assertEquals(0L,doc.note(n.id)!!.start);assertTrue(doc.note(n.id)!!.end>0)
        doc.move(n.id,-10000,-200);assertEquals(0,doc.note(n.id)!!.key);assertEquals(0L,doc.note(n.id)!!.start)
        doc.length(n.id,-10000);assertEquals(1L,doc.note(n.id)!!.end)
        repeat(80){doc.move(n.id,1,0)};repeat(64){doc.undo()};assertFalse(doc.canUndo)
    }
    @Test fun danglingNotesGetAnOffOnlyWhenChangedAndIndexIncludesMovedNotes(){
        val base=fixture();val source=base.copy(records=base.records.filter {it.status!=0x80})
        val doc=MidiEditDocument(source);val n=doc.notes[0];assertNull(n.offId)
        doc.move(n.id,2000,0);assertTrue(doc.visible(2000,4000).any {it.id==n.id})
        assertFalse(doc.visible(0,100).any {it.id==n.id})
        val song=doc.song();assertTrue(song.records.any {it.status==0x80 && it.tick>1200})
    }
}

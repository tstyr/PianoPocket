package dev.pianopocket

import dev.pianopocket.midi.MidiOutputWriter
import dev.pianopocket.midi.MidiByteDecoder
import org.junit.Assert.*
import org.junit.Test

class MidiOutputWriterTest {
    private data class Packet(val bytes: ByteArray,val time: Long)
    @Test fun channelMessagesHaveCorrectLengthsVelocityAndTimestamp() {
        val packets=mutableListOf<Packet>();val writer=MidiOutputWriter({b,n,t->packets+=Packet(b.copyOf(n),t)},{})
        for(status in listOf(0x80,0x90,0xa0,0xb0,0xc0,0xd0,0xe0))writer.message(status or 4,60,90,123456L)
        assertEquals(listOf(3,3,3,3,2,2,3),packets.map {it.bytes.size})
        assertTrue(packets.all {it.time==123456L})
        val decoded=mutableListOf<List<Int>>();val decoder=MidiByteDecoder {s,a,b->decoded+=listOf(s,a,b)}
        packets.forEach {decoder.feed(it.bytes,0,it.bytes.size)}
        assertEquals(listOf(0x84,0x94,0xa4,0xb4,0xc4,0xd4,0xe4),decoded.map {it[0]})
        assertEquals(listOf(90,90,90,90,0,0,90),decoded.map {it[2]})
    }
    @Test fun immediatePanicFlushesAndReleasesPedalAndNotesOnAllChannels() {
        val packets=mutableListOf<Packet>();var flushed=0;val writer=MidiOutputWriter({b,n,t->packets+=Packet(b.copyOf(n),t)},{flushed++})
        writer.silence();assertEquals(1,flushed);assertEquals(48,packets.size)
        for(ch in 0..15)assertEquals(listOf(64,120,123),packets.filter {it.bytes[0].toInt() and 15==ch}.map {it.bytes[1].toInt()})
        assertTrue(packets.all {it.time==0L && it.bytes[2]==0.toByte()})
    }
    @Test fun loopResetIsTimestampedAndOnlyTouchesSongChannels() {
        val packets=mutableListOf<Packet>();var flushed=0;val writer=MidiOutputWriter({b,n,t->packets+=Packet(b.copyOf(n),t)},{flushed++})
        writer.silence((1 shl 2) or (1 shl 7),true,300000)
        assertEquals(0,flushed);assertEquals(10,packets.size)
        assertEquals(setOf(2,7),packets.map {it.bytes[0].toInt() and 15}.toSet())
        assertTrue(packets.all {it.time==300000L})
        assertArrayEquals(byteArrayOf(0xe2.toByte(),0,64),packets[4].bytes)
    }
    @Test fun zeroVelocityNoteAndPitchBendKeepTheirData() {
        val packets=mutableListOf<Packet>();val writer=MidiOutputWriter({b,n,t->packets+=Packet(b.copyOf(n),t)},{})
        writer.message(0x90,60,0,-1);writer.message(0xef,127,127,0)
        assertArrayEquals(byteArrayOf(0x90.toByte(),60,0),packets[0].bytes)
        assertEquals(0L,packets[0].time);assertArrayEquals(byteArrayOf(0xef.toByte(),127,127),packets[1].bytes)
    }
    @Test(expected=IllegalArgumentException::class) fun sysexMustNotBeMisrepresentedAsChannelData(){MidiOutputWriter({_,_,_->},{ }).message(0xf0,1,2,0)}
    @Test(expected=IllegalArgumentException::class) fun invalidDataIsRejected(){MidiOutputWriter({_,_,_->},{ }).message(0x90,128,100,0)}
}

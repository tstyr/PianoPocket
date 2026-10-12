package dev.pianopocket

import dev.pianopocket.video.AvcPackets
import dev.pianopocket.video.MovieMuxer
import dev.pianopocket.video.VideoOptions
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer

class MovieMuxerTest {
    private val sps=byteArrayOf(0x67,0x42,0,0x1e,1,2,3)
    private val pps=byteArrayOf(0x68,1,2)
    private fun start(n: ByteArray)=byteArrayOf(0,0,0,1)+n
    @Test fun annexBConfigurationAndPacketsAreLengthPrefixed() {
        val config=AvcPackets.configuration(listOf(start(sps),start(pps)))
        assertArrayEquals(byteArrayOf(1,0x42,0,0x1e,0xff.toByte(),0xe1.toByte()),config.copyOf(6))
        assertArrayEquals(byteArrayOf(0,0,0,7)+sps+byteArrayOf(0,0,0,3)+pps,AvcPackets.sample(start(sps)+byteArrayOf(0,0,1)+pps))
        val already=byteArrayOf(0,0,1,0)+ByteArray(256){0x65};assertArrayEquals(already,AvcPackets.sample(already))
        assertTrue(runCatching { AvcPackets.sample(byteArrayOf(1,2,3)) }.isFailure)
        assertTrue(runCatching { AvcPackets.configuration(listOf(start(sps))) }.isFailure)
    }
    @Test fun realMovieHierarchyHasCorrectBrandTrackTablesAndOffsets() {
        for(qt in listOf(false,true)) {
            val file=File.createTempFile("pocket-mux-",if(qt)".mov"else ".mp4")
            try {
                MovieMuxer(file,qt).use { mux->
                    val audio=mux.addTrack(MovieMuxer.Format(false,byteArrayOf(0x11,0x90.toByte()),48000,1024))
                    val video=mux.addTrack(MovieMuxer.Format(true,AvcPackets.configuration(listOf(start(sps),start(pps))),90000,3000,320,180))
                    mux.write(audio,byteArrayOf(11,12,13),0);mux.write(audio,byteArrayOf(14,15),21333)
                    mux.write(video,byteArrayOf(0,0,0,2,0x65,0x10),0,true);mux.write(video,byteArrayOf(0,0,0,2,0x41,0x20),33333);mux.finish()
                }
                val bytes=file.readBytes();val b=ByteBuffer.wrap(bytes)
                assertEquals("ftyp",String(bytes,4,4));assertEquals(if(qt)"qt  "else "isom",String(bytes,8,4))
                val media=b.getInt(0);assertEquals(1,b.getInt(media));assertEquals("mdat",String(bytes,media+4,4))
                val end=media+b.getLong(media+8).toInt();assertEquals("moov",String(bytes,end+4,4))
                val text=String(bytes,Charsets.ISO_8859_1);for(type in listOf("mvhd","tkhd","mdhd","hdlr","vmhd","smhd","dref","stsd","stts","stsc","stsz","co64","stss","avcC","esds"))assertTrue(type,text.contains(type))
                // Decode all two co64 tables, then ensure their offsets point into actual mdat payload.
                var at=0;var tables=0
                while(true){val index=text.indexOf("co64",at);if(index<0)break;val n=b.getInt(index+8);assertEquals(2,n);repeat(n){val offset=b.getLong(index+12+it*8);assertTrue(offset>=media+16 && offset<end)};tables++;at=index+4};assertEquals(2,tables)
            }finally{file.delete()}
        }
    }
    @Test fun rejectsMissingTracksNonMonotonicTimesAndOversizedSettings() {
        val file=File.createTempFile("pocket-invalid-",".mov")
        try {MovieMuxer(file,true).use { mux->
            val track=mux.addTrack(MovieMuxer.Format(false,byteArrayOf(0x11,0x90.toByte()),48000,1024))
            assertTrue(runCatching { mux.write(track,byteArrayOf(1),100000) }.isFailure)
            mux.write(track,byteArrayOf(1),0)
            assertTrue(runCatching { mux.write(track,byteArrayOf(1),0) }.isFailure)
            assertTrue(runCatching { mux.write(track,byteArrayOf(1),7232000000) }.isFailure)
            assertTrue(runCatching { mux.finish() }.isFailure)
        }}finally{file.delete()}
        for(o in listOf(30 to 720,60 to 1080)){assertEquals(o.second*16/9,VideoOptions(o.first,o.second).width)}
        assertTrue(runCatching { VideoOptions(120,2160) }.isFailure)
    }
}

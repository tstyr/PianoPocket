package dev.pianopocket.tools

import dev.pianopocket.video.AvcPackets
import dev.pianopocket.video.MovieMuxer
import java.io.DataInputStream
import java.io.File

/** Host-only packet fixture: original FFmpeg-generated imagery/tone, never proprietary samples. */
fun main(args: Array<String>) {
    require(args.size==2)
    DataInputStream(File(args[0]).inputStream().buffered()).use { input->
        require(input.readInt()==0x56504b54)
        val fps=input.readInt();val width=input.readInt();val height=input.readInt();val rate=input.readInt()
        fun packets(): List<ByteArray> {val count=input.readInt();require(count in 1..500000);return List(count){val n=input.readInt();require(n in 1..(32*1024*1024));ByteArray(n).also { input.readFully(it) }}}
        val frames=packets();val audio=packets();val config=ByteArray(2).also { input.readFully(it) };require(input.read()==-1)
        for(qt in listOf(false,true))MovieMuxer(File(args[1]+if(qt)".mov"else ".mp4"),qt).use { mux->
            val a=mux.addTrack(MovieMuxer.Format(false,config,rate,1024));audio.forEachIndexed { i,b->mux.write(a,b,i*1024L*1000000/rate) }
            val v=mux.addTrack(MovieMuxer.Format(true,AvcPackets.configuration(listOf(frames[0])),90000,90000/fps,width,height))
            frames.forEachIndexed { i,b->mux.write(v,AvcPackets.sample(b),i*1000000L/fps,AvcPackets.nals(b).any { it[0].toInt() and 31==5 }) };mux.finish()
        }
        println("Muxed ${frames.size} AVC frames / ${audio.size} AAC packets at ${fps}fps ${width}x${height} ${rate}Hz into MP4 and QuickTime MOV")
    }
}

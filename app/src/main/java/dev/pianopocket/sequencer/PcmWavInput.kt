package dev.pianopocket.sequencer

import java.io.File
import java.io.RandomAccessFile

/** Bounded RIFF reader for the PCM16 stereo intermediary of Android's AAC encoder. */
class PcmWavInput(file: File): AutoCloseable {
    private val input=RandomAccessFile(file,"r")
    val rate: Int
    val frames: Long
    private var remaining: Long
    init {
        try {
            fun tag(): String {val b=ByteArray(4);input.readFully(b);return String(b,Charsets.US_ASCII)}
            fun u16()=input.readUnsignedByte() or (input.readUnsignedByte() shl 8)
            fun u32(): Long {var n=0L;repeat(4){n=n or (input.readUnsignedByte().toLong() shl (it*8))};return n}
            require(tag()=="RIFF"){"AAC input is not RIFF WAV"};val limit=u32()+8
            require(limit<=input.length() && tag()=="WAVE"){"Truncated WAV"}
            var sampleRate=0;var formatOk=false;var offset=0L;var bytes=0L
            while(input.filePointer+8<=limit){val name=tag();val size=u32();val start=input.filePointer;require(size<=limit-start){"Truncated WAV chunk"}
                if(name=="fmt "){require(size>=16);val format=u16();val channels=u16();sampleRate=u32().toInt();u32();val align=u16();val bits=u16();formatOk=format==1 && channels==2 && align==4 && bits==16 && sampleRate in listOf(44100,48000)}
                if(name=="data" && offset==0L){offset=start;bytes=size}
                input.seek(start+size+(size and 1))
            }
            require(formatOk && offset>0 && bytes>0 && bytes%4==0L){"AAC requires non-empty stereo PCM16 WAV"}
            rate=sampleRate;frames=bytes/4;remaining=bytes;input.seek(offset)
        } catch(e: Throwable){input.close();throw e}
    }
    fun read(buffer: ByteArray,maxBytes: Int=buffer.size): Int {
        require(maxBytes in 4..buffer.size)
        val count=minOf(remaining,(maxBytes-maxBytes%4).toLong()).toInt()
        if(count==0)return -1
        input.readFully(buffer,0,count);remaining-=count;return count
    }
    override fun close()=input.close()
}

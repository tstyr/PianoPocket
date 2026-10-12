package dev.pianopocket.video

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.RandomAccessFile

/** A bounded AVC/AAC ISO BMFF writer. QuickTime gets a qt brand and a real movie atom hierarchy. */
class MovieMuxer(output: File,private val quickTime: Boolean): AutoCloseable {
    data class Format(val video: Boolean,val config: ByteArray,val timescale: Int,val lastDuration: Int,val width: Int=0,val height: Int=0)
    private class Track(val format: Format) {
        var count=0;var sizes=IntArray(4096);var offsets=LongArray(4096);var times=LongArray(4096);var keys=BooleanArray(4096)
        fun append(size: Int,offset: Long,time: Long,key: Boolean) {
            require(count<500000){"動画は1トラック50万パケットまでです"}
            require(time>=0 && (count==0 && time==0L || count>0 && time>times[count-1])){"Encoder timestamps must start at zero and increase (no B frames)"}
            if(count==sizes.size){val n=minOf(500000,count*2);sizes=sizes.copyOf(n);offsets=offsets.copyOf(n);times=times.copyOf(n);keys=keys.copyOf(n)}
            sizes[count]=size;offsets[count]=offset;times[count]=time;keys[count]=key;count++
        }
        fun duration(i: Int): Int = if(i+1==count)format.lastDuration else (times[i+1]-times[i]).also { require(it in 1..Int.MAX_VALUE.toLong()) }.toInt()
        val length: Long get()=times[count-1]+format.lastDuration
    }
    private val file=RandomAccessFile(output,"rw")
    private val tracks=mutableListOf<Track>();private var finished=false;private var closed=false
    private val mediaStart: Long
    init {
        try {
            file.setLength(0)
            file.write(box("ftyp") { tag(if(quickTime)"qt  "else "isom");writeInt(0x200);tag(if(quickTime)"qt  "else "isom");if(!quickTime){tag("iso2");tag("avc1");tag("mp41")} })
            mediaStart=file.filePointer;file.writeInt(1);file.writeBytes("mdat");file.writeLong(0)
        }catch(e: Throwable){file.close();throw e}
    }
    fun addTrack(format: Format): Int {
        check(!finished && !closed);require(tracks.size<2 && tracks.none { it.format.video==format.video })
        require(format.timescale in 1..192000 && format.lastDuration>0 && format.config.size in 2..65536)
        if(format.video)require(format.width in 2..4096 && format.height in 2..4096)
        tracks+=Track(format);return tracks.lastIndex
    }
    fun write(track: Int,packet: ByteArray,timeMicros: Long,key: Boolean=false) {
        // Up to one extra second accommodates AAC encoder priming/padding at the duration cap.
        check(!finished && !closed);require(packet.size in 1..(32*1024*1024) && timeMicros in 0..(7231L*1000000))
        val t=tracks[track];val offset=file.filePointer
        t.append(packet.size,offset,(timeMicros*t.format.timescale+500000)/1000000,key);file.write(packet)
    }
    fun finish() {
        check(!finished && !closed);require(tracks.size==2 && tracks.all { it.count>0 }){"Movie needs non-empty AVC and AAC tracks"}
        val end=file.filePointer;file.seek(mediaStart+8);file.writeLong(end-mediaStart);file.seek(end)
        val length=tracks.maxOf { (it.length*1000+it.format.timescale-1)/it.format.timescale }
        require(length<=Int.MAX_VALUE)
        file.write(box("moov") {
            write(full("mvhd") { writeInt(0);writeInt(0);writeInt(1000);writeInt(length.toInt());writeInt(0x10000);writeShort(0x100);zeros(10);matrix();zeros(24);writeInt(tracks.size+1) })
            tracks.forEachIndexed { index,t->write(trackBox(t,index+1)) }
        });file.fd.sync();finished=true
    }
    override fun close(){if(!closed){closed=true;file.close()}}
    private fun trackBox(t: Track,id: Int)=box("trak") {
        val f=t.format;val movieDuration=(t.length*1000+f.timescale-1)/f.timescale;require(t.length<=Int.MAX_VALUE)
        write(full("tkhd",7) { writeInt(0);writeInt(0);writeInt(id);writeInt(0);writeInt(movieDuration.toInt());zeros(8);writeShort(0);writeShort(0);writeShort(if(f.video)0 else 0x100);writeShort(0);matrix();writeInt(f.width shl 16);writeInt(f.height shl 16) })
        write(box("mdia") {
            write(full("mdhd") { writeInt(0);writeInt(0);writeInt(f.timescale);writeInt(t.length.toInt());writeShort(0x55c4);writeShort(0) })
            write(full("hdlr") { writeInt(0);tag(if(f.video)"vide"else "soun");zeros(12);writeBytes(if(f.video)"PianoPocket video\u0000"else "PianoPocket audio\u0000") })
            write(box("minf") {
                write(if(f.video)full("vmhd",1){zeros(8)}else full("smhd"){zeros(4)})
                write(box("dinf") { write(full("dref") { writeInt(1);write(full("url ",1){}) }) })
                write(box("stbl") {
                    write(full("stsd") { writeInt(1);write(sampleEntry(f)) })
                    write(full("stts") {
                        val runs=mutableListOf<Pair<Int,Int>>();var duration=-1;var count=0
                        repeat(t.count){val next=t.duration(it);if(next!=duration && count>0){runs+=count to duration;count=0};duration=next;count++};runs+=count to duration
                        writeInt(runs.size);runs.forEach { writeInt(it.first);writeInt(it.second) }
                    })
                    write(full("stsc") { writeInt(1);writeInt(1);writeInt(1);writeInt(1) })
                    write(full("stsz") { writeInt(0);writeInt(t.count);repeat(t.count){writeInt(t.sizes[it])} })
                    write(full("co64") { writeInt(t.count);repeat(t.count){writeLong(t.offsets[it])} })
                    if(f.video)write(full("stss") { writeInt((0 until t.count).count { t.keys[it] });repeat(t.count){if(t.keys[it])writeInt(it+1)} })
                })
            })
        })
    }
    private fun sampleEntry(f: Format)=box(if(f.video)"avc1"else "mp4a") {
        zeros(6);writeShort(1)
        if(f.video) {
            zeros(16);writeShort(f.width);writeShort(f.height);writeInt(0x480000);writeInt(0x480000);writeInt(0);writeShort(1);zeros(32);writeShort(24);writeShort(-1)
            write(box("avcC"){write(f.config)})
        } else {
            zeros(8);writeShort(2);writeShort(16);writeShort(0);writeShort(0);writeInt(f.timescale shl 16)
            val config=descriptor(5,f.config)
            val decoder=descriptor(4,bytes { writeByte(0x40);writeByte(0x15);zeros(3);writeInt(320000);writeInt(320000);write(config) })
            val es=descriptor(3,bytes { writeShort(1);writeByte(0);write(decoder);write(descriptor(6,byteArrayOf(2))) })
            write(full("esds"){write(es)})
        }
    }
    companion object {
        private fun bytes(body: DataOutputStream.()->Unit): ByteArray {val buffer=ByteArrayOutputStream();DataOutputStream(buffer).use { it.body() };return buffer.toByteArray()}
        private fun box(type: String,body: DataOutputStream.()->Unit): ByteArray {val data=bytes(body);return bytes { writeInt(data.size+8);tag(type);write(data) }}
        private fun full(type: String,flags: Int=0,body: DataOutputStream.()->Unit)=box(type){writeInt(flags);body()}
        private fun DataOutputStream.tag(value: String){require(value.length==4);writeBytes(value)}
        private fun DataOutputStream.zeros(n: Int){write(ByteArray(n))}
        private fun DataOutputStream.matrix(){intArrayOf(0x10000,0,0,0,0x10000,0,0,0,0x40000000).forEach(::writeInt)}
        private fun descriptor(tag: Int,data: ByteArray)=bytes { writeByte(tag);val size=data.size;require(size<0x10000000);writeByte(((size ushr 21) and 127) or 128);writeByte(((size ushr 14) and 127) or 128);writeByte(((size ushr 7) and 127) or 128);writeByte(size and 127);write(data) }
    }
}

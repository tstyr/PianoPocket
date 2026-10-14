package dev.pianopocket.sequencer

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

data class NormalizationResult(val samplePeak: Double,val estimatedPeak: Double,val gainDb: Double,val ceilingDb: Double)

/** Two bounded-memory passes over a Float32 render. Never used by the audio callback. */
object WavNormalizer {
    private data class Wave(val rate: Int,val offset: Long,val bytes: Long)
    private fun wave(f: RandomAccessFile): Wave {
        fun tag(): String {val b=ByteArray(4);f.readFully(b);return String(b,Charsets.US_ASCII)}
        fun u16()=f.readUnsignedByte() or (f.readUnsignedByte() shl 8)
        fun u32(): Long {var x=0L;repeat(4){x=x or (f.readUnsignedByte().toLong() shl (8*it))};return x}
        require(f.length()>=44 && tag()=="RIFF"){"音量調整のWAVが不正です"};val limit=u32()+8
        require(limit<=f.length() && tag()=="WAVE"){"WAVが途中で終了しています"}
        var rate=0;var valid=false;var offset=0L;var bytes=0L;var chunks=0
        while(f.filePointer+8<=limit){require(++chunks<=1024);val name=tag();val size=u32();val start=f.filePointer;require(size<=limit-start)
            if(name=="fmt "){require(size>=16);val type=u16();val channels=u16();rate=u32().toInt();val byteRate=u32();val align=u16();val bits=u16();valid=type==3 && channels==2 && bits==32 && align==8 && byteRate==rate*8L && rate in listOf(44100,48000)}
            if(name=="data"){require(offset==0L);offset=start;bytes=size}
            f.seek(start+size+(size and 1))
        }
        require(valid && offset>0 && bytes>0 && bytes%8==0L){"音量調整にはステレオFloat32 WAVが必要です"}
        return Wave(rate,offset,bytes)
    }
    fun adjust(input: File,output: File,bits: Int,lossy: Boolean,cancel:()->Boolean={false},progress:(Float)->Unit={}): NormalizationResult {
        require(bits in listOf(16,24,32) && input.canonicalPath!=output.canonicalPath)
        var success=false
        fun checkCancel(){check(!cancel()){"書き出しをキャンセルしました"}}
        try {RandomAccessFile(input,"r").use {source->
            checkCancel();val wave=wave(source);val raw=ByteArray(65536);val buffer=ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
            var done=0L;var peak=0.0;var estimate=0.0
            val history=Array(2){DoubleArray(4)}
            fun observe(v: Double,ch: Int){
                require(v.isFinite()){"WAVに無効な音声値があります"};peak=maxOf(peak,abs(v));estimate=maxOf(estimate,abs(v))
                val h=history[ch];h[0]=h[1];h[1]=h[2];h[2]=h[3];h[3]=v
                // Cubic intermediate samples estimate overshoots; this is not a BS.1770 true-peak meter.
                for(p in 1..3){val t=p*.25;val value=.5*((2*h[1])+(-h[0]+h[2])*t+(2*h[0]-5*h[1]+4*h[2]-h[3])*t*t+(-h[0]+3*h[1]-3*h[2]+h[3])*t*t*t);estimate=maxOf(estimate,abs(value))}
            }
            source.seek(wave.offset)
            while(done<wave.bytes){checkCancel();val n=minOf(raw.size.toLong(),wave.bytes-done).toInt();source.readFully(raw,0,n);buffer.position(0)
                repeat(n/4){i->observe(buffer.float.toDouble(),i and 1)};done+=n;progress(.45f*done/wave.bytes)
            }
            repeat(3){observe(0.0,0);observe(0.0,1)}
            val ceilingDb=if(lossy)-2.0 else -1.0;val ceiling=10.0.pow(ceilingDb/20)
            // Avoid making near-silent tails/noise loud. A quiet performance gets at most +12 dB.
            val gain=if(estimate<.001)1.0 else minOf(ceiling/estimate,10.0.pow(12.0/20))
            val frames=wave.bytes/8;val outputBytes=frames*2*(bits/8);val extra=if(bits==32)14 else 0
            require(outputBytes+36+extra<=0xffffffffL){"WAVが保存できるサイズを超えています"}
            val outRaw=ByteArray(65536/4*(bits/8));val outBuffer=ByteBuffer.wrap(outRaw).order(ByteOrder.LITTLE_ENDIAN)
            var seed=0x504f434b45544cL
            fun random(): Double {seed=seed*6364136223846793005L+1442695040888963407L;return (seed ushr 40).toDouble()/16777216.0}
            RandomAccessFile(output,"rw").use {target->
                target.setLength(0)
                fun tag(s: String){target.write(s.toByteArray(Charsets.US_ASCII))}
                fun put(x: Long,n: Int){repeat(n){target.write((x ushr (it*8)).toInt())}}
                tag("RIFF");put(outputBytes+36+extra,4);tag("WAVEfmt ");put(if(bits==32)18 else 16,4);put(if(bits==32)3 else 1,2);put(2,2);put(wave.rate.toLong(),4);put(wave.rate*2L*(bits/8),4);put((2*(bits/8)).toLong(),2);put(bits.toLong(),2)
                if(bits==32){put(0,2);tag("fact");put(4,4);put(frames,4)};tag("data");put(outputBytes,4)
                source.seek(wave.offset);done=0
                while(done<wave.bytes){checkCancel();val n=minOf(raw.size.toLong(),wave.bytes-done).toInt();source.readFully(raw,0,n);buffer.position(0);outBuffer.clear()
                    repeat(n/4){val v=buffer.float.toDouble()*gain
                        if(bits==32)outBuffer.putFloat(v.toFloat())else {
                            val max=(1L shl (bits-1))-1;val noise=if(peak==0.0)0.0 else random()-random()
                            val pcm=(v*max+noise).roundToLong().coerceIn(-max-1,max).toInt()
                            if(bits==16)outBuffer.putShort(pcm.toShort())else {outBuffer.put(pcm.toByte());outBuffer.put((pcm shr 8).toByte());outBuffer.put((pcm shr 16).toByte())}
                        }
                    }
                    target.write(outRaw,0,outBuffer.position());done+=n;progress(.45f+.55f*done/wave.bytes)
                }
                target.fd.sync()
            }
            checkCancel();success=true;return NormalizationResult(peak,estimate,20*log10(gain),ceilingDb)
        }}finally{if(!success)output.delete()}
    }
}

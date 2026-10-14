package dev.pianopocket.sequencer

import java.io.ByteArrayOutputStream
import kotlin.math.floor
import kotlin.math.ceil

/** Creates one performance snapshot shared by MIDI, audio and video exports. */
object SongExportSettings {
    fun apply(song: MidiSong,speed: Float=1f,muted: Set<Int> = emptySet(),from: Long=0,to: Long=song.duration,
        keyswitches: Map<Int,Set<Int>> = emptyMap()): MidiSong {
        require(speed in Practice.speeds && from>=0 && to>from && to<=song.duration) {"書き出し区間が不正です"}
        val filtered=normalize(song,song.records.filter {it.status !in 0x80..0xef || it.track !in muted},song.endTick)
        fun tickAt(time: Long): Double {
            var tick=0L;var micros=0.0;var tempo=500000
            for(r in filtered.records.filter {it.status==0xff && it.data[0]==0x51.toByte()}){
                val next=micros+(r.tick-tick)*tempo.toDouble()/song.ppqn
                if(next>time)break
                micros=next;tick=r.tick;tempo=tempo(r)
            }
            return (tick+(time-micros)*song.ppqn/tempo.toDouble()).coerceIn(0.0,song.endTick.toDouble())
        }
        val start=floor(tickAt(from)).toLong();val end=ceil(tickAt(to)).toLong().coerceAtLeast(start+1)
        val prefix=mutableListOf<MidiRecord>()
        val effectiveTempo=filtered.records.lastOrNull {it.tick<start && it.status==0xff && it.data[0]==0x51.toByte()}?.let(::tempo) ?: 500000
        prefix+=MidiRecord(0,0,0xff,tempoBytes(effectiveTempo))
        if(from>0){
            for(type in listOf(0x58,0x59))filtered.records.lastOrNull {it.tick<start && it.status==0xff && it.data[0].toInt() and 255==type}?.let {prefix+=it.copy(tick=0)}
            filtered.records.filter {it.status==0xff && it.data[0]==3.toByte() && it.tick<start}.forEach {prefix+=it.copy(tick=0)}
            val cc=Array(16){arrayOfNulls<MidiRecord>(120)};val programs=arrayOfNulls<MidiRecord>(16);val bends=arrayOfNulls<MidiRecord>(16)
            val switches=linkedMapOf<Pair<Int,Int>,MidiRecord>()
            filtered.records.takeWhile {it.tick<start}.filter {it.status in 0x80..0xef}.forEach {r->
                val ch=r.status and 15;val a=r.data[0].toInt() and 127
                when(r.status and 0xf0){
                    0xb0 -> when(a){
                        121->{val bank0=cc[ch][0];val bank32=cc[ch][32];cc[ch].fill(null);cc[ch][0]=bank0;cc[ch][32]=bank32;cc[ch][7]=r.copy(data=byteArrayOf(7,127));cc[ch][11]=r.copy(data=byteArrayOf(11,127));cc[ch][64]=r.copy(data=byteArrayOf(64,0));bends[ch]=r.copy(status=0xe0 or ch,data=byteArrayOf(0,64))}
                        120,123->cc[ch][64]=r.copy(data=byteArrayOf(64,0))
                        else->if(a<120)cc[ch][a]=r
                    }
                    0xc0->programs[ch]=r
                    0xe0->bends[ch]=r
                    0x90->if(r.data.getOrElse(1){0}.toInt()>0 && a in (keyswitches[r.track] ?: keyswitches[-1].orEmpty()))switches[r.track to ch]=r
                }
            }
            for(ch in 0..15){for(bank in listOf(0,32))cc[ch][bank]?.let {prefix+=it.copy(tick=0,track=0)};programs[ch]?.let {prefix+=it.copy(tick=0,track=0)}
                cc[ch].filterIndexed {index,_->index!=0 && index!=32}.filterNotNull().forEach {prefix+=it.copy(tick=0,track=0)};bends[ch]?.let {prefix+=it.copy(tick=0,track=0)}}
            switches.values.forEach {r->val track=if(keyswitches.containsKey(r.track))r.track else 0;prefix+=r.copy(tick=0,track=track);prefix+=MidiRecord(0,track,0x80 or (r.status and 15),byteArrayOf(r.data[0],0))}
            filtered.notes.filter {it.start<from && it.end>from}.forEach {n->
                prefix+=MidiRecord(0,n.track,0x90 or n.channel,byteArrayOf(n.key.toByte(),n.velocity.toByte()))
                if(n.keyEnd<=from)prefix+=MidiRecord(0,n.track,0x80 or n.channel,byteArrayOf(n.key.toByte(),0))
            }
        }
        val cropped=filtered.records.filter {it.tick>=start && (it.tick<end || to==song.duration && it.tick==end)}.map {it.copy(tick=it.tick-start)}
        val length=end-start
        val close=mutableListOf<MidiRecord>()
        filtered.notes.filter {it.start<to && it.keyEnd>=to}.forEach {n->close+=MidiRecord(length,n.track,0x80 or n.channel,byteArrayOf(n.key.toByte(),0))}
        filtered.messages.map {it.track to (it.status and 15)}.distinct().forEach {(track,ch)->close+=MidiRecord(length,track,0xb0 or ch,byteArrayOf(64,0));close+=MidiRecord(length,track,0xb0 or ch,byteArrayOf(123,0))}
        val records=(prefix+cropped+close).map {r->if(r.status==0xff && r.data[0]==0x51.toByte()){
            val changed=kotlin.math.round(tempo(r)/speed.toDouble()).toInt();require(changed in 1..0xffffff){"速度変更後のテンポがMIDIの範囲を超えています"};r.copy(data=tempoBytes(changed))
        }else r}
        return normalize(song,records,length)
    }
    private fun tempo(record: MidiRecord)=((record.data[1].toInt() and 255) shl 16) or ((record.data[2].toInt() and 255) shl 8) or (record.data[3].toInt() and 255)
    private fun tempoBytes(value: Int)=byteArrayOf(0x51,(value ushr 16).toByte(),(value ushr 8).toByte(),value.toByte())
    private fun normalize(source: MidiSong,records: List<MidiRecord>,end: Long): MidiSong {
        val bytes=ByteArrayOutputStream();MidiFileCodec.write(source.copy(records=records,endTick=end),bytes,1)
        return MidiFileCodec.read(bytes.toByteArray().inputStream()).let {it.copy(warnings=(source.warnings+it.warnings).distinct())}
    }
}

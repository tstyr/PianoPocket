package dev.pianopocket.sequencer

import java.io.*
import java.util.ArrayDeque
import kotlin.math.roundToLong

data class SongMessage(val tick: Long, val micros: Long, val track: Int, val status: Int, val a: Int, val b: Int)
data class MidiRecord(val tick: Long, val track: Int, val status: Int, val data: ByteArray)
data class RollNote(val key: Int, val velocity: Int, val channel: Int, val track: Int, val start: Long, val keyEnd: Long, val end: Long)
data class MidiSong(val ppqn: Int, val tracks: Int, val records: List<MidiRecord>, val messages: List<SongMessage>, val duration: Long, val bpm: Double, val warnings: List<String>, val endTick: Long = records.maxOfOrNull { it.tick } ?: 0L) {
    val notes = makeNotes(messages, duration)
    val noteIndex = NoteIndex(notes)
    val keyIndex = NoteIndex(notes,keyOnly=true)
    val sustainIndex = SustainIndex(messages)
    companion object {
        fun recorded(input: List<SongMessage>, end: Long): MidiSong {
            val events = input.sortedBy { it.micros }
            val records = mutableListOf(MidiRecord(0, 0, 0xff, byteArrayOf(0x51, 7, 0xa1.toByte(), 0x20)))
            events.forEach { e -> records += MidiRecord((e.micros * 480.0 / 500000).roundToLong(), e.track, e.status, byteArrayOf(e.a.toByte(), e.b.toByte()).copyOf(if(e.status and 0xe0 == 0xc0) 1 else 2)) }
            return MidiSong(480, 1, records, events, end.coerceAtLeast(events.lastOrNull()?.micros ?: 0), 120.0, emptyList(), (end * 480.0 / 500000).roundToLong())
        }
    }
}

/** A bounded Standard MIDI File codec, independent of the live MIDI packet decoder. */
object MidiFileCodec {
    const val MAX_BYTES = 16 * 1024 * 1024
    const val MAX_EVENTS = 200000
    const val MAX_MICROS = 2 * 60 * 60 * 1000000L
    private class Cursor(val bytes: ByteArray, var pos: Int = 0, val end: Int = bytes.size) {
        fun u8(): Int { require(pos < end) { "MIDIが途中で終了しています" }; return bytes[pos++].toInt() and 255 }
        fun number(n: Int): Long { var v=0L; repeat(n){v=(v shl 8) or u8().toLong()}; return v }
        fun take(n: Int): ByteArray { require(n >= 0 && n <= end-pos) { "MIDIチャンクの長さが不正です" }; return bytes.copyOfRange(pos,pos+n).also { pos+=n } }
        fun vlq(): Long { var n=0L; repeat(4){val b=u8();n=(n shl 7) or (b and 127).toLong();if(b<128)return n};error("MIDI可変長数値が長すぎます") }
    }
    fun read(input: InputStream): MidiSong {
        val out=ByteArrayOutputStream();val buffer=ByteArray(8192)
        while(true){val n=input.read(buffer);if(n<0)break;require(out.size()+n<=MAX_BYTES){"MIDIは16 MiBまでです"};out.write(buffer,0,n)}
        val c=Cursor(out.toByteArray())
        require(String(c.take(4),Charsets.US_ASCII)=="MThd"){"Standard MIDI Fileではありません"}
        val header=c.number(4).toInt();require(header in 6..1024){"MIDIヘッダーが不正です"}
        val format=c.number(2).toInt();val tracks=c.number(2).toInt();val ppqn=c.number(2).toInt();c.take(header-6)
        require(format in 0..1 && tracks in 1..256 && (format!=0 || tracks==1)){"MIDI Type 0/1のみ対応（最大256トラック）"}
        require(ppqn in 1..32767){"SMPTE時間ベースのMIDIは未対応です"}
        val records=mutableListOf<MidiRecord>();var maxTick=0L
        val warnings=linkedSetOf<String>()
        repeat(tracks){track ->
            require(String(c.take(4),Charsets.US_ASCII)=="MTrk"){"MIDIトラックが見つかりません"}
            val length=c.number(4);require(length<=c.end-c.pos){"MIDIトラック長が不正です"}
            val t=Cursor(c.bytes,c.pos,c.pos+length.toInt());c.pos=t.end
            var tick=0L;var running=0
            while(t.pos<t.end){
                tick+=t.vlq();maxTick=maxOf(maxTick,tick)
                var status=t.u8();var first=-1
                if(status<128){first=status;status=running;require(status in 0x80..0xef){"MIDI running statusが不正です"}}
                if(status==0xff){
                    running=0;val type=t.u8();val size=t.vlq();require(size<=t.end-t.pos){"MIDI meta長が不正です"}
                    val data=t.take(size.toInt())
                    if(type==0x2f){require(data.isEmpty()){ "End of Trackが不正です" };break}
                    if(type==0x51)require(data.size==3 && data.any { it.toInt()!=0 }){"MIDIテンポが不正です"}
                    records+=MidiRecord(tick,track,status,byteArrayOf(type.toByte())+data)
                } else if(status==0xf0 || status==0xf7){
                    running=0;val size=t.vlq();require(size<=t.end-t.pos){"SysEx長が不正です"};records+=MidiRecord(tick,track,status,t.take(size.toInt()));warnings+="SysExはファイルに保存しますが、内蔵再生・MIDI出力には適用しません。"
                } else {
                    require(status in 0x80..0xef){"未対応のMIDIステータス"};running=status
                    val count=if(status and 0xe0 == 0xc0)1 else 2
                    val data=ByteArray(count);data[0]=(if(first>=0)first else t.u8()).toByte();if(count==2)data[1]=t.u8().toByte()
                    require(data.all { it.toInt()>=0 }){"MIDIデータバイトが不正です"}
                    records+=MidiRecord(tick,track,status,data)
                    if(status and 0xf0 in listOf(0xa0,0xc0,0xd0))warnings+="Program Change / pressureは内蔵音源には適用しません。MIDI出力へは送信します。"
                    if(status and 0xf0 == 0xb0 && data[0].toInt() !in listOf(7,11,64,120,121,123))warnings+="追加のCCは音源に対応するサンプル選択条件がある場合に適用します。連続した変調は未対応です。"
                    if(status and 0xf0 == 0x90 && (status and 15)==9)warnings+="音源はトラックごとの指定を使います。GMの自動音色切替は未対応です。"
                }
                require(records.size<=MAX_EVENTS){"MIDIイベントは20万件までです"}
            }
        }
        val sorted=records.sortedWith(compareBy<MidiRecord>{it.tick}.thenBy{it.track})
        var tick=0L;var micros=0.0;var tempo=500000;var firstTempo=0
        val messages=mutableListOf<SongMessage>()
        sorted.forEach { e ->
            micros+=(e.tick-tick)*tempo.toDouble()/ppqn;tick=e.tick
            require(micros<=MAX_MICROS){"MIDIは2時間までです"}
            if(e.status==0xff && e.data[0].toInt() and 255==0x51){tempo=((e.data[1].toInt() and 255) shl 16) or ((e.data[2].toInt() and 255) shl 8) or (e.data[3].toInt() and 255);if(firstTempo==0)firstTempo=tempo}
            if(e.status in 0x80..0xef)messages+=SongMessage(e.tick,micros.roundToLong(),e.track,e.status,e.data[0].toInt() and 127,e.data.getOrElse(1){0}.toInt() and 127)
        }
        val duration=(micros+(maxTick-tick)*tempo.toDouble()/ppqn).roundToLong()
        require(duration<=MAX_MICROS){"MIDIは2時間までです"}
        return MidiSong(ppqn,tracks,sorted,messages,duration,60000000.0/(if(firstTempo==0)500000 else firstTempo),warnings.toList(), maxTick)
    }
    fun write(song: MidiSong, output: OutputStream, format: Int) {
        require(format in 0..1)
        val target=DataOutputStream(output);val count=if(format==0)1 else song.tracks
        target.writeBytes("MThd");target.writeInt(6);target.writeShort(format);target.writeShort(count);target.writeShort(song.ppqn)
        fun vlq(out: OutputStream,n: Long){require(n in 0..0xfffffff);var value=n;val b=IntArray(4);var i=3;b[i]=(value and 127).toInt();while(value>127){value=value ushr 7;b[--i]=(value and 127).toInt() or 128};while(i<4)out.write(b[i++])}
        repeat(count){track ->
            val b=ByteArrayOutputStream();var previous=0L
            song.records.filter { format==0 || it.track==track }.sortedWith(compareBy<MidiRecord>{it.tick}.thenBy{it.track}).forEach { e ->
                vlq(b,e.tick-previous);previous=e.tick;b.write(e.status)
                if(e.status==0xff){b.write(e.data[0].toInt());vlq(b,(e.data.size-1).toLong());b.write(e.data,1,e.data.size-1)}
                else {if(e.status>=0xf0)vlq(b,e.data.size.toLong());b.write(e.data)}
            }
            // Keep trailing rests; imported tick duration can extend beyond the last channel message.
            val lastTick=song.endTick
            vlq(b,(lastTick-previous).coerceAtLeast(0));b.write(byteArrayOf(0xff.toByte(),0x2f,0))
            target.writeBytes("MTrk");target.writeInt(b.size());b.writeTo(target)
        }
        target.flush()
    }
}

private fun makeNotes(messages: List<SongMessage>, duration: Long): List<RollNote> {
    data class Start(val message: SongMessage, var released: Long=-1)
    val down=Array(16){Array(128){ArrayDeque<Start>()}};val pending=Array(16){mutableListOf<Start>()};val pedal=BooleanArray(16)
    val out=mutableListOf<RollNote>()
    fun finish(s: Start,end: Long){val e=s.message;out+=RollNote(e.a,e.b,e.status and 15,e.track,e.micros,if(s.released<0)end else s.released,maxOf(end,e.micros))}
    messages.forEach { e ->
        val ch=e.status and 15
        when(e.status and 0xf0){
            0x90,0x80 -> if(e.status and 0xf0==0x90 && e.b>0)down[ch][e.a].addLast(Start(e)) else down[ch][e.a].pollFirst()?.let { s->s.released=e.micros;if(pedal[ch])pending[ch]+=s else finish(s,e.micros) }
            0xb0 -> if(e.a==64){pedal[ch]=e.b>=64;if(!pedal[ch]){pending[ch].forEach { finish(it,e.micros) };pending[ch].clear()}}else if(e.a in listOf(120,121,123)){
                pending[ch].forEach { finish(it,e.micros) };pending[ch].clear();pedal[ch]=false
                if(e.a!=121)down[ch].forEach { q->while(q.isNotEmpty())finish(q.removeFirst(),e.micros) }
            }
        }
    }
    down.forEach { row->row.forEach { q->q.forEach { finish(it,duration) } } };pending.forEach { q->q.forEach { finish(it,duration) } }
    return out.sortedBy { it.start }
}

/** Interval segment tree: long held notes remain visible without scanning the whole song. */
class NoteIndex(private val notes: List<RollNote>,private val keyOnly: Boolean=false) {
    private val ends=LongArray(maxOf(1,notes.size*4))
    init { if(notes.isNotEmpty())build(1,0,notes.lastIndex) }
    private fun build(node: Int,l: Int,r: Int): Long {ends[node]=if(l==r){if(keyOnly)notes[l].keyEnd else notes[l].end} else {val m=(l+r)/2;maxOf(build(node*2,l,m),build(node*2+1,m+1,r))};return ends[node]}
    fun visible(from: Long,to: Long,draw: (RollNote)->Unit) { if(notes.isNotEmpty())visit(1,0,notes.lastIndex,from,to,draw) }
    private fun visit(node: Int,l: Int,r: Int,from: Long,to: Long,draw: (RollNote)->Unit) {
        if(ends[node]<from || notes[l].start>to)return
        if(l==r){draw(notes[l]);return};val m=(l+r)/2;visit(node*2,l,m,from,to,draw);visit(node*2+1,m+1,r,from,to,draw)
    }
}

/** Indexed CC64 state. Any input channel holding the pedal lights SUS. */
class SustainIndex(messages: List<SongMessage>) {
    private val times: LongArray
    private val values: BooleanArray
    init {
        val channels=BooleanArray(16);val changes=mutableListOf<Pair<Long,Boolean>>()
        messages.forEach { e->if(e.status and 0xf0==0xb0 && e.a in listOf(64,120,121,123)) {
            channels[e.status and 15]=e.a==64 && e.b>=64
            val active=channels.any { it }
            if(changes.lastOrNull()?.second!=active)changes+=e.micros to active
        } }
        times=LongArray(changes.size){changes[it].first};values=BooleanArray(changes.size){changes[it].second}
    }
    fun active(micros: Long): Boolean {
        var low=0;var high=times.size
        while(low<high){val mid=(low+high) ushr 1;if(times[mid]<=micros)low=mid+1 else high=mid}
        return low>0 && values[low-1]
    }
}

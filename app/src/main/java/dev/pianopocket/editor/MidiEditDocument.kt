package dev.pianopocket.editor

import dev.pianopocket.sequencer.*
import java.io.ByteArrayOutputStream
import java.util.ArrayDeque
import kotlin.math.roundToLong

data class EditNote(val id: Int, val offId: Int?, val key: Int, val velocity: Int, val channel: Int,
    val track: Int, val start: Long, val end: Long)

/** Changes are sparse; undo stores individual edits, not copies of a large MIDI song. */
class MidiEditDocument(val source: MidiSong) {
    val notes: List<EditNote>
    private val changes=mutableMapOf<Int,EditNote?>()
    private data class Change(val id: Int,val before: EditNote?,val after: EditNote?)
    private val undo=ArrayDeque<Change>()
    private val redo=ArrayDeque<Change>()
    private val byId: Map<Int,EditNote>
    private val ends: LongArray
    val canUndo get()=undo.isNotEmpty()
    val canRedo get()=redo.isNotEmpty()
    val dirty get()=changes.isNotEmpty()
    val maxTick=source.endTick+source.ppqn*64L

    init {
        val active=mutableMapOf<Triple<Int,Int,Int>,ArrayDeque<Int>>()
        val result=mutableListOf<EditNote>()
        fun close(id: Int,offId: Int?,end: Long){val r=source.records[id];result+=EditNote(id,offId,r.data[0].toInt() and 127,r.data[1].toInt() and 127,r.status and 15,r.track,r.tick,maxOf(r.tick+1,end))}
        source.records.forEachIndexed {i,r->
            val kind=r.status and 0xf0
            if(kind==0x90 || kind==0x80){
                val k=Triple(r.track,r.status and 15,r.data[0].toInt() and 127)
                if(kind==0x90 && r.data[1].toInt() and 127>0)active.getOrPut(k){ArrayDeque()}.addLast(i)
                else active[k]?.pollFirst()?.let {close(it,i,r.tick)}
            }else if(kind==0xb0 && r.data[0].toInt() and 127 in listOf(120,123)){
                // Channel-mode messages release every track sharing that channel.
                active.filterKeys {it.second==r.status and 15}.values.forEach {q->while(q.isNotEmpty())close(q.removeFirst(),null,r.tick)}
            }
        }
        active.values.forEach {q->q.forEach {close(it,null,source.endTick)}}
        notes=result.sortedBy {it.start};byId=notes.associateBy {it.id}
        ends=LongArray(maxOf(1,notes.size*4))
        fun build(node: Int,l: Int,r: Int): Long {ends[node]=if(l==r)notes[l].end else {val m=(l+r)/2;maxOf(build(node*2,l,m),build(node*2+1,m+1,r))};return ends[node]}
        if(notes.isNotEmpty())build(1,0,notes.lastIndex)
    }
    fun note(id: Int): EditNote?=if(changes.containsKey(id))changes[id]else byId[id]
    fun current(): List<EditNote> = notes.mapNotNull {note(it.id)}
    fun visible(from: Long,to: Long): List<EditNote> {
        val out=mutableListOf<EditNote>()
        fun visit(node: Int,l: Int,r: Int){
            if(ends[node]<from || notes[l].start>to)return
            if(l==r){val n=notes[l];if(!changes.containsKey(n.id))out+=n;return}
            val m=(l+r)/2;visit(node*2,l,m);visit(node*2+1,m+1,r)
        }
        if(notes.isNotEmpty())visit(1,0,notes.lastIndex)
        changes.values.filterNotNull().filterTo(out){it.end>=from && it.start<=to}
        return out
    }
    fun update(note: EditNote){
        val original=byId[note.id] ?: error("音符が見つかりません")
        require(note.offId==original.offId && note.track==original.track && note.channel==original.channel)
        require(note.key in 0..127 && note.velocity in 1..127 && note.start>=0 && note.end>note.start && note.end<=maxTick){"音符の範囲が不正です"}
        change(note.id,note)
    }
    fun delete(id: Int){require(byId.containsKey(id));change(id,null)}
    private fun store(id: Int,n: EditNote?){if(n==byId[id])changes.remove(id)else changes[id]=n}
    private fun change(id: Int,n: EditNote?){val before=note(id);if(before==n)return;undo.addLast(Change(id,before,n));if(undo.size>64)undo.removeFirst();redo.clear();store(id,n)}
    fun undo(){undo.pollLast()?.let {store(it.id,it.before);redo.addLast(it)}}
    fun redo(){redo.pollLast()?.let {store(it.id,it.after);undo.addLast(it)}}
    fun move(id: Int,delta: Long,semitones: Int){note(id)?.let {n->val length=n.end-n.start;val start=(n.start+delta).coerceIn(0,maxTick-length);update(n.copy(start=start,end=start+length,key=(n.key+semitones).coerceIn(0,127)))}}
    fun length(id: Int,delta: Long){note(id)?.let {update(it.copy(end=(it.end+delta).coerceIn(it.start+1,maxTick)))}}
    fun quantize(id: Int,divisions: Int){require(divisions in listOf(1,2,4));note(id)?.let {n->
        val step=source.ppqn.toDouble()/divisions
        val start=(n.start/step).roundToLong().let {(it*step).roundToLong()}.coerceIn(0,maxTick-1)
        val end=(n.end/step).roundToLong().let {(it*step).roundToLong()}.coerceIn(start+1,maxTick)
        update(n.copy(start=start,end=end))
    }}
    fun song(): MidiSong {
        // Keep untouched records byte for byte, including SysEx, release velocities and meta events.
        val removed=mutableSetOf<Int>();val added=mutableListOf<MidiRecord>()
        changes.forEach {(id,n)->val old=byId.getValue(id);removed+=id;old.offId?.let {removed+=it}
            if(n!=null){val on=source.records[id];added+=on.copy(tick=n.start,data=byteArrayOf(n.key.toByte(),n.velocity.toByte()))
                val off=old.offId?.let {source.records[it]}
                added+=MidiRecord(n.end,n.track,off?.status ?: (0x80 or n.channel),byteArrayOf(n.key.toByte(),off?.data?.get(1) ?: 0))
            }
        }
        val records=(source.records.filterIndexed {i,_->i !in removed}+added).sortedWith(compareBy<MidiRecord>{it.tick}.thenBy{it.track}.thenBy{if(it.status and 0xf0==0x80 || it.status and 0xf0==0x90 && it.data[1].toInt()==0)0 else if(it.status and 0xf0==0x90)2 else 1})
        val end=maxOf(source.endTick,records.maxOfOrNull {it.tick} ?: 0)
        val bytes=ByteArrayOutputStream();MidiFileCodec.write(source.copy(records=records,endTick=end),bytes,1)
        return MidiFileCodec.read(bytes.toByteArray().inputStream())
    }
}

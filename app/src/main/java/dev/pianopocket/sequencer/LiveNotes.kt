package dev.pianopocket.sequencer

import java.util.ArrayDeque

data class LiveSnapshot(val notes: List<RollNote>, val held: Set<Int>, val sustained: Set<Int>, val now: Long)
class LiveNotes(private val clock: () -> Long = System::nanoTime) {
    private val epoch=clock()
    private data class Held(val key: Int,val channel: Int,val velocity: Int,val start: Long,var off: Long=-1)
    private val held=Array(17){Array(128){ArrayDeque<Held>()}}
    private val released=Array(17){mutableListOf<Held>()}
    private val pedal=BooleanArray(17)
    private val history=ArrayDeque<RollNote>()
    private fun now()=(clock()-epoch)/1000
    private fun close(n: Held,t: Long){history.addLast(RollNote(n.key,n.velocity,n.channel,0,n.start,if(n.off<0)t else n.off,t));while(history.size>2048)history.removeFirst()}
    @Synchronized fun event(status: Int,a: Int,b: Int,channel: Int=status and 15) {
        val t=now()
        when(status and 0xf0){
            0x90 -> if(b>0){val q=held[channel][a];if(q.size==4)close(q.removeFirst(),t);q.addLast(Held(a,channel,b,t))}else off(channel,a,t)
            0x80 -> off(channel,a,t)
            0xb0 -> if(a==64){pedal[channel]=b>=64;if(!pedal[channel]){released[channel].forEach { close(it,t) };released[channel].clear()}} else if(a in listOf(120,121,123))clearChannel(channel,t)
        }
    }
    private fun off(ch: Int,key: Int,t: Long){held[ch][key].pollFirst()?.let { n->n.off=t;if(pedal[ch])released[ch]+=n else close(n,t) };if(released[ch].size>512)close(released[ch].removeAt(0),t)}
    private fun clearChannel(ch: Int,t: Long){held[ch].forEach { q->while(q.isNotEmpty())close(q.removeFirst(),t) };released[ch].forEach { close(it,t) };released[ch].clear();pedal[ch]=false}
    @Synchronized fun clear(){repeat(17){clearChannel(it,now())};history.clear()}
    @Synchronized fun snapshot(): LiveSnapshot {
        val t=now();while(history.isNotEmpty() && history.first().end<t-8000000)history.removeFirst()
        val active=ArrayList<Held>();held.forEach { row->row.forEach { q->if(q.isNotEmpty())q.forEach { active+=it } } };val tails=ArrayList<Held>();released.forEach { q->q.forEach { tails+=it } }
        val notes=history.toList()+(active+tails).map { RollNote(it.key,it.velocity,it.channel,0,it.start,if(it.off<0)t else it.off,t) }
        return LiveSnapshot(notes,active.map { it.key }.toSet(),tails.map { it.key }.toSet(),t)
    }
}

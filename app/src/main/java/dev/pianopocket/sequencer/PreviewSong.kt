package dev.pianopocket.sequencer

/** A short snapshot with controller and sounding-note reconstruction for an audible preview. */
object PreviewSong {
    fun slice(song: MidiSong,position: Long,seconds: Int=5): MidiSong {
        require(seconds in 1..5)
        val start=position.coerceIn(0,song.duration).let {if(it>=song.duration)0 else it};val end=minOf(song.duration,start+seconds*1000000L)
        val prefix=mutableListOf<SongMessage>();val controllers=Array(16){arrayOfNulls<SongMessage>(128)};val bends=arrayOfNulls<SongMessage>(16)
        song.messages.takeWhile {it.micros<start}.forEach { e->val ch=e.status and 15
            if(e.status and 0xf0==0xb0){if(e.a in listOf(120,121,123)){controllers[ch][64]=e.copy(a=64,b=0);if(e.a==121){controllers[ch].fill(null);bends[ch]=null}}else controllers[ch][e.a]=e}
            if(e.status and 0xf0==0xe0)bends[ch]=e
        }
        controllers.forEach {row->row.filterNotNull().forEach {prefix+=it.copy(tick=0,micros=0)}};bends.filterNotNull().forEach {prefix+=it.copy(tick=0,micros=0)}
        song.noteIndex.visible(start,start){n->if(n.start<start && n.end>start){prefix+=SongMessage(0,0,n.track,0x90 or n.channel,n.key,n.velocity);if(n.keyEnd<=start)prefix+=SongMessage(0,0,n.track,0x80 or n.channel,n.key,0)}}
        val events=prefix+song.messages.filter {it.micros>=start && it.micros<end}.map {it.copy(tick=0,micros=it.micros-start)}
        // Close remaining keys/pedals at the preview boundary; a short tail is rendered afterwards.
        val length=(end-start).coerceAtLeast(1)
        val closed=events+(0..15).flatMap {ch->listOf(SongMessage(0,length,0,0xb0 or ch,123,0),SongMessage(0,length,0,0xb0 or ch,64,0))}
        return MidiSong(song.ppqn,song.tracks,emptyList(),closed,length,song.bpm,song.warnings)
    }
}

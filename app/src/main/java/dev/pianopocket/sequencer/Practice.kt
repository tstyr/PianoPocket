package dev.pianopocket.sequencer

object Practice {
    val speeds=listOf(.25f,.5f,.75f,1f,1.25f,1.5f,2f)
    fun messages(song: MidiSong,muted: Set<Int>)=song.messages.filter { it.track !in muted || it.status and 0xf0 !in listOf(0x80,0x90) }
    fun trackName(song: MidiSong,track: Int): String {
        val text=song.records.firstOrNull {it.track==track && it.status==0xff && it.data.firstOrNull()==3.toByte()}?.data?.let {String(it,1,minOf(160,it.size-1),Charsets.UTF_8).take(60)}
        return text?.takeIf {it.isNotBlank()} ?: "Track ${track+1}"
    }
}

package dev.pianopocket.sequencer

object Practice {
    val speeds=listOf(.25f,.5f,.75f,1f,1.25f,1.5f,2f)
    fun target(key: Int,track: Int,options: GuidedPractice,muted: Set<Int>): Boolean = options.enabled && track !in muted && (options.tracks==null || track in options.tracks) && when(options.hand){PracticeHand.BOTH->true;PracticeHand.LEFT->key<options.splitKey;PracticeHand.RIGHT->key>=options.splitKey}
    fun messages(song: MidiSong,muted: Set<Int>,options: GuidedPractice=GuidedPractice())=song.messages.filter { it.status and 0xf0 !in listOf(0x80,0x90) || (it.track !in muted && !target(it.a,it.track,options,muted)) }
    fun gates(song: MidiSong,muted: Set<Int>,options: GuidedPractice): List<PracticeGate> {
        if(!options.enabled || !options.waitForInput)return emptyList()
        val gates=mutableListOf<PracticeGate>()
        song.notes.filter {target(it.key,it.track,options,muted)}.sortedBy {it.start}.forEach {note->
            val last=gates.lastOrNull()
            if(last!=null && note.start-last.micros<=10000 && (note.start==last.micros || note.key !in last.keys))gates[gates.lastIndex]=last.copy(keys=last.keys+note.key)
            else gates+=PracticeGate(note.start,setOf(note.key))
        }
        require(gates.size<=100000) {"練習ポイントは10万件までです"};return gates
    }
    fun beats(song: MidiSong): List<PracticeBeat> {
        val records=song.records.filter {it.status==0xff && (it.data.firstOrNull()==0x51.toByte() || it.data.firstOrNull()==0x58.toByte())}.sortedWith(compareBy<MidiRecord>{it.tick}.thenBy{it.track})
        val signatures=records.filter {it.data.firstOrNull()==0x58.toByte()};var signatureCursor=0
        var cursor=0;var tick=0L;var micros=0.0;var tempo=500000;var signatureTick=0L;var numerator=4;var unit=song.ppqn.toDouble()
        val result=mutableListOf<PracticeBeat>()
        var next=0.0
        while(next<=song.endTick){
            require(result.size<100000) {"メトロノームの拍数上限を超えています"}
            while(cursor<records.size && records[cursor].tick<=next){
                val r=records[cursor++];micros+=(r.tick-tick)*tempo.toDouble()/song.ppqn;tick=r.tick
                if(r.data[0]==0x51.toByte() && r.data.size>=4){tempo=((r.data[1].toInt() and 255) shl 16) or ((r.data[2].toInt() and 255) shl 8) or (r.data[3].toInt() and 255);require(tempo>0)}
                else if(r.data[0]==0x58.toByte() && r.data.size>=3){val n=r.data[1].toInt() and 255;val denominator=r.data[2].toInt() and 255;if(n in 1..32 && denominator in 0..6){numerator=n;unit=song.ppqn*4.0/(1L shl denominator);signatureTick=r.tick}}
            }
            val time=micros+(next-tick)*tempo.toDouble()/song.ppqn
            val bar=(next-signatureTick)/(unit*numerator)
            result+=PracticeBeat(kotlin.math.round(time).toLong(),kotlin.math.abs(bar-kotlin.math.round(bar))<.00001)
            var proposed=next+unit
            while(signatureCursor<signatures.size && signatures[signatureCursor].tick<=next)signatureCursor++
            signatures.getOrNull(signatureCursor)?.tick?.let {if(it<proposed)proposed=it.toDouble()}
            next=proposed
        }
        return result.filter {it.micros<=song.duration}
    }
    fun trackName(song: MidiSong,track: Int): String {
        val text=song.records.firstOrNull {it.track==track && it.status==0xff && it.data.firstOrNull()==3.toByte()}?.data?.let {String(it,1,minOf(160,it.size-1),Charsets.UTF_8).take(60)}
        return text?.takeIf {it.isNotBlank()} ?: "Track ${track+1}"
    }
}
enum class PracticeHand {BOTH,LEFT,RIGHT}
data class GuidedPractice(val enabled: Boolean=false,val waitForInput: Boolean=true,val hand: PracticeHand=PracticeHand.BOTH,val splitKey: Int=60,val tracks: Set<Int>?=null) {init {require(splitKey in 21..108)}}
data class PracticeGate(val micros: Long,val keys: Set<Int>)
data class PracticeBeat(val micros: Long,val accent: Boolean)

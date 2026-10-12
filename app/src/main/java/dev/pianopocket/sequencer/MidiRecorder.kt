package dev.pianopocket.sequencer

data class RecordedTake(val messages: List<SongMessage>,val duration: Long) { fun toSong()=MidiSong.recorded(messages,duration) }

/** Capture on the MIDI receiver thread; snapshots are taken by the UI, never by the audio callback. */
class MidiRecorder(private val clock: () -> Long = System::nanoTime) {
    private val messages=ArrayList<SongMessage>()
    private var anchor=0L
    private var recording=false
    private var capped=false
    private val keys=Array(16){IntArray(128)}
    private val pedals=BooleanArray(16)
    @Synchronized fun start() { messages.clear();keys.forEach { it.fill(0) };pedals.fill(false);anchor=clock();recording=true;capped=false }
    @Synchronized fun capture(status: Int,a: Int,b: Int,timestamp: Long=0) {
        if(!recording)return
        val now=clock();val time=if(timestamp>0 && timestamp in anchor..now)timestamp else now
        val micros=((time-anchor)/1000).coerceAtLeast(messages.lastOrNull()?.micros ?: 0)
        if(messages.size>=MidiFileCodec.MAX_EVENTS-16384 || micros>=MidiFileCodec.MAX_MICROS){capped=true;recording=false;closeKeys(micros.coerceAtMost(MidiFileCodec.MAX_MICROS));return}
        val ch=status and 15;val type=status and 0xf0
        if(type==0x90 && b>0){if(keys[ch][a]>=4)messages+=SongMessage(0,micros,0,0x80 or ch,a,0)else keys[ch][a]++} else if(type==0x80 || type==0x90)keys[ch][a]=(keys[ch][a]-1).coerceAtLeast(0)
        if(type==0xb0){if(a==64)pedals[ch]=b>=64;if(a==120 || a==123)keys[ch].fill(0);if(a==121)pedals[ch]=false}
        messages+=SongMessage(0,micros,0,status,a,b)
    }
    private fun closeKeys(end: Long) {
        repeat(16){ch->repeat(128){note->repeat(keys[ch][note].coerceAtMost(4)){messages+=SongMessage(0,end,0,0x80 or ch,note,0)};keys[ch][note]=0};if(pedals[ch])messages+=SongMessage(0,end,0,0xb0 or ch,64,0);pedals[ch]=false}
    }
    @Synchronized fun stop(): RecordedTake {val end=((clock()-anchor)/1000).coerceIn(0,MidiFileCodec.MAX_MICROS);recording=false;closeKeys(end);return RecordedTake(messages.toList(),maxOf(end,messages.lastOrNull()?.micros ?: 0))}
    @Synchronized fun elapsed(): Long {
        val time=((clock()-anchor)/1000).coerceAtLeast(0)
        if(recording && time>=MidiFileCodec.MAX_MICROS){capped=true;recording=false;closeKeys(MidiFileCodec.MAX_MICROS)}
        return if(recording)time else (messages.lastOrNull()?.micros ?: 0)
    }
    @Synchronized fun isRecording()=recording
    @Synchronized fun limitReached()=capped
}

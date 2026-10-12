package dev.pianopocket.sequencer

data class SequenceState(
    val library: List<MidiEntry> = emptyList(), val song: MidiSong? = null,
    val selected: String = "", val title: String = "MIDIを選択", val playing: Boolean = false,
    val position: Long = 0, val recording: Boolean = false, val recordingMicros: Long = 0,
    val live: LiveSnapshot = LiveSnapshot(emptyList(),emptySet(),emptySet(),0),
    val rollVisible: Boolean = true, val noteLabels: Boolean = true, val lookAhead: Float = 5f,
    val viewport: KeyboardViewport = KeyboardViewport(),
    val exporting: Boolean = false, val exportProgress: Float = 0f
) { val fullKeyboard: Boolean get()=viewport.full }
enum class ExportFormat(val label: String,val extension: String,val mime: String,val bits: Int) {
    MIDI0("MIDI Type 0", "mid", "audio/midi", 0), MIDI1("MIDI Type 1", "mid", "audio/midi", 0),
    WAV16("WAV · PCM 16-bit", "wav", "audio/wav", 16), WAV24("WAV · PCM 24-bit", "wav", "audio/wav", 24),
    WAV32("WAV · Float32", "wav", "audio/wav", 32),
    FLAC("FLAC · lossless 24-bit", "flac", "audio/flac", 24),
    MP3("MP3 · LAME", "mp3", "audio/mpeg", 24),
    M4A("M4A · AAC-LC", "m4a", "audio/mp4", 16);
    val compressed: Boolean get()=this in listOf(FLAC,MP3,M4A)
    val lossy: Boolean get()=this==MP3 || this==M4A
}

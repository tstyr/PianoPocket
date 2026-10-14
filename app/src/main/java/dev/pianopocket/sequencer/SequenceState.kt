package dev.pianopocket.sequencer

data class SequenceState(
    val library: List<MidiEntry> = emptyList(), val song: MidiSong? = null,
    val sourceSong: MidiSong? = null, val transpose: Int = 0,
    val selected: String = "", val title: String = "MIDIを選択", val playing: Boolean = false,
    val position: Long = 0, val recording: Boolean = false, val recordingMicros: Long = 0,
    val live: LiveSnapshot = LiveSnapshot(emptyList(),emptySet(),emptySet(),0),
    val rollVisible: Boolean = true, val noteLabels: Boolean = true, val lookAhead: Float = 5f,
    val viewport: KeyboardViewport = KeyboardViewport(),
    val speed: Float=1f,val loopA: Long=0,val loopB: Long=0,val looping: Boolean=false,
    val hiddenTracks: Set<Int> = emptySet(),val mutedTracks: Set<Int> = emptySet(),
    val guided: GuidedPractice=GuidedPractice(),val waiting: Boolean=false,val waitingKeys: Set<Int> = emptySet(),
    val metronome: Boolean=false,val metronomeBpm: Float=120f,val metronomeVolume: Float=.35f,
    val exportSelection: ExportSelection=ExportSelection(),
    val exporting: Boolean = false, val exportProgress: Float = 0f,
    val exportPhase: String="",val exportEtaSeconds: Long?=null,val previewPath: String?=null
) { val fullKeyboard: Boolean get()=viewport.full }
data class ExportSelection(val format: ExportFormat=ExportFormat.WAV24,val rate: Int=48000,val tail: Int=8,val bitrate: Int=256,val video: dev.pianopocket.video.VideoOptions=dev.pianopocket.video.VideoOptions(),val usePlayback: Boolean=true,val loopOnly: Boolean=false) {
    init {require(rate in listOf(44100,48000) && tail in 0..30 && bitrate in listOf(128,192,256,320))}
}
enum class ExportFormat(val label: String,val extension: String,val mime: String,val bits: Int) {
    MIDI0("MIDI Type 0", "mid", "audio/midi", 0), MIDI1("MIDI Type 1", "mid", "audio/midi", 0),
    WAV16("WAV · PCM 16-bit", "wav", "audio/wav", 16), WAV24("WAV · PCM 24-bit", "wav", "audio/wav", 24),
    WAV32("WAV · Float32", "wav", "audio/wav", 32),
    FLAC("FLAC · lossless 24-bit", "flac", "audio/flac", 24),
    MP3("MP3 · LAME", "mp3", "audio/mpeg", 24),
    M4A("M4A · AAC-LC", "m4a", "audio/mp4", 16),
    MP4("MP4 · H.264 + AAC", "mp4", "video/mp4", 16),
    MOV("MOV · H.264 + AAC", "mov", "video/quicktime", 16);
    val video: Boolean get()=this==MP4 || this==MOV
    val compressed: Boolean get()=this in listOf(FLAC,MP3,M4A,MP4,MOV)
    val lossy: Boolean get()=this==MP3 || this==M4A || video
}

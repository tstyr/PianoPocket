package dev.pianopocket.video

import android.net.Uri
import dev.pianopocket.sequencer.ExportFormat
import dev.pianopocket.sequencer.KeyboardViewport
import dev.pianopocket.sequencer.MidiSong

data class ExportRegion(val path: String,val ints: IntArray,val floats: FloatArray)
data class ExportEffect(val type: Int,val params: FloatArray,val ir: String)
data class ExportBank(val bank: Int,val regions: List<ExportRegion>,val effects: List<ExportEffect>,val curves: Map<Int,FloatArray>,val groups: List<Pair<Int,Float>>,val master: Float)
data class ExportRequest(
    val destination: Uri?,val format: ExportFormat,val rate: Int,val tail: Int,val bitrate: Int,val video: VideoOptions,
    val song: MidiSong,val title: String,val memoryMiB: Int,val volume: Float,val voices: Int,
    val regions: List<ExportRegion>,val effects: List<ExportEffect>,val curves: Map<Int,FloatArray>,val groups: List<Float>,val master: List<Float>,
    val labels: Boolean,val viewport: KeyboardViewport,val lookAhead: Float,val preview: Boolean=false,val trackBanks: Map<Int,Int> = emptyMap(),val banks: List<ExportBank> = emptyList()
)
data class ExportStatus(val id: Long=0,val running: Boolean=false,val progress: Float=0f,val step: Int=0,val phase: String="",val etaSeconds: Long?=null,val message: String="",val previewPath: String?=null)

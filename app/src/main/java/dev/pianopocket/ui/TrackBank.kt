package dev.pianopocket.ui
import dev.pianopocket.library.LibraryEntry
import dev.pianopocket.library.Preset
internal data class TrackBank(val bank: Int,val entry: LibraryEntry,val preset: Preset,val groups: List<Float>,val offset: Int,val master: Float)

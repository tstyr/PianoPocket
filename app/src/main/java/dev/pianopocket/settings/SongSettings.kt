package dev.pianopocket.settings

import dev.pianopocket.sequencer.KeyboardViewport
import dev.pianopocket.sequencer.Practice
import dev.pianopocket.sequencer.SequenceState
import java.io.*

/** A stopped session, separate from the original MIDI file and instrument settings. */
data class SongSettings(
    val instrument: String = "", val transpose: Int = 0, val speed: Float = 1f,
    val position: Long = 0, val loopA: Long = 0, val loopB: Long = 0, val looping: Boolean = false,
    val viewport: KeyboardViewport = KeyboardViewport(),
    val mutedTracks: Set<Int> = emptySet(), val hiddenTracks: Set<Int> = emptySet()
) {
    fun restore(sequence: SequenceState): SequenceState {
        val song = checkNotNull(sequence.song)
        val a = loopA.coerceIn(0, song.duration)
        val b = loopB.coerceIn(a, song.duration)
        return sequence.copy(transpose = transpose, speed = speed, position = position.coerceIn(0, song.duration),
            loopA = a, loopB = b, looping = looping && b - a >= 100000,
            viewport = viewport, mutedTracks = mutedTracks.filter { it < song.tracks }.toSet(),
            hiddenTracks = hiddenTracks.filter { it < song.tracks }.toSet(), playing = false)
    }
    companion object {
        fun capture(instrument: String, sequence: SequenceState) = SongSettings(instrument, sequence.transpose,
            sequence.speed, sequence.position, sequence.loopA, sequence.loopB, sequence.looping,
            sequence.viewport, sequence.mutedTracks, sequence.hiddenTracks)
    }
}

class SongSettingsStore(private val home: File) {
    init { check(home.isDirectory || home.mkdirs()) }
    private fun file(id: String) = File(home, SoundSettings.hash(id) + ".song")
    fun read(id: String): SongSettings? {
        val source = file(id)
        if (!source.exists()) return null
        try {
            require(source.length() <= 16384)
            return decode(source.readBytes())
        } catch (error: Exception) {
            // Retain corrupt data for inspection, and allow the original MIDI to open normally.
            source.renameTo(File(home, source.name + ".broken"))
            return null
        }
    }
    fun write(id: String, settings: SongSettings) {
        val target = file(id); val temp = File(home, target.name + ".tmp")
        try {
            FileOutputStream(temp).use { it.write(encode(settings)); it.fd.sync() }
            check(temp.renameTo(target)) { "曲の設定を保存できません" }
        } finally { temp.delete() }
    }
    fun delete(id: String) { file(id).delete(); File(home, file(id).name + ".broken").delete() }
    companion object {
        fun encode(settings: SongSettings): ByteArray {
            require(settings.instrument.length <= 256 && settings.transpose in -24..24 && settings.speed in Practice.speeds)
            require(settings.position >= 0 && settings.loopA >= 0 && settings.loopB >= settings.loopA)
            val view = settings.viewport
            require(view.zoom.isFinite() && view.zoom in 1f..6.5f && view.start.isFinite() && view.start in 0f..view.maxStart)
            val bytes = ByteArrayOutputStream()
            DataOutputStream(bytes).use { out ->
                out.writeInt(0x50505331); out.writeUTF(settings.instrument); out.writeInt(settings.transpose)
                out.writeFloat(settings.speed); out.writeLong(settings.position); out.writeLong(settings.loopA)
                out.writeLong(settings.loopB); out.writeBoolean(settings.looping); out.writeFloat(view.zoom); out.writeFloat(view.start)
                listOf(settings.mutedTracks, settings.hiddenTracks).forEach { tracks ->
                    require(tracks.size <= 1024 && tracks.all { it in 0..1023 })
                    out.writeInt(tracks.size); tracks.sorted().forEach(out::writeInt)
                }
            }
            return bytes.toByteArray()
        }
        fun decode(bytes: ByteArray): SongSettings {
            require(bytes.size <= 16384)
            DataInputStream(bytes.inputStream()).use { input ->
                require(input.readInt() == 0x50505331)
                val instrument = input.readUTF(); val transpose = input.readInt(); val speed = input.readFloat()
                val position = input.readLong(); val a = input.readLong(); val b = input.readLong(); val loop = input.readBoolean()
                val viewport = KeyboardViewport(input.readFloat(), input.readFloat())
                fun tracks(): Set<Int> {
                    val count = input.readInt(); require(count in 0..1024)
                    val list = List(count) { input.readInt() }
                    require(list.distinct().size == count)
                    return list.toSet()
                }
                val settings = SongSettings(instrument, transpose, speed, position, a, b, loop, viewport, tracks(), tracks())
                require(input.read() == -1); encode(settings) // Validate all values before restoration.
                return settings
            }
        }
    }
}

package dev.pianopocket.sequencer

/** Transform a copy; original library files and performance timing stay intact. */
object SongTranspose {
    const val MIN = -24
    const val MAX = 24
    fun apply(source: MidiSong, semitones: Int): MidiSong {
        require(semitones in MIN..MAX)
        if (semitones == 0) return source
        fun pitch(status: Int) = status and 15 != 9 && status and 0xf0 in listOf(0x80, 0x90, 0xa0)
        var omitted = 0
        val messages = source.messages.mapNotNull { event ->
            if (!pitch(event.status)) event else {
                val key = event.a + semitones
                if (key in 0..127) event.copy(a = key) else {
                    if (event.status and 0xf0 == 0x90 && event.b > 0) omitted++
                    null
                }
            }
        }
        val records = source.records.mapNotNull { record ->
            if (pitch(record.status)) {
                val key = (record.data[0].toInt() and 127) + semitones
                if (key !in 0..127) null else record.copy(data = record.data.copyOf().also { it[0] = key.toByte() })
            } else if (semitones % 12 != 0 && record.status == 0xff && record.data.size == 3 && record.data[0] == 0x59.toByte() && record.data[1].toInt() in -7..7 && record.data[2].toInt() in 0..1) {
                val original = record.data[1].toInt()
                val target = Math.floorMod(original * 7 + semitones, 12)
                val fifths = (-7..7).filter { Math.floorMod(it * 7, 12) == target }
                    .minWith(compareBy<Int> { kotlin.math.abs(it) }.thenBy { if (original < 0) it else -it })
                record.copy(data = record.data.copyOf().also { it[1] = fifths.toByte() })
            } else record
        }
        return source.copy(records = records, messages = messages,
            warnings = source.warnings + if (omitted > 0) listOf("転調でMIDI音域外になった${omitted}音を除外しました。") else emptyList())
    }
}

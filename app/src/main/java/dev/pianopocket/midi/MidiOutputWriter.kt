package dev.pianopocket.midi

/** MIDI 1.0 wire encoding. Reused buffer; called only by the MIDI I/O worker. */
class MidiOutputWriter(private val send: (ByteArray, Int, Long) -> Unit, private val flush: () -> Unit) {
    private val bytes = ByteArray(3)
    fun message(status: Int, first: Int, second: Int, nanos: Long) {
        require(status in 0x80..0xef && first in 0..127 && second in 0..127)
        bytes[0] = status.toByte(); bytes[1] = first.toByte(); bytes[2] = second.toByte()
        val length = if (status and 0xe0 == 0xc0) 2 else 3
        send(bytes, length, nanos.coerceAtLeast(0))
    }
    fun silence(channels: Int = 0xffff, resetControllers: Boolean = false, nanos: Long = 0) {
        if (nanos == 0L) flush()
        for (channel in 0..15) if (channels and (1 shl channel) != 0) {
            message(0xb0 or channel, 64, 0, nanos)
            message(0xb0 or channel, 120, 0, nanos)
            message(0xb0 or channel, 123, 0, nanos)
            if (resetControllers) {
                message(0xb0 or channel, 121, 0, nanos)
                message(0xe0 or channel, 0, 64, nanos)
            }
        }
    }
}

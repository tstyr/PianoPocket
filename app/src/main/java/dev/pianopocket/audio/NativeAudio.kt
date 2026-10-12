package dev.pianopocket.audio

object NativeAudio {
    init { System.loadLibrary("pianopocket") }
    external fun start(device: Int, bursts: Int): Boolean
    external fun stop()
    external fun begin(memoryMiB: Int)
    external fun abort()
    external fun region(path: String, ints: IntArray, floats: FloatArray)
    external fun velocityCurve(group: Int, curve: FloatArray)
    external fun effect(type: Int, params: FloatArray, ir: String)
    external fun replaceImpulse(index: Int, path: String, params: FloatArray)
    external fun commit()
    external fun event(type: Int, channel: Int, a: Int, b: Int, value: Float)
    external fun stats(): DoubleArray
    fun noteOn(note: Int, velocity: Int, channel: Int = 16) = event(0, channel, note, velocity, 0f)
    fun noteOff(note: Int, channel: Int = 16) = event(1, channel, note, 0, 0f)
    fun cc(number: Int, value: Int, channel: Int = 16) = event(2, channel, number, value, 0f)
    fun panic() = event(5, 0, 0, 0, 0f)
}

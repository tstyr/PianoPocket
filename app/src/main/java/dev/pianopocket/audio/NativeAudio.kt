package dev.pianopocket.audio

object NativeAudio {
    init { System.loadLibrary("pianopocket") }
    external fun start(device: Int, bursts: Int): Boolean
    external fun stop()
    external fun suspendPerformance()
    external fun stability(enabled: Boolean)
    external fun begin(memoryMiB: Int)
    external fun abort()
    external fun decodeSample(source: String,target: String)
    external fun region(path: String, ints: IntArray, floats: FloatArray)
    external fun velocityCurve(group: Int, curve: FloatArray)
    external fun effect(type: Int, params: FloatArray, ir: String)
    external fun replaceImpulse(index: Int, path: String, params: FloatArray)
    external fun commit()
    external fun event(type: Int, channel: Int, a: Int, b: Int, value: Float)
    external fun stats(): DoubleArray
    external fun timeline(times: LongArray, messages: IntArray, duration: Long)
    external fun transport(): DoubleArray
    external fun seekTimeline(micros: Long)
    external fun practiceLoop(begin: Long,end: Long,enabled: Boolean)
    external fun practicePlan(times: LongArray,keys: LongArray,beats: LongArray,accents: IntArray,wait: Boolean,metro: Boolean,bpm: Float,volume: Float,follow: Boolean)
    external fun practiceStatus(): LongArray
    external fun exportBegin(memoryMiB: Int, rate: Int)
    external fun exportControl(type: Int, a: Int, b: Int, value: Float)
    external fun exportWav(path: String, times: LongArray, messages: IntArray, duration: Long, bits: Int, tail: Int)
    external fun encodeCompressed(wav: String,output: String,format: Int,bitrate: Int)
    external fun cancelExport()
    external fun exportProgress(): Float
    external fun resetExportProgress()
    fun noteOn(note: Int, velocity: Int, channel: Int = 16) = event(0, channel, note, velocity, 0f)
    fun noteOff(note: Int, channel: Int = 16) = event(1, channel, note, 0, 0f)
    fun cc(number: Int, value: Int, channel: Int = 16) = event(2, channel, number, value, 0f)
    fun panic() = event(5, 0, 0, 0, 0f)
}

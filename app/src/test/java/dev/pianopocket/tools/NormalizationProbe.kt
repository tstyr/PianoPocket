package dev.pianopocket.tools

import dev.pianopocket.sequencer.WavNormalizer
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/** Original stereo tone fixture, passed through the production offline normalization path. */
fun main(args: Array<String>) {
    require(args.size==1);val home=File(args[0]).apply {mkdirs()};val input=File(home,"original-float.wav")
    val frames=48000*3;val bytes=ByteBuffer.allocate(58+frames*8).order(ByteOrder.LITTLE_ENDIAN)
    bytes.put("RIFF".toByteArray()).putInt(bytes.capacity()-8).put("WAVEfmt ".toByteArray()).putInt(18).putShort(3).putShort(2).putInt(48000).putInt(384000).putShort(8).putShort(32).putShort(0).put("fact".toByteArray()).putInt(4).putInt(frames).put("data".toByteArray()).putInt(frames*8)
    repeat(frames){i->val t=i/48000.0;val envelope=(1-exp(-t*80))*exp(-t*1.2);val value=((sin(2*PI*261.625*t)+sin(2*PI*329.628*t)+sin(2*PI*391.995*t))*.055*envelope).toFloat();bytes.putFloat(value).putFloat(value*.65f)}
    input.writeBytes(bytes.array())
    for(bits in listOf(16,24,32)){val result=WavNormalizer.adjust(input,File(home,"adjusted-$bits.wav"),bits,true);println("bits=$bits samplePeak=${result.samplePeak} estimatedPeak=${result.estimatedPeak} gainDb=${result.gainDb} ceilingDb=${result.ceilingDb}")}
}

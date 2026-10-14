package dev.pianopocket

import dev.pianopocket.sequencer.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.pow
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavNormalizerTest {
    private fun source(samples: FloatArray): File {
        require(samples.size%2==0)
        val b=ByteBuffer.allocate(44+samples.size*4).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(b.capacity()-8).put("WAVEfmt ".toByteArray()).putInt(16).putShort(3).putShort(2).putInt(48000).putInt(384000).putShort(8).putShort(32).put("data".toByteArray()).putInt(samples.size*4)
        samples.forEach {b.putFloat(it)}
        return File.createTempFile("pocket-normalize-",".wav").apply {writeBytes(b.array())}
    }
    private fun values(file: File,bits: Int): DoubleArray {
        val bytes=file.readBytes();val offset=if(bits==32)58 else 44;val b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);b.position(offset)
        return DoubleArray((bytes.size-offset)/(bits/8)) {
            if(bits==32)b.float.toDouble()else if(bits==16)b.short/32768.0 else {var v=(b.get().toInt() and 255) or ((b.get().toInt() and 255) shl 8) or ((b.get().toInt() and 255) shl 16);if(v and 0x800000!=0)v-=0x1000000;v/8388608.0}
        }
    }
    @Test fun sharedGainKeepsStereoAndDynamicsInFloatAndPcm(){
        val input=source(floatArrayOf(.3f,.15f,.6f,.3f,.15f,.075f,0f,0f))
        try {for(bits in listOf(16,24,32)){
            val output=File.createTempFile("pocket-adjusted-",".wav")
            try {val result=WavNormalizer.adjust(input,output,bits,false);val v=values(output,bits)
                assertEquals(2.0,v[0]/v[1],.001);assertEquals(2.0,v[2]/v[0],.001)
                assertTrue(v.maxOf {kotlin.math.abs(it)}<=.892);assertTrue(result.gainDb>0)
                assertEquals(8,v.size)
            }finally{output.delete()}
        }}finally{input.delete()}
    }
    @Test fun gainIsLimitedSilenceStaysSilentAndNearNoiseIsNotBoosted(){
        for(amplitude in listOf(0f,.00001f,.01f)){
            val input=source(FloatArray(64){amplitude});val output=File.createTempFile("pocket-adjusted-",".wav")
            try {val r=WavNormalizer.adjust(input,output,32,false)
                assertEquals(if(amplitude<.001)0.0 else 12.0,r.gainDb,1e-8)
                assertEquals(amplitude*(if(amplitude<.001)1.0 else 10.0.pow(.6)),values(output,32)[0],1e-7)
            }finally{input.delete();output.delete()}
        }
    }
    @Test fun lossyFormatsLeaveExtraHeadroomAndInterpolationDetectsOvershoot(){
        val input=source(floatArrayOf(-1f,-1f,1f,1f,1f,1f,-1f,-1f));val output=File.createTempFile("pocket-adjusted-",".wav")
        try {val r=WavNormalizer.adjust(input,output,24,true);assertTrue(r.estimatedPeak>r.samplePeak);assertEquals(-2.0,r.ceilingDb,0.0);assertTrue(values(output,24).maxOf {kotlin.math.abs(it)}<.795)}finally{input.delete();output.delete()}
    }
    @Test fun cancelledAndInvalidFloatExportsRemoveOnlyOutput(){
        val input=source(FloatArray(10000){.1f});val output=File.createTempFile("pocket-adjusted-",".wav")
        try {var calls=0;try {WavNormalizer.adjust(input,output,16,false,{++calls>3});fail()}catch(_:IllegalStateException){}
            assertFalse(output.exists());assertTrue(input.exists())
            input.writeBytes(source(floatArrayOf(Float.NaN,0f)).let {val b=it.readBytes();it.delete();b})
            try {WavNormalizer.adjust(input,output,32,false);fail()}catch(_:IllegalArgumentException){};assertFalse(output.exists())
        }finally{input.delete();output.delete()}
    }
    @Test fun pcm16IntermediaryCanBeReadByAacInput(){
        val input=source(FloatArray(200){.15f});val output=File.createTempFile("pocket-adjusted-",".wav")
        try {WavNormalizer.adjust(input,output,16,true);PcmWavInput(output).use {assertEquals(100L,it.frames);assertEquals(400,it.read(ByteArray(400)))}}finally{input.delete();output.delete()}
    }
}

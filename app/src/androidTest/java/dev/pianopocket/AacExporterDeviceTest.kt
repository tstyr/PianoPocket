package dev.pianopocket

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.pianopocket.sequencer.AacExporter
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

@RunWith(AndroidJUnit4::class)
class AacExporterDeviceTest {
    private fun writeTone(wav: File,rate: Int) {
                val header=ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
                header.put("RIFF".toByteArray()).putInt(36+rate*4).put("WAVEfmt ".toByteArray()).putInt(16).putShort(1).putShort(2).putInt(rate).putInt(rate*4).putShort(4).putShort(16).put("data".toByteArray()).putInt(rate*4)
                FileOutputStream(wav).use { file->file.write(header.array());val frame=ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);repeat(rate){i->frame.clear();frame.putShort((sin(2*PI*440*i/rate)*16000).toInt().toShort());frame.putShort((sin(2*PI*660*i/rate)*8000).toInt().toShort());file.write(frame.array())} }
    }
    @Test fun realAndroidAacMuxesAndDecodesBothSampleRates() {
        assumeTrue(AacExporter.available())
        val root=InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        for(rate in listOf(44100,48000)) {
            val wav=File(root,"aac-test-$rate.wav");val output=File(root,"aac-test-$rate.m4a")
            try {
                writeTone(wav,rate)
                AacExporter.encode(wav,output,256,AtomicBoolean(false)){}
                val extractor=MediaExtractor();var decoder: MediaCodec?=null
                try {
                    extractor.setDataSource(output.path);assertEquals(1,extractor.trackCount)
                    val format=extractor.getTrackFormat(0);assertEquals("audio/mp4a-latm",format.getString(MediaFormat.KEY_MIME));assertEquals(rate,format.getInteger(MediaFormat.KEY_SAMPLE_RATE));assertEquals(2,format.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
                    assertTrue(format.getLong(MediaFormat.KEY_DURATION) in 900000L..1200000L)
                    extractor.selectTrack(0);format.setInteger(MediaFormat.KEY_PCM_ENCODING,AudioFormat.ENCODING_PCM_16BIT)
                    val codec=MediaCodec.createDecoderByType("audio/mp4a-latm");decoder=codec;codec.configure(format,null,null,0);codec.start()
                    var ended=false;var fed=false;val info=MediaCodec.BufferInfo();var square=0.0;var count=0L;val deadline=System.nanoTime()+30000000000L
                    while(!ended && System.nanoTime()<deadline) {
                        if(!fed){val index=codec.dequeueInputBuffer(10000);if(index>=0){val buffer=checkNotNull(codec.getInputBuffer(index));val n=extractor.readSampleData(buffer,0);if(n<0){codec.queueInputBuffer(index,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);fed=true}else{codec.queueInputBuffer(index,0,n,extractor.sampleTime,0);extractor.advance()}}}
                        val index=codec.dequeueOutputBuffer(info,10000)
                        if(index>=0){try{val buffer=checkNotNull(codec.getOutputBuffer(index)).order(ByteOrder.LITTLE_ENDIAN);buffer.position(info.offset);buffer.limit(info.offset+info.size);while(buffer.remaining()>=2){val value=buffer.short/32768.0;square+=value*value;count++};ended=info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM!=0}finally{codec.releaseOutputBuffer(index,false)}}
                    }
                    assertTrue("AAC decoder reached EOS",ended);assertTrue("Decoded stereo frames",count>rate);assertTrue("Decoded audible tone",sqrt(square/count)>.1)
                } finally {runCatching { decoder?.stop() };decoder?.release();extractor.release()}
            } finally {wav.delete();output.delete()}
        }
    }
    @Test fun cancelRemovesPartialM4a() {
        assumeTrue(AacExporter.available())
        val root=InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        val output=File(root,"aac-cancel.m4a")
        val wav=File(root,"aac-cancel.wav")
        try {writeTone(wav,48000);assertTrue(runCatching { AacExporter.encode(wav,output,256,AtomicBoolean(true)){} }.isFailure);assertFalse(output.exists())}finally{wav.delete();output.delete()}
    }
}

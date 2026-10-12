package dev.pianopocket

import android.graphics.Color
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.pianopocket.sequencer.KeyboardViewport
import dev.pianopocket.sequencer.MidiSong
import dev.pianopocket.sequencer.SongMessage
import dev.pianopocket.video.VideoExporter
import dev.pianopocket.video.VideoOptions
import dev.pianopocket.video.VideoRollRenderer
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class VideoExporterDeviceTest {
    private fun song()=MidiSong.recorded(listOf(SongMessage(0,0,0,0x90,60,100),SongMessage(0,100000,0,0xb0,64,127),SongMessage(0,500000,0,0x80,60,0),SongMessage(0,800000,0,0xb0,64,0)),1000000)
    private fun tone(file: File) {
        val rate=48000;val bytes=ByteBuffer.allocate(44+rate*4).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray()).putInt(36+rate*4).put("WAVEfmt ".toByteArray()).putInt(16).putShort(1).putShort(2).putInt(rate).putInt(rate*4).putShort(4).putShort(16).put("data".toByteArray()).putInt(rate*4)
        repeat(rate){i->val value=(sin(2*PI*440*i/rate)*10000).toInt().toShort();bytes.putShort(value).putShort(value)};file.writeBytes(bytes.array())
    }
    private fun decodeVideo(file: File,expected: Int) {
        val extractor=MediaExtractor();var decoder: MediaCodec?=null
        try {
            extractor.setDataSource(file.path);assertEquals(2,extractor.trackCount)
            val tracks=(0 until extractor.trackCount).map(extractor::getTrackFormat)
            val video=tracks.indexOfFirst { it.getString(MediaFormat.KEY_MIME)=="video/avc" };assertTrue(video>=0)
            assertTrue(tracks.any { it.getString(MediaFormat.KEY_MIME)=="audio/mp4a-latm" && it.getInteger(MediaFormat.KEY_CHANNEL_COUNT)==2 })
            val format=tracks[video];assertEquals(1280,format.getInteger(MediaFormat.KEY_WIDTH));assertEquals(720,format.getInteger(MediaFormat.KEY_HEIGHT));assertTrue(format.getLong(MediaFormat.KEY_DURATION) in 990000L..1100000L)
            extractor.selectTrack(video);val codec=MediaCodec.createDecoderByType("video/avc");decoder=codec;codec.configure(format,null,null,0);codec.start()
            var inputEnded=false;var ended=false;var frames=0;var last=-1L;val info=MediaCodec.BufferInfo();val deadline=System.nanoTime()+60000000000L
            while(!ended && System.nanoTime()<deadline) {
                if(!inputEnded){val index=codec.dequeueInputBuffer(10000);if(index>=0){val b=checkNotNull(codec.getInputBuffer(index));val n=extractor.readSampleData(b,0);if(n<0){codec.queueInputBuffer(index,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputEnded=true}else{codec.queueInputBuffer(index,0,n,extractor.sampleTime,0);extractor.advance()}}}
                val index=codec.dequeueOutputBuffer(info,10000)
                if(index>=0){try{if(info.size>0){assertTrue(info.presentationTimeUs>last);last=info.presentationTimeUs;frames++};ended=info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM!=0}finally{codec.releaseOutputBuffer(index,false)}}
            }
            assertTrue("Video decoder reached EOS",ended);assertEquals(expected,frames)
        }finally{runCatching { decoder?.stop() };runCatching { decoder?.release() };extractor.release()}
    }
    @Test fun realAndroidSurfaceEncoderProducesDecodableMp4AndQuickTimeMov() {
        assumeTrue(VideoExporter.supports(VideoOptions()))
        val root=InstrumentationRegistry.getInstrumentation().targetContext.cacheDir;val wav=File(root,"video-device.wav")
        try {
            tone(wav)
            for(fps in listOf(30,60)){val options=VideoOptions(fps,720);if(!VideoExporter.supports(options))continue
                for(qt in listOf(false,true)){val output=File(root,if(qt)"video-device.mov"else "video-device.mp4")
                    try{VideoExporter.encode(wav,output,song(),qt,options,192,true,KeyboardViewport(),5f,AtomicBoolean(false)){};assertEquals(if(qt)"qt  "else "isom",String(output.inputStream().use { it.readBytes().copyOfRange(8,12) }));decodeVideo(output,fps)}finally{output.delete()}
                }
            }
        }finally{wav.delete()}
    }
    @Test fun physicalKeyRollAndOpaqueCaptionRenderWithoutSustainExtension() {
        val song=song();assertEquals(500000L,song.notes.single().keyEnd);assertEquals(800000L,song.notes.single().end)
        VideoRollRenderer(song,1280,720,true,KeyboardViewport(),5f).use { renderer->
            renderer.draw(250000);assertEquals(255,Color.alpha(renderer.bitmap.getPixel(20,20)))
            val first=renderer.bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888,false)
            try{renderer.draw(600000);assertFalse(first.sameAs(renderer.bitmap))}finally{first.recycle()}
        }
    }
    @Test fun cancellationRemovesPartialMovieAndAudioIntermediary() {
        assumeTrue(VideoExporter.supports(VideoOptions()))
        val root=InstrumentationRegistry.getInstrumentation().targetContext.cacheDir;val wav=File(root,"video-cancel.wav");val output=File(root,"video-cancel.mov")
        try{tone(wav);assertTrue(runCatching { VideoExporter.encode(wav,output,song(),true,VideoOptions(),192,true,KeyboardViewport(),5f,AtomicBoolean(true)){} }.isFailure);assertFalse(output.exists());assertFalse(File(root,"video-cancel-video-audio.m4a").exists())}finally{wav.delete();output.delete()}
    }
}

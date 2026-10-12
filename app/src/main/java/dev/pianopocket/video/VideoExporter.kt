package dev.pianopocket.video

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import dev.pianopocket.sequencer.AacExporter
import dev.pianopocket.sequencer.KeyboardViewport
import dev.pianopocket.sequencer.MidiSong
import dev.pianopocket.sequencer.PcmWavInput
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.ceil

data class VideoOptions(val fps: Int=30,val height: Int=720) {
    init {require(fps in listOf(30,60) && height in listOf(720,1080))}
    val width: Int get()=height*16/9
    val bitrate: Int get()=(if(height==1080)8000000 else 4000000)*(if(fps==60)2 else 1)
}
object VideoExporter {
    private const val MIME="video/avc"
    fun supports(options: VideoOptions): Boolean=runCatching { AacExporter.available() && encoder(options).isNotEmpty() }.getOrDefault(false)
    fun supportedOptions(): List<VideoOptions> = listOf(VideoOptions(),VideoOptions(30,1080),VideoOptions(60,720),VideoOptions(60,1080)).filter(::supports)
    private fun encoder(options: VideoOptions): String {
        return MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.firstOrNull { info->
            info.isEncoder && info.supportedTypes.any { it.equals(MIME,true) } && runCatching { val caps=info.getCapabilitiesForType(MIME);val video=checkNotNull(caps.videoCapabilities);MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface in caps.colorFormats && video.areSizeAndRateSupported(options.width,options.height,options.fps.toDouble()) && video.bitrateRange.contains(options.bitrate) && caps.profileLevels.any { it.profile==MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline } }.getOrDefault(false)
        }?.name ?: error("この端末では${options.height}p / ${options.fps}fpsのH.264保存を利用できません。720p / 30fpsを試してください。")
    }
    fun encode(wav: File,output: File,song: MidiSong,quickTime: Boolean,options: VideoOptions,bitrate: Int,labels: Boolean,viewport: KeyboardViewport,lookAhead: Float,cancel: AtomicBoolean,progress:(Float)->Unit) {
        val name=encoder(options);val duration=PcmWavInput(wav).use { it.frames*1000000/it.rate }
        require(duration in 1..(7230L*1000000));val count=ceil(duration*options.fps/1000000.0).toInt()
        val aac=File(output.parentFile,output.nameWithoutExtension+"-video-audio.m4a");var success=false
        try {
            AacExporter.encode(wav,aac,bitrate,cancel){progress(it*.15f)}
            MovieMuxer(output,quickTime).use { mux->
                val extractor=MediaExtractor()
                try {
                    extractor.setDataSource(aac.path)
                    val track=(0 until extractor.trackCount).firstOrNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)=="audio/mp4a-latm" } ?: error("AAC track missing")
                    val format=extractor.getTrackFormat(track);val rate=format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    require(format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)==2)
                    val audio=mux.addTrack(MovieMuxer.Format(false,bufferBytes(checkNotNull(format.getByteBuffer("csd-0"))),rate,1024))
                    extractor.selectTrack(track);val buffer=ByteBuffer.allocate(1024*1024);val start=extractor.sampleTime
                    while(true){check(!cancel.get()){"書き出しをキャンセルしました"};buffer.clear();val n=extractor.readSampleData(buffer,0);if(n<0)break;require(n<=buffer.capacity());buffer.position(0);buffer.limit(n);mux.write(audio,bufferBytes(buffer),extractor.sampleTime-start);extractor.advance()}
                }finally{extractor.release()}
                val codec=MediaCodec.createByCodecName(name);var started=false;var input: android.view.Surface?=null
                try {
                    val format=MediaFormat.createVideoFormat(MIME,options.width,options.height).apply {
                        setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);setInteger(MediaFormat.KEY_BIT_RATE,options.bitrate)
                        setInteger(MediaFormat.KEY_FRAME_RATE,options.fps);setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,2);setInteger(MediaFormat.KEY_PROFILE,MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline);setInteger(MediaFormat.KEY_MAX_B_FRAMES,0)
                    }
                    codec.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);val surface=codec.createInputSurface();input=surface;codec.start();started=true
                    EncoderSurface(surface).use { egl->VideoRollRenderer(song,options.width,options.height,labels,viewport,lookAhead).use { renderer->
                        val info=MediaCodec.BufferInfo();var video=-1;var ended=false;var samples=0;var activity=System.nanoTime()
                        fun drain(wait: Boolean) {
                            while(!ended) {
                                check(!cancel.get()){"書き出しをキャンセルしました"}
                                val index=codec.dequeueOutputBuffer(info,if(wait)10000 else 0)
                                if(index==MediaCodec.INFO_TRY_AGAIN_LATER){check(!wait || System.nanoTime()-activity<30000000000L){"Video encoder timed out"};return}
                                if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                                    check(video<0);val out=codec.outputFormat;val parts=(0..2).mapNotNull { out.getByteBuffer("csd-$it")?.let(::bufferBytes) }
                                    video=mux.addTrack(MovieMuxer.Format(true,AvcPackets.configuration(parts),90000,90000/options.fps,options.width,options.height));activity=System.nanoTime()
                                }else if(index>=0) {
                                    try {
                                        if(info.size>0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG==0){check(video>=0);val buffer=checkNotNull(codec.getOutputBuffer(index));buffer.position(info.offset);buffer.limit(info.offset+info.size);mux.write(video,AvcPackets.sample(bufferBytes(buffer)),info.presentationTimeUs,info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME!=0);samples++}
                                        if(info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM!=0)ended=true
                                    }finally{codec.releaseOutputBuffer(index,false)}
                                    activity=System.nanoTime()
                                }
                            }
                        }
                        repeat(count){frame->check(!cancel.get()){"書き出しをキャンセルしました"};drain(false);renderer.draw(frame*1000000L/options.fps);egl.draw(renderer.bitmap,frame*1000000000L/options.fps);drain(false);progress(.15f+.85f*(frame+1)/count)}
                        codec.signalEndOfInputStream();while(!ended)drain(true)
                        check(samples==count){"Video encoder dropped frames ($samples / $count)"}
                    } }
                    mux.finish();progress(1f);success=true
                }finally{if(started)runCatching { codec.stop() };runCatching { codec.release() };runCatching { input?.release() }}
            }
        }finally{aac.delete();if(!success)output.delete()}
    }
    private fun bufferBytes(buffer: ByteBuffer): ByteArray=buffer.duplicate().let { b->ByteArray(b.remaining()).also { b.get(it) } }
}

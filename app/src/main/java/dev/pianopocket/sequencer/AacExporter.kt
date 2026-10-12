package dev.pianopocket.sequencer

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** Uses Android's real AAC-LC encoder and MP4 muxer, on the export worker only. */
object AacExporter {
    private const val MIME="audio/mp4a-latm"
    fun available(): Boolean=runCatching { MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { it.isEncoder && it.supportedTypes.any { type->type.equals(MIME,true) } } }.getOrDefault(false)
    fun encode(wav: File,output: File,bitrate: Int,cancel: AtomicBoolean,progress:(Float)->Unit) {
        require(bitrate in listOf(128,192,256,320)){"Invalid AAC bitrate"}
        require(wav.canonicalPath!=output.canonicalPath)
        require(available()){"この端末にはAACエンコーダーがありません。WAV / FLAC / MP3を使用してください。"}
        var codec: MediaCodec?=null;var muxer: MediaMuxer?=null;var codecStarted=false;var muxerStarted=false;var success=false
        try {
            PcmWavInput(wav).use { pcm->
                val format=MediaFormat.createAudioFormat(MIME,pcm.rate,2).apply {
                    setInteger(MediaFormat.KEY_AAC_PROFILE,MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                    setInteger(MediaFormat.KEY_BIT_RATE,bitrate*1000)
                    setInteger(MediaFormat.KEY_MAX_INPUT_SIZE,16384)
                }
                val name=MediaCodecList(MediaCodecList.REGULAR_CODECS).findEncoderForFormat(format) ?: error("この端末では ${pcm.rate} Hz / ${bitrate} kbpsのAAC保存に対応していません。レート・音質を変更してください。")
                val encoder=MediaCodec.createByCodecName(name);codec=encoder
                encoder.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);encoder.start();codecStarted=true
                val container=MediaMuxer(output.path,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);muxer=container
                val info=MediaCodec.BufferInfo();val bytes=ByteArray(16384);var inputEnded=false;var outputEnded=false;var fed=0L;var track=-1;var activity=System.nanoTime()
                while(!outputEnded) {
                    check(!cancel.get()){"書き出しをキャンセルしました"}
                    if(!inputEnded) {
                        val index=encoder.dequeueInputBuffer(10000)
                        if(index>=0) {
                            val buffer=checkNotNull(encoder.getInputBuffer(index));buffer.clear()
                            require(buffer.remaining()>=4){"AAC encoder input buffer is too small"}
                            val count=pcm.read(bytes,minOf(bytes.size,buffer.remaining()))
                            val time=fed*1000000/pcm.rate
                            if(count<0){encoder.queueInputBuffer(index,0,0,time,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputEnded=true}
                            else {buffer.put(bytes,0,count);encoder.queueInputBuffer(index,0,count,time,0);fed+=count/4;progress(fed.toFloat()/pcm.frames)}
                            activity=System.nanoTime()
                        }
                    }
                    val index=encoder.dequeueOutputBuffer(info,10000)
                    when {
                        index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            check(!muxerStarted){"AAC encoder changed its output format twice"}
                            track=container.addTrack(encoder.outputFormat);container.start();muxerStarted=true;activity=System.nanoTime()
                        }
                        index>=0 -> {
                            try {
                                if(info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG!=0)info.size=0
                                if(info.size>0){check(muxerStarted && track>=0){"AAC data arrived before format"};val buffer=checkNotNull(encoder.getOutputBuffer(index));buffer.position(info.offset);buffer.limit(info.offset+info.size);container.writeSampleData(track,buffer,info)}
                                if(info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM!=0)outputEnded=true
                            } finally { encoder.releaseOutputBuffer(index,false) }
                            activity=System.nanoTime()
                        }
                    }
                    check(System.nanoTime()-activity<30000000000L){"AAC encoder timed out"}
                }
                check(muxerStarted){"AAC encoder produced no audio"}
                container.stop();muxerStarted=false
                check(output.length()>0){"M4A output is empty"};progress(1f);success=true
            }
        } finally {
            if(codecStarted)runCatching { codec?.stop() }
            runCatching { codec?.release() }
            if(muxerStarted)runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            if(!success)output.delete()
        }
    }
}

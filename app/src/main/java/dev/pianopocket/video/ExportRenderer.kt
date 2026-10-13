package dev.pianopocket.video

import android.content.Context
import dev.pianopocket.audio.NativeAudio
import dev.pianopocket.sequencer.*
import java.io.File

/** Blocking worker job, with its own immutable sound/MIDI snapshot and no Activity dependency. */
object ExportRenderer {
    fun render(context: Context,id: Long,r: ExportRequest): File? {
        val temp=File(context.cacheDir,"render-${android.os.Process.myPid()}-$id.wav")
        val encodedDirectory=if(r.preview)File(context.cacheDir,"previews").apply {check(isDirectory || mkdirs()){"プレビュー保存先を作成できません"}}else context.cacheDir
        val encoded=File(encodedDirectory,temp.nameWithoutExtension+".${r.format.extension}")
        var keepPreview=false
        fun cancelled(){check(!ExportCoordinator.cancelled.get()){"書き出しをキャンセルしました"}}
        try {
            cancelled();ExportCoordinator.unloadPerformance()
            if(r.format.bits==0){val uri=checkNotNull(r.destination);ExportCoordinator.report(id,30,"MIDI保存",0f,.8f);context.contentResolver.openOutputStream(uri,"wt")?.use {MidiFileCodec.write(r.song,it,if(r.format==ExportFormat.MIDI0)0 else 1)} ?: error("保存先を開けません");return null}
            val seconds=r.song.duration/1000000.0+r.tail
            val pcm=seconds*r.rate*2*(r.format.bits/8);val needed=if(r.format.video)pcm+seconds*(r.video.bitrate+r.bitrate*1000)/8 else pcm*(if(r.format.compressed)2 else 1)
            require(context.cacheDir.usableSpace>needed+128*1048576){"書き出し用の空き容量が不足しています"}
            NativeAudio.exportBegin(r.memoryMiB,r.rate)
            r.regions.forEach {cancelled();NativeAudio.region(it.path,it.ints,it.floats)};r.curves.forEach { (g,c)->NativeAudio.velocityCurve(g,c) };r.effects.forEach {cancelled();NativeAudio.effect(it.type,it.params,it.ir)}
            NativeAudio.exportControl(4,0,0,r.volume);NativeAudio.exportControl(8,r.voices,0,0f);r.groups.forEachIndexed {i,g->NativeAudio.exportControl(6,i,0,g)};r.master.forEach {NativeAudio.exportControl(9,0,0,it)}
            val messages=r.song.messages;val times=LongArray(messages.size){messages[it].micros};val data=IntArray(messages.size*3);messages.forEachIndexed {i,e->data[i*3]=e.status;data[i*3+1]=e.a;data[i*3+2]=e.b}
            val pcmWeight=if(r.format.compressed).35f else .95f
            ExportCoordinator.native(id,10,"音声生成",0f,pcmWeight){NativeAudio.exportWav(temp.path,times,data,r.song.duration,r.format.bits,r.tail)}
            var rendered=temp;cancelled()
            if(r.format.compressed){
                if(r.format.video){VideoExporter.encode(temp,encoded,r.song,r.format==ExportFormat.MOV,r.video,r.bitrate,r.labels,r.viewport,r.lookAhead,ExportCoordinator.cancelled){p->
                    if(p<.15f)ExportCoordinator.report(id,20,"AAC生成",p/.15f,.35f+.6f*p)else ExportCoordinator.report(id,21,"映像生成",(p-.15f)/.85f,.35f+.6f*p)
                }}else if(r.format==ExportFormat.M4A)AacExporter.encode(temp,encoded,r.bitrate,ExportCoordinator.cancelled){ExportCoordinator.report(id,20,"AAC生成",it,.35f+.6f*it)}
                else ExportCoordinator.native(id,20,"${r.format.label}生成",.35f,.6f){NativeAudio.encodeCompressed(temp.path,encoded.path,if(r.format==ExportFormat.FLAC)1 else 2,r.bitrate)}
                rendered=encoded
            }
            cancelled()
            if(r.preview){keepPreview=true;return rendered}
            ExportCoordinator.report(id,30,"保存先へコピー",0f,.95f);val uri=checkNotNull(r.destination)
            context.contentResolver.openOutputStream(uri,"wt")?.use {out->rendered.inputStream().use {input->val buffer=ByteArray(65536);var copied=0L;val size=rendered.length().coerceAtLeast(1)
                while(true){cancelled();val n=input.read(buffer);if(n<0)break;out.write(buffer,0,n);copied+=n;val fraction=copied.toFloat()/size;ExportCoordinator.report(id,30,"保存先へコピー",fraction,.95f+.04f*fraction)}
            }} ?: error("保存先を開けません")
            return null
        }catch(e: Throwable){NativeAudio.abort();r.destination?.let {runCatching {android.provider.DocumentsContract.deleteDocument(context.contentResolver,it)}};throw e}
        finally{if(!keepPreview || encoded!=temp)temp.delete();if(!keepPreview && encoded!=temp)encoded.delete()}
    }
}

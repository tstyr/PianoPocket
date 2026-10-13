package dev.pianopocket.video

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dev.pianopocket.audio.NativeAudio
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** Process ownership outlives an Activity/ViewModel. All native mutations use AudioWorkQueue. */
object ExportCoordinator {
    private val mutable=MutableStateFlow(ExportStatus());val status=mutable.asStateFlow()
    val cancelled=AtomicBoolean(false)
    @Volatile private var pending: ExportRequest?=null
    private val estimate=ExportEstimate()
    private data class NativePhase(val id: Long,val step: Int,val name: String,val base: Float,val weight: Float)
    @Volatile private var nativePhase: NativePhase?=null
    val running: Boolean get()=status.value.running
    @Volatile var performanceUnloaded=false;private set
    fun unloadPerformance(){performanceUnloaded=true;NativeAudio.suspendPerformance()}
    fun performanceRestored(){performanceUnloaded=false}
    @Synchronized fun submit(context: Context,request: ExportRequest) {
        check(!running){"別の書き出しが進行中です"}
        status.value.previewPath?.let {File(it).delete()}
        val id=System.nanoTime();cancelled.set(false);pending=request;mutable.value=ExportStatus(id,true,phase="準備",message=if(request.preview)"プレビューを生成中"else "${request.format.label}を書き出し中")
        try{ContextCompat.startForegroundService(context,Intent(context,ExportService::class.java))}catch(e: Exception){pending=null;mutable.value=mutable.value.copy(running=false,message="書き出しを開始できません: ${e.message}");request.destination?.let {runCatching {android.provider.DocumentsContract.deleteDocument(context.contentResolver,it)}};throw e}
    }
    @Synchronized fun takeRequest(): Pair<Long,ExportRequest>? {val request=pending ?: return null;pending=null;return status.value.id to request}
    fun cancel(){cancelled.set(true);NativeAudio.cancelExport()}
    @Synchronized fun report(id: Long,step: Int,name: String,fraction: Float,total: Float) {
        val current=status.value;if(!current.running || id!=current.id || step<current.step)return
        val eta=estimate.update(android.os.SystemClock.elapsedRealtime(),"$id:$step:$name",fraction.coerceIn(0f,1f))
        mutable.value=current.copy(step=step,phase=name,progress=maxOf(current.progress,total.coerceIn(0f,.99f)),etaSeconds=eta)
    }
    fun native(id: Long,step: Int,name: String,base: Float,weight: Float,action:()->Unit) {
        NativeAudio.resetExportProgress();val marker=NativePhase(id,step,name,base,weight);nativePhase=marker;report(id,step,name,0f,base)
        try{action();report(id,step,name,1f,base+weight)}finally{if(nativePhase===marker)nativePhase=null}
    }
    fun pollNative(){val marker=nativePhase ?: return;val fraction=NativeAudio.exportProgress();if(nativePhase===marker)report(marker.id,marker.step,marker.name,fraction,marker.base+marker.weight*fraction)}
    @Synchronized fun finish(id: Long,message: String,preview: File?=null,success: Boolean=false) {if(id!=status.value.id)return;nativePhase=null;pending=null;mutable.value=status.value.copy(running=false,progress=if(success)1f else status.value.progress,phase="",etaSeconds=null,message=message,previewPath=preview?.path)}
    @Synchronized fun clearPreview(){status.value.previewPath?.let {path->dev.pianopocket.audio.AudioWorkQueue.executor.execute {File(path).delete()}};mutable.value=status.value.copy(previewPath=null)}
}

package dev.pianopocket.download

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

data class DownloadStatus(val id: Long=0,val running: Boolean=false,val bytes: Long=0,val total: Long=0,val phase: String="",val message: String="",val success: Boolean=false)
object DownloadCoordinator {
    private val mutable=MutableStateFlow(DownloadStatus())
    val status=mutable.asStateFlow()
    private val active=AtomicBoolean();private val ids=AtomicLong()
    @Volatile var canceled=AtomicBoolean()
        private set
    fun start(context: Context): Boolean {
        if(!active.compareAndSet(false,true))return false
        val id=ids.incrementAndGet();canceled=AtomicBoolean();mutable.value=DownloadStatus(id=id,running=true,phase="準備",message="ダウンロードを準備しています")
        try{context.applicationContext.startForegroundService(Intent(context,DownloadService::class.java).putExtra("job",id))}
        catch(e:Throwable){finish(id,"ダウンロードを開始できません: ${e.message}",false)}
        return active.get()
    }
    fun cancel(context: Context){canceled.set(true);context.startService(Intent(context,DownloadService::class.java).setAction("cancel"))}
    fun report(id: Long,bytes: Long,total: Long,phase: String){if(mutable.value.id==id && active.get())mutable.value=mutable.value.copy(bytes=bytes,total=total,phase=phase,message=phase)}
    fun finish(id: Long,message: String,success: Boolean){if(mutable.value.id==id){active.set(false);mutable.value=mutable.value.copy(running=false,message=message,success=success)}}
}

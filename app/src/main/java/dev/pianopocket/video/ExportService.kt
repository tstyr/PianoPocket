package dev.pianopocket.video

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.content.ContextCompat
import dev.pianopocket.MainActivity
import dev.pianopocket.R
import dev.pianopocket.audio.AudioWorkQueue
import kotlinx.coroutines.*

class ExportService: Service() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var wake: PowerManager.WakeLock?=null;private var started=false;private var ownedId=0L
    private var progressJob: Job?=null;private var notificationJob: Job?=null
    private val notificationManager get()=getSystemService(NotificationManager::class.java)
    private fun canNotify()=Build.VERSION.SDK_INT<33 || ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED
    override fun onCreate(){super.onCreate();notificationManager.createNotificationChannel(NotificationChannel("export","書き出し",NotificationManager.IMPORTANCE_LOW))}
    private fun notification(status: ExportStatus,ongoing: Boolean): Notification {
        val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder=Notification.Builder(this,"export").setSmallIcon(R.drawable.ic_pocket).setContentTitle(if(ongoing)"PianoPocket · 書き出し"else "PianoPocket").setContentText(if(ongoing)"${status.phase} ${"%.0f".format(status.progress*100)}%"else status.message).setOnlyAlertOnce(true).setOngoing(ongoing).setContentIntent(open).setAutoCancel(!ongoing)
        if(ongoing){builder.setProgress(100,(status.progress*100).toInt(),status.progress==0f);val cancel=PendingIntent.getService(this,1,Intent(this,ExportService::class.java).setAction("cancel"),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE);builder.addAction(Notification.Action.Builder(null,"キャンセル",cancel).build())}
        return builder.build()
    }
    override fun onStartCommand(intent: Intent?,flags: Int,startId: Int): Int {
        if(intent?.action=="cancel"){ExportCoordinator.cancel();if(!ExportCoordinator.running)stopSelf();return START_NOT_STICKY}
        if(started)return START_NOT_STICKY
        val job=ExportCoordinator.takeRequest();if(job==null){stopSelf();return START_NOT_STICKY}
        val (id,request)=job;started=true;ownedId=id
        try {
            val type=if(Build.VERSION.SDK_INT>=35)ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING else ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            startForeground(71,notification(ExportCoordinator.status.value,true),type)
            ExportCoordinator.report(id,0,"音源準備",0f,0f)
            wake=getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"PianoPocket:Export").also {it.acquire(6*60*60*1000L)}
            progressJob=scope.launch {while(isActive && ExportCoordinator.running){ExportCoordinator.pollNative();delay(250)}}
            notificationJob=scope.launch {while(isActive && ExportCoordinator.running){delay(1000);if(canNotify())runCatching {notificationManager.notify(71,notification(ExportCoordinator.status.value,true))}}}
            AudioWorkQueue.executor.execute {
                var preview: java.io.File?=null;var success=false
                val message=try{preview=ExportRenderer.render(applicationContext,id,request);success=true;if(preview!=null)"プレビューを生成しました"else "保存しました: ${request.format.label}"}catch(e: Throwable){e.message ?: "書き出しに失敗しました"}
                Handler(Looper.getMainLooper()).post {
                    progressJob?.cancel();notificationJob?.cancel();wake?.let {if(it.isHeld)it.release()};wake=null
                    stopForeground(STOP_FOREGROUND_REMOVE);started=false;ExportCoordinator.finish(id,message,preview,success)
                    if(canNotify())runCatching {notificationManager.notify(72,notification(ExportCoordinator.status.value,false))}
                    stopSelfResult(startId)
                }
            }
        }catch(e: Exception){request.destination?.let {runCatching {android.provider.DocumentsContract.deleteDocument(contentResolver,it)}};ExportCoordinator.finish(id,"書き出しを開始できません: ${e.message}");stopSelf()}
        return START_NOT_STICKY
    }
    override fun onTimeout(startId: Int,fgsType: Int){ExportCoordinator.cancel();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()}
    override fun onDestroy(){scope.cancel();if(ExportCoordinator.running && ExportCoordinator.status.value.id==ownedId)ExportCoordinator.cancel();wake?.let {if(it.isHeld)it.release()};wake=null;super.onDestroy()}
    override fun onBind(intent: Intent?)=null
}

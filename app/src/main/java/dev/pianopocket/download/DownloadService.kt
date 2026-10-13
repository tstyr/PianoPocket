package dev.pianopocket.download

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
import dev.pianopocket.library.LibraryRepository
import kotlinx.coroutines.*
import java.io.File
import java.io.InterruptedIOException
import java.io.SequenceInputStream
import java.util.Collections
import java.util.concurrent.Executors

class DownloadService:Service() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val io=Executors.newSingleThreadExecutor {job->Thread(job,"PianoPocketDownload")}
    private var id=0L;private var started=false;private var wake:PowerManager.WakeLock?=null
    private var notificationJob: Job?=null
    private var cancellation=java.util.concurrent.atomic.AtomicBoolean()
    private val network=HttpsChunkSource()
    private val manager get()=getSystemService(NotificationManager::class.java)
    private fun canNotify()=Build.VERSION.SDK_INT<33 || ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED
    override fun onCreate(){super.onCreate();manager.createNotificationChannel(NotificationChannel("downloads","音源のダウンロード",NotificationManager.IMPORTANCE_LOW))}
    private fun notification(ongoing: Boolean):Notification {
        val status=DownloadCoordinator.status.value
        val open=PendingIntent.getActivity(this,3,Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder=Notification.Builder(this,"downloads").setSmallIcon(R.drawable.ic_pocket).setContentTitle("PianoPocket · 音源ダウンロード").setContentText(if(ongoing)"${status.phase} · ${status.bytes/1048576}/${status.total/1048576} MiB"else status.message).setOnlyAlertOnce(true).setOngoing(ongoing).setAutoCancel(!ongoing).setContentIntent(open)
        if(ongoing){builder.setProgress(100,if(status.total>0)(status.bytes*100/status.total).toInt()else 0,status.total==0L);val cancel=PendingIntent.getService(this,4,Intent(this,DownloadService::class.java).setAction("cancel"),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE);builder.addAction(Notification.Action.Builder(null,"キャンセル",cancel).build())}
        return builder.build()
    }
    override fun onStartCommand(intent: Intent?,flags: Int,startId: Int):Int {
        if(intent?.action=="cancel"){DownloadCoordinator.canceled.set(true);scope.launch(Dispatchers.IO){network.abort()};if(!started)stopSelf();return START_NOT_STICKY}
        val job=intent?.getLongExtra("job",0) ?: 0
        if(started || job==0L || job!=DownloadCoordinator.status.value.id){if(!started)stopSelf();return START_NOT_STICKY}
        started=true;id=job;val token=DownloadCoordinator.canceled;cancellation=token
        try {
            startForeground(81,notification(true),ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            wake=getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"PianoPocket:Download").also {it.acquire(2*60*60*1000L)}
            notificationJob=scope.launch {while(isActive){delay(1000);if(canNotify())runCatching {manager.notify(81,notification(true))}}}
            io.execute {
                var success=false
                val message=try {
                    val pack=DownloadCatalog.load(applicationContext)
                    val existing=AudioWorkQueue.executor.submit<dev.pianopocket.library.LibraryEntry?> {LibraryRepository(applicationContext).findDownloaded(pack.id)}.get()
                    if(existing!=null){success=true;"${pack.name} は追加済みです"}else{
                        val folder=File(cacheDir,"sample-downloads/${pack.id}").apply {mkdirs()}
                        val cached=folder.listFiles().orEmpty().sumOf {it.length()}
                        require(folder.usableSpace+cached>=pack.requiredBytes) {"空き容量が不足しています。約${pack.requiredBytes/1048576} MiB必要です"}
                        var last=0L
                        val files=VerifiedDownloader(network).receive(pack,folder,{token.get()}) {bytes->val now=SystemClock.elapsedRealtime();if(now-last>250 || bytes==pack.bytes){DownloadCoordinator.report(job,bytes,pack.bytes,"ダウンロード");last=now}}
                        checkCanceled(token);DownloadCoordinator.report(job,pack.bytes,pack.bytes,"検証済み · ライブラリへ追加")
                        val importStarted=java.util.concurrent.atomic.AtomicBoolean()
                        val imported=AudioWorkQueue.executor.submit {
                            importStarted.set(true)
                            checkCanceled(token)
                            SequenceInputStream(Collections.enumeration(files.map {it.inputStream()})).use {input->
                                LibraryRepository(applicationContext).importDownloaded(input,pack.id,pack.expandedBytes) {checkCanceled(token)}
                            }
                        }
                        while(!imported.isDone){if(token.get() && !importStarted.get())imported.cancel(false);Thread.sleep(100)}
                        imported.get();success=true;folder.deleteRecursively();"${pack.name} を音源ライブラリへ追加しました"
                    }
                }catch(e:Throwable){if(token.get())"ダウンロードをキャンセルしました。再試行で続きから取得できます"else "音源の取得に失敗しました: ${e.cause?.message ?: e.message}"}
                Handler(Looper.getMainLooper()).post {
                    notificationJob?.cancel();wake?.let {if(it.isHeld)it.release()};wake=null
                    stopForeground(STOP_FOREGROUND_REMOVE);started=false;DownloadCoordinator.finish(job,message,success)
                    if(canNotify())runCatching {manager.notify(82,notification(false))};stopSelfResult(startId)
                }
            }
        }catch(e:Throwable){notificationJob?.cancel();wake?.let {if(it.isHeld)it.release()};wake=null;started=false;DownloadCoordinator.finish(job,"ダウンロードを開始できません: ${e.message}",false);stopSelf()}
        return START_NOT_STICKY
    }
    private fun checkCanceled(token: java.util.concurrent.atomic.AtomicBoolean){if(token.get())throw InterruptedIOException("キャンセルしました")}
    override fun onTimeout(startId: Int,fgsType: Int){DownloadCoordinator.canceled.set(true);scope.launch(Dispatchers.IO){network.abort()};stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()}
    override fun onDestroy(){scope.cancel();if(started)cancellation.set(true);wake?.let {if(it.isHeld)it.release()};wake=null;io.shutdown();super.onDestroy()}
    override fun onBind(intent: Intent?)=null
}

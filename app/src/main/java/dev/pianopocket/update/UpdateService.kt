package dev.pianopocket.update

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*
import dev.pianopocket.MainActivity
import dev.pianopocket.R
import kotlinx.coroutines.*

class UpdateService: Service() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var started=false
    private var notices: Job?=null
    private var wake: PowerManager.WakeLock?=null
    private val manager get()=getSystemService(NotificationManager::class.java)
    override fun onCreate(){super.onCreate();manager.createNotificationChannel(NotificationChannel("updates","アプリの更新",NotificationManager.IMPORTANCE_LOW))}
    private fun notification(ongoing: Boolean): Notification {
        val status=UpdateCoordinator.status.value
        val open=PendingIntent.getActivity(this,101,Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n=Notification.Builder(this,"updates").setSmallIcon(R.drawable.ic_pocket).setContentTitle("PianoPocket · アプリの更新")
            .setContentText(if(ongoing)status.phase else status.message).setContentIntent(open).setOnlyAlertOnce(true).setOngoing(ongoing).setAutoCancel(!ongoing)
        if(ongoing){val bytes=status.info?.bytes ?: 1;n.setProgress(100,(status.bytes*100/bytes).toInt().coerceIn(0,100),false)
            val cancel=PendingIntent.getService(this,102,Intent(this,UpdateService::class.java).setAction("cancel"),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            n.addAction(Notification.Action.Builder(null,"キャンセル",cancel).build())
        }
        return n.build()
    }
    override fun onStartCommand(intent: Intent?,flags: Int,startId: Int): Int {
        if(intent?.action=="cancel"){UpdateCoordinator.cancel();if(!started)stopSelf();return START_NOT_STICKY}
        if(started || !UpdateCoordinator.status.value.running){if(!started)stopSelf();return START_NOT_STICKY}
        started=true
        try {
            startForeground(101,notification(true),ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            wake=getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"PianoPocket:Update").also {it.acquire(30*60*1000L)}
            notices=scope.launch {while(isActive){delay(1000);runCatching {manager.notify(101,notification(true))}}}
            scope.launch {
                var ready=false
                val message=withContext(Dispatchers.IO){try {UpdateCoordinator.receive(applicationContext);ready=true;"更新APKを取得しました。アプリからインストールできます"}
                    catch(e: Exception){if(UpdateCoordinator.cancelled.get())"キャンセルしました。再試行で再開できます"else "APKを取得できません: ${e.message}"}}
                notices?.cancel();wake?.let {if(it.isHeld)it.release()};wake=null
                started=false;UpdateCoordinator.finish(message,ready);stopForeground(STOP_FOREGROUND_REMOVE);runCatching {manager.notify(102,notification(false))};stopSelfResult(startId)
            }
        }catch(e: Exception){started=false;UpdateCoordinator.finish("更新を開始できません: ${e.message}",false);stopSelf()}
        return START_NOT_STICKY
    }
    override fun onTimeout(startId: Int,fgsType: Int){UpdateCoordinator.cancel();UpdateCoordinator.finish("更新の取得が時間切れになりました。再試行できます",false);stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()}
    override fun onDestroy(){scope.cancel();notices?.cancel();wake?.let {if(it.isHeld)it.release()};wake=null;if(started){UpdateCoordinator.cancel();UpdateCoordinator.finish("ダウンロードが中断されました。再試行で再開できます",false)};super.onDestroy()}
    override fun onBind(intent: Intent?)=null
}

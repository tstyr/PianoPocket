package dev.pianopocket.playback

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.media.*
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.*
import androidx.core.content.ContextCompat
import dev.pianopocket.MainActivity
import dev.pianopocket.R
import dev.pianopocket.ui.PocketViewModel
import kotlinx.coroutines.*

/** Native rendering and MIDI sending remain on their existing threads. */
class PlaybackService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var model: PocketViewModel? = null
    private var wake: PowerManager.WakeLock? = null
    private var focus: AudioFocusRequest? = null
    private var media: MediaSession? = null
    private var receiverRegistered = false
    private val audio get() = getSystemService(AudioManager::class.java)
    private val notifications get() = getSystemService(NotificationManager::class.java)
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                model?.backgroundInterrupted("イヤホンが外れたため演奏を停止しました")
                stopSelf()
            }
        }
    }
    private fun action(name: String) = PendingIntent.getService(this, name.hashCode(),
        Intent(this, PlaybackService::class.java).setAction(name), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun notification(): Notification {
        val s = model?.state?.value
        val open = PendingIntent.getActivity(this, 80,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = Notification.Builder(this, "playback").setSmallIcon(R.drawable.ic_pocket)
            .setContentTitle(if (s?.sequence?.recording == true) "PianoPocket · 録音中" else "PianoPocket")
            .setContentText(if (s?.sequence?.song != null) s.sequence.title else s?.preset?.name ?: "キーボード演奏")
            .setContentIntent(open).setOnlyAlertOnce(true).setOngoing(true)
        if (s?.sequence?.song != null && !s.sequence.recording) builder.addAction(Notification.Action.Builder(null,
            if (s.sequence.playing) "一時停止" else "再生", action(if (s.sequence.playing) "pause" else "play")).build())
        builder.addAction(Notification.Action.Builder(null, "終了", action("stop")).build())
        media?.let { builder.setStyle(Notification.MediaStyle().setMediaSession(it.sessionToken)) }
        return builder.build()
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "stop") { model?.backgroundPlayback(false); stopSelf(); return START_NOT_STICKY }
        model?.let { existing ->
            if (intent?.action == "pause") existing.panic()
            if (intent?.action == "play" && !existing.state.value.sequence.playing) existing.play()
            return START_NOT_STICKY
        }
        val owned = PerformanceSession.current()
        if (owned == null || !owned.state.value.backgroundPlayback || owned.state.value.sequence.exporting || owned.state.value.sequence.previewPath != null) {
            stopSelf(); return START_NOT_STICKY
        }
        model = owned
        try {
            notifications.createNotificationChannel(NotificationChannel("playback", "演奏", NotificationManager.IMPORTANCE_LOW))
            startForeground(80, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            media = MediaSession(this, "PianoPocket").apply {
                setCallback(object : MediaSession.Callback() {
                    override fun onPlay() { if (!owned.state.value.sequence.playing) owned.play() }
                    override fun onPause() { owned.panic() }
                    override fun onStop() { owned.backgroundPlayback(false); stopSelf() }
                })
                isActive = true
            }
            focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setOnAudioFocusChangeListener({ change ->
                    when (change) {
                        AudioManager.AUDIOFOCUS_GAIN -> owned.audioFocus(true)
                        AudioManager.AUDIOFOCUS_LOSS -> { owned.backgroundInterrupted("他のアプリが音を使用するため演奏を停止しました"); stopSelf() }
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> owned.audioFocus(false)
                    }
                }, Handler(Looper.getMainLooper())).build()
            if (audio.requestAudioFocus(checkNotNull(focus)) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                owned.backgroundInterrupted("音声を使用できないため演奏を停止しました"); stopSelf(); return START_NOT_STICKY
            }
            PerformanceSession.serviceStarted(); owned.backgroundService(true)
            ContextCompat.registerReceiver(this, noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)
            receiverRegistered = true
            wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PianoPocket:Playback")
            scope.launch {
                var renewed = 0L
                while (isActive) {
                    val s = owned.state.value
                    if (!s.backgroundPlayback || s.sequence.exporting || s.sequence.previewPath != null) { stopSelf(); break }
                    if (owned.isForeground || !s.ready) { wake?.let { if (it.isHeld) it.release() }; renewed = 0L }
                    else if (SystemClock.elapsedRealtime() - renewed >= 30 * 60 * 1000L || wake?.isHeld != true) {
                        wake?.let { if (it.isHeld) it.release(); it.acquire(60 * 60 * 1000L) }; renewed = SystemClock.elapsedRealtime()
                    }
                    val state = if (s.sequence.playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
                    media?.setPlaybackState(PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_STOP)
                        .setState(state, s.sequence.position / 1000, s.sequence.speed).build())
                    runCatching { notifications.notify(80, notification()) }
                    delay(1000)
                }
            }
        } catch (error: Exception) { owned.backgroundStartFailed(); stopSelf() }
        return START_NOT_STICKY
    }
    override fun onDestroy() {
        scope.cancel()
        if (receiverRegistered) unregisterReceiver(noisy)
        focus?.let { audio.abandonAudioFocusRequest(it) }; focus = null
        wake?.let { if (it.isHeld) it.release() }; wake = null
        media?.release(); media = null
        model?.backgroundService(false); model = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        PerformanceSession.serviceStopped()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?) = null
}

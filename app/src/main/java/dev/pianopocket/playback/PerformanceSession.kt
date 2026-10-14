package dev.pianopocket.playback

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import dev.pianopocket.ui.PocketViewModel

/** The activity and foreground service share one MIDI controller and one native engine. */
object PerformanceSession {
    private var store: ViewModelStore? = null
    private var model: PocketViewModel? = null
    private var activities = 0
    private var visibleActivities = 0
    private var service = false
    private var starting = false
    fun activityCreated(app: Application): PocketViewModel {
        activities++
        if (model == null) {
            val owned = ViewModelStore().also { store = it }
            val owner = object : ViewModelStoreOwner { override val viewModelStore = owned }
            model = ViewModelProvider(owner, ViewModelProvider.AndroidViewModelFactory.getInstance(app))[PocketViewModel::class.java]
        }
        return checkNotNull(model)
    }
    fun activityStarted() {
        visibleActivities++; model?.foreground(true)
    }
    fun activityStopped(changingConfiguration: Boolean) {
        visibleActivities = (visibleActivities - 1).coerceAtLeast(0)
        if (visibleActivities == 0 && !changingConfiguration) model?.foreground(false)
    }
    fun activityDestroyed(changingConfiguration: Boolean) {
        activities = (activities - 1).coerceAtLeast(0)
        if (!changingConfiguration) releaseIfUnused()
    }
    fun current(): PocketViewModel? = model
    fun serviceStarted(): PocketViewModel? { starting = false; service = model != null; return model }
    fun serviceStopped() {
        service = false; starting = false; releaseIfUnused()
        val owned = model ?: return
        val state = owned.state.value
        // An off/on toggle may have occurred while the old service was shutting down.
        if (owned.isForeground && state.backgroundPlayback && state.ready && !state.sequence.exporting && state.sequence.previewPath == null)
            start(owned.getApplication<Application>())
    }
    private fun releaseIfUnused() {
        if (activities == 0 && !service && !starting) { store?.clear(); store = null; model = null; visibleActivities = 0 }
    }
    fun start(context: Context) {
        if (service || starting) return
        starting = true
        try { ContextCompat.startForegroundService(context, Intent(context, PlaybackService::class.java)) }
        catch (error: Exception) { starting = false; model?.backgroundStartFailed(); releaseIfUnused() }
    }
    fun stop(context: Context) { starting = false; val command = Intent(context, PlaybackService::class.java); context.stopService(command) }
}

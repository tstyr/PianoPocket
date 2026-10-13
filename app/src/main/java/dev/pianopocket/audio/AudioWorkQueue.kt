package dev.pianopocket.audio

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/** Process-owned JNI transaction actor; shared by the UI and the export service. */
object AudioWorkQueue {
    val executor=Executors.newSingleThreadExecutor { work->Thread(work,"PianoPocketWorker") }
    private val owners=AtomicLong()
    fun newUiOwner(): Long=owners.incrementAndGet()
    fun isCurrentUi(owner: Long)=owners.get()==owner
}

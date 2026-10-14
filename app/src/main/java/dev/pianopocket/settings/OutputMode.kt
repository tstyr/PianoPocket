package dev.pianopocket.settings

/** Connection loss must never leave a new session silently muted. */
enum class OutputMode(val label: String) {
    INTERNAL("内蔵音源"), EXTERNAL("外部MIDI"), BOTH("両方");
    fun localVolume(savedVolume: Float, connected: Boolean): Float =
        if(this == EXTERNAL && connected) 0f else savedVolume
    fun sendsMidi(connected: Boolean): Boolean = connected && this != INTERNAL
}

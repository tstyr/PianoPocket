package dev.pianopocket.ui

class FrameGate(private val target: Int) {
    private var previous=0L
    fun due(time: Long): Boolean {
        if(time<=previous)return false
        if(previous!=0L && time-previous+500000L<1000000000L/target)return false
        previous=time;return true
    }
}

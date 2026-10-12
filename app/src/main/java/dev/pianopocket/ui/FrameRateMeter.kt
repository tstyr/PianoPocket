package dev.pianopocket.ui

/** Counts delivered window draw frames, never substitutes the display's refresh-rate setting. */
class FrameRateMeter {
    private var first=0L;private var previous=0L;private var intervals=0
    fun frame(time: Long): Double? {
        if(time<=0 || time<=previous)return null
        if(first==0L || time-previous>2000000000L){first=time;previous=time;intervals=0;return null}
        previous=time;intervals++
        if(time-first<1000000000L)return null
        val fps=intervals*1000000000.0/(time-first);first=time;intervals=0;return fps
    }
}

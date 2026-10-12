package dev.pianopocket.sequencer

/** Shared, bounded horizontal view of the real A0..C8 keyboard (52 white keys). */
data class KeyboardViewport(val zoom: Float=1f,val start: Float=0f) {
    val maxStart: Float get()=1f-1f/zoom
    val full: Boolean get()=zoom<=1.0001f
    fun screen(world: Float): Float=(world-start)*zoom
    fun world(screen: Float): Float=start+screen/zoom
    fun transform(factor: Float,anchor: Float,pan: Float): KeyboardViewport {
        if(!factor.isFinite() || factor<=0 || !anchor.isFinite() || !pan.isFinite())return this
        val nextZoom=(zoom*factor).coerceIn(1f,6.5f)
        // detectTransformGestures supplies the previous centroid and its displacement.
        val nextStart=world(anchor)-(anchor+pan)/nextZoom
        return KeyboardViewport(nextZoom,nextStart.coerceIn(0f,1f-1f/nextZoom))
    }
    fun shiftOctave(direction: Int)=copy(start=(start+direction*7f/52f).coerceIn(0f,maxStart))
    companion object {
        fun middleTwoOctaves()=KeyboardViewport(52f/14f,23f/52f) // C4..B5
    }
}

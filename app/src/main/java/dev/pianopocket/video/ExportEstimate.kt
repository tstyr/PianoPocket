package dev.pianopocket.video

/** Estimate the current phase only. Unknown phases and stalled progress are not fake ETAs. */
class ExportEstimate {
    private var phase="";private val samples=ArrayDeque<Pair<Long,Float>>()
    @Synchronized fun update(nowMillis: Long,name: String,fraction: Float): Long? {
        if(!fraction.isFinite() || fraction !in 0f..1f)return null
        if(phase!=name || samples.lastOrNull()?.let {nowMillis<it.first || fraction<it.second}==true){phase=name;samples.clear()}
        samples+=nowMillis to fraction
        while(samples.size>30 || samples.size>2 && nowMillis-samples.first().first>15000)samples.removeFirst()
        val first=samples.first();val elapsed=nowMillis-first.first;val done=fraction-first.second
        if(elapsed<1000 || done<.02f || fraction>=1f)return null
        return (((1-fraction)*elapsed/done)/1000).toLong().coerceIn(0,86400)
    }
}

package dev.pianopocket.audio

/** Bounded diagnostic record. Collection never runs on the audio callback. */
class DiagnosticSession {
    data class Row(val elapsedMillis: Long,val mode: Int,val stats: List<Double>)
    private val rows=mutableListOf<Row>();private var started=0L;private var header=""
    var recording=false;private set
    @Synchronized fun start(now: Long,description: String){rows.clear();started=now;header=description;recording=true}
    @Synchronized fun stop(){recording=false}
    @Synchronized fun capture(now: Long,mode: Int,stats: List<Double>): Boolean {
        if(!recording)return false
        require(stats.size>=18)
        rows+=Row(((now-started)/1000000).coerceAtLeast(0),mode,stats.take(18))
        if(rows.size>=120 || now-started>=60000000000L)recording=false
        return recording
    }
    @Synchronized fun report(): String {
        fun delta(index: Int): String {
            if(rows.isEmpty() || rows.any { it.stats[index]<0 })return "unknown"
            var count=0L;var resets=0;rows.zipWithNext().forEach { (a,b)->val d=b.stats[index]-a.stats[index];if(d>=0)count+=d.toLong()else resets++ }
            return "$count (counter resets: $resets)"
        }
        return buildString {
            appendLine("PianoPocket audio diagnostic — snapshots, not end-to-end latency")
            appendLine(header);appendLine("rows=${rows.size}, recording=$recording")
            appendLine("XRun delta=${delta(3)}; cache misses delta=${delta(8)}; read errors delta=${delta(11)}; event drops delta=${delta(9)}; steals delta=${delta(15)}; cache request drops delta=${delta(16)}")
            appendLine("max observed callback load=${rows.maxOfOrNull { it.stats[5] } ?: 0.0}%, max observed voices=${rows.maxOfOrNull { it.stats[6] } ?: 0.0}, max observed limiter reduction=${rows.maxOfOrNull { it.stats[13] } ?: 0.0}dB")
            appendLine("No counters alone prove an audible-noise cause. Compare normal=0 / dry=1 / quiet 440Hz-left+660Hz-right tone=2 on the same output. Test tone bypasses samples, microphone gain and effects; Output volume and limiter remain active.")
            appendLine("ms,mode,rate,buffer,burst,xrun,latencyMs,loadPercent,voices,bytes,misses,eventDrops,disconnected,readErrors,preLimiterPeak,reductionDb,limiterDelayMs,steals,requestDrops,cachePages")
            rows.forEach { r->append(r.elapsedMillis);append(',');append(r.mode);r.stats.forEach { append(',');append(if(it.isFinite())it.toString()else "unknown") };appendLine() }
        }
    }
}

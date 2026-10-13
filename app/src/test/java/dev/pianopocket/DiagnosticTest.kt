package dev.pianopocket
import dev.pianopocket.audio.DiagnosticSession
import org.junit.Assert.*
import org.junit.Test
class DiagnosticTest {
    @Test fun recordsCounterResetsAndUnavailableXrunsWithoutInventingResults() {
        val s=DiagnosticSession();s.start(0,"device=test")
        fun stats(x: Double,m: Double)=MutableList(18){0.0}.apply { this[3]=x;this[8]=m;this[5]=60.0 }
        s.capture(0,0,stats(-1.0,8.0));s.capture(500000000,1,stats(-1.0,12.0));s.capture(1000000000,2,stats(-1.0,1.0))
        val report=s.report();assertTrue(report.contains("XRun delta=unknown"));assertTrue(report.contains("cache misses delta=4 (counter resets: 1)"));assertTrue(report.contains("device=test"));assertTrue(report.contains("max observed callback load=60.0"))
    }
    @Test fun boundsDurationAndSampleCountAndStartsFresh() {
        val s=DiagnosticSession();s.start(0,"");repeat(120){s.capture(it*1000000L,0,List(18){0.0})};assertFalse(s.recording)
        s.start(10,"");assertFalse(s.capture(60000000010,2,List(18){0.0}));assertTrue(s.report().contains("rows=1"))
    }
}

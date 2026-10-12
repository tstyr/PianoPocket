package dev.pianopocket
import dev.pianopocket.ui.FrameRateMeter
import org.junit.Assert.*
import org.junit.Test
class FrameRateTest {
    @Test fun reportsDeliveredFramesAt60And120WithoutAssumingTheScreenRate() {
        for(rate in listOf(30,60,120)){val meter=FrameRateMeter();var measured: Double?=null;for(i in 0..rate+1)meter.frame(1000000000L+i*1000000000L/rate)?.let { measured=it };assertNotNull(measured);assertEquals(rate.toDouble(),measured!!,.1)}
    }
    @Test fun pauseAndDuplicateCallbacksDoNotBecomeFalseFps() {
        val meter=FrameRateMeter();assertNull(meter.frame(1000000000));assertNull(meter.frame(1000000000));assertNull(meter.frame(900000000));assertNull(meter.frame(5000000000));var value:Double?=null;for(i in 1..61)meter.frame(5000000000L+i*1000000000L/60)?.let {value=it};assertEquals(60.0,value!!,.1)
    }
}

package dev.pianopocket.ui

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun FpsOverlay(window: Window?,modifier: Modifier=Modifier) {
    var value by remember { mutableStateOf("— FPS") }
    DisposableEffect(window) {
        if(window==null)return@DisposableEffect onDispose {}
        val thread=HandlerThread("PocketFrameMetrics").apply { start() }
        val main=Handler(Looper.getMainLooper());val alive=AtomicBoolean(true);val meter=FrameRateMeter()
        val listener=Window.OnFrameMetricsAvailableListener { _,metrics,_->
            meter.frame(metrics.getMetric(FrameMetrics.VSYNC_TIMESTAMP))?.let { fps->
                main.post { if(alive.get())value="%.0f FPS".format(fps) }
            }
        }
        window.addOnFrameMetricsAvailableListener(listener,Handler(thread.looper))
        onDispose { alive.set(false);window.removeOnFrameMetricsAvailableListener(listener);thread.quitSafely() }
    }
    Text(value,modifier.background(Color(0xff111821)).padding(horizontal=4.dp,vertical=1.dp),color=Color.White,fontSize=10.sp)
}

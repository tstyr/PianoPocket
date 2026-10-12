package dev.pianopocket.ui

import android.app.Activity
import android.os.Build
import androidx.compose.runtime.*

@Composable
fun PerformanceFrameClock(activity: Activity?,model: PocketViewModel,target: Int) {
    DisposableEffect(activity,target) {
        model.displayFramesActive=true
        val window=activity?.window
        val oldRate=window?.attributes?.preferredRefreshRate ?: 0f
        val oldMode=window?.attributes?.preferredDisplayModeId ?: 0
        if(window!=null) {
            val display=if(Build.VERSION.SDK_INT>=30)activity.display else activity.windowManager.defaultDisplay
            val current=display?.mode
            val mode=display?.supportedModes?.filter { it.physicalWidth==current?.physicalWidth && it.physicalHeight==current.physicalHeight && it.refreshRate<=target+.5f }?.maxByOrNull { it.refreshRate }
            window.attributes=window.attributes.apply { preferredRefreshRate=target.toFloat();preferredDisplayModeId=mode?.modeId ?: 0 }
        }
        onDispose {model.displayFramesActive=false;if(window!=null)window.attributes=window.attributes.apply {preferredRefreshRate=oldRate;preferredDisplayModeId=oldMode} }
    }
    LaunchedEffect(model,target) {
        val gate=FrameGate(target)
        while(true)withFrameNanos { time->if(gate.due(time))model.displayFrame() }
    }
}

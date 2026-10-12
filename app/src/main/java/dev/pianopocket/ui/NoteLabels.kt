package dev.pianopocket.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val labelTypeface by lazy { Typeface.create("sans-serif",Typeface.BOLD) }

// Keep every name on one baseline. Condense wide names horizontally, never shorten ファ vertically.
internal fun DrawScope.drawNoteLabel(note: Int,x: Float,top: Float,width: Float,height: Float,paint: Paint,black: Boolean=false,roll: Boolean=false) {
    val text=noteLabelLines(note).single()
    val border=1.dp.toPx()
    val available=(width-2*border-2).coerceAtLeast(1f)
    paint.typeface=labelTypeface;paint.textAlign=Paint.Align.CENTER
    paint.alpha=255;paint.textScaleX=1f
    paint.textSize=minOf((if(black)13.sp else 16.sp).toPx(),available*.98f,if(roll)(height-4).coerceAtLeast(1f)/1.2f else Float.MAX_VALUE)
    paint.textScaleX=minOf(1f,available/paint.measureText(text).coerceAtLeast(1f))
    val metrics=paint.fontMetrics
    val baseline=top+height-(if(roll)3.dp else 7.dp).toPx()-metrics.descent
    drawIntoCanvas { canvas->
        paint.style=Paint.Style.STROKE;paint.strokeWidth=border;paint.strokeJoin=Paint.Join.ROUND;paint.color=0xff111821.toInt()
        canvas.nativeCanvas.drawText(text,x,baseline,paint)
        paint.style=Paint.Style.FILL;paint.color=0xffffffff.toInt()
        canvas.nativeCanvas.drawText(text,x,baseline,paint)
    }
    paint.textScaleX=1f
}

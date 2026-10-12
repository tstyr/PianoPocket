package dev.pianopocket.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.sp

private val labelTypeface by lazy { Typeface.create("sans-serif",Typeface.NORMAL) }

// Stack multi-character names so ファ / シャープ stay legible on a full 88-key keyboard.
internal fun DrawScope.drawNoteLabel(note: Int,x: Float,top: Float,width: Float,height: Float,paint: Paint,color: Int,black: Boolean=false,fromTop: Boolean=false) {
    val lines=noteLabelLines(note)
    paint.typeface=labelTypeface
    paint.color=color
    paint.textSize=minOf((if(black)11.sp else 13.sp).toPx(),(width-2).coerceAtLeast(1f)*.9f,if(fromTop)(height-6).coerceAtLeast(1f)/lines.size/1.2f else Float.MAX_VALUE)
    val metrics=paint.fontMetrics;val lineHeight=(metrics.descent-metrics.ascent)*.94f
    val block=lineHeight*lines.size
    val first=if(fromTop)top-metrics.ascent+3 else top+height-5-block-metrics.ascent
    drawIntoCanvas { canvas->lines.forEachIndexed { i,line->canvas.nativeCanvas.drawText(line,x,first+i*lineHeight,paint) } }
}

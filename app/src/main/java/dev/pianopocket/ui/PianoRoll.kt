package dev.pianopocket.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.pianopocket.sequencer.*

private val noteColors=listOf(Color(0xff86bca7),Color(0xff84a8d0),Color(0xffc5a4cd),Color(0xffcbb579),Color(0xffc78d8b))
@Composable
fun PianoRoll(state: PocketState,model: PocketViewModel,modifier: Modifier=Modifier) {
    val seq=state.sequence
    val layout=remember {KeyLayout(21,108)}
    val viewport=seq.viewport
    val paint=remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign=Paint.Align.CENTER;color=0xff16231c.toInt() } }
    val liveMode=seq.recording || !seq.playing && seq.live.notes.isNotEmpty()
    val position=if(liveMode)seq.live.now else seq.position
    val span=(seq.lookAhead*1000000).toLong()
    Box(modifier) {
        Canvas(Modifier.fillMaxSize().background(Color(0xff1d2732)).semantics { contentDescription="ピアノロール。2指のピンチで横幅を拡大・縮小。横ドラッグで音域を移動。" }.pointerInput(model) {
            detectTransformGestures { centroid,pan,zoom,_ ->
                if(size.width>0 && (zoom!=1f || pan.x!=0f))model.zoomKeyboard(zoom,centroid.x/size.width,pan.x/size.width)
            }
        }) {
            layout.shapes.filter { !it.black }.forEach { key -> val x=viewport.screen(key.left)*size.width;if(x in 0f..size.width)drawLine(Color(0xff2b3540),Offset(x,0f),Offset(x,size.height)) }
            fun y(t: Long):Float = if(liveMode)size.height-((position-t).toDouble()/span*size.height).toFloat() else size.height-((t-position).toDouble()/span*size.height).toFloat()
            val begin=if(liveMode)position-span else position
            val end=if(liveMode)position else position+span
            val seconds=(begin/1000000)..(end/1000000)
            seconds.forEach { s->val yy=y(s*1000000);drawLine(Color(0xff2a3540),Offset(0f,yy),Offset(size.width,yy),1f) }
            clipRect {
                fun note(n: RollNote,live: Boolean=false){
                    val key=layout.shapes.firstOrNull { it.note==n.key } ?: return
                    val x=viewport.screen(key.left)*size.width+1;val w=(key.width*viewport.zoom*size.width-2).coerceAtLeast(2f)
                    if(x+w<0 || x>=size.width)return
                    val color=if(live)noteColors[0]else noteColors[n.track%noteColors.size]
                    val start=y(n.start);val stop=y(n.keyEnd)
                    val top=minOf(start,stop);val height=kotlin.math.abs(start-stop).coerceAtLeast(3f)
                    drawRoundRect(color,Offset(x,top),Size(w,height),CornerRadius(minOf(4.dp.toPx(),w*.15f)))
                    // Keep the caption attached to the actual note, including below the strike line.
                    // clipRect removes it naturally; never clamp its baseline to the screen edge.
                    if(seq.noteLabels && height>12.dp.toPx()){
                        drawNoteLabel(n.key,x+w/2,top,w,height,paint,roll=true)
                    }
                }
                if(liveMode)seq.live.notes.filter { it.keyEnd>=begin }.forEach { note(it,true) }
                else {seq.song?.keyIndex?.visible(begin,end){note(it)};seq.live.notes.filter { it.keyEnd>=seq.live.now }.forEach { n->note(n.copy(start=position,keyEnd=position+100000,end=position+100000),true) }}
            }
            drawLine(Color(0xff86bca7),Offset(0f,size.height-1),Offset(size.width,size.height-1),2.dp.toPx())
        }
    }
}

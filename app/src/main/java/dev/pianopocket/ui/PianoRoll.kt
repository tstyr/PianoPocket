package dev.pianopocket.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.unit.dp
import dev.pianopocket.sequencer.*

private val noteColors=listOf(Color(0xff86bca7),Color(0xff84a8d0),Color(0xffc5a4cd),Color(0xffcbb579),Color(0xffc78d8b))
@Composable
fun PianoRoll(state: PocketState,modifier: Modifier=Modifier) {
    val seq=state.sequence
    val low=if(seq.fullKeyboard)21 else (seq.octave+1)*12
    val layout=remember(low,seq.fullKeyboard){KeyLayout(low,if(seq.fullKeyboard)108 else low+23)}
    val paint=remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign=Paint.Align.CENTER;color=0xff16231c.toInt() } }
    val liveMode=seq.recording || !seq.playing && seq.live.notes.isNotEmpty()
    val position=if(liveMode)seq.live.now else seq.position
    val span=(seq.lookAhead*1000000).toLong()
    Column(modifier) {
        Text(if(liveMode)"リアルタイム入力 · 演奏履歴（未来の音は予測しません）"else "Falling notes · ${seq.title}",Modifier.padding(horizontal=20.dp,vertical=4.dp),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.secondary,maxLines=1)
        Canvas(Modifier.fillMaxWidth().weight(1f).padding(horizontal=12.dp).background(Color(0xff12161b))) {
            layout.shapes.filter { !it.black }.forEach { key -> drawLine(Color(0xff252b32),Offset(key.left*size.width,0f),Offset(key.left*size.width,size.height)) }
            fun y(t: Long):Float = if(liveMode)size.height-((position-t).toDouble()/span*size.height).toFloat() else size.height-((t-position).toDouble()/span*size.height).toFloat()
            val begin=if(liveMode)position-span else position
            val end=if(liveMode)position else position+span
            val seconds=(begin/1000000)..(end/1000000)
            seconds.forEach { s->val yy=y(s*1000000);drawLine(Color(0xff2a313a),Offset(0f,yy),Offset(size.width,yy),1f) }
            clipRect {
                fun note(n: RollNote,live: Boolean=false){
                    val key=layout.shapes.firstOrNull { it.note==n.key } ?: return
                    val x=key.left*size.width+1;val w=(key.width*size.width-2).coerceAtLeast(2f)
                    val color=if(live)noteColors[0]else noteColors[n.track%noteColors.size]
                    val start=y(n.start);val stop=y(n.keyEnd);val released=y(n.end)
                    if(n.end>n.keyEnd)drawRect(Color(0xffb3a26e).copy(alpha=.35f),Offset(x,minOf(stop,released)),Size(w,kotlin.math.abs(stop-released).coerceAtLeast(2f)))
                    val top=minOf(start,stop);val height=kotlin.math.abs(start-stop).coerceAtLeast(3f)
                    drawRoundRect(color.copy(alpha=.55f+n.velocity/127f*.45f),Offset(x,top),Size(w,height),CornerRadius(minOf(4.dp.toPx(),w*.15f)))
                    if(seq.noteLabels && w>18.dp.toPx() && height>20.dp.toPx()){
                        paint.textSize=minOf(13.dp.toPx(),w*.45f);drawIntoCanvas { it.nativeCanvas.drawText(solfege(n.key),x+w/2,top.coerceAtLeast(0f)+16.dp.toPx(),paint) }
                    }
                }
                if(liveMode)seq.live.notes.filter { it.end>=begin }.forEach { note(it,true) }
                else {seq.song?.noteIndex?.visible(begin,end){note(it)};seq.live.notes.filter { it.end>=seq.live.now-span }.forEach { n->note(n.copy(start=position,keyEnd=position+100000,end=position+100000),true) }}
            }
            drawLine(Color(0xff86bca7),Offset(0f,size.height-1),Offset(size.width,size.height-1),2.dp.toPx())
        }
    }
}

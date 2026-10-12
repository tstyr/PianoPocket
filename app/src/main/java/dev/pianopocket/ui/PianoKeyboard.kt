package dev.pianopocket.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun PianoKeyboard(state: PocketState,model: PocketViewModel,modifier: Modifier=Modifier) {
    val seq=state.sequence
    val enabled=state.ready && !state.busy && !seq.exporting
    val low=if(seq.fullKeyboard)21 else (seq.octave+1)*12
    val high=if(seq.fullKeyboard)108 else low+23
    val layout=remember(low,high){KeyLayout(low,high)}
    var sustain by remember { mutableStateOf(false) }
    var held by remember { mutableStateOf(setOf<Int>()) }
    val paint=remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign=Paint.Align.CENTER } }
    DisposableEffect(enabled,low) { onDispose { model.pedal(false);sustain=false;held=emptySet() } }
    val external=remember(seq.position,seq.playing,seq.song,seq.live){
        val active=seq.live.held.toMutableSet();val tails=seq.live.sustained.toMutableSet()
        if(seq.playing)seq.song?.noteIndex?.visible(seq.position,seq.position){if(seq.position<it.keyEnd)active+=it.key else tails+=it.key}
        active to tails
    }
    Column(modifier.background(Color(0xff20242a)).padding(horizontal=12.dp,vertical=6.dp)) {
        Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            FilterChip(selected=seq.fullKeyboard,onClick={model.fullKeyboard(!seq.fullKeyboard)},label={Text("88鍵")})
            if(!seq.fullKeyboard){
                TextButton(onClick={model.octave((seq.octave-1).coerceAtLeast(1))},enabled=seq.octave>1){Text("−")}
                Text("C${seq.octave}",Modifier.padding(top=15.dp),style=MaterialTheme.typography.labelMedium)
                TextButton(onClick={model.octave((seq.octave+1).coerceAtMost(7))},enabled=seq.octave<7){Text("＋")}
            }
            Spacer(Modifier.weight(1f))
            FilterChip(selected=sustain,onClick={sustain=!sustain;model.pedal(sustain)},enabled=enabled,label={Text("ペダル")})
            TextButton(onClick={sustain=false;model.panic()}){Text("停止")}
        }
        Canvas(Modifier.fillMaxWidth().weight(1f).semantics { contentDescription="マルチタッチのピアノ鍵盤。下側ほど強いベロシティ。" }.pointerInput(layout,enabled) {
            if(!enabled)return@pointerInput
            val touches=mutableMapOf<PointerId,Int>()
            try {
                awaitPointerEventScope { while(true){
                    val event=awaitPointerEvent()
                    event.changes.forEach { change ->
                        val old=touches[change.id]
                        val next=if(change.pressed)layout.hit(change.position.x/size.width,change.position.y/size.height)else null
                        if(old!=next){touches.remove(change.id);if(old!=null && old !in touches.values)model.key(old,0)
                            if(next!=null){if(next !in touches.values)model.key(next,(48+change.position.y/size.height*79).toInt().coerceIn(1,127));touches[change.id]=next};held=touches.values.toSet()}
                        change.consume()
                    }
                } }
            }finally{touches.values.toSet().forEach { model.key(it,0) };held=emptySet()}
        }) {
            layout.shapes.forEach { key ->
                val down=key.note in held || key.note in external.first
                val tail=key.note in external.second
                val color=if(key.black){if(down)Color(0xff488a77)else if(tail)Color(0xff6a6248)else Color(0xff10141a)}else{if(down)Color(0xffa8d4bf)else if(tail)Color(0xffcfcaab)else Color(0xffe4e6e7)}
                val left=key.left*size.width;val width=key.width*size.width;val height=if(key.black)size.height*.62f else size.height
                drawRect(color,Offset(left+1,0f),Size((width-2).coerceAtLeast(1f),height))
                if(seq.noteLabels){
                    paint.color=if(key.black)0xffc5ccce.toInt()else 0xff404a50.toInt();paint.textSize=minOf(14.dp.toPx(),width*.45f)
                    drawIntoCanvas { it.nativeCanvas.drawText(solfege(key.note),left+width/2,height-10.dp.toPx(),paint) }
                }
            }
        }
    }
}

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
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun PianoKeyboard(state: PocketState,model: PocketViewModel,modifier: Modifier=Modifier) {
    val seq=state.sequence
    val enabled=state.ready && !state.busy && !seq.exporting
    val layout=remember {KeyLayout(21,108)}
    val viewport by rememberUpdatedState(seq.viewport)
    var held by remember { mutableStateOf(setOf<Int>()) }
    val paint=remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign=Paint.Align.CENTER } }
    DisposableEffect(enabled) { onDispose { model.pedal(false);held=emptySet() } }
    val external=remember(seq.position,seq.playing,seq.song,seq.live,seq.mutedTracks){
        val active=seq.live.held.toMutableSet()
        if(seq.playing)seq.song?.keyIndex?.visible(seq.position,seq.position){if(seq.position<it.keyEnd && it.track !in seq.mutedTracks)active+=it.key}
        active
    }
    Box(modifier.background(Color(0xff20242a))) {
        Canvas(Modifier.fillMaxSize().semantics { contentDescription="マルチタッチのピアノ鍵盤。下側ほど強いベロシティ。" }.pointerInput(layout,enabled) {
            if(!enabled)return@pointerInput
            val touches=mutableMapOf<PointerId,Int>()
            try {
                awaitPointerEventScope { while(true){
                    val event=awaitPointerEvent()
                    event.changes.forEach { change ->
                        val old=touches[change.id]
                        // Another finger may be panning the roll. A stationary held finger keeps its note.
                        val next=if(!change.pressed)null else if(old!=null && change.position==change.previousPosition)old
                            else layout.hit(change.position.x/size.width,change.position.y/size.height,viewport)
                        if(old!=next){touches.remove(change.id);if(old!=null && old !in touches.values)model.key(old,0)
                            if(next!=null){if(next !in touches.values)model.key(next,(48+change.position.y/size.height*79).toInt().coerceIn(1,127));touches[change.id]=next};held=touches.values.toSet()}
                        change.consume()
                    }
                } }
            }finally{touches.values.toSet().forEach { model.key(it,0) };held=emptySet()}
        }) {
            clipRect { layout.shapes.forEach { key ->
                val down=key.note in held || key.note in external
                val color=if(key.black){if(down)Color(0xff488a77)else Color(0xff10141a)}else{if(down)Color(0xffa8d4bf)else Color(0xffe4e6e7)}
                val left=viewport.screen(key.left)*size.width;val width=key.width*viewport.zoom*size.width;val height=if(key.black)size.height*.62f else size.height
                if(left+width<0 || left>=size.width)return@forEach
                drawRect(color,Offset(left+1,0f),Size((width-2).coerceAtLeast(1f),height))
                if(seq.noteLabels){
                    drawNoteLabel(key.note,left+width/2,0f,width,height,paint,black=key.black)
                }
            } }
        }
    }
}

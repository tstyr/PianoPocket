package dev.pianopocket.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.pianopocket.audio.NativeAudio

private val whitePitch=listOf(0,2,4,5,7,9,11)
private val blackAfter=mapOf(0 to 1,1 to 3,3 to 6,4 to 8,5 to 10)

@Composable
fun PianoKeyboard(enabled: Boolean, modifier: Modifier=Modifier) {
    var octave by remember { mutableIntStateOf(4) }
    var sustain by remember { mutableStateOf(false) }
    var held by remember { mutableStateOf(setOf<Int>()) }
    DisposableEffect(enabled,octave) { onDispose { NativeAudio.cc(64,0);NativeAudio.panic();held=emptySet() } }
    Column(modifier.background(Color(0xff20242a)).padding(horizontal=12.dp,vertical=8.dp)) {
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick={NativeAudio.panic();octave=(octave-1).coerceAtLeast(1)},enabled=octave>1){Text("− Oct")}
            Text("C$octave — B${octave+1}",modifier=Modifier.padding(top=12.dp).weight(1f),style=MaterialTheme.typography.labelLarge)
            OutlinedButton(onClick={NativeAudio.panic();octave=(octave+1).coerceAtMost(7)},enabled=octave<7){Text("+ Oct")}
            FilterChip(selected=sustain,onClick={sustain=!sustain;NativeAudio.cc(64,if(sustain)127 else 0)},enabled=enabled,label={Text("Sustain")})
            TextButton(onClick={sustain=false;NativeAudio.panic()}){Text("停止")}
        }
        val base=(octave+1)*12
        Canvas(Modifier.fillMaxWidth().weight(1f).semantics { contentDescription="2オクターブのピアノ鍵盤。鍵盤の下側ほど強い音量。複数指で演奏できます。" }.pointerInput(base,enabled) {
            if(!enabled)return@pointerInput
            val touches=mutableMapOf<PointerId,Int>()
            fun hit(x: Float,y: Float):Int? {
                if(x<0 || y<0 || x>=size.width || y>=size.height)return null
                val w=size.width/14f
                if(y<size.height*.62f)for(o in 0..1)for((after,pitch)in blackAfter){val center=(o*7+after+1)*w;if(x>=center-w*.31f && x<center+w*.31f)return base+o*12+pitch}
                val index=(x/w).toInt().coerceIn(0,13);return base+(index/7)*12+whitePitch[index%7]
            }
            try {
                awaitPointerEventScope {
                    while(true){
                        val event=awaitPointerEvent()
                        for(change in event.changes){
                            val old=touches[change.id];val next=if(change.pressed)hit(change.position.x,change.position.y)else null
                            if(old!=next){
                                touches.remove(change.id)
                                if(old!=null && old !in touches.values)NativeAudio.noteOff(old)
                                if(next!=null){if(next !in touches.values)NativeAudio.noteOn(next,(48+change.position.y/size.height*79).toInt().coerceIn(1,127));touches[change.id]=next}
                                held=touches.values.toSet()
                            };change.consume()
                        }
                    }
                }
            }finally{touches.values.toSet().forEach { NativeAudio.noteOff(it) };held=emptySet()}
        }) {
            val w=size.width/14f
            for(i in 0 until 14){val note=base+(i/7)*12+whitePitch[i%7];drawRect(if(note in held)Color(0xffa8caba)else Color(0xffe4e6e7),Offset(i*w+1,0f),Size(w-2,size.height))}
            for(o in 0..1)for((after,pitch)in blackAfter){val center=(o*7+after+1)*w;val note=base+o*12+pitch;drawRect(if(note in held)Color(0xff497f6d)else Color(0xff111419),Offset(center-w*.31f,0f),Size(w*.62f,size.height*.62f))}
        }
    }
}

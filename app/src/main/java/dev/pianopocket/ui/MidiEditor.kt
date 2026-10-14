package dev.pianopocket.ui

import android.graphics.Paint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.pianopocket.editor.*
import dev.pianopocket.sequencer.MidiEntry
import kotlin.math.*

data class MidiEditSession(val entry: MidiEntry,val document: MidiEditDocument)

/** A horizontal editor with 48dp tools; the falling performance roll stays separate. */
@Composable internal fun MidiEditor(session: MidiEditSession,saving: Boolean,error: String,onClose:()->Unit,onSave:(MidiEditDocument)->Unit) {
    val doc=session.document
    var revision by remember(doc){mutableIntStateOf(0)}
    var selected by remember(doc){mutableStateOf<Int?>(null)}
    var discard by remember {mutableStateOf(false)}
    var track by remember(doc){mutableIntStateOf(-1)}
    var trackMenu by remember {mutableStateOf(false)}
    var divisions by remember {mutableIntStateOf(4)}
    var snap by remember {mutableStateOf(true)}
    val density=LocalDensity.current.density
    val gutter=52*density
    var beatWidth by remember {mutableFloatStateOf(84*density)}
    var rowHeight by remember {mutableFloatStateOf(28*density)}
    var offset by remember(doc){mutableDoubleStateOf(maxOf(0L,(doc.notes.firstOrNull()?.start ?: 0)-doc.source.ppqn).toDouble())}
    var topKey by remember(doc){mutableFloatStateOf(((doc.notes.firstOrNull()?.key ?: 60)+8).coerceIn(12,127).toFloat())}
    var canvasSize by remember {mutableStateOf(IntSize.Zero)}
    var dragged by remember {mutableStateOf<EditNote?>(null)}
    val scale=beatWidth/doc.source.ppqn
    fun tickScale()=beatWidth/doc.source.ppqn
    val visible=remember(doc,revision,offset,scale,canvasSize,track){doc.visible(offset.toLong(),(offset+maxOf(0f,canvasSize.width-gutter)/scale).toLong()).filter {track<0 || it.track==track}}
    val latestVisible by rememberUpdatedState(visible)
    val chosen=revision.let {selected?.let(doc::note)}
    fun refresh(){revision++}
    fun close(){if(!saving){if(doc.dirty)discard=true else onClose()}}
    BackHandler(onBack=::close)
    val labelPaint=remember(density){Paint(Paint.ANTI_ALIAS_FLAG).apply {textSize=12*density;isFakeBoldText=true}}
    fun position(n: EditNote)=Offset(gutter+((n.start-offset)*tickScale()).toFloat(),(topKey-n.key)*rowHeight)
    fun hit(p: Offset): EditNote? {
        if(p.x<gutter)return null
        val candidates=latestVisible.map {n->val a=position(n);val right=a.x+maxOf(8*density,(n.end-n.start)*tickScale())
            val dx=maxOf(a.x-p.x,p.x-right,0f);val dy=maxOf(a.y-p.y,p.y-a.y-rowHeight,0f);n to (dx*dx+dy*dy)}
            .filter {it.second<(20*density).pow(2)}
        return candidates.minByOrNull {it.second}?.first
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            PocketIconButton(PocketIcon.Back,"編集を閉じる",enabled=!saving,onClick=::close)
            Text("MIDI編集",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
            PocketIconButton(PocketIcon.Undo,"元に戻す",enabled=!saving && doc.canUndo,onClick={doc.undo();refresh()})
            PocketIconButton(PocketIcon.Redo,"やり直す",enabled=!saving && doc.canRedo,onClick={doc.redo();refresh()})
            TextButton(onClick={onSave(doc)},enabled=!saving && doc.dirty){Text(if(saving)"保存中"else "コピー保存")}
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){
            Box {TextButton(onClick={trackMenu=true}){Text(if(track<0)"全トラック"else "トラック ${track+1}")}
                DropdownMenu(expanded=trackMenu,onDismissRequest={trackMenu=false}){(listOf(-1)+doc.notes.map {it.track}.distinct().sorted()).forEach {t->DropdownMenuItem(text={Text(if(t<0)"全トラック"else "トラック ${t+1}")},onClick={track=t;selected=null;trackMenu=false})}}}
            FilterChip(selected=snap,onClick={snap=!snap},label={Text("吸着")})
            listOf(1,2,4).forEach {d->FilterChip(selected=divisions==d,onClick={divisions=d},label={Text("1/${d*4}")})}
        }
        Canvas(Modifier.fillMaxWidth().weight(1f).onSizeChanged {canvasSize=it}.semantics {contentDescription="音符をタップして選択。ドラッグで音程と位置を変更。背景をドラッグして移動、2本指で拡大縮小。"}
            .pointerInput(doc,revision,track,snap,divisions,saving){
                if(saving)return@pointerInput
                awaitEachGesture {
                    val down=awaitFirstDown(requireUnconsumed=false)
                    val target=hit(down.position)
                    val startOffset=offset;val startTop=topKey;val startScale=tickScale();val startHeight=rowHeight
                    var delta=Offset.Zero;var moved=false;var multi=false
                    if(target!=null)selected=target.id else selected=null
                    try {
                        while(true){
                            val event=awaitPointerEvent();val pressed=event.changes.count {it.pressed}
                            if(pressed>=2){
                                multi=true;dragged=null
                                val center=event.calculateCentroid(useCurrent=false);val pan=event.calculatePan();val zoom=event.calculateZoom()
                                val tick=offset+(center.x-gutter)/tickScale();val key=topKey-center.y/rowHeight
                                beatWidth=(beatWidth*zoom).coerceIn(36*density,480*density);rowHeight=(rowHeight*zoom).coerceIn(20*density,64*density)
                                offset=(tick-(center.x+pan.x-gutter)/(beatWidth/doc.source.ppqn)).coerceIn(0.0,doc.maxTick.toDouble())
                                topKey=(key+(center.y+pan.y)/rowHeight).coerceIn(0f,127f)
                            }else if(!multi){
                                val pointer=event.changes.firstOrNull {it.id==down.id} ?: break
                                delta=pointer.position-down.position
                                if(delta.getDistance()>viewConfiguration.touchSlop)moved=true
                                if(moved && target!=null){
                                    var shift=(delta.x/startScale).roundToLong()
                                    if(snap){val step=doc.source.ppqn.toDouble()/divisions;shift=(((target.start+shift)/step).roundToLong()*step).roundToLong()-target.start}
                                    val length=target.end-target.start;val tick=(target.start+shift).coerceIn(0,doc.maxTick-length)
                                    dragged=target.copy(start=tick,end=tick+length,key=(target.key-(delta.y/startHeight).roundToInt()).coerceIn(0,127))
                                }else if(moved){offset=(startOffset-delta.x/startScale).coerceIn(0.0,doc.maxTick.toDouble());topKey=(startTop+delta.y/startHeight).coerceIn(0f,127f)}
                            }
                            event.changes.forEach {it.consume()}
                            if(pressed==0){if(!multi)dragged?.let {doc.update(it);refresh()};break}
                        }
                    }finally{dragged=null}
                }
            }) {
            drawRect(Color(0xff1c232c))
            for(k in 0..127){val y=(topKey-k)*rowHeight;if(y+rowHeight<0 || y>size.height)continue
                if(k%12 in listOf(1,3,6,8,10))drawRect(Color(0xff252e3a),Offset(0f,y),Size(size.width,rowHeight))
                drawLine(Color(0xff35404b),Offset(gutter,y),Offset(size.width,y),density*.5f)
                labelPaint.color=android.graphics.Color.WHITE
                drawContext.canvas.nativeCanvas.drawText(solfege(k)+if(k%12==0)"${k/12-1}"else "",4*density,y+rowHeight*.72f,labelPaint)
            }
            val step=doc.source.ppqn.toDouble()/divisions
            val first=floor(offset/step).toLong();val last=ceil((offset+(size.width-gutter)/scale)/step).toLong()
            for(i in first..last){val x=gutter+((i*step-offset)*scale).toFloat();drawLine(if(i%divisions==0L)Color(0xff576170)else Color(0xff303b47),Offset(x,0f),Offset(x,size.height),density*.6f)}
            visible.forEach {original->val n=if(original.id==dragged?.id)dragged!! else original;val p=position(n)
                val x=maxOf(gutter,p.x);val right=minOf(size.width,p.x+maxOf(8*density,(n.end-n.start)*scale));if(right<=x || p.y+rowHeight<0 || p.y>size.height)return@forEach
                val selectedNote=n.id==selected;val color=if(selectedNote)Color(0xffffd36a)else listOf(Color(0xff9ec6b4),Color(0xff9cb6da),Color(0xffbaacd9),Color(0xffe5b29e))[n.track%4]
                drawRoundRect(color,Offset(x,p.y+2*density),Size(right-x,rowHeight-4*density),androidx.compose.ui.geometry.CornerRadius(3*density))
                if(selectedNote)drawRoundRect(Color.White,Offset(x,p.y+2*density),Size(right-x,rowHeight-4*density),androidx.compose.ui.geometry.CornerRadius(3*density),style=Stroke(2*density))
                if(right-x>28*density){labelPaint.color=android.graphics.Color.rgb(22,27,32);drawContext.canvas.nativeCanvas.drawText(solfege(n.key),x+4*density,p.y+rowHeight*.72f,labelPaint)}
            }
        }
        Text(if(chosen==null)"音符をタップ · 2本指で拡大"else "${solfege(chosen.key)}${chosen.key/12-1} · トラック ${chosen.track+1} · 強さ ${chosen.velocity}",Modifier.padding(horizontal=12.dp,vertical=2.dp),style=MaterialTheme.typography.labelMedium)
        if(chosen!=null){
            val n=chosen
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically){
                PocketIconButton(PocketIcon.Delete,"選んだ音符を削除",enabled=!saving,onClick={doc.delete(n.id);selected=null;refresh()})
                val tick=maxOf(1,(doc.source.ppqn.toDouble()/divisions).roundToLong())
                EditorTool("前へ",saving){doc.move(n.id,-tick,0);refresh()};EditorTool("後へ",saving){doc.move(n.id,tick,0);refresh()}
                EditorTool("短く",saving){doc.length(n.id,-tick);refresh()};EditorTool("長く",saving){doc.length(n.id,tick);refresh()}
                EditorTool("♭",saving){doc.move(n.id,0,-1);refresh()};EditorTool("♯",saving){doc.move(n.id,0,1);refresh()}
                EditorTool("揃える",saving){doc.quantize(n.id,divisions);refresh()}
            }
            var velocity by remember(n.id,n.velocity){mutableFloatStateOf(n.velocity.toFloat())}
            Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically){Text("強さ",style=MaterialTheme.typography.labelMedium);Slider(value=velocity,onValueChange={velocity=it},valueRange=1f..127f,enabled=!saving,modifier=Modifier.weight(1f),onValueChangeFinished={doc.update(n.copy(velocity=velocity.roundToInt()));refresh()})}
        }
        if(saving)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(error.isNotBlank())Text(error,Modifier.padding(horizontal=12.dp),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
    }
    if(discard)AlertDialog(onDismissRequest={discard=false},title={Text("編集を破棄しますか？")},text={Text("元のMIDIは変更されていません。")},confirmButton={TextButton(onClick={discard=false;onClose()}){Text("破棄して閉じる")}},dismissButton={TextButton(onClick={discard=false}){Text("編集に戻る")}})
}
@Composable private fun EditorTool(text: String,saving: Boolean,action:()->Unit){TextButton(onClick=action,enabled=!saving,modifier=Modifier.heightIn(min=48.dp)){Text(text)}}

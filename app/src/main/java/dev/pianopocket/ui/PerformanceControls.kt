package dev.pianopocket.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.pianopocket.sequencer.ExportFormat

@Composable
fun PerformanceToolbar(state: PocketState,model: PocketViewModel,export:(ExportFormat,Int,Int,Int,dev.pianopocket.video.VideoOptions)->Unit,midi:()->Unit,instrument:()->Unit,settings:()->Unit,hideUi:()->Unit) {
    val seq=state.sequence
    var exporting by rememberSaveable { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf(false) }
    var practicing by remember {mutableStateOf(false)}
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal=4.dp),verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick=instrument,modifier=Modifier.weight(1f),contentPadding=PaddingValues(horizontal=8.dp)) {
                Column(Modifier.fillMaxWidth()) {
                    Text(state.preset?.name ?: "音源を選択",style=MaterialTheme.typography.titleSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                    Text(state.midi,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.secondary,maxLines=1,overflow=TextOverflow.Ellipsis)
                }
            }
            PocketIconButton(PocketIcon.Midi,"MIDIファイルを選択",enabled=!seq.recording,onClick=midi)
            PocketIconButton(if(seq.playing)PocketIcon.Pause else PocketIcon.Play,if(seq.playing)"一時停止"else "再生",enabled=seq.song!=null && state.ready && !state.busy && !seq.recording && !seq.exporting,onClick=model::play)
            PocketIconButton(PocketIcon.Record,if(seq.recording)"録音を停止して保存"else "録音を開始",enabled=state.ready && !state.busy && !seq.exporting,selected=seq.recording,onClick={if(seq.recording)model.stopRecording()else model.startRecording()})
            PocketIconButton(PocketIcon.Stop,"すべての音を停止",onClick=model::panic)
            PocketIconButton(PocketIcon.Mixer,"音源・マイク・エフェクト",onClick=instrument)
            PocketIconButton(PocketIcon.Eye,"操作UIを隠す",onClick=hideUi)
            Box {
                PocketIconButton(PocketIcon.More,"その他の操作",onClick={more=true})
                DropdownMenu(expanded=more,onDismissRequest={more=false}) {
                    DropdownMenuItem(text={Text("MIDI / 音声 / 動画を書き出し")},enabled=seq.song!=null && !seq.recording && !state.busy && !seq.exporting,onClick={more=false;exporting=true})
                    DropdownMenuItem(text={Text("速度・リピート・トラック")},enabled=seq.song!=null && !state.busy && !seq.recording && !seq.exporting,onClick={more=false;practicing=true})
                    DropdownMenuItem(text={Text(if(seq.rollVisible)"ロールを隠す"else "ロールを表示")},onClick={more=false;model.rollVisible(!seq.rollVisible)})
                    DropdownMenuItem(text={Text("状態・互換性警告")},onClick={more=false;info=true})
                    DropdownMenuItem(text={Text("設定・MIDI入力")},onClick={more=false;settings()})
                }
            }
        }
        if(seq.song!=null && !seq.recording) {
            var seeking by remember(seq.selected){mutableStateOf<Float?>(null)}
            Row(Modifier.padding(horizontal=12.dp).height(24.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("${durationLabel(seq.position)} / ${durationLabel(seq.song.duration)}",style=MaterialTheme.typography.labelSmall)
                Slider(value=seeking ?: (seq.position/1000000f).coerceIn(0f,(seq.song.duration/1000000f).coerceAtLeast(.001f)),onValueChange={seeking=it},valueRange=0f..(seq.song.duration/1000000f).coerceAtLeast(.001f),enabled=!state.busy && !seq.recording && !seq.exporting,modifier=Modifier.weight(1f),onValueChangeFinished={seeking?.let { model.seek((it*1000000).toLong()) };seeking=null})
            }
        }
        if(seq.exporting)Row(verticalAlignment=Alignment.CenterVertically) {
            LinearProgressIndicator(progress={seq.exportProgress},modifier=Modifier.weight(1f))
            PocketIconButton(PocketIcon.Close,"書き出しをキャンセル",onClick=model::cancelExport)
        }
    }
    if(practicing)PracticeDialog(state,model){practicing=false}
    if(exporting)ExportDialog(seq.title,state.ready,state.aacAvailable,state.videoOptions,onDismiss={exporting=false}){f,r,t,b,v->exporting=false;export(f,r,t,b,v)}
    if(info)AlertDialog(onDismissRequest={info=false},title={Text("状態・互換性警告")},text={Column(Modifier.verticalScroll(rememberScrollState())) {
        Text(state.message)
        Text(state.midi)
        Text("ボイス ${state.stats[6].toInt()}/${state.voices} · DSP ${"%.1f".format(state.stats[5])}% · XRun ${state.stats[3].toInt()}")
        state.preset?.warnings?.forEach { Text("• $it",style=MaterialTheme.typography.bodySmall) }
        seq.song?.warnings?.forEach { Text("• $it",style=MaterialTheme.typography.bodySmall) }
    }},confirmButton={TextButton(onClick={info=false}){Text("閉じる")}})
}
@Composable
fun KeyboardTools(state: PocketState,model: PocketViewModel,modifier: Modifier=Modifier) {
    val seq=state.sequence
    Surface(modifier,shape=MaterialTheme.shapes.small,color=MaterialTheme.colorScheme.surface.copy(alpha=.96f)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            SustainIndicator(state,Modifier.padding(horizontal=8.dp))
            PocketIconButton(PocketIcon.Zoom,if(seq.fullKeyboard)"2オクターブに拡大"else "88鍵を表示",selected=!seq.fullKeyboard,onClick={model.fullKeyboard(!seq.fullKeyboard)})
            if(!seq.fullKeyboard) {
                PocketIconButton(PocketIcon.Back,"低いオクターブへ",enabled=seq.viewport.start>0,onClick={model.shiftKeyboard(-1)})
                Text("${(seq.viewport.zoom*100).toInt()}%",style=MaterialTheme.typography.labelSmall)
                PocketIconButton(PocketIcon.Forward,"高いオクターブへ",enabled=seq.viewport.start<seq.viewport.maxStart,onClick={model.shiftKeyboard(1)})
            }
            PocketIconButton(PocketIcon.Pedal,if(state.uiPedal)"ペダルを離す"else "ペダルを踏む",selected=state.uiPedal,enabled=state.ready && !state.busy && !seq.exporting,onClick={model.pedal(!state.uiPedal)})
        }
    }
}
@Composable
fun InstrumentPanel(state: PocketState,model: PocketViewModel,close:()->Unit,library:()->Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val preset=state.preset
    val enabled=state.ready && !state.busy && !state.sequence.exporting
    Column {
        Row(Modifier.padding(start=16.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(preset?.name ?: "音源",style=MaterialTheme.typography.titleMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Text("NATIVE SAMPLER",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.secondary);SustainIndicator(state)}
            }
            PocketIconButton(PocketIcon.Library,"音源ライブラリを開く",enabled=!state.sequence.recording,onClick=library)
            PocketIconButton(PocketIcon.Pedal,if(state.uiPedal)"ペダルを離す"else "ペダルを踏む",selected=state.uiPedal,enabled=enabled,onClick={model.pedal(!state.uiPedal)})
            PocketIconButton(PocketIcon.Close,"音源パネルを閉じる",onClick=close)
        }
        TabRow(selectedTabIndex=page) { listOf("ミキサー","エフェクト","情報").forEachIndexed { i,name->Tab(selected=page==i,onClick={page=i},text={Text(name)}) } }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(preset!=null)SavedSoundsPanel(state,model)
            if(preset==null)OutlinedButton(onClick=library){Text("音源を選択")} else when(page) {
                0 -> {
                    // Only real XML bindings become microphone strips. No invented Helsinki parameters.
                    val microphones=preset.controls.withIndex().filter { c->c.value.bindings.any { it.type=="group" } }
                    microphones.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                            row.forEach { (i,c)->
                                Card(Modifier.weight(1f)) { Column(Modifier.fillMaxWidth().padding(vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                                    Text(c.name,style=MaterialTheme.typography.labelMedium,maxLines=2,modifier=Modifier.padding(horizontal=4.dp))
                                    Box(Modifier.height(156.dp).fillMaxWidth().clip(MaterialTheme.shapes.small),contentAlignment=Alignment.Center) {
                                        Slider(value=c.value.coerceIn(c.min,c.max),onValueChange={model.control(i,it)},valueRange=c.min..c.max,enabled=enabled,modifier=Modifier.requiredSize(156.dp,48.dp).semantics { contentDescription=c.name }.graphicsLayer { rotationZ=-90f })
                                    }
                                    Text("%.2f".format(c.value),style=MaterialTheme.typography.labelSmall)
                                } }
                            }
                            repeat(3-row.size){Spacer(Modifier.weight(1f))}
                        }
                    }
                    if(microphones.isEmpty())preset.groups.forEachIndexed { i,name->Parameter(name,state.groups.getOrElse(i){1f},0f..2f,enabled){model.group(i,it)} }
                    preset.controls.forEachIndexed { i,c->if(c.bindings.none { it.type=="group" || it.type=="effect" })Parameter(c.name,c.value,c.min..c.max,enabled){model.control(i,it)} }
                    HorizontalDivider()
                    Parameter("Output",state.volume,0f..1f,enabled,model::volume)
                    Text("出力保護 ${"%.1f".format(state.stats[13])} dB · DSP ${"%.1f".format(state.stats[5])}%",style=MaterialTheme.typography.labelSmall)
                }
                1 -> {
                    preset.controls.forEachIndexed { i,c->if(c.bindings.none { it.type=="group" } && c.bindings.any { it.type=="effect" })Parameter(c.name,c.value,c.min..c.max,enabled){model.control(i,it)} }
                    preset.menus.forEachIndexed { i,menu->IrMenuControl(menu,enabled) { model.impulse(i,it) } }
                    preset.effects.forEachIndexed { index,fx->
                        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                Text("${index+1} · ${fx.name}",Modifier.weight(1f),style=MaterialTheme.typography.titleSmall)
                                Switch(modifier=Modifier.semantics { contentDescription="${fx.name}の有効・無効" },checked=fx.params[4]>=.5f,onCheckedChange={model.fx(index,4,if(it)1f else 0f)},enabled=enabled)
                            }
                            val parameters=when(fx.type) {
                                0 -> listOf(Triple(0,"Wet Level",0f..1f),Triple(1,"Room Size",0f..1f),Triple(2,"Damping",0f..1f))
                                1 -> listOf(Triple(0,"Wet Level",0f..1f),Triple(1,"Delay Time (s)",0f..20f),Triple(2,"Feedback",0f..0.995f),Triple(3,"Stereo Offset (s)",-10f..10f))
                                2 -> listOf(Triple(0,"Mix",0f..1f),Triple(1,"Mod Depth",0f..1f),Triple(2,"Mod Rate (Hz)",0f..10f))
                                6 -> listOf(Triple(1,"Level",0f..4f))
                                7 -> listOf(Triple(0,"IR Mix",0f..1f))
                                else -> listOf(Triple(1,"Frequency (Hz)",20f..22000f),Triple(2,"Q",.01f..5f))+(if(fx.type>=8 && fx.type!=9)listOf(Triple(3,"Gain",.01f..4f))else emptyList())
                            }
                            parameters.forEach { (slot,name,range)->
                                val bound=preset.controls.firstOrNull { c->c.bindings.any { it.type=="effect" && it.index==index && it.slot==slot } }
                                if(bound==null)Parameter(name,fx.params[slot],range,enabled && fx.params[4]>=.5f){model.fx(index,slot,it)}
                                else Text("${bound.name}に連動",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.secondary)
                            }
                            if(fx.type==7)Text("IRの音量を補正 · 256 frames処理遅延",style=MaterialTheme.typography.bodySmall)
                        } }
                    }
                    if(preset.effects.isEmpty())Text("このプリセットには対応エフェクトがありません。")
                }
                2 -> {
                    Text(state.message,style=MaterialTheme.typography.bodySmall)
                    Text("${preset.regions.size} zones · ${preset.warnings.size} 件の互換性警告")
                    Text("対応するXMLコントロールとエフェクト順序を表示します。Windows VSTホストは未実装です。",style=MaterialTheme.typography.bodySmall)
                    preset.warnings.forEach { Text("• $it",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.secondary) }
                }
            }
        }
    }
}

@Composable private fun SustainIndicator(state: PocketState,modifier: Modifier=Modifier) {
    val seq=state.sequence
    val active=state.uiPedal || seq.live.pedalDown || seq.playing && seq.song?.sustainIndex?.active(seq.position)==true
    Text("SUS",modifier.semantics { contentDescription=if(active)"サステイン中"else "サステイン解除" },color=if(active)Color(0xffffd66b)else MaterialTheme.colorScheme.secondary,style=MaterialTheme.typography.labelMedium)
}

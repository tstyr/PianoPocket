package dev.pianopocket.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import dev.pianopocket.library.LibraryEntry
import java.text.DateFormat
import java.util.Date

private val colors=darkColorScheme(primary=Color(0xff9ec6b4),background=Color(0xff181b1f),surface=Color(0xff20242a),onSurface=Color(0xffe1e3e5),secondary=Color(0xffb5bec7))
@Composable
fun PocketScreen(model: PocketViewModel,import:()->Unit,bluetooth:()->Unit,importMidi:()->Unit,export:(dev.pianopocket.sequencer.ExportFormat,Int,Int)->Unit) {
    val state by model.state.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.sequence.selected){if(tab==1 && state.sequence.selected.isNotBlank())tab=0}
    var deleting by remember { mutableStateOf<LibraryEntry?>(null) }
    val window=LocalWindowInfo.current.containerSize
    val landscape=window.width>window.height
    MaterialTheme(colorScheme=colors) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=if(landscape)4.dp else 12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        if(!landscape)Text("PIANOPOCKET",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
                        Text(state.preset?.name ?: "音源を選択",style=MaterialTheme.typography.titleLarge,maxLines=1)
                    }
                    Text(state.midi,style=MaterialTheme.typography.labelMedium,modifier=Modifier.padding(top=10.dp).widthIn(max=120.dp),maxLines=1)
                    TextButton(onClick={tab=3},modifier=Modifier.height(32.dp)){Text("設定")}
                }
                TabRow(selectedTabIndex=tab) { listOf("演奏","MIDI","音源","設定").forEachIndexed { i,label->Tab(selected=tab==i,onClick={tab=i},text={Text(label)}) } }
                if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(state.message,Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=8.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.secondary,maxLines=2)
                if(tab==0 && state.sequence.rollVisible) {
                    SequenceTransport(state,model,export)
                    PianoRoll(state,Modifier.fillMaxWidth().weight(1f))
                } else {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=20.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    when(tab) {
                        0 -> {
                            SequenceTransport(state,model,export)
                            state.preset?.let { preset ->
                            Text("マイク / グループ",style=MaterialTheme.typography.titleMedium)
                            if(preset.controls.none { c->c.bindings.any { it.type=="group" } })preset.groups.forEachIndexed { i,name->Parameter(name,state.groups.getOrElse(i){1f},0f..2f,state.ready && !state.busy){model.group(i,it)} }
                            if(preset.controls.isNotEmpty())Text("プリセットの対応コントロール",style=MaterialTheme.typography.titleMedium)
                            preset.controls.forEachIndexed { i,c->Parameter(c.name,c.value,c.min..c.max,state.ready && !state.busy){model.control(i,it)} }
                            preset.menus.forEachIndexed { i,menu->IrMenuControl(menu,state.ready && !state.busy) { model.impulse(i,it) } }
                            preset.effects.forEachIndexed { index,fx ->
                                Row { Text("${index+1}. ${fx.name}",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium);Switch(checked=fx.params[4]>=.5f,onCheckedChange={model.fx(index,4,if(it)1f else 0f)},enabled=state.ready && !state.busy) }
                                val parameters=when(fx.type) {
                                    0 -> listOf(Triple(0,"Wet Level",0f..1f),Triple(1,"Room Size",0f..1f),Triple(2,"Damping",0f..1f))
                                    1 -> listOf(Triple(0,"Wet Level",0f..1f),Triple(1,"Delay Time (s)",0f..20f),Triple(2,"Feedback",0f..0.995f),Triple(3,"Stereo Offset (s)",-10f..10f))
                                    2 -> listOf(Triple(0,"Mix",0f..1f),Triple(1,"Mod Depth",0f..1f),Triple(2,"Mod Rate (Hz)",0f..10f))
                                    6 -> listOf(Triple(1,"Level",0f..4f))
                                    7 -> listOf(Triple(0,"IR Mix · 256 frame処理遅延",0f..1f))
                                    else -> listOf(Triple(1,"Frequency (Hz)",20f..22000f),Triple(2,"Q",.01f..5f))+(if(fx.type>=8 && fx.type!=9)listOf(Triple(3,"Gain",.01f..4f))else emptyList())
                                }
                                parameters.forEach { (slot,name,range)->
                                    val bound=preset.controls.firstOrNull { c->c.bindings.any { it.type=="effect" && it.index==index && it.slot==slot } }
                                    if(bound==null)Parameter(name,fx.params[slot],range,state.ready && !state.busy){model.fx(index,slot,it)}
                                    else Text("${bound.name}で調整",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.secondary)
                                }
                            }
                            Parameter("Output",state.volume,0f..1f,state.ready && !state.busy,model::volume)
                            Text("ボイス ${state.stats[6].toInt()}/${state.voices} · DSP ${"%.1f".format(state.stats[5])}% · キャッシュミス ${state.stats[8].toLong()}",style=MaterialTheme.typography.bodySmall)
                            if(preset.warnings.isNotEmpty()) { Text("互換性レポート",style=MaterialTheme.typography.titleMedium);preset.warnings.forEach { Text("• $it",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.secondary) } }
                        } ?: Text("音源タブから音源を選択してください。")
                        }
                        1 -> MidiLibraryPanel(state,model,importMidi){tab=0}
                        2 -> {
                            Button(onClick=import,enabled=!state.busy){Text("音源をインポート")}
                            Text("ZIP / ZIP形式のdslibrary、またはdspreset＋サンプルフォルダー。利用者が正規に取得した音源を選択してください。",style=MaterialTheme.typography.bodySmall)
                            TextButton(onClick=model::rescan,enabled=!state.busy){Text("再スキャン")}
                            state.library.forEach { entry ->
                                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                                    Text(entry.name,style=MaterialTheme.typography.titleMedium)
                                    Text("${"%.1f".format(entry.bytes/1048576.0)} MiB · ${if(entry.lastUsed==0L)"未使用" else DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(entry.lastUsed))}",style=MaterialTheme.typography.bodySmall)
                                    Text(if(entry.id==state.selected)"読み込み済み・警告を演奏画面で確認" else "互換性は選択時に解析",style=MaterialTheme.typography.bodySmall)
                                    Row { TextButton(onClick={model.load(entry);tab=0},enabled=!state.busy){Text("選択")};TextButton(onClick={deleting=entry},enabled=!state.busy){Text("削除") } }
                                } }
                            }
                        }
                        3 -> {
                            Text("表示",style=MaterialTheme.typography.titleMedium)
                            Row { Text("カタカナのドレミ",Modifier.weight(1f));Switch(checked=state.sequence.noteLabels,onCheckedChange=model::noteLabels) }
                            Row { Text("ロールビュー",Modifier.weight(1f));Switch(checked=state.sequence.rollVisible,onCheckedChange=model::rollVisible) }
                            Parameter("ロール表示時間 (${state.sequence.lookAhead.toInt()} 秒)",state.sequence.lookAhead,2f..10f,true,model::lookAhead)
                            Text("88鍵は全体表示。鍵盤の88鍵ボタンをオフにすると2オクターブに拡大します。",style=MaterialTheme.typography.bodySmall)
                            Text("オーディオ",style=MaterialTheme.typography.titleMedium)
                            DeviceMenu(state,model)
                            BufferControl(state,model)
                            Parameter("最大発音数 (${state.voices} sample voices)",state.voices.toFloat(),32f..128f,true){model.voices(it.toInt())}
                            Parameter("先頭WAV＋キャッシュ予算 (${state.memoryMiB} MiB)",state.memoryMiB.toFloat(),32f..512f,true){model.memory(it.toInt())}
                            Text("${state.stats[0].toInt()} Hz / Float stereo · ${state.stats[1].toInt()} frames buffer · ${state.stats[2].toInt()} frames/burst")
                            Text("推定出力遅延: ${if(state.stats[4]<0)"取得不可" else "%.2f ms".format(state.stats[4])}（Oboe timestamp。鍵盤から音までの実測ではありません）",style=MaterialTheme.typography.bodySmall)
                            Text("XRun: ${state.stats[3].toInt()} · DSPコールバック予算: ${"%.1f".format(state.stats[5])}%")
                            Text("サンプル常駐: ${"%.1f".format(state.stats[7]/1048576)} MiB · イベント欠落: ${state.stats[9].toLong()} · 読み込みエラー: ${state.stats[11].toLong()}",style=MaterialTheme.typography.bodySmall)
                            Text("出力保護: ${"%.2f".format(state.stats[13])} dB減衰 · ${"%.2f".format(state.stats[14])} ms先読み",style=MaterialTheme.typography.bodySmall)
                            Text("保護前ピーク: ${if(state.stats[12]<=0)"−∞" else "%.1f".format(20*kotlin.math.log10(state.stats[12]))} dBFS。減衰が大きい場合はOutputを下げてください。",style=MaterialTheme.typography.bodySmall)
                            Text("プロセスPSS: ${"%.1f".format(state.processMiB)} MiB（UI・DSPを含む）",style=MaterialTheme.typography.bodySmall)
                            if(state.stats[10]>0)Text("オーディオ切断を検出しました。経路を再開してください。")
                            OutlinedButton(onClick={model.audio()},enabled=!state.busy){Text("オーディオを再開")}
                            Text("有線またはUSB出力を推奨します。Bluetoothイヤホンは大きく遅延する場合があります。10ms以下は保証しません。",style=MaterialTheme.typography.bodySmall)
                            Text("MIDI入力",style=MaterialTheme.typography.titleMedium)
                            MidiInputMenu(state,model)
                            ChannelMenu(state,model)
                            OutlinedButton(onClick=bluetooth){Text("Bluetooth MIDIを検索")}
                            state.ble.forEach { d->TextButton(onClick={model.bluetooth(d.address)}){Text(d.name)} }
                            Text("USB MIDIは自動検出。Pitch Bendは±2半音。UI鍵盤はMIDIチャンネルと独立。",style=MaterialTheme.typography.bodySmall)
                            Text("PianoPocket 0.3.1 alpha · Dark theme\nHelsinki v1.6は実ファイルでホスト検証。Galaxy S26の起動・MIDI・レイテンシは実機未検証。Windows VSTは別フォルダーの実験手順で扱います。",style=MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                }
                if(tab==0)PianoKeyboard(state,model,Modifier.fillMaxWidth().height(if(landscape)120.dp else 210.dp))
            }
            deleting?.let { entry -> AlertDialog(onDismissRequest={deleting=null},title={Text("音源フォルダーを削除")},text={Text("${entry.name} と同じインポートに含まれる全プリセット・サンプルを削除します。")},confirmButton={TextButton(onClick={model.delete(entry);deleting=null}){Text("削除")}},dismissButton={TextButton(onClick={deleting=null}){Text("キャンセル")}}) }
        }
    }
}
@Composable private fun Parameter(label: String,value: Float,range: ClosedFloatingPointRange<Float>,enabled: Boolean,onChange:(Float)->Unit) {
    Column { Row { Text(label,Modifier.weight(1f),style=MaterialTheme.typography.labelLarge);Text("%.2f".format(value),style=MaterialTheme.typography.labelMedium) };Slider(value=value.coerceIn(range.start,range.endInclusive),onValueChange=onChange,valueRange=range,enabled=enabled) }
}
@Composable private fun BufferControl(state: PocketState,model: PocketViewModel) {
    var pending by remember(state.bursts) { mutableFloatStateOf(state.bursts.toFloat()) }
    Column { Text("バッファ (${pending.toInt()} bursts)");Slider(value=pending,onValueChange={pending=it},valueRange=1f..8f,steps=6,enabled=!state.busy,onValueChangeFinished={model.audio(bursts=pending.toInt())}) }
}
@Composable private fun IrMenuControl(menu: dev.pianopocket.library.IrMenu,enabled: Boolean,onSelect:(Int)->Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box { OutlinedButton(onClick={expanded=true},enabled=enabled){Text("${menu.name}: ${menu.options[menu.selected].name}")};DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) { menu.options.forEachIndexed { index,choice->DropdownMenuItem(text={Text(choice.name)},onClick={expanded=false;onSelect(index)}) } } }
}
@Composable private fun DeviceMenu(state: PocketState,model: PocketViewModel) { var expanded by remember { mutableStateOf(false) };Box { OutlinedButton(onClick={expanded=true},enabled=!state.busy){Text(state.outputs.firstOrNull { it.id==state.device }?.label ?: "システム既定")};DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) { state.outputs.forEach { d->DropdownMenuItem(text={Text(d.label)},onClick={expanded=false;model.audio(device=d.id)}) } } } }
@Composable private fun ChannelMenu(state: PocketState,model: PocketViewModel) { var expanded by remember { mutableStateOf(false) };Box { OutlinedButton(onClick={expanded=true}){Text(if(state.channel<0)"全チャンネル" else "Channel ${state.channel+1}")};DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) { (-1..15).forEach { ch->DropdownMenuItem(text={Text(if(ch<0)"全チャンネル" else "Channel ${ch+1}")},onClick={expanded=false;model.channel(ch)}) } } } }

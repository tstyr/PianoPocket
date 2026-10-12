package dev.pianopocket.ui

import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.pianopocket.library.LibraryEntry
import java.text.DateFormat
import java.util.Date

private val colors=darkColorScheme(primary=Color(0xff9ec6b4),background=Color(0xff181b1f),surface=Color(0xff20242a),onSurface=Color(0xffe1e3e5),secondary=Color(0xffb5bec7))
@Composable
fun PocketScreen(model: PocketViewModel,import:()->Unit,bluetooth:()->Unit,importMidi:()->Unit,export:(dev.pianopocket.sequencer.ExportFormat,Int,Int,Int)->Unit) {
    val state by model.state.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var instrument by rememberSaveable { mutableStateOf(false) }
    var notice by remember { mutableStateOf(true) }
    LaunchedEffect(state.message){notice=true;kotlinx.coroutines.delay(5000);notice=false}
    var licenses by remember { mutableStateOf(false) }
    var licenseText by remember { mutableStateOf("読み込み中") }
    val context=androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(licenses){if(licenses)licenseText=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){context.assets.open("encoder-licenses.txt").bufferedReader().use { it.readText() }}}
    var deleting by remember { mutableStateOf<LibraryEntry?>(null) }
    val activity=LocalActivity.current
    val window=LocalWindowInfo.current.containerSize
    val landscape=window.width>window.height
    DisposableEffect(activity,tab,state.landscapePerformance) {
        if(activity==null)return@DisposableEffect onDispose {}
        activity.requestedOrientation=if(tab==0 && state.landscapePerformance)ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        val bars=WindowCompat.getInsetsController(activity.window,activity.window.decorView)
        bars.systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if(tab==0)bars.hide(WindowInsetsCompat.Type.systemBars()) else bars.show(WindowInsetsCompat.Type.systemBars())
        onDispose { bars.show(WindowInsetsCompat.Type.systemBars());activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
    BackHandler(tab!=0 || instrument){if(instrument)instrument=false else tab=0}
    var lastMidi by rememberSaveable { mutableStateOf(state.sequence.selected) }
    LaunchedEffect(state.sequence.selected){
        if(state.sequence.selected!=lastMidi && tab==1 && state.sequence.selected.isNotBlank())tab=0
        lastMidi=state.sequence.selected
    }
    MaterialTheme(colorScheme=colors) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
                if(tab==0) {
                    BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                        val panelWidth=if(landscape) minOf(420.dp,maxWidth*.6f) else maxWidth
                        val keyboardHeight=(maxHeight*.29f).coerceIn(96.dp,180.dp)
                        Column(Modifier.fillMaxSize()) {
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                if(state.sequence.rollVisible)PianoRoll(state,Modifier.fillMaxSize())
                                else Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text(state.preset?.name ?: "音源を選択",style=MaterialTheme.typography.headlineSmall,color=MaterialTheme.colorScheme.secondary)}
                                Surface(Modifier.align(Alignment.TopCenter).fillMaxWidth(),color=MaterialTheme.colorScheme.background.copy(alpha=.9f)) {
                                    PerformanceToolbar(state,model,export,{tab=1},{instrument=!instrument},{tab=3})
                                }
                                if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp).align(Alignment.TopCenter))
                                Column(Modifier.align(Alignment.TopStart).padding(start=12.dp,top=if(state.sequence.song!=null && !state.sequence.recording)78.dp else 54.dp).widthIn(max=340.dp)) {
                                    Text(if(state.sequence.recording)"REC · ${durationLabel(state.sequence.recordingMicros)} · ${inputLabel(state)}" else if(state.sequence.playing)state.sequence.title else if(state.sequence.live.held.isNotEmpty())"LIVE · ${inputLabel(state)}" else state.sequence.title,style=MaterialTheme.typography.labelSmall,color=if(state.sequence.recording)Color(0xffe6a397)else MaterialTheme.colorScheme.secondary,maxLines=1)
                                    if(notice || state.busy || !state.ready)Text(state.message,style=MaterialTheme.typography.bodySmall,maxLines=2)
                                }
                                KeyboardTools(state,model,Modifier.align(Alignment.BottomEnd).padding(4.dp))
                                if(instrument)Surface(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(panelWidth),tonalElevation=4.dp,shadowElevation=8.dp) {
                                    InstrumentPanel(state,model,{instrument=false},{instrument=false;tab=2})
                                }
                            }
                            PianoKeyboard(state,model,Modifier.fillMaxWidth().height(keyboardHeight))
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
                        PocketIconButton(PocketIcon.Back,"演奏に戻る",onClick={tab=0})
                        Text(listOf("演奏","MIDIライブラリ","音源ライブラリ","設定")[tab],Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
                        PocketIconButton(PocketIcon.Midi,"MIDIライブラリ",selected=tab==1,onClick={tab=1})
                        PocketIconButton(PocketIcon.Library,"音源ライブラリ",selected=tab==2,onClick={tab=2})
                        PocketIconButton(PocketIcon.Settings,"設定",selected=tab==3,onClick={tab=3})
                    }
                    if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(state.message,Modifier.padding(horizontal=20.dp,vertical=8.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.secondary,maxLines=3)
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=20.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        when(tab) {
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
                            Row { Text("演奏画面は横向きを優先",Modifier.weight(1f));Switch(checked=state.landscapePerformance,onCheckedChange=model::landscapePerformance) }
                            Text("オフにすると端末の向きに従います。設定・ライブラリ・ファイル選択は回転を制限しません。",style=MaterialTheme.typography.bodySmall)
                            Row { Text("カタカナのドレミ",Modifier.weight(1f));Switch(checked=state.sequence.noteLabels,onCheckedChange=model::noteLabels) }
                            Row { Text("ロールビュー",Modifier.weight(1f));Switch(checked=state.sequence.rollVisible,onCheckedChange=model::rollVisible) }
                            Parameter("ロール表示時間 (${state.sequence.lookAhead.toInt()} 秒)",state.sequence.lookAhead,2f..10f,true,model::lookAhead)
                            Text("鍵盤の拡大アイコンで88鍵／2オクターブを切り替えます。矢印でオクターブを移動。",style=MaterialTheme.typography.bodySmall)
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
                            TextButton(onClick={licenses=true}){Text("エンコーダーのライセンス・ソース") }
                            Text("PianoPocket 0.4.1 alpha · Dark theme\nHelsinki v1.6は実ファイルでホスト検証。この版のUI・USB/BLE・レイテンシは開発環境で実機未検証。Windows VSTは別フォルダーの実験手順で扱います。",style=MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            if(licenses)AlertDialog(onDismissRequest={licenses=false},title={Text("エンコーダーのライセンス")},text={Text(licenseText,Modifier.verticalScroll(rememberScrollState()),style=MaterialTheme.typography.bodySmall)},confirmButton={TextButton(onClick={licenses=false}){Text("閉じる")}})
            deleting?.let { entry -> AlertDialog(onDismissRequest={deleting=null},title={Text("音源フォルダーを削除")},text={Text("${entry.name} と同じインポートに含まれる全プリセット・サンプルを削除します。")},confirmButton={TextButton(onClick={model.delete(entry);deleting=null}){Text("削除")}},dismissButton={TextButton(onClick={deleting=null}){Text("キャンセル")}}) }
        }
    }
}
@Composable internal fun Parameter(label: String,value: Float,range: ClosedFloatingPointRange<Float>,enabled: Boolean,onChange:(Float)->Unit) {
    Column { Row { Text(label,Modifier.weight(1f),style=MaterialTheme.typography.labelLarge);Text("%.2f".format(value),style=MaterialTheme.typography.labelMedium) };Slider(value=value.coerceIn(range.start,range.endInclusive),onValueChange=onChange,valueRange=range,enabled=enabled) }
}
@Composable private fun BufferControl(state: PocketState,model: PocketViewModel) {
    var pending by remember(state.bursts) { mutableFloatStateOf(state.bursts.toFloat()) }
    Column { Text("バッファ (${pending.toInt()} bursts)");Slider(value=pending,onValueChange={pending=it},valueRange=1f..8f,steps=6,enabled=!state.busy,onValueChangeFinished={model.audio(bursts=pending.toInt())}) }
}
@Composable internal fun IrMenuControl(menu: dev.pianopocket.library.IrMenu,enabled: Boolean,onSelect:(Int)->Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box { OutlinedButton(onClick={expanded=true},enabled=enabled){Text("${menu.name}: ${menu.options[menu.selected].name}")};DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) { menu.options.forEachIndexed { index,choice->DropdownMenuItem(text={Text(choice.name)},onClick={expanded=false;onSelect(index)}) } } }
}
@Composable private fun DeviceMenu(state: PocketState,model: PocketViewModel) { var expanded by remember { mutableStateOf(false) };Box { OutlinedButton(onClick={expanded=true},enabled=!state.busy){Text(state.outputs.firstOrNull { it.id==state.device }?.label ?: "システム既定")};DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) { state.outputs.forEach { d->DropdownMenuItem(text={Text(d.label)},onClick={expanded=false;model.audio(device=d.id)}) } } } }
@Composable private fun ChannelMenu(state: PocketState,model: PocketViewModel) { var expanded by remember { mutableStateOf(false) };Box { OutlinedButton(onClick={expanded=true}){Text(if(state.channel<0)"全チャンネル" else "Channel ${state.channel+1}")};DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) { (-1..15).forEach { ch->DropdownMenuItem(text={Text(if(ch<0)"全チャンネル" else "Channel ${ch+1}")},onClick={expanded=false;model.channel(ch)}) } } } }

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
fun PocketScreen(model: PocketViewModel,import:()->Unit,bluetooth:()->Unit,importMidi:()->Unit,export:(dev.pianopocket.sequencer.ExportFormat,Int,Int,Int,dev.pianopocket.video.VideoOptions)->Unit,preview:(Int,Int,dev.pianopocket.video.VideoOptions)->Unit) {
    val state by model.state.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var settingsPage by rememberSaveable { mutableStateOf(SettingsPage.Overview) }
    val contentScroll=rememberScrollState()
    LaunchedEffect(tab,settingsPage){contentScroll.scrollTo(0)}
    LaunchedEffect(tab){if(tab!=3)settingsPage=SettingsPage.Overview}
    var uiHidden by rememberSaveable { mutableStateOf(false) }
    var instrument by rememberSaveable { mutableStateOf(false) }
    var notice by remember { mutableStateOf(true) }
    LaunchedEffect(state.message){notice=true;kotlinx.coroutines.delay(5000);notice=false}
    var licenses by remember { mutableStateOf(false) }
    var licenseText by remember { mutableStateOf("読み込み中") }
    val context=androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(licenses){if(licenses)licenseText=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){context.assets.open("instruments/PocketUpright/NOTICE.txt").bufferedReader().use { it.readText() }+"\n\n"+context.assets.open("instruments/PocketUpright/LICENSE.txt").bufferedReader().use {it.readText()}+"\n\n"+context.assets.open("decoder-licenses.txt").bufferedReader().use {it.readText()}+"\n\n"+context.assets.open("encoder-licenses.txt").bufferedReader().use { it.readText() }}}
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
    val back: ()->Unit = {
        if(uiHidden)uiHidden=false
        else if(instrument)instrument=false
        else if(tab==3 && settingsPage.parent!=null)settingsPage=settingsPage.parent!!
        else tab=0
    }
    BackHandler(tab!=0 || instrument || uiHidden,onBack=back)
    var lastMidi by rememberSaveable { mutableStateOf(state.sequence.selected) }
    LaunchedEffect(state.sequence.selected){
        if(state.sequence.selected!=lastMidi && tab==1 && state.sequence.selected.isNotBlank())tab=0
        lastMidi=state.sequence.selected
    }
    MaterialTheme(colorScheme=colors) {
        state.sequence.previewPath?.let {VideoPreview(it,model)}
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
                if(tab==0) {
                    PerformanceFrameClock(activity,model,state.targetFps)
                    BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                        val panelWidth=if(landscape) minOf(420.dp,maxWidth*.6f) else maxWidth
                        val keyboardHeight=(maxHeight*.29f).coerceIn(96.dp,180.dp)
                        Column(Modifier.fillMaxSize()) {
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                if(state.sequence.rollVisible)PianoRoll(state,model,Modifier.fillMaxSize())
                                else if(!uiHidden)Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text(state.preset?.name ?: "音源を選択",style=MaterialTheme.typography.headlineSmall,color=MaterialTheme.colorScheme.secondary)}
                                if(!uiHidden)Column(Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
                                    Surface(color=MaterialTheme.colorScheme.background.copy(alpha=.9f)) {
                                        PerformanceToolbar(state,model,export,preview,{instrument=false;tab=1},{instrument=false;tab=2},{instrument=!instrument},{instrument=false;settingsPage=SettingsPage.Overview;tab=3},{instrument=false;uiHidden=true})
                                    }
                                    Column(Modifier.padding(start=12.dp,top=2.dp).widthIn(max=340.dp)) {
                                        Text(if(state.sequence.recording)"REC · ${durationLabel(state.sequence.recordingMicros)} · ${inputLabel(state)}" else if(state.sequence.live.held.isNotEmpty() && !state.sequence.playing)"LIVE · ${inputLabel(state)}" else state.sequence.title,style=MaterialTheme.typography.labelSmall,color=if(state.sequence.recording)Color(0xffe6a397)else MaterialTheme.colorScheme.secondary,maxLines=1)
                                        if(notice || state.busy || !state.ready)Text(state.message,style=MaterialTheme.typography.bodySmall,maxLines=2)
                                    }
                                }
                                if(!uiHidden && state.busy)LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp).align(Alignment.TopCenter))
                                if(!uiHidden)KeyboardTools(state,model,Modifier.align(Alignment.BottomEnd).padding(4.dp))
                                if(instrument && !uiHidden)Surface(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(panelWidth),tonalElevation=4.dp,shadowElevation=8.dp) {
                                    InstrumentPanel(state,model,{instrument=false},{instrument=false;tab=2})
                                }
                            }
                            PianoKeyboard(state,model,Modifier.fillMaxWidth().height(keyboardHeight))
                        }
                        if(uiHidden)PocketIconButton(PocketIcon.Eye,"操作UIを表示",selected=true,modifier=Modifier.align(Alignment.TopEnd).padding(4.dp),onClick={uiHidden=false})
                        if(state.showFps)FpsOverlay(activity?.window,Modifier.align(Alignment.BottomStart).padding(start=4.dp,bottom=3.dp))
                    }
                } else {
                    Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
                        PocketIconButton(PocketIcon.Back,if(tab==3 && settingsPage.parent!=null)"前の設定に戻る"else "演奏に戻る",onClick=back)
                        Text(if(tab==3)settingsPage.title else listOf("演奏","MIDIライブラリ","音源ライブラリ")[tab],Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
                        LibraryMenu(enabled=!state.sequence.recording,selected=tab==1 || tab==2,midi={tab=1},instruments={tab=2})
                        PocketIconButton(PocketIcon.Settings,"設定",selected=tab==3,onClick={settingsPage=SettingsPage.Overview;tab=3})
                    }
                    if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
                    if(tab!=3 || notice || state.busy || !state.ready)Text(state.message,Modifier.padding(horizontal=20.dp,vertical=8.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.secondary,maxLines=if(tab==3)2 else 3)
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(contentScroll).padding(horizontal=20.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        when(tab) {
                            1 -> MidiLibraryPanel(state,model,importMidi){tab=0}
                            2 -> {
                            DownloadPanel(state,model)
                            Button(onClick=import,enabled=!state.busy){Text("音源をインポート")}
                            Text("ZIP / ZIP形式のdslibrary、またはdspreset・SFZ＋サンプルフォルダー。",style=MaterialTheme.typography.bodySmall)
                            TextButton(onClick=model::restoreBundled,enabled=!state.busy){Text("内蔵ピアノを読み込む")}
                            TextButton(onClick=model::rescan,enabled=!state.busy){Text("再スキャン")}
                            state.library.forEach { entry ->
                                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                                    Text(entry.name,style=MaterialTheme.typography.titleMedium)
                                    Text("${"%.1f".format((entry.bytes+entry.decodedBytes)/1048576.0)} MiB · ${if(entry.lastUsed==0L)"未使用" else DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(entry.lastUsed))}",style=MaterialTheme.typography.bodySmall)
                                    Text(if(entry.id==state.selected)"読み込み済み・警告を演奏画面で確認" else "互換性は選択時に解析",style=MaterialTheme.typography.bodySmall)
                                    Row { TextButton(onClick={model.load(entry);tab=0},enabled=!state.busy){Text("選択")};TextButton(onClick={deleting=entry},enabled=!state.busy){Text("削除") } }
                                } }
                            }
                            }
                            3 -> SettingsPanel(state,model,settingsPage,{settingsPage=it},bluetooth,{licenses=true},notifications={
                                runCatching {context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,context.packageName))}
                                    .onFailure {model.message("通知設定を開けません")}
                            })
                        }
                    }
                }
            }
            if(licenses)AlertDialog(onDismissRequest={licenses=false},title={Text("ライセンス")},text={Text(licenseText,Modifier.verticalScroll(rememberScrollState()),style=MaterialTheme.typography.bodySmall)},confirmButton={TextButton(onClick={licenses=false}){Text("閉じる")}})
            deleting?.let { entry -> AlertDialog(onDismissRequest={deleting=null},title={Text("音源フォルダーを削除")},text={Text("${entry.name} と同じインポートに含まれる全プリセット・サンプルを削除します。")},confirmButton={TextButton(onClick={model.delete(entry);deleting=null}){Text("削除")}},dismissButton={TextButton(onClick={deleting=null}){Text("キャンセル")}}) }
        }
    }
}
@Composable internal fun Parameter(label: String,value: Float,range: ClosedFloatingPointRange<Float>,enabled: Boolean,onChange:(Float)->Unit) {
    Column { Row { Text(label,Modifier.weight(1f),style=MaterialTheme.typography.labelLarge);Text("%.2f".format(value),style=MaterialTheme.typography.labelMedium) };Slider(value=value.coerceIn(range.start,range.endInclusive),onValueChange=onChange,valueRange=range,enabled=enabled) }
}
@Composable internal fun BufferControl(state: PocketState,model: PocketViewModel) {
    var pending by remember(state.bursts) { mutableFloatStateOf(state.bursts.toFloat()) }
    Column { Text("${if(state.stableAudio)"手動基準バッファ"else "バッファ"} (${pending.toInt()} bursts)");Slider(value=pending,onValueChange={pending=it},valueRange=1f..8f,steps=6,enabled=!state.busy && !state.sequence.recording && !state.sequence.exporting,onValueChangeFinished={model.audio(bursts=pending.toInt())}) }
}
@Composable internal fun IrMenuControl(menu: dev.pianopocket.library.IrMenu,enabled: Boolean,onSelect:(Int)->Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box { OutlinedButton(onClick={expanded=true},enabled=enabled){Text("${menu.name}: ${menu.options[menu.selected].name}")};DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) { menu.options.forEachIndexed { index,choice->DropdownMenuItem(text={Text(choice.name)},onClick={expanded=false;onSelect(index)}) } } }
}
@Composable internal fun DeviceMenu(state: PocketState,model: PocketViewModel) { var expanded by remember { mutableStateOf(false) };val enabled=!state.busy && !state.sequence.recording && !state.sequence.exporting;Box { OutlinedButton(onClick={expanded=true},enabled=enabled){Text(if(state.device==0)"自動" else state.outputs.firstOrNull { it.id==state.device }?.label ?: "自動")};DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) { state.outputs.forEach { d->DropdownMenuItem(text={Text(d.label)},enabled=enabled,onClick={expanded=false;model.audio(device=d.id)}) } } } }
@Composable internal fun ChannelMenu(state: PocketState,model: PocketViewModel) { var expanded by remember { mutableStateOf(false) };Box { OutlinedButton(onClick={expanded=true}){Text(if(state.channel<0)"すべてのチャンネル" else "チャンネル ${state.channel+1}")};DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) { (-1..15).forEach { ch->DropdownMenuItem(text={Text(if(ch<0)"すべてのチャンネル" else "チャンネル ${ch+1}")},onClick={expanded=false;model.channel(ch)}) } } } }

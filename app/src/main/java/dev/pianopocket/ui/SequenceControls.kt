package dev.pianopocket.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.pianopocket.sequencer.*
import java.text.DateFormat
import java.util.Date

fun durationLabel(micros: Long): String {val seconds=(micros/1000000).coerceAtLeast(0);return "%d:%02d".format(seconds/60,seconds%60)}
@Composable internal fun ExportDialog(name: String,audioReady: Boolean,onDismiss:()->Unit,onExport:(ExportFormat,Int,Int)->Unit) {
    var format by remember { mutableStateOf(ExportFormat.WAV24) }
    var rate by remember { mutableIntStateOf(48000) }
    var tail by remember { mutableFloatStateOf(8f) }
    AlertDialog(onDismissRequest=onDismiss,title={Text("書き出し形式")},text={Column(Modifier.verticalScroll(rememberScrollState())) {
        Text(name,maxLines=2)
        ExportFormat.entries.forEach { f->Row { RadioButton(selected=format==f,onClick={format=f});TextButton(onClick={format=f}){Text(f.label)} } }
        if(format.bits>0){
            Row { FilterChip(selected=rate==44100,onClick={rate=44100},label={Text("44.1 kHz")});Spacer(Modifier.width(8.dp));FilterChip(selected=rate==48000,onClick={rate=48000},label={Text("48 kHz")}) }
            Text("残響の追加時間: ${tail.toInt()}秒",style=MaterialTheme.typography.bodySmall)
            Slider(value=tail,onValueChange={tail=it},valueRange=0f..30f,steps=29)
            Text("現在の音源・マイク音量・エフェクトでステレオ音声を生成します。保存先を選ぶ時点の設定を使います。",style=MaterialTheme.typography.bodySmall)
        }else Text("MIDIにはノート・ペダル・テンポなどを保存します。サンプル音源やエフェクト音は含まれません。",style=MaterialTheme.typography.bodySmall)
    }},confirmButton={TextButton(onClick={onExport(format,rate,tail.toInt())},enabled=format.bits==0 || audioReady){Text("保存先を選択")}},dismissButton={TextButton(onClick=onDismiss){Text("キャンセル")}})
}
@Composable
fun MidiLibraryPanel(state: PocketState,model: PocketViewModel,importMidi:()->Unit,onOpen:()->Unit) {
    val seq=state.sequence
    var deleting by remember { mutableStateOf<MidiEntry?>(null) }
    Text("MIDIライブラリ",style=MaterialTheme.typography.titleMedium)
    Text("ファイルを選ぶと現在の音源で演奏します。録音した演奏もここに保存されます。",style=MaterialTheme.typography.bodySmall)
    val cards:List<MidiEntry?> = listOf(null)+seq.library
    cards.chunked(2).forEach { pair->Row(horizontalArrangement=Arrangement.spacedBy(12.dp),modifier=Modifier.fillMaxWidth()){
        pair.forEach { entry->
            Card(modifier=Modifier.weight(1f).height(210.dp),colors=CardDefaults.cardColors(containerColor=if(entry?.id==seq.selected)Color(0xff385b4e)else MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.SpaceBetween) {
                    if(entry==null){Text("＋",style=MaterialTheme.typography.headlineLarge,color=MaterialTheme.colorScheme.primary);Button(onClick=importMidi,enabled=!state.busy && !seq.recording){Text("MIDIを追加")};Text(".mid / .midi · Type 0 / 1",style=MaterialTheme.typography.labelSmall)}
                    else {
                        Text(entry.name,style=MaterialTheme.typography.titleSmall,maxLines=3)
                        Text("${"%.1f".format(entry.bpm)} BPM · ${durationLabel(entry.duration)}",style=MaterialTheme.typography.labelMedium)
                        Text(DateFormat.getDateInstance(DateFormat.SHORT).format(Date(entry.imported)),style=MaterialTheme.typography.labelSmall)
                        Row { TextButton(onClick={model.selectMidi(entry);onOpen()},enabled=!state.busy && !seq.recording){Text(if(entry.id==seq.selected)"選択中"else "開く")};TextButton(onClick={deleting=entry},enabled=!state.busy && !seq.recording){Text("削除")}}
                    }
                }
            }
        }
        if(pair.size==1)Spacer(Modifier.weight(1f))
    } }
    deleting?.let { entry->AlertDialog(onDismissRequest={deleting=null},title={Text("MIDIを削除")},text={Text(entry.name)},confirmButton={TextButton(onClick={model.deleteMidi(entry);deleting=null}){Text("削除")}},dismissButton={TextButton(onClick={deleting=null}){Text("キャンセル")}}) }
    seq.song?.let { song->Text("${seq.title} · ${song.notes.size} notes / ${song.tracks} tracks",style=MaterialTheme.typography.bodySmall);song.warnings.forEach { Text("• $it",style=MaterialTheme.typography.bodySmall) } }
}
fun inputLabel(state: PocketState)=when(state.midiInput){"all"->"全MIDI入力＋画面鍵盤";"screen"->"画面鍵盤";"none"->"入力なし";else->state.inputs.firstOrNull { it.id==state.midiInput }?.label ?: "切断済み"}
@Composable fun MidiInputMenu(state: PocketState,model: PocketViewModel) {
    var expanded by remember {mutableStateOf(false)}
    Box { OutlinedButton(onClick={expanded=true},enabled=!state.busy && !state.sequence.recording){Text(inputLabel(state),maxLines=2)};DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) {
        (listOf(dev.pianopocket.midi.MidiInput("all","全MIDI入力＋画面鍵盤"),dev.pianopocket.midi.MidiInput("screen","画面鍵盤"),dev.pianopocket.midi.MidiInput("none","入力なし"))+state.inputs).forEach { d->DropdownMenuItem(text={Text(d.label)},onClick={expanded=false;model.input(d.id)}) }
    } }
}

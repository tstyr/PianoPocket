package dev.pianopocket.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.pianopocket.sequencer.Practice
import dev.pianopocket.sequencer.PracticeHand

@Composable fun PracticeDialog(state: PocketState,model: PocketViewModel,close:()->Unit) {
    val seq=state.sequence;val song=seq.song;val enabled=state.ready && !state.busy && !seq.recording && !seq.exporting
    AlertDialog(onDismissRequest=close,title={Text("練習")},text={Column(Modifier.verticalScroll(rememberScrollState())) {
        Row {Text("メトロノーム",Modifier.weight(1f));Switch(checked=seq.metronome,onCheckedChange=model::metronome,enabled=enabled)}
        if(song==null){Text("${seq.metronomeBpm.toInt()} BPM");Slider(value=seq.metronomeBpm,onValueChange=model::metronomeBpm,valueRange=20f..300f,enabled=enabled)}
        else Text("MIDIのテンポ・拍子に追従します。",style=MaterialTheme.typography.bodySmall)
        Text("クリック音量");Slider(value=seq.metronomeVolume,onValueChange=model::metronomeVolume,enabled=enabled)
        if(song!=null){
        HorizontalDivider(Modifier.padding(vertical=8.dp))
        Row {Text("鍵盤で練習",Modifier.weight(1f));Switch(checked=seq.guided.enabled,onCheckedChange={model.guidedPractice(seq.guided.copy(enabled=it))},enabled=enabled)}
        Row {Text("正しい鍵盤の入力を待つ",Modifier.weight(1f));Switch(checked=seq.guided.waitForInput,onCheckedChange={model.guidedPractice(seq.guided.copy(waitForInput=it))},enabled=enabled && seq.guided.enabled)}
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){PracticeHand.entries.forEach { hand->FilterChip(selected=seq.guided.hand==hand,onClick={model.guidedPractice(seq.guided.copy(hand=hand))},enabled=enabled,label={Text(when(hand){PracticeHand.BOTH->"両手";PracticeHand.LEFT->"左手";PracticeHand.RIGHT->"右手"})})}}
        Text("練習する手の音は自動再生せず、もう片方が伴奏になります。和音は必要な鍵盤を順に押しても進めます。",style=MaterialTheme.typography.bodySmall)
        if(seq.guided.hand!=PracticeHand.BOTH){var split by remember(seq.guided.splitKey){mutableFloatStateOf(seq.guided.splitKey.toFloat())};Text("左右の分割キー: ${noteLabelLines(split.toInt()).joinToString("")}${split.toInt()/12-1}");Slider(value=split,onValueChange={split=it},onValueChangeFinished={model.guidedPractice(seq.guided.copy(splitKey=split.toInt()))},valueRange=48f..72f,steps=23,enabled=enabled);Text("左右は分割キーで判定します。手が交差する曲は対象トラックでも絞れます。",style=MaterialTheme.typography.bodySmall)}
        Text("速度 · ${"%.0f".format(song.bpm*seq.speed)} BPM",style=MaterialTheme.typography.titleSmall)
        Practice.speeds.chunked(4).forEach { row->Row {row.forEach { speed->FilterChip(selected=seq.speed==speed,onClick={model.speed(speed)},enabled=enabled,label={Text("${speed}×")})}} }
        Text("音程を維持してMIDIの演奏速度を変えます。",style=MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick=model::markA,enabled=enabled){Text("A ${durationLabel(seq.loopA)}")}
            OutlinedButton(onClick=model::markB,enabled=enabled){Text("B ${durationLabel(seq.loopB)}")}
        }
        Text("再生位置を動かしてA／Bを押すと区間を設定します。最短0.1秒。",style=MaterialTheme.typography.bodySmall)
        Row {Text("A–Bリピート",Modifier.weight(1f));Switch(checked=seq.looping,onCheckedChange=model::looping,enabled=enabled && song.duration>=100000)}
        HorizontalDivider(Modifier.padding(vertical=8.dp))
        Text("トラック",style=MaterialTheme.typography.titleSmall)
        (0 until song.tracks).forEach { track->
            Column {
                Text(Practice.trackName(song,track),maxLines=2)
                Row {FilterChip(selected=track !in seq.hiddenTracks,onClick={model.trackVisible(track,track in seq.hiddenTracks)},enabled=enabled,label={Text("表示")});Spacer(Modifier.width(8.dp));FilterChip(selected=track in seq.mutedTracks,onClick={model.trackMuted(track,track !in seq.mutedTracks)},enabled=enabled,label={Text("ミュート")});Spacer(Modifier.width(8.dp));val targets=seq.guided.tracks ?: (0 until song.tracks).toSet();FilterChip(selected=track in targets,onClick={model.guidedPractice(seq.guided.copy(tracks=if(track in targets)targets-track else targets+track))},enabled=enabled,label={Text("練習")})}
            }
        }
        Text("ミュートしたノートは灰色で表示。共有チャンネルのペダル・音量CCは有効です。練習設定は再生用で、書き出しは元のMIDI全体・通常速度です。",style=MaterialTheme.typography.bodySmall)
        }
    }},confirmButton={TextButton(onClick=close){Text("閉じる")}})
}

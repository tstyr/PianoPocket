package dev.pianopocket.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.pianopocket.sequencer.Practice

@Composable fun PracticeDialog(state: PocketState,model: PocketViewModel,close:()->Unit) {
    val seq=state.sequence;val song=seq.song ?: return;val enabled=!state.busy && !seq.recording && !seq.exporting
    AlertDialog(onDismissRequest=close,title={Text("練習")},text={Column(Modifier.verticalScroll(rememberScrollState())) {
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
                Row {FilterChip(selected=track !in seq.hiddenTracks,onClick={model.trackVisible(track,track in seq.hiddenTracks)},enabled=enabled,label={Text("表示")});Spacer(Modifier.width(8.dp));FilterChip(selected=track in seq.mutedTracks,onClick={model.trackMuted(track,track !in seq.mutedTracks)},enabled=enabled,label={Text("ミュート")})}
            }
        }
        Text("ミュートしたノートは灰色で表示。共有チャンネルのペダル・音量CCは有効です。練習設定は再生用で、書き出しは元のMIDI全体・通常速度です。",style=MaterialTheme.typography.bodySmall)
    }},confirmButton={TextButton(onClick=close){Text("閉じる")}})
}

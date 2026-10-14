package dev.pianopocket.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.pianopocket.sequencer.SongTranspose

@Composable
internal fun TransposeDialog(state: PocketState, model: PocketViewModel, close: () -> Unit) {
    val value = state.sequence.transpose
    val enabled = state.sequence.song != null && state.ready && !state.busy && !state.sequence.recording && !state.sequence.exporting
    AlertDialog(onDismissRequest = close, title = { Text("MIDIを転調") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                PocketIconButton(PocketIcon.Back, "半音下げる", enabled = enabled && value > SongTranspose.MIN, onClick = { model.transpose(value - 1) })
                Text("${if(value>0)"+"else ""}$value 半音", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.headlineSmall)
                PocketIconButton(PocketIcon.Forward, "半音上げる", enabled = enabled && value < SongTranspose.MAX, onClick = { model.transpose(value + 1) })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = { model.transpose((value - 12).coerceAtLeast(SongTranspose.MIN)) }, enabled = enabled && value > SongTranspose.MIN) { Text("−1オクターブ") }
                TextButton(onClick = { model.transpose(0) }, enabled = enabled) { Text("元に戻す") }
            }
            TextButton(onClick = { model.transpose((value + 12).coerceAtMost(SongTranspose.MAX)) }, enabled = enabled && value < SongTranspose.MAX) { Text("＋1オクターブ") }
            Text("再生・ロール・書き出しに反映します。画面鍵盤や入力キーボードの音程は変えません。ドラムチャンネルは転調しません。", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(onClick = close) { Text("閉じる") } })
}

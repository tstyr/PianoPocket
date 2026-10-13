package dev.pianopocket.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

@Composable fun DiagnosticsPanel(state: PocketState,model: PocketViewModel) {
    val clipboard=LocalClipboardManager.current
    DisposableEffect(model){onDispose {model.diagnosticMode(0)}}
    Text("音声の切り分け",style=MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        listOf("通常","FXなし","テスト音").forEachIndexed { i,name->FilterChip(selected=state.diagnosticMode==i,onClick={model.diagnosticMode(i)},enabled=state.ready && !state.busy && !state.sequence.recording,label={Text(name)}) }
    }
    Text("同じイヤホンで比較してください。テスト音は小さめの左右別サイン波です。テスト音へ切り替えるとMIDI再生を停止します。FXなしは元のエフェクト設定を変更しません。",style=MaterialTheme.typography.bodySmall)
    Row {
        OutlinedButton(onClick={if(state.diagnosticRecording)model.stopDiagnosis()else model.startDiagnosis()},enabled=state.ready && !state.busy){Text(if(state.diagnosticRecording)"記録を停止"else "60秒記録")}
        TextButton(onClick={clipboard.setText(AnnotatedString(model.diagnosticReport()));model.message("診断結果をコピーしました")}){Text("結果をコピー")}
    }
    Text("XRun増加は出力の供給遅れ、キャッシュミスは読み込み遅れ、ボイス置換は上限到達の手がかりです。数字だけでノイズの原因を断定しません。",style=MaterialTheme.typography.bodySmall)
}

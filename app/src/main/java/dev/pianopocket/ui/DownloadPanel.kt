package dev.pianopocket.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable fun DownloadPanel(state: PocketState,model: PocketViewModel) {
    val pack=state.downloadPack ?: return
    val status=state.download
    Card(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(pack.name,style=MaterialTheme.typography.titleMedium)
        Text(pack.description,style=MaterialTheme.typography.bodySmall)
        Text("無料 · ${pack.license} · VSCO 2 CE / Simon Dalzell, Ivy Audio / Versilian Studios",style=MaterialTheme.typography.bodySmall)
        Text("GitHubから直接取得します。アカウント不要。取得後はオフラインで演奏できます。",style=MaterialTheme.typography.bodySmall)
        if(status.running){
            Text("${status.phase} · ${"%.1f".format(status.bytes/1048576.0)} / ${"%.1f".format(pack.bytes/1048576.0)} MiB")
            LinearProgressIndicator(progress={(status.bytes.toFloat()/pack.bytes).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())
            OutlinedButton(onClick=model::cancelPianoDownload){Text("キャンセル")}
        }else if(state.downloadedInstrument.isNotEmpty()){
            Button(onClick=model::useDownloadedPiano,enabled=!state.busy && !state.sequence.exporting){Text("高品質ピアノを選択")}
        }else {
            Text("通信量 約${"%.0f".format(pack.bytes/1048576.0)} MiB · 空き容量 約${pack.requiredBytes/1048576} MiBが必要です",style=MaterialTheme.typography.bodySmall)
            Button(onClick=model::downloadPiano,enabled=!state.busy && !state.sequence.exporting){Text(if(status.id>0)"再開・再試行"else "ダウンロード")}
        }
        if(status.id>0 && !status.running)Text(status.message,style=MaterialTheme.typography.bodySmall)
    }}
}

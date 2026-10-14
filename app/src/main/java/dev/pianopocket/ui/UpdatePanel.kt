package dev.pianopocket.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.pianopocket.update.*

@Composable internal fun UpdatePanel(state: PocketState,model: PocketViewModel){
    val update=state.update;val context=LocalContext.current
    var notes by remember {mutableStateOf(false)}
    OutlinedButton(onClick=model::checkUpdate,enabled=!update.checking && !update.running){Text(if(update.checking)"確認中"else "更新を確認")}
    if(update.message.isNotBlank())Text(update.message,style=MaterialTheme.typography.bodySmall)
    update.info?.let {info->
        if(UpdateCoordinator.supported(info)){
            Text("${info.versionName} · ${"%.1f".format(info.bytes/1048576.0)} MiB",style=MaterialTheme.typography.titleSmall)
            TextButton(onClick={notes=true}){Text("変更点を見る")}
            if(update.running){
                Text(update.phase,style=MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(progress={(update.bytes.toFloat()/info.bytes).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())
                TextButton(onClick=model::cancelUpdate){Text("キャンセル")}
            }else if(update.ready)Button(onClick={UpdateCoordinator.install(context)},enabled=!update.checking && !state.busy && !state.sequence.recording && !state.sequence.exporting){Text("インストール")}
            else Button(onClick=model::downloadUpdate,enabled=!update.checking){Text("APKをダウンロード")}
        }
    }
    Text("GitHubから取得します。自動更新は行いません。",style=MaterialTheme.typography.bodySmall)
    if(notes)AlertDialog(onDismissRequest={notes=false},title={Text(update.info?.versionName ?: "変更点")},text={Text(update.info?.notes ?: "",Modifier.verticalScroll(rememberScrollState()))},confirmButton={TextButton(onClick={notes=false}){Text("閉じる")}})
}

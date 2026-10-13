package dev.pianopocket.ui

import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File

@Composable fun VideoPreview(path: String,model: PocketViewModel) {
    var view by remember {mutableStateOf<VideoView?>(null)}
    var error by remember {mutableStateOf<String?>(null)}
    DisposableEffect(path){onDispose{view?.stopPlayback()}}
    AlertDialog(onDismissRequest=model::closePreview,title={Text("5秒プレビュー")},text={Column {
        AndroidView(factory={context->VideoView(context).apply {
            view=this;setMediaController(MediaController(context));setOnPreparedListener {it.isLooping=false;start()}
            setOnErrorListener {_,_,_->error="プレビューをこの端末で再生できません";true}
            setVideoURI(androidx.core.content.FileProvider.getUriForFile(context,"${context.packageName}.preview",File(path)))
        }},modifier=Modifier.fillMaxWidth().height(220.dp))
        error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        Text("現在位置から最大5秒＋1秒の残響。保存形式・音源設定を調整してから本番を書き出せます。",style=MaterialTheme.typography.bodySmall)
    }},confirmButton={TextButton(onClick=model::closePreview){Text("閉じる")}})
}

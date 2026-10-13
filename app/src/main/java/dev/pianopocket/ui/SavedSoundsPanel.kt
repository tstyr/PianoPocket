package dev.pianopocket.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable fun SavedSoundsPanel(state: PocketState,model: PocketViewModel) {
    var saving by remember {mutableStateOf(false)};var expanded by remember {mutableStateOf(false)};var name by remember {mutableStateOf("")};var deleting by remember {mutableStateOf<String?>(null)}
    var factory by remember {mutableStateOf(false)}
    val enabled=state.ready && !state.busy && !state.sequence.recording && !state.sequence.exporting
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Box {OutlinedButton(onClick={expanded=true},enabled=enabled && state.soundFavorites.isNotEmpty()){Text("お気に入り")};DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) {
            state.soundFavorites.forEach { sound->DropdownMenuItem(text={Text(sound)},onClick={expanded=false;model.recallSound(sound)},trailingIcon={TextButton(onClick={expanded=false;deleting=sound}){Text("削除")}}) }
        }}
        TextButton(onClick={name="";saving=true},enabled=enabled){Text("現在の音を保存")}
        TextButton(onClick={factory=true},enabled=enabled){Text("初期値")}
    }
    Text("音源ごとに設定を自動保存します。お気に入りは32件まで。",style=MaterialTheme.typography.labelSmall)
    if(saving)AlertDialog(onDismissRequest={saving=false},title={Text("お気に入りの音を保存")},text={Column {
        OutlinedTextField(value=name,onValueChange={name=it.take(40)},label={Text("名前")},singleLine=true)
        Text("同じ名前なら置き換えます。マイク・エフェクト・IR・出力音量・発音上限を保存。",style=MaterialTheme.typography.bodySmall)
    }},confirmButton={TextButton(onClick={model.saveSound(name.trim());saving=false},enabled=name.isNotBlank()){Text("保存")}},dismissButton={TextButton(onClick={saving=false}){Text("キャンセル")}})
    deleting?.let { sound->AlertDialog(onDismissRequest={deleting=null},title={Text("お気に入りを削除")},text={Text(sound)},confirmButton={TextButton(onClick={model.deleteSound(sound);deleting=null}){Text("削除")}},dismissButton={TextButton(onClick={deleting=null}){Text("キャンセル")}}) }
    if(factory)AlertDialog(onDismissRequest={factory=false},title={Text("音源の初期設定へ戻す")},text={Text("現在の調整をプリセットの初期値へ戻します。保存したお気に入りは残ります。")},confirmButton={TextButton(onClick={model.resetSound();factory=false}){Text("戻す")}},dismissButton={TextButton(onClick={factory=false}){Text("キャンセル")}})
}

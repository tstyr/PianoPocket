package dev.pianopocket.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun TrackInstrumentPicker(track: Int,state: PocketState,model: PocketViewModel,close:()->Unit){
    var search by remember {mutableStateOf("")}
    val choices=remember(state.library,search){state.library.filter {it.name.contains(search,true)}}
    AlertDialog(onDismissRequest=close,title={Text("トラックの音源")},text={Column {
        OutlinedTextField(value=search,onValueChange={search=it},singleLine=true,label={Text("音源を探す")})
        TextButton(onClick={model.trackInstrument(track,"");close()}){Text("主音源を使う")}
        LazyColumn(Modifier.heightIn(max=340.dp)){
            items(choices,key={it.id}){entry->TextButton(onClick={model.trackInstrument(track,entry.id);close()},enabled=!state.busy && !state.sequence.recording && !state.sequence.exporting){Text(entry.name)}}
        }
    }},confirmButton={TextButton(onClick=close){Text("閉じる")}})
}

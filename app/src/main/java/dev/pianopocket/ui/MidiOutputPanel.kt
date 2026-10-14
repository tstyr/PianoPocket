package dev.pianopocket.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun MidiOutputPanel(state: PocketState, model: PocketViewModel) {
    var devices by remember { mutableStateOf(false) }
    var channels by remember { mutableStateOf(false) }
    val enabled = !state.busy && !state.sequence.recording && !state.sequence.exporting
    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    Text("MIDI出力", style = MaterialTheme.typography.titleSmall)
    Box {
        OutlinedButton(onClick = { devices = true }, enabled = enabled) {
            Text(if (state.midiOutput == "none") "オフ" else state.midiOutputs.firstOrNull { it.id == state.midiOutput }?.label ?: "切断済み")
        }
        DropdownMenu(expanded = devices, onDismissRequest = { devices = false }) {
            DropdownMenuItem(text = { Text("オフ") }, enabled = enabled, onClick = { devices = false; model.midiOutput("none") })
            state.midiOutputs.forEach { output ->
                DropdownMenuItem(text = { Text(output.label) }, enabled = enabled, onClick = { devices = false; model.midiOutput(output.id) })
            }
        }
    }
    OutputModeControl(state, model)
    if (state.midiOutput != "none") {
        Text("MIDI再生と画面鍵盤を送ります。", style = MaterialTheme.typography.bodySmall)
        Box {
            OutlinedButton(onClick = { channels = true }, enabled = enabled && !state.sequence.playing) {
                Text("画面鍵盤：チャンネル ${state.screenMidiChannel + 1}")
            }
            DropdownMenu(expanded = channels, onDismissRequest = { channels = false }) {
                (0..15).forEach { channel -> DropdownMenuItem(text = { Text("チャンネル ${channel + 1}") }, enabled = enabled && !state.sequence.playing, onClick = { channels = false; model.screenMidiChannel(channel) }) }
            }
        }
        Row {
            Text("入力キーボードも出力する", Modifier.weight(1f))
            Switch(checked = state.midiThru, onCheckedChange = model::midiThru, enabled = enabled && !state.sequence.playing)
        }
    }
    Text(state.midiOutputStatus, style = MaterialTheme.typography.bodySmall)
    if (state.midiOutputs.isEmpty()) Text("USB・Bluetooth MIDI機器を接続すると、送信先が表示されます。", style = MaterialTheme.typography.bodySmall)
}

package dev.pianopocket.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.pianopocket.settings.OutputMode

@Composable
internal fun OutputModeControl(state: PocketState, model: PocketViewModel) {
    Text("鳴らす音", style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutputMode.entries.forEach { mode ->
            FilterChip(selected = state.outputMode == mode, onClick = { model.outputMode(mode) },
                enabled = !state.busy && !state.sequence.exporting && !state.sequence.recording &&
                    (mode == OutputMode.INTERNAL || state.midiOutput != "none"),
                label = { Text(mode.label) }, modifier = Modifier.weight(1f))
        }
    }
    if (state.midiOutput == "none") Text("外部MIDIを使うには、MIDI出力の送信先を選びます。", style = MaterialTheme.typography.bodySmall)
}

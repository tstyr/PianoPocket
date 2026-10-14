package dev.pianopocket.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
internal fun LibraryMenu(
    enabled: Boolean = true,
    selected: Boolean = false,
    midi: () -> Unit,
    instruments: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        PocketIconButton(PocketIcon.Midi, "MIDI・音源ライブラリ", enabled = enabled,
            selected = selected, onClick = { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("MIDIライブラリ") }, enabled = enabled,
                onClick = { expanded = false; midi() })
            DropdownMenuItem(text = { Text("音源ライブラリ") }, enabled = enabled,
                onClick = { expanded = false; instruments() })
        }
    }
}

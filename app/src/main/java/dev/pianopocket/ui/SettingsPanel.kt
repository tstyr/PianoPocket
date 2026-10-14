package dev.pianopocket.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal enum class SettingsPage(val title: String) {
    Overview("設定"), Display("表示"), Audio("音の設定"), Midi("MIDIキーボード"),
    Advanced("詳細設定"), AudioTuning("オーディオの調整"), Status("動作状況"),
    Diagnostics("音のトラブル"), About("アプリについて");

    val parent: SettingsPage?
        get() = when (this) {
            Overview -> null
            AudioTuning, Status, Diagnostics -> Advanced
            else -> Overview
        }
}

@Composable
internal fun SettingsPanel(
    state: PocketState,
    model: PocketViewModel,
    page: SettingsPage,
    open: (SettingsPage) -> Unit,
    bluetooth: () -> Unit,
    licenses: () -> Unit,
    notifications: () -> Unit
) {
    val audioEnabled = !state.busy && !state.sequence.recording && !state.sequence.exporting
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (page) {
            SettingsPage.Overview -> {
                SettingsLink("表示", "ドレミ・ロール・画面の向き") { open(SettingsPage.Display) }
                SettingsLink("音の設定", if (state.stableAudio) "音切れを抑える" else "反応を優先") { open(SettingsPage.Audio) }
                SettingsLink("MIDIキーボード", state.midi) { open(SettingsPage.Midi) }
                SettingsLink("アプリについて", "バージョン・ライセンス") { open(SettingsPage.About) }
                TextButton(onClick = { open(SettingsPage.Advanced) }) { Text("詳細設定") }
            }
            SettingsPage.Display -> {
                SettingsSwitch("ドレミを表示", state.sequence.noteLabels, model::noteLabels)
                SettingsSwitch("ロールを表示", state.sequence.rollVisible, model::rollVisible)
                SettingsSwitch("横画面で演奏", state.landscapePerformance, model::landscapePerformance)
                Text("なめらかさ", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(30, 60, 90, 120).forEach { fps ->
                        FilterChip(selected = state.targetFps == fps, onClick = { model.targetFps(fps) },
                            label = { Text("$fps") }, modifier = Modifier.weight(1f))
                    }
                }
                Text("最大fps。実際のなめらかさは端末によって変わります。", style = MaterialTheme.typography.bodySmall)
                SettingsSwitch("FPSを左下に表示", state.showFps, model::showFps)
                Text("ノートの表示時間：${state.sequence.lookAhead.toInt()}秒", style = MaterialTheme.typography.titleSmall)
                Slider(value = state.sequence.lookAhead, onValueChange = model::lookAhead, valueRange = 2f..10f)
            }
            SettingsPage.Audio -> {
                Text("音の出方", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !state.stableAudio, onClick = { model.stableAudio(false) },
                        enabled = audioEnabled, label = { Text("反応を優先") })
                    FilterChip(selected = state.stableAudio, onClick = { model.stableAudio(true) },
                        enabled = audioEnabled, label = { Text("音切れを抑える") })
                }
                Text(if (state.stableAudio) "音切れを抑えるため、少し遅れることがあります。" else "弾いてから音が出るまでの反応を優先します。", style = MaterialTheme.typography.bodySmall)
                Text("音の出力先", style = MaterialTheme.typography.titleSmall)
                DeviceMenu(state, model)
                Text("演奏には有線・USBイヤホンをおすすめします。", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { model.audio() }, enabled = audioEnabled) { Text("音を再接続") }
            }
            SettingsPage.Midi -> {
                Text("使うキーボード", style = MaterialTheme.typography.titleSmall)
                MidiInputMenu(state, model)
                Text("USBキーボードはつなぐだけで使えます。", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = bluetooth) { Text("Bluetoothキーボードを探す") }
                state.ble.forEach { device ->
                    TextButton(onClick = { model.bluetooth(device.address) }) { Text(device.name) }
                }
                Text("入力チャンネル", style = MaterialTheme.typography.titleSmall)
                ChannelMenu(state, model)
            }
            SettingsPage.Advanced -> {
                SettingsLink("オーディオの調整", "バッファ・発音数・メモリ") { open(SettingsPage.AudioTuning) }
                SettingsLink("動作状況", "遅延・負荷・メモリ使用量") { open(SettingsPage.Status) }
                SettingsLink("音のトラブルを調べる", "テスト音・診断記録") { open(SettingsPage.Diagnostics) }
            }
            SettingsPage.AudioTuning -> {
                BufferControl(state, model)
                Parameter("最大発音数", state.voices.toFloat(), 32f..1024f, audioEnabled) { model.voices(it.toInt()) }
                Text("1つの鍵盤でも、マイクや音の層ごとに複数の発音枠を使います。", style = MaterialTheme.typography.bodySmall)
                Parameter("音源メモリ上限（MiB）", state.memoryMiB.toFloat(), 32f..512f, audioEnabled) { model.memory(it.toInt()) }
                Text("メモリ上限は次に音源を読み込むときに適用します。", style = MaterialTheme.typography.bodySmall)
                if (state.stableAudio) Text("安定優先では、音切れに応じてバッファを最大8まで増やします。", style = MaterialTheme.typography.bodySmall)
            }
            SettingsPage.Status -> AudioStatus(state)
            SettingsPage.Diagnostics -> DiagnosticsPanel(state, model)
            SettingsPage.About -> {
                Text("PianoPocket", style = MaterialTheme.typography.titleLarge)
                Text(dev.pianopocket.BuildConfig.VERSION_NAME, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = licenses) { Text("音源・ライブラリのライセンス") }
                TextButton(onClick = notifications) { Text("通知の設定") }
                Text("書き出し・ダウンロードの進行を通知します。", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SettingsLink(title: String, subtitle: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        ListItem(headlineContent = { Text(title) },
            supportingContent = { Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            trailingContent = { Text("›", style = MaterialTheme.typography.headlineSmall) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent))
    }
}

@Composable
private fun SettingsSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        headlineContent = { Text(label) }, trailingContent = { Switch(checked = checked, onCheckedChange = null) })
}

@Composable
private fun AudioStatus(state: PocketState) {
    val stats = state.stats
    StatusRow("音声", "${stats[0].toInt()} Hz / Float stereo")
    StatusRow("バッファ", "${stats[1].toInt()} frames / ${stats[2].toInt()} per burst")
    StatusRow("推定出力遅延", if (stats[4] < 0) "取得不可" else "%.2f ms".format(stats[4]))
    Text("Oboe timestampによる推定です。鍵盤から音までの実測ではありません。", style = MaterialTheme.typography.bodySmall)
    Text("10ms以下は保証しません。Bluetoothイヤホンでは遅延が大きくなることがあります。", style = MaterialTheme.typography.bodySmall)
    HorizontalDivider()
    StatusRow("使用中の発音枠", "${stats[6].toInt()} / ${state.voices}")
    StatusRow("XRun", stats[3].toInt().toString())
    StatusRow("DSPコールバック予算", "%.1f%%".format(stats[5]))
    StatusRow("ボイス置換", stats[15].toLong().toString())
    StatusRow("先読み要求落ち", stats[16].toLong().toString())
    StatusRow("イベント欠落", stats[9].toLong().toString())
    StatusRow("読み込みエラー", stats[11].toLong().toString())
    HorizontalDivider()
    StatusRow("サンプル常駐", "%.1f MiB".format(stats[7] / 1048576))
    StatusRow("キャッシュ", "${stats[17].toInt()} pages")
    StatusRow("プロセスPSS", "%.1f MiB".format(state.processMiB))
    StatusRow("出力保護", "%.2f dB減衰".format(stats[13]))
    StatusRow("出力保護の先読み", "%.2f ms".format(stats[14]))
    StatusRow("保護前ピーク", if (stats[12] <= 0) "−∞ dBFS" else "%.1f dBFS".format(20 * kotlin.math.log10(stats[12])))
    Text("出力保護の減衰が大きい場合は、ミキサーのOutputを下げてください。", style = MaterialTheme.typography.bodySmall)
    if (stats[10] > 0) Text("オーディオ切断を検出しました。「音の設定」で再接続できます。")
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

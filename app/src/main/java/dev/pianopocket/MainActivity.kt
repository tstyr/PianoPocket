package dev.pianopocket

import android.Manifest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import dev.pianopocket.ui.PocketScreen
import dev.pianopocket.ui.PocketViewModel
import dev.pianopocket.sequencer.ExportFormat

class MainActivity:ComponentActivity() {
    private lateinit var model:PocketViewModel
    private var pendingPreset:Uri?=null
    private var pendingFormat=ExportFormat.WAV24
    private var pendingRate=48000
    private var pendingTail=8
    private var pendingBitrate=256
    private val midiFile=registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri->if(uri!=null)model.importMidi(uri) }
    private val saveFile=registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result->
        if(result.resultCode==RESULT_OK)result.data?.data?.let { model.export(it,pendingFormat,pendingRate,pendingTail,pendingBitrate) }
    }
    private val directory=registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { root -> val preset=pendingPreset;pendingPreset=null;if(root!=null && preset!=null)model.importPreset(preset,root) }
    private val file=registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) {
            var name=""
            contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { c -> if(c.moveToFirst())name=c.getString(0) }
            when(name.substringAfterLast('.').lowercase()) {
                "dspreset" -> { pendingPreset=uri;model.message("dspresetと同じフォルダーを選択してください。相対パスの基準になります。");directory.launch(null) }
                "zip","dslibrary" -> model.importArchive(uri)
                else -> model.message("対応形式はdspreset、ZIP、ZIP形式のdslibraryです。SFZ/SF2は未対応です。")
            }
        }
    }
    private val permissions=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted -> if(granted.values.all { it })model.scanBluetooth()else model.message("Bluetooth MIDI検索にはBluetooth権限が必要です。") }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);enableEdgeToEdge();model=ViewModelProvider(this)[PocketViewModel::class.java]
        pendingPreset=savedInstanceState?.getString("presetUri")?.let(Uri::parse)
        pendingFormat=runCatching { ExportFormat.valueOf(savedInstanceState?.getString("exportFormat") ?: "WAV24") }.getOrDefault(ExportFormat.WAV24)
        pendingRate=savedInstanceState?.getInt("exportRate",48000) ?: 48000;pendingTail=savedInstanceState?.getInt("exportTail",8) ?: 8
        pendingBitrate=savedInstanceState?.getInt("exportBitrate",256) ?: 256
        setContent { PocketScreen(model,{file.launch(arrayOf("*/*"))},{ permissions.launch(if(Build.VERSION.SDK_INT>=31)arrayOf(Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT)else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)) },{midiFile.launch(arrayOf("*/*"))},{format,rate,tail,bitrate->
            pendingFormat=format;pendingRate=rate;pendingTail=tail;pendingBitrate=bitrate
            val name=model.state.value.sequence.title.substringBeforeLast('.').replace(Regex("[^\\p{L}\\p{N} _.-]"),"_").take(80).ifBlank { "PianoPocket" }
            saveFile.launch(android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(android.content.Intent.CATEGORY_OPENABLE);type=format.mime;putExtra(android.content.Intent.EXTRA_TITLE,"$name.${format.extension}") })
        }) }
    }
    override fun onStart(){super.onStart();model.foreground(true)}
    override fun onStop(){if(!isChangingConfigurations)model.foreground(false);super.onStop()}
    override fun onSaveInstanceState(outState: Bundle){pendingPreset?.let { outState.putString("presetUri",it.toString()) };outState.putString("exportFormat",pendingFormat.name);outState.putInt("exportRate",pendingRate);outState.putInt("exportTail",pendingTail);outState.putInt("exportBitrate",pendingBitrate);super.onSaveInstanceState(outState)}
}

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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect
import dev.pianopocket.playback.PerformanceSession
import dev.pianopocket.ui.PocketScreen
import dev.pianopocket.ui.PocketViewModel
import dev.pianopocket.sequencer.ExportFormat
import dev.pianopocket.video.VideoOptions
import dev.pianopocket.sequencer.MidiFilePolicy

class MainActivity:ComponentActivity() {
    private lateinit var model:PocketViewModel
    private var pendingPreset:Uri?=null
    private var pendingFormat=ExportFormat.WAV24
    private var pendingRate=48000
    private var pendingTail=8
    private var pendingBitrate=256
    private var pendingVideo=VideoOptions()
    private var pendingExportUri: Uri?=null
    private var pendingPreview=false
    private val playbackNotificationPermission=registerForActivityResult(ActivityResultContracts.RequestPermission()){}
    private val notificationPermission=registerForActivityResult(ActivityResultContracts.RequestPermission()){performPendingExport()}
    private val midiFile=registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri->if(uri!=null)model.importMidi(uri) }
    private val saveFile=registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result->
        if(result.resultCode==RESULT_OK)result.data?.data?.let {pendingExportUri=it;pendingPreview=false;notifyThenExport()}
    }
    private val directory=registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { root -> val preset=pendingPreset;pendingPreset=null;if(root!=null && preset!=null)model.importPreset(preset,root) }
    private val file=registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) {
            var name=""
            contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { c -> if(c.moveToFirst())name=c.getString(0) }
            when(name.substringAfterLast('.').lowercase()) {
                "dspreset","sfz" -> { pendingPreset=uri;model.message("選んだプリセットと同じフォルダーを選択してください。相対パスの基準になります。");directory.launch(null) }
                "sf2","sf3" -> model.importSoundFont(uri)
                "zip","dslibrary" -> model.importArchive(uri)
                else -> model.message("対応形式はdspreset、SFZ、SF2、SF3、ZIP、ZIP形式のdslibraryです。")
            }
        }
    }
    private val permissions=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted -> if(granted.values.all { it })model.scanBluetooth()else model.message("Bluetooth MIDI検索にはBluetooth権限が必要です。") }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);enableEdgeToEdge();model=PerformanceSession.activityCreated(application)
        pendingPreset=savedInstanceState?.getString("presetUri")?.let(Uri::parse)
        pendingFormat=runCatching { ExportFormat.valueOf(savedInstanceState?.getString("exportFormat") ?: "WAV24") }.getOrDefault(ExportFormat.WAV24)
        pendingRate=savedInstanceState?.getInt("exportRate",48000) ?: 48000;pendingTail=savedInstanceState?.getInt("exportTail",8) ?: 8
        pendingBitrate=savedInstanceState?.getInt("exportBitrate",256) ?: 256
        pendingExportUri=savedInstanceState?.getString("pendingExportUri")?.let(Uri::parse);pendingPreview=savedInstanceState?.getBoolean("pendingPreview",false) ?: false
        pendingVideo=runCatching { VideoOptions(savedInstanceState?.getInt("videoFps",30) ?: 30,savedInstanceState?.getInt("videoHeight",720) ?: 720) }.getOrDefault(VideoOptions())
        setContent { PocketScreen(model,{file.launch(arrayOf("*/*"))},{ permissions.launch(if(Build.VERSION.SDK_INT>=31)arrayOf(Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT)else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)) },{midiFile.launch(MidiFilePolicy.mimeTypes)},{format,rate,tail,bitrate,video->
            pendingFormat=format;pendingRate=rate;pendingTail=tail;pendingBitrate=bitrate;pendingVideo=video
            val name=model.state.value.sequence.title.substringBeforeLast('.').replace(Regex("[^\\p{L}\\p{N} _.-]"),"_").take(80).ifBlank { "PianoPocket" }
            saveFile.launch(android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(android.content.Intent.CATEGORY_OPENABLE);type=format.mime;putExtra(android.content.Intent.EXTRA_TITLE,"$name.${format.extension}") })
        },{rate,bitrate,video->pendingRate=rate;pendingBitrate=bitrate;pendingVideo=video;pendingPreview=true;notifyThenExport()}) }
        observePlaybackNotifications()
    }
    private fun observePlaybackNotifications(){
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED){
                model.state.map {it.backgroundPlayback}.distinctUntilChanged().collect {enabled ->
                    val prefs=getSharedPreferences("pianopocket",MODE_PRIVATE)
                    if(enabled && Build.VERSION.SDK_INT>=33 && androidx.core.content.ContextCompat.checkSelfPermission(this@MainActivity,Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED && !prefs.getBoolean("playbackNotificationAsked",false)){
                        prefs.edit().putBoolean("playbackNotificationAsked",true).apply()
                        playbackNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }
        }
    }
    private fun notifyThenExport(){
        val prefs=getSharedPreferences("pianopocket",MODE_PRIVATE)
        if(Build.VERSION.SDK_INT>=33 && androidx.core.content.ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED && !prefs.getBoolean("exportNotificationAsked",false)){prefs.edit().putBoolean("exportNotificationAsked",true).apply();notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)}else performPendingExport()
    }
    private fun performPendingExport(){val uri=pendingExportUri;pendingExportUri=null;val preview=pendingPreview;pendingPreview=false;if(uri!=null)model.export(uri,pendingFormat,pendingRate,pendingTail,pendingBitrate,pendingVideo)else if(preview)model.previewVideo(pendingRate,pendingBitrate,pendingVideo)}
    override fun onStart(){super.onStart();PerformanceSession.activityStarted()}
    override fun onStop(){PerformanceSession.activityStopped(isChangingConfigurations);super.onStop()}
    override fun onDestroy(){PerformanceSession.activityDestroyed(isChangingConfigurations);super.onDestroy()}
    override fun onSaveInstanceState(outState: Bundle){pendingPreset?.let { outState.putString("presetUri",it.toString()) };outState.putString("exportFormat",pendingFormat.name);outState.putInt("exportRate",pendingRate);outState.putInt("exportTail",pendingTail);outState.putInt("exportBitrate",pendingBitrate);outState.putInt("videoFps",pendingVideo.fps);outState.putInt("videoHeight",pendingVideo.height);pendingExportUri?.let {outState.putString("pendingExportUri",it.toString())};outState.putBoolean("pendingPreview",pendingPreview);super.onSaveInstanceState(outState)}
}

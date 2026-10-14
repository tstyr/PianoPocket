package dev.pianopocket.ui

import android.app.Application
import android.media.AudioManager
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.pianopocket.playback.PerformanceSession
import dev.pianopocket.audio.NativeAudio
import dev.pianopocket.library.*
import dev.pianopocket.midi.*
import dev.pianopocket.settings.OutputMode
import dev.pianopocket.settings.SongSettings
import dev.pianopocket.settings.SongSettingsStore
import dev.pianopocket.settings.Preferences
import dev.pianopocket.settings.SoundSettings
import dev.pianopocket.settings.SoundSettingsStore
import dev.pianopocket.settings.SoundRecord
import dev.pianopocket.settings.FavoriteSound
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import dev.pianopocket.audio.AudioWorkQueue
import dev.pianopocket.sequencer.*
import dev.pianopocket.video.VideoOptions
import dev.pianopocket.video.VideoExporter
import dev.pianopocket.video.ExportCoordinator
import dev.pianopocket.video.ExportRequest
import dev.pianopocket.video.ExportRegion
import dev.pianopocket.video.ExportEffect
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

data class OutputDevice(val id: Int, val label: String)
data class PocketState(
    val midiEdit: MidiEditSession? = null,val midiEditSaving: Boolean=false,val midiEditError: String="",
    val downloadPack: dev.pianopocket.download.DownloadPack?=null,val download:dev.pianopocket.download.DownloadStatus=dev.pianopocket.download.DownloadStatus(),val downloadedInstrument: String="",
    val soundFavorites: List<String> = emptyList(),
    val backgroundPlayback: Boolean = false,
    val stableAudio: Boolean = false,
    val diagnosticMode: Int = 0, val diagnosticRecording: Boolean = false,
    val targetFps: Int = 60,
    val showFps: Boolean = false,
    val aacAvailable: Boolean = false, val videoOptions: List<VideoOptions> = emptyList(),
    val articulation: Int = -1,
    val landscapePerformance: Boolean = true, val uiPedal: Boolean = false,
    val library: List<LibraryEntry> = emptyList(), val selected: String = "", val preset: Preset? = null,
    val busy: Boolean = false, val message: String = "準備中", val midi: String = "MIDI未接続",
    val stats: List<Double> = List(18){0.0}, val groups: List<Float> = emptyList(), val volume: Float = .7f,
    val voices: Int = 512, val memoryMiB: Int = 256, val bursts: Int = 2,
    val ble: List<BleMidiDevice> = emptyList(), val outputs: List<OutputDevice> = emptyList(), val device: Int = 0,
    val channel: Int = -1, val ready: Boolean = false, val processMiB: Float = 0f,
    val inputs: List<MidiInput> = emptyList(), val midiInput: String = "all", val sequence: SequenceState = SequenceState(),
    val outputMode: OutputMode=OutputMode.INTERNAL,
    val midiOutputs: List<MidiOutput> = emptyList(),val midiOutput: String="none",val midiOutputStatus: String="MIDI出力オフ",val midiThru: Boolean=false,val screenMidiChannel: Int=15
)
class PocketViewModel(app: Application):AndroidViewModel(app) {
    private val executor=AudioWorkQueue.executor
    private val audioOwner=AudioWorkQueue.newUiOwner()
    private val worker=executor.asCoroutineDispatcher()
    private val repository=LibraryRepository(app)
    private val preferences=Preferences(app)
    private val soundStore=SoundSettingsStore(File(app.filesDir,"sound-settings"))
    private var soundSaveJob: Job?=null
    private var trackBanks: List<TrackBank> = emptyList()
    private val songStore=SongSettingsStore(File(app.filesDir,"song-settings"))
    private var lastSongSave: Pair<String,SongSettings>?=null
    private val midiLibrary=MidiLibrary(app)
    private val recorder=MidiRecorder()
    private val diagnostics=dev.pianopocket.audio.DiagnosticSession()
    private val live=LiveNotes()
    private val mutable=MutableStateFlow(PocketState(backgroundPlayback=preferences.backgroundPlayback,stableAudio=preferences.stableAudio,targetFps=preferences.targetFps,showFps=preferences.showFps,landscapePerformance=preferences.landscapePerformance,voices=preferences.voiceLimit,memoryMiB=preferences.memoryMiB,bursts=preferences.bursts, sequence=SequenceState(metronomeBpm=preferences.metronomeBpm,metronomeVolume=preferences.metronomeVolume,exportSelection=preferences.exportSelection,noteLabels=preferences.noteLabels,rollVisible=preferences.rollVisible,lookAhead=preferences.lookAhead)))
    val state=mutable.asStateFlow()
    @Volatile private var foreground=false
    @Volatile private var backgroundServiceActive=false
    @Volatile private var focusAvailable=true
    val isForeground: Boolean get()=foreground
    private val performanceActive: Boolean get()=(foreground || backgroundServiceActive) && focusAvailable
    private var initialized=false
    private val midi=MidiController(app,{ text->mutable.update { it.copy(midi=text) } },{ list->mutable.update { it.copy(ble=list) } },{ list->
        val removed=state.value.inputs.any { old->list.none { it.id==old.id } }
        if(removed){live.clear();if(state.value.sequence.recording)viewModelScope.launch { stopRecording() }}
        mutable.update { it.copy(inputs=list,midiInput=if(it.midiInput !in listOf("all","none","screen") && list.none { d->d.id==it.midiInput })"none" else it.midiInput) }
    },::receiveMidi,{available,id,message->mutable.update {it.copy(midiOutputs=available,midiOutput=id,midiOutputStatus=message)};viewModelScope.launch {applyOutputRoute()}})
    init {
        mutable.update {it.copy(downloadPack=dev.pianopocket.download.DownloadCatalog.load(app))}
        viewModelScope.launch {dev.pianopocket.download.DownloadCoordinator.status.collect {status->
            mutable.update {it.copy(download=status)}
            if(!status.running && status.id>0 && status.success)work {refreshLibrary()}
        }}
        work {
            NativeAudio.stability(preferences.stableAudio)
            mutable.update { it.copy(aacAvailable=AacExporter.available(),videoOptions=VideoExporter.supportedOptions()) }
            val pid=android.os.Process.myPid().toString()
            getApplication<Application>().cacheDir.listFiles().orEmpty().filter { it.name.startsWith("render-") && it.extension in listOf("wav","flac","mp3","m4a","mp4","mov") && it.name.split('-').getOrNull(1)!=pid && !ExportCoordinator.running }.forEach { it.delete() }
            File(getApplication<Application>().cacheDir,"previews").listFiles().orEmpty().filter {it.name.startsWith("render-") && it.name.split('-').getOrNull(1)!=pid && !ExportCoordinator.running}.forEach {it.delete()}
            mutable.update { it.copy(sequence=it.sequence.copy(library=midiLibrary.entries())) }
        }
        viewModelScope.launch {
            var previousRunning=false;var previousPreview: String?=null
            ExportCoordinator.status.collect { result->
                applyOutputRoute()
                val resume=previousRunning && !result.running && result.previewPath==null || previousPreview!=null && result.previewPath==null && !result.running
                mutable.update { it.copy(uiPedal=if(result.running)false else it.uiPedal,busy=if(result.running)true else if(previousRunning)false else it.busy,ready=if(result.running || result.previewPath!=null)false else it.ready,message=if(result.id>0)result.message else it.message,sequence=it.sequence.copy(exporting=result.running,exportProgress=result.progress,exportPhase=result.phase,exportEtaSeconds=result.etaSeconds,previewPath=result.previewPath,playing=if(result.running)false else it.sequence.playing)) }
                previousRunning=result.running;previousPreview=result.previewPath
                syncBackgroundService()
                if(resume && performanceActive)work {openAudioWorker(true);mutable.update {it.copy(message=result.message)}}
            }
        }
        viewModelScope.launch {
            while(isActive){delay(1000);withContext(worker){runCatching {saveCurrentSong(throttled=true)}}}
        }
        viewModelScope.launch {
            while(isActive){delay(100);if(foreground && !displayFramesActive || !foreground && backgroundServiceActive)displayFrame()}
        }
        viewModelScope.launch {
            while(isActive){delay(500);if(foreground)withContext(worker){val values=NativeAudio.stats().toList();val pss=android.os.Debug.getPss()/1024f;val recording=diagnostics.capture(System.nanoTime(),state.value.diagnosticMode,values);mutable.update { it.copy(stats=values,processMiB=pss,diagnosticRecording=recording) };refreshOutputs()}}
        }
    }
    @Volatile var displayFramesActive=false
    fun targetFps(value: Int){if(value !in listOf(30,60,90,120))return;preferences.targetFps=value;mutable.update { it.copy(targetFps=value) }}
    fun displayFrame() {
        if(!performanceActive)return
        val transport=NativeAudio.transport()
        if(!foreground){
            mutable.update {s->s.copy(sequence=s.sequence.copy(position=(transport[0]*1000000).toLong(),
                playing=!s.sequence.exporting && s.sequence.previewPath==null && transport[1]>0,
                recordingMicros=if(s.sequence.recording)recorder.elapsed()else s.sequence.recordingMicros))}
            if(state.value.sequence.recording && !recorder.isRecording())stopRecording()
            return
        }
        val snapshot=live.snapshot();val practice=NativeAudio.practiceStatus()
        val waiting=practice[0]!=0L;val expected=if(waiting)(0..127).filter {((practice[1+it/64] ushr (it%64)) and 1L)!=0L}.toSet()else emptySet()
        mutable.update { s->s.copy(message=if(transport[2]>0)"MIDIのイベント密度が処理上限を超えたため、再生を停止しました。"else s.message,sequence=s.sequence.copy(position=(transport[0]*1000000).toLong(),playing=!s.sequence.exporting && s.sequence.previewPath==null && transport[1]>0,waiting=waiting,waitingKeys=expected,live=snapshot,recordingMicros=if(s.sequence.recording)recorder.elapsed()else s.sequence.recordingMicros,exportProgress=if(s.sequence.exporting)ExportCoordinator.status.value.progress else s.sequence.exportProgress)) }
        if(state.value.sequence.recording && !recorder.isRecording())stopRecording()
    }
    private fun work(action: suspend () -> Unit) { viewModelScope.launch { withContext(worker) { try { action() } catch(e: Exception) { NativeAudio.abort();mutable.update { it.copy(busy=false,message=e.message ?: e.javaClass.simpleName) } } catch(e: OutOfMemoryError) { NativeAudio.abort();mutable.update { it.copy(busy=false,message="メモリ不足。音源を減らすかメモリ設定を下げて再試行してください。") } } } } }
    fun foreground(active: Boolean) {
        if(foreground==active)return
        foreground=active
        if(active)focusAvailable=true
        applyOutputRoute()
        if(!active){diagnosticMode(0);stopDiagnosis();if(!backgroundServiceActive && state.value.sequence.recording)stopRecording()}
        if(active){if(!ExportCoordinator.running && ExportCoordinator.status.value.previewPath==null)work {openAudioWorker()} }
        else work {saveCurrentSong();soundSaveJob?.cancel();saveCurrentSound();if(!performanceActive)suspendAudioWorker()}
    }
    fun backgroundPlayback(enabled: Boolean){
        preferences.backgroundPlayback=enabled;mutable.update {it.copy(backgroundPlayback=enabled)}
        if(!enabled){panic();PerformanceSession.stop(getApplication())}else syncBackgroundService()
    }
    fun backgroundService(active: Boolean){
        backgroundServiceActive=active;applyOutputRoute()
        if(active && !state.value.ready)work {openAudioWorker()}
        if(!active && !foreground)work {saveCurrentSong();saveCurrentSound();suspendAudioWorker()}
    }
    fun backgroundStartFailed(){
        preferences.backgroundPlayback=false;mutable.update {it.copy(backgroundPlayback=false,message="背景演奏を開始できません。アプリを開いて演奏してください。")}
    }
    fun audioFocus(available: Boolean){
        focusAvailable=available;applyOutputRoute()
        if(!available){panic();work {suspendAudioWorker()}}else if(performanceActive)work {openAudioWorker()}
    }
    fun backgroundInterrupted(message: String){
        audioFocus(false);preferences.backgroundPlayback=false
        mutable.update {it.copy(backgroundPlayback=false,message=message)}
    }
    private fun syncBackgroundService(){
        viewModelScope.launch {
            val s=state.value
            if(!s.backgroundPlayback || s.sequence.exporting || s.sequence.previewPath!=null)PerformanceSession.stop(getApplication())
            else if(foreground && s.ready)PerformanceSession.start(getApplication())
        }
    }
    private fun suspendAudioWorker(){
        NativeAudio.event(10,0,0,0,0f);live.clear();NativeAudio.panic();NativeAudio.stop()
        mutable.update {it.copy(ready=false,uiPedal=false,sequence=it.sequence.copy(playing=false))}
    }
    private fun openAudioWorker(forceReload: Boolean=false) {
        if(!performanceActive || ExportCoordinator.running || ExportCoordinator.status.value.previewPath!=null)return
        if(state.value.ready && !forceReload && !ExportCoordinator.performanceUnloaded){syncBackgroundService();return}
        check(NativeAudio.start(state.value.device,state.value.bursts)){"オーディオ出力を開始できません"}
        if(!initialized){mutable.update {it.copy(busy=true,message="内蔵ピアノを準備しています")};val entries=repository.ensureBundled();mutable.update {it.copy(library=entries,busy=false)};refreshLibrary();initialized=true}
        val entry=state.value.library.firstOrNull {it.id==(state.value.selected.ifBlank {preferences.lastInstrument})} ?: state.value.library.firstOrNull()
        if(entry!=null && (state.value.preset==null || forceReload || ExportCoordinator.performanceUnloaded))loadInternal(entry)else mutable.update {it.copy(ready=true)}
        if(state.value.sequence.song==null){val last=state.value.sequence.library.firstOrNull {it.id==preferences.lastMidi};if(last!=null)selectSong(last,midiLibrary.load(last))else installPractice(state.value.sequence)}
        syncBackgroundService()
    }
    private fun refreshOutputs() {
        val devices=getApplication<Application>().getSystemService(AudioManager::class.java).getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        mutable.update { it.copy(outputs=listOf(OutputDevice(0,"システム既定"))+devices.map { d->OutputDevice(d.id,d.productName.toString()) }) }
    }
    fun load(entry: LibraryEntry) = work { loadInternal(entry) }
    private fun loadInternal(entry: LibraryEntry,override: SoundSettings?=null,factory: Boolean=false,tracks: Map<Int,String> = state.value.sequence.trackInstruments) {
        soundSaveJob?.cancel();saveCurrentSound()
        diagnosticMode(0)
        NativeAudio.event(10,0,0,0,0f);live.clear()
        mutable.update { it.copy(busy=true,message="XMLと参照WAVを確認中") }
        val base=repository.parse(entry) {p->mutable.update {it.copy(message=p)}}
        val schema=SoundSettings.schema(base)
        val readResult=runCatching {soundStore.read(entry.id,schema)}
        if(readResult.isFailure)soundStore.backupCorrupt(entry.id,schema)
        val record=readResult.getOrDefault(SoundRecord())
        val saved=if(factory)null else (override ?: record.last)?.takeIf {it.valid(base)}
        val settingsWarning=if(readResult.isFailure)listOf("保存設定が壊れていたため退避し、初期値を使います。")else if(!factory && record.last!=null && saved==null)listOf("保存設定が現在の音源に適合しないため初期値を使います。")else emptyList()
        val preset=base.copy(warnings=base.warnings+settingsWarning,controls=base.controls.mapIndexed { i,c->c.copy(value=saved?.controls?.get(i) ?: c.value) },menus=base.menus.mapIndexed { i,m->m.copy(selected=saved?.menus?.get(i) ?: m.selected) })
        NativeAudio.begin(state.value.memoryMiB)
        preset.regions.forEachIndexed { i,r ->
            NativeAudio.region(repository.resolve(entry,r.path).path,r.ints,r.floats)
            if(i%16==0)mutable.update { it.copy(message="音源準備 ${i+1}/${preset.regions.size}") }
        }
        preset.velocityCurves.forEach { (group,curve)->NativeAudio.velocityCurve(group,curve) }
        var configured=preset
        var initialGroups=preset.groupVolumes
        preset.controls.forEach { c->val reflected=reflectTargets(configured,initialGroups,c,c.value);configured=reflected.first;initialGroups=reflected.second }
        preset.menus.forEach { menu->val option=menu.options[menu.selected];configured=configured.copy(effects=configured.effects.mapIndexed { i,fx->if(i==option.effectIndex)fx.copy(ir=option.path)else fx }) }
        if(saved!=null){initialGroups=saved.groups;configured=configured.copy(effects=configured.effects.mapIndexed { i,fx->fx.copy(params=saved.effects[i].toFloatArray()) })}
        configured.effects.forEach { fx -> NativeAudio.effect(fx.type,fx.params,if(fx.ir.isBlank())"" else repository.resolve(entry,fx.ir).path) }
        val extras=stageTrackBanks(configured,entry.id,tracks)
        NativeAudio.commit();trackBanks=extras
        configured=configured.copy(warnings=configured.warnings+extras.flatMap {bank->bank.preset.warnings.map {"${bank.entry.name}: $it"}})
        ExportCoordinator.performanceRestored()
        val voices=(saved?.voices ?: preferences.voiceLimit).coerceIn(32,1024)
        val output=saved?.output ?: .7f
        NativeAudio.event(8,0,voices,0,0f);NativeAudio.event(4,0,0,0,state.value.outputMode.localVolume(output,state.value.midiOutput!="none"))
        initialGroups.forEachIndexed { i,v -> NativeAudio.event(6,0,i,0,v) };extras.forEach {bank->bank.groups.forEachIndexed {i,v->NativeAudio.event(6,0,bank.offset+i,0,v)}}
        configured.controls.filter { c->c.bindings.any { it.type=="master" } }.forEach { applyControl(it,it.value) }
        if(state.value.sequence.song!=null)NativeAudio.seekTimeline(state.value.sequence.position)
        repository.used(entry);preferences.lastInstrument=entry.id
        if(!performanceActive)NativeAudio.stop()
        mutable.update { it.copy(articulation=configured.regions.map {r->r.ints.getOrElse(27){-1}}.firstOrNull {it>=0} ?: -1,preset=configured,selected=entry.id,busy=false,groups=initialGroups,voices=voices,volume=output,soundFavorites=record.favorites.map { it.name },ready=performanceActive,message="${preset.regions.size} zones · ${preset.warnings.size} 件の互換性警告") }
        saveCurrentSound();syncBackgroundService()
    }
    private fun stageTrackBanks(main: Preset,mainId: String,choices: Map<Int,String>): List<TrackBank>{
        val routing=TrackRouting.plan(mainId,choices);var offset=main.groups.size
        val result=routing.instruments.mapIndexed {index,id ->
            val entry=state.value.library.firstOrNull {it.id==id} ?: error("トラックに指定した音源がありません。主音源を選び直してください。")
            val base=repository.parse(entry){p->mutable.update {it.copy(message=p)}}
            val saved=runCatching {soundStore.read(id,SoundSettings.schema(base)).last}.getOrNull()?.takeIf {it.valid(base)}
            var preset=base.copy(controls=base.controls.mapIndexed {i,c->c.copy(value=saved?.controls?.get(i) ?: c.value)},menus=base.menus.mapIndexed {i,m->m.copy(selected=saved?.menus?.get(i) ?: m.selected)})
            var groups=preset.groupVolumes
            preset.controls.forEach {c->val reflected=reflectTargets(preset,groups,c,c.value);preset=reflected.first;groups=reflected.second}
            preset.menus.forEach {menu->val selected=menu.options[menu.selected];preset=preset.copy(effects=preset.effects.mapIndexed {i,fx->if(i==selected.effectIndex)fx.copy(ir=selected.path)else fx})}
            if(saved!=null){groups=saved.groups;preset=preset.copy(effects=preset.effects.mapIndexed {i,fx->fx.copy(params=saved.effects[i].toFloatArray())})}
            require(offset+groups.size<=256){"マイク・グループの合計が256を超えています。音源を減らしてください。"}
            val bank=index+1
            preset.regions.forEach {r->val mapped=TrackRouting.region(r,offset,bank);NativeAudio.region(repository.resolve(entry,r.path).path,mapped.ints,mapped.floats)}
            preset.velocityCurves.forEach {(g,curve)->NativeAudio.velocityCurve(offset+g,curve)}
            preset.effects.forEach {fx->NativeAudio.bankEffect(bank,fx.type,fx.params,if(fx.ir.isBlank())""else repository.resolve(entry,fx.ir).path)}
            val master=(preset.controls.flatMap {c->c.bindings.filter {it.type=="master"}.map {bindingValue(it,c,c.value)}}.lastOrNull() ?: 1f).coerceIn(0f,8f)
            NativeAudio.bankMaster(bank,master)
            TrackBank(bank,entry,preset,groups,offset,master).also {offset+=groups.size}
        }
        routing.tracks.forEach {(track,bank)->NativeAudio.trackRoute(track,bank)}
        return result
    }
    fun trackInstrument(track: Int,id: String)=work {
        val s=state.value;val song=s.sequence.song ?: return@work
        require(track in 0 until song.tracks);if(s.busy || s.sequence.exporting || s.sequence.recording)return@work
        require(id.isEmpty() || s.library.any {it.id==id})
        val choices=if(id.isEmpty())s.sequence.trackInstruments-track else s.sequence.trackInstruments+(track to id)
        TrackRouting.plan(s.selected,choices)
        val entry=s.library.first {it.id==s.selected};loadInternal(entry,tracks=choices)
        rebuildPractice(s.sequence.copy(trackInstruments=choices));saveCurrentSong()
        mutable.update {it.copy(message="トラックの音源を変更しました")}
    }
    fun importSoundFont(uri: Uri)=work {mutable.update {it.copy(busy=true)};val added=repository.importSoundFont(uri){p->mutable.update {it.copy(message=p)}};refreshLibrary();loadInternal(added.first())}
    fun importArchive(uri: Uri)=work { mutable.update { it.copy(busy=true) };val imported=repository.importArchive(uri){p->mutable.update { it.copy(message=p) } };mutable.update { it.copy(library=repository.entries(),busy=false,message="${imported.size} プリセットをインポートしました。ライブラリで選択してください。") } }
    fun importPreset(uri: Uri, tree: Uri)=work { mutable.update { it.copy(busy=true) };val imported=repository.importPreset(uri,tree){p->mutable.update { it.copy(message=p) } };mutable.update { it.copy(library=repository.entries(),busy=false) };loadInternal(imported.first()) }
    fun delete(entry: LibraryEntry)=work {
        val old=state.value;val removed=old.library.filter {it.root==entry.root}.map {it.id}.toSet()
        val mainRemoved=old.selected in removed;val mappedRemoved=old.sequence.trackInstruments.values.any {it in removed}
        if(mainRemoved || mappedRemoved){NativeAudio.suspendPerformance();live.clear();mutable.update {it.copy(ready=false,uiPedal=false)}}
        soundSaveJob?.cancel();removed.forEach(soundStore::delete);repository.delete(entry);refreshLibrary()
        val sequence=old.sequence.copy(trackInstruments=old.sequence.trackInstruments.filterValues {it !in removed},playing=false)
        mutable.update {it.copy(sequence=sequence,preset=if(mainRemoved)null else it.preset,selected=if(mainRemoved)""else it.selected)}
        if(mappedRemoved && !mainRemoved){state.value.library.firstOrNull {it.id==old.selected}?.let {loadInternal(it)};rebuildPractice(sequence)}
        saveCurrentSong();mutable.update {it.copy(message="音源フォルダーを削除しました")}
    }

    fun rescan()=work { repository.rescan();refreshLibrary();mutable.update {it.copy(message="再スキャン完了")} }
    private fun refreshLibrary(){val pack=state.value.downloadPack;mutable.update {it.copy(library=repository.entries(),downloadedInstrument=pack?.let {p->repository.findDownloaded(p.id)?.id} ?: "")}}
    fun downloadPiano(){if(!state.value.busy && !ExportCoordinator.running)dev.pianopocket.download.DownloadCoordinator.start(getApplication())}
    fun cancelPianoDownload(){dev.pianopocket.download.DownloadCoordinator.cancel(getApplication())}
    fun useDownloadedPiano()=work {val id=state.value.downloadPack?.id ?: return@work;val entry=repository.findDownloaded(id) ?: error("音源がありません。再取得してください");loadInternal(entry);refreshLibrary()}
    fun restoreBundled()=work { mutable.update {it.copy(busy=true)};val entry=repository.installBundled().first();mutable.update {it.copy(library=repository.entries(),busy=false)};loadInternal(entry) }
    fun message(text: String){mutable.update { it.copy(message=text) }}
    fun volume(value: Float){NativeAudio.event(4,0,0,0,state.value.outputMode.localVolume(value,state.value.midiOutput!="none"));mutable.update { it.copy(volume=value) };saveSoundSoon()}
    fun group(index: Int,value: Float){NativeAudio.event(6,0,index,0,value);mutable.update { s->s.copy(groups=s.groups.mapIndexed { i,v->if(i==index)value else v }) };saveSoundSoon()}
    fun fx(index: Int, slot: Int, value: Float){NativeAudio.event(7,0,index,slot,value);mutable.update { s->s.copy(preset=s.preset?.copy(effects=s.preset.effects.mapIndexed { i,e->if(i==index)e.copy(params=e.params.copyOf().also { it[slot]=value }) else e })) };saveSoundSoon()}
    private fun applyControl(control: Control,value: Float){
        control.bindings.forEach { b -> NativeAudio.event(when(b.type){"group"->6;"master"->9;else->7},0,b.index,b.slot,bindingValue(b,control,value)) }
    }
    private fun bindingValue(b: Binding,c: Control,value: Float):Float {
        val out=if(b.outMin.isNaN() || b.outMax.isNaN())value else b.outMin+(value-c.min)/(c.max-c.min)*(b.outMax-b.outMin)
        return if(b.decibels)Math.pow(10.0,out/20.0).toFloat()else out
    }
    private fun reflectTargets(preset: Preset,groups: List<Float>,control: Control,value: Float):Pair<Preset,List<Float>> {
        val updated=groups.toMutableList();val fx=preset.effects.map { it.copy(params=it.params.copyOf()) }
        control.bindings.forEach { b->val target=bindingValue(b,control,value);if(b.type=="group")updated[b.index]=target else if(b.type=="effect")fx[b.index].params[b.slot]=target }
        return preset.copy(effects=fx) to updated
    }
    fun control(index: Int,value: Float){state.value.preset?.controls?.getOrNull(index)?.let { c->applyControl(c,value) };mutable.update { s->
        val p=s.preset ?: return@update s;val c=p.controls.getOrNull(index) ?: return@update s;val (changed,groups)=reflectTargets(p,s.groups,c,value)
        s.copy(preset=changed.copy(controls=p.controls.mapIndexed { i,control->if(i==index)control.copy(value=value)else control }),groups=groups)
    };saveSoundSoon()}
    fun voices(value: Int){preferences.voiceLimit=value;NativeAudio.event(8,0,value,0,0f);mutable.update { it.copy(voices=value) };saveSoundSoon()}
    fun impulse(menuIndex: Int,optionIndex: Int)=work {
        val preset=state.value.preset ?: return@work;val menu=preset.menus[menuIndex];val choice=menu.options[optionIndex]
        val entry=state.value.library.first { it.id==state.value.selected };val fx=preset.effects[choice.effectIndex]
        mutable.update { it.copy(busy=true,message="IRを準備中: ${choice.name}") }
        NativeAudio.replaceImpulse(choice.effectIndex,repository.resolve(entry,choice.path).path,fx.params)
        mutable.update { s->s.copy(busy=false,message="IR: ${choice.name}",preset=s.preset?.copy(effects=s.preset.effects.mapIndexed { i,e->if(i==choice.effectIndex)e.copy(ir=choice.path)else e },menus=s.preset.menus.mapIndexed { i,m->if(i==menuIndex)m.copy(selected=optionIndex)else m })) }
        saveCurrentSound()
    }
    fun stableAudio(value: Boolean)=work {preferences.stableAudio=value;NativeAudio.stability(value);mutable.update { it.copy(stableAudio=value,message=if(value)"安定優先: XRun増加時にバッファを増やします"else "低遅延: 手動のバッファ設定を使います") }}
    fun memory(value: Int){preferences.memoryMiB=value;mutable.update { it.copy(memoryMiB=value,message="メモリ上限は次回の音源読み込みから適用されます") }}
    fun audio(device: Int=state.value.device,bursts: Int=state.value.bursts)=work { preferences.bursts=bursts;mutable.update { it.copy(device=device,bursts=bursts) };NativeAudio.panic();check(NativeAudio.start(device,bursts)) { "オーディオ経路を開けません" };state.value.library.firstOrNull { it.id==state.value.selected }?.let { loadInternal(it) } }
    fun channel(value: Int){mutable.update { it.copy(uiPedal=false) };if(state.value.sequence.recording)stopRecording();live.clear();NativeAudio.panic();midi.channel=value;mutable.update { it.copy(channel=value) }}
    fun scanBluetooth()=midi.scanBluetooth()
    fun bluetooth(address: String)=midi.connectBluetooth(address)
    private fun receiveMidi(status: Int,a: Int,b: Int,timestamp: Long,allowOutput: Boolean) {
        if(!performanceActive || state.value.sequence.exporting || !state.value.ready)return
        val flags=if(allowOutput)0 else 256
        when(status and 0xf0){
            0x90 -> NativeAudio.event(flags or if(b==0)1 else 0,status and 15,a,b,0f)
            0x80 -> NativeAudio.event(flags or 1,status and 15,a,b,0f)
            0xb0 -> NativeAudio.event(flags or 2,status and 15,a,b,0f)
            0xe0 -> NativeAudio.event(flags or 3,status and 15,0,0,((b*128+a)-8192)/8192f*2)
            0xa0,0xc0,0xd0 -> NativeAudio.event(flags or 14,status and 15,a,b,(status and 0xf0).toFloat())
        }
        live.event(status,a,b);recorder.capture(status,a,b,timestamp)
    }
    fun key(note: Int,velocity: Int) {
        if(!performanceActive || !state.value.ready || state.value.sequence.exporting)return
        if(velocity>0)NativeAudio.noteOn(note,velocity)else NativeAudio.noteOff(note)
        live.event(if(velocity>0)0x90 else 0x80,note,velocity,16)
        if(state.value.midiInput in listOf("all","screen"))recorder.capture(if(velocity>0)0x9f else 0x8f,note,velocity)
    }
    fun showFps(value: Boolean){preferences.showFps=value;mutable.update { it.copy(showFps=value) }}
    fun landscapePerformance(value: Boolean){preferences.landscapePerformance=value;mutable.update { it.copy(landscapePerformance=value) }}
    fun articulation(key: Int){if(state.value.preset?.articulations?.none {it.key==key}!=false)return;key(key,100);key(key,0);mutable.update {it.copy(articulation=key)}}
    fun pedal(down: Boolean){mutable.update { it.copy(uiPedal=down) };val value=if(down)127 else 0;NativeAudio.cc(64,value);live.event(0xb0,64,value,16);if(state.value.midiInput in listOf("all","screen"))recorder.capture(0xbf,64,value)}
    fun panic(){diagnosticMode(0);mutable.update { it.copy(uiPedal=false) };if(state.value.sequence.recording)stopRecording();NativeAudio.event(10,0,0,0,0f);NativeAudio.panic();live.clear()}
    fun input(id: String){mutable.update { it.copy(uiPedal=false) };if(state.value.sequence.recording)stopRecording();NativeAudio.panic();live.clear();midi.selected=if(id=="screen")"none" else id;mutable.update { it.copy(midiInput=id) }}
    fun midiOutput(id: String){if(state.value.sequence.recording || state.value.sequence.exporting)return;panic();if(id!="none" && state.value.outputMode==OutputMode.INTERNAL)mutable.update {it.copy(outputMode=OutputMode.BOTH)};midi.selectOutput(id)}
    fun outputMode(mode: OutputMode){
        val s=state.value;if(s.busy || s.sequence.exporting || s.sequence.recording || mode!=OutputMode.INTERNAL && s.midiOutput=="none")return
        mutable.update {it.copy(outputMode=mode)};applyOutputRoute()
    }
    private fun applyOutputRoute(){
        val s=state.value;val connected=s.midiOutput!="none"
        NativeAudio.event(4,0,0,0,s.outputMode.localVolume(s.volume,connected))
        midi.performanceActive(performanceActive && !ExportCoordinator.running && ExportCoordinator.status.value.previewPath==null && s.outputMode.sendsMidi(connected))
    }
    fun midiThru(enabled: Boolean){val s=state.value;mutable.update {it.copy(midiThru=enabled)};midi.outputOptions(enabled,s.screenMidiChannel)}
    fun screenMidiChannel(channel: Int){if(channel !in 0..15)return;mutable.update {it.copy(screenMidiChannel=channel)};midi.outputOptions(state.value.midiThru,channel)}
    fun fullKeyboard(value: Boolean){mutable.update { it.copy(sequence=it.sequence.copy(viewport=if(value)KeyboardViewport()else KeyboardViewport.middleTwoOctaves())) }}
    fun zoomKeyboard(factor: Float,anchor: Float,pan: Float){mutable.update { it.copy(sequence=it.sequence.copy(viewport=it.sequence.viewport.transform(factor,anchor,pan))) }}
    fun shiftKeyboard(direction: Int){mutable.update { it.copy(sequence=it.sequence.copy(viewport=it.sequence.viewport.shiftOctave(direction))) }}
    fun noteLabels(value: Boolean){preferences.noteLabels=value;mutable.update { it.copy(sequence=it.sequence.copy(noteLabels=value)) }}
    fun rollVisible(value: Boolean){preferences.rollVisible=value;mutable.update { it.copy(sequence=it.sequence.copy(rollVisible=value)) }}
    fun lookAhead(value: Float){preferences.lookAhead=value;mutable.update { it.copy(sequence=it.sequence.copy(lookAhead=value)) }}
    private fun arrays(song: MidiSong,muted: Set<Int> = emptySet(),guided: dev.pianopocket.sequencer.GuidedPractice=dev.pianopocket.sequencer.GuidedPractice()): Pair<LongArray,IntArray> {
        val messages=Practice.messages(song,muted,guided)
        val times=LongArray(messages.size){messages[it].micros};val data=IntArray(times.size*4)
        messages.forEachIndexed { i,e->data[i*4]=e.status;data[i*4+1]=e.a;data[i*4+2]=e.b;data[i*4+3]=e.track };return times to data
    }
    private fun selectSong(entry: MidiEntry,song: MidiSong){
        saveCurrentSong()
        val saved=songStore.read(entry.id)
        val desiredEntry=state.value.library.firstOrNull {it.id==saved?.instrument} ?: state.value.library.firstOrNull {it.id==state.value.selected}
        val transposed=SongTranspose.apply(song,saved?.transpose ?: 0)
        var fresh=state.value.sequence.copy(library=midiLibrary.entries(),song=transposed,sourceSong=song,
            transpose=0,selected=entry.id,title=entry.name,playing=false,position=0,speed=1f,
            loopA=0,loopB=song.duration,looping=false,hiddenTracks=emptySet(),mutedTracks=emptySet(),trackInstruments=emptyMap(),
            guided=GuidedPractice(),waiting=false,waitingKeys=emptySet())
        if(saved!=null)fresh=saved.restore(fresh)
        fresh=fresh.copy(trackInstruments=fresh.trackInstruments.filterValues {id->state.value.library.any {it.id==id}})
        if(desiredEntry!=null && (desiredEntry.id!=state.value.selected || fresh.trackInstruments!=state.value.sequence.trackInstruments))loadInternal(desiredEntry,tracks=fresh.trackInstruments)
        rebuildPractice(fresh)
        preferences.lastMidi=entry.id
        mutable.update {it.copy(message=if(saved!=null)"曲の設定を復元しました" else "${song.notes.size} notes · ${"%.1f".format(song.bpm)} BPM${if(song.warnings.isEmpty())"" else " · 互換性警告あり"}")}
        saveCurrentSong()
    }
    private fun saveCurrentSong(s: PocketState=state.value,throttled: Boolean=false){
        val seq=s.sequence;if(seq.song==null || seq.selected.isBlank())return
        val record=SongSettings.capture(s.selected,seq)
        val previous=lastSongSave?.takeIf {it.first==seq.selected}?.second
        if(record==previous)return
        if(throttled && seq.playing && previous!=null && record.copy(position=0)==previous.copy(position=0) && kotlin.math.abs(record.position-previous.position)<5_000_000)return
        songStore.write(seq.selected,record);lastSongSave=seq.selected to record
    }

    fun importMidi(uri: Uri)=work { if(state.value.sequence.recording)stopRecording();mutable.update { it.copy(busy=true) };val (entry,song)=midiLibrary.import(uri);selectSong(entry,song);mutable.update { it.copy(busy=false) } }
    fun selectMidi(entry: MidiEntry)=work { selectSong(entry,midiLibrary.load(entry)) }
    fun editMidi(entry: MidiEntry?=state.value.sequence.library.firstOrNull {it.id==state.value.sequence.selected}) {
        if(entry==null || state.value.busy || state.value.sequence.recording || state.value.sequence.exporting || state.value.midiEdit!=null)return
        work {saveCurrentSong();NativeAudio.event(10,0,0,0,0f);NativeAudio.panic();live.clear()
            val document=dev.pianopocket.editor.MidiEditDocument(midiLibrary.load(entry))
            mutable.update {it.copy(midiEdit=MidiEditSession(entry,document),midiEditError="",sequence=it.sequence.copy(playing=false))}
        }
    }
    fun closeMidiEditor(){if(!state.value.midiEditSaving)mutable.update {it.copy(midiEdit=null,midiEditError="")}}
    fun saveMidiEdit(document: dev.pianopocket.editor.MidiEditDocument){
        val session=state.value.midiEdit ?: return
        if(state.value.midiEditSaving || session.document!==document || !document.dirty)return
        mutable.update {it.copy(midiEditSaving=true,midiEditError="")}
        viewModelScope.launch {withContext(worker){try {
            val song=document.song();val entry=midiLibrary.save(session.entry.name.substringBeforeLast('.').take(140)+" · 編集.mid",song)
            val old=songStore.read(session.entry.id);if(old!=null)songStore.write(entry.id,old.copy(position=0,looping=false))
            selectSong(entry,song)
            mutable.update {it.copy(midiEdit=null,midiEditSaving=false,message="編集したコピーをMIDIライブラリに保存しました")}
        }catch(e: Exception){mutable.update {it.copy(midiEditSaving=false,midiEditError=e.message ?: "保存できませんでした")}}
        catch(e: OutOfMemoryError){mutable.update {it.copy(midiEditSaving=false,midiEditError="メモリが不足しています。ほかのアプリを閉じて再試行してください。")}}}}
    }
    fun deleteMidi(entry: MidiEntry)=work { if(state.value.sequence.selected==entry.id){NativeAudio.timeline(longArrayOf(),intArrayOf(),0);val seq=state.value.sequence.copy(song=null,sourceSong=null,transpose=0,selected="",title="MIDIを選択",playing=false,position=0,guided=dev.pianopocket.sequencer.GuidedPractice(),waiting=false,waitingKeys=emptySet());installPractice(seq);mutable.update { it.copy(sequence=seq) } };songStore.delete(entry.id);if(lastSongSave?.first==entry.id)lastSongSave=null;midiLibrary.delete(entry);mutable.update { it.copy(sequence=it.sequence.copy(library=midiLibrary.entries())) } }
    fun play()=work {
        val seq=state.value.sequence;val song=seq.song ?: return@work
        if(seq.recording || seq.exporting || !state.value.ready)return@work
        if(!seq.playing && seq.position>=song.duration)NativeAudio.seekTimeline(0)
        NativeAudio.event(10,0,if(seq.playing)0 else 1,0,0f)
    }
    fun seek(micros: Long)=work { val resume=state.value.sequence.playing;NativeAudio.seekTimeline(micros.coerceIn(0,state.value.sequence.song?.duration ?: 0));if(resume)NativeAudio.event(10,0,1,0,0f) }
    fun speed(value: Float){if(value !in Practice.speeds)return;NativeAudio.event(11,0,0,0,value);mutable.update {it.copy(sequence=it.sequence.copy(speed=value))}}
    fun transpose(value: Int)=work {
        val seq=state.value.sequence;val source=seq.sourceSong ?: return@work
        if(seq.recording || seq.exporting || state.value.busy)return@work
        require(value in SongTranspose.MIN..SongTranspose.MAX)
        val song=SongTranspose.apply(source,value)
        rebuildPractice(seq.copy(song=song,transpose=value))
        mutable.update {it.copy(message=if(value==0)"元の音程に戻しました" else "転調 ${if(value>0)"+"else ""}$value 半音"+if(song.notes.size<source.notes.size)" · 音域外の音を除外"else "")}
    }
    fun markA()=work {val seq=state.value.sequence;val duration=seq.song?.duration ?: return@work;require(duration>=100000){"リピートには0.1秒以上必要です"};val a=seq.position.coerceIn(0,duration-100000);val b=if(seq.loopB>a+100000)seq.loopB else duration;configurePracticeLoop(a,b,seq.looping)}
    fun markB()=work {val seq=state.value.sequence;require(seq.position>=seq.loopA+100000){"BはAから0.1秒以上後に設定してください"};configurePracticeLoop(seq.loopA,seq.position,seq.looping)}
    fun looping(enabled: Boolean)=work {val seq=state.value.sequence;configurePracticeLoop(seq.loopA,seq.loopB,enabled)}
    private fun configurePracticeLoop(a: Long,b: Long,enabled: Boolean){val duration=state.value.sequence.song?.duration ?: return;require(!enabled || a>=0 && b<=duration && b-a>=100000){"リピート区間が不正です"};NativeAudio.practiceLoop(a,b,enabled);if(!performanceActive)NativeAudio.stop();mutable.update {it.copy(sequence=it.sequence.copy(loopA=a,loopB=b,looping=enabled))}}
    fun trackVisible(track: Int,visible: Boolean){val seq=state.value.sequence;if(track !in 0 until (seq.song?.tracks ?: 0))return;mutable.update {it.copy(sequence=it.sequence.copy(hiddenTracks=if(visible)it.sequence.hiddenTracks-track else it.sequence.hiddenTracks+track))}}
    fun trackMuted(track: Int,muted: Boolean)=work {
        val seq=state.value.sequence;val song=seq.song ?: return@work;require(track in 0 until song.tracks);val tracks=if(muted)seq.mutedTracks+track else seq.mutedTracks-track;rebuildPractice(seq.copy(mutedTracks=tracks))
    }
    private fun installPractice(seq: dev.pianopocket.sequencer.SequenceState){
        val gates=seq.song?.let {Practice.gates(it,seq.mutedTracks,seq.guided)} ?: emptyList()
        val ticks=if(seq.metronome)seq.song?.let(Practice::beats) ?: emptyList()else emptyList()
        val times=gates.map {it.micros}.toLongArray();val masks=LongArray(gates.size*2)
        gates.forEachIndexed {i,g->g.keys.forEach {key->masks[i*2+key/64]=masks[i*2+key/64] or (1L shl (key%64))}}
        NativeAudio.practicePlan(times,masks,ticks.map {it.micros}.toLongArray(),ticks.map {if(it.accent)1 else 0}.toIntArray(),seq.guided.enabled && seq.guided.waitForInput,seq.metronome,seq.metronomeBpm,seq.metronomeVolume,seq.song!=null)
    }
    private fun rebuildPractice(seq: dev.pianopocket.sequencer.SequenceState){
        val song=seq.song
        if(song!=null){val (times,data)=arrays(song,seq.mutedTracks,seq.guided);NativeAudio.timeline(times,data,song.duration)}
        installPractice(seq)
        if(song!=null){NativeAudio.event(11,0,0,0,seq.speed);NativeAudio.practiceLoop(seq.loopA,seq.loopB,seq.looping);NativeAudio.seekTimeline(seq.position);if(seq.playing)NativeAudio.event(10,0,1,0,0f)}
        if(!performanceActive)NativeAudio.stop();mutable.update {it.copy(sequence=seq.copy(waiting=false,waitingKeys=emptySet()))}
    }
    fun guidedPractice(options: dev.pianopocket.sequencer.GuidedPractice)=work {
        val seq=state.value.sequence;val song=seq.song ?: return@work
        require(!options.enabled || song.notes.any {Practice.target(it.key,it.track,options,seq.mutedTracks)}) {"練習するノートがありません。手・トラック・ミュートを確認してください。"}
        rebuildPractice(seq.copy(guided=options))
    }
    fun metronome(enabled: Boolean)=work {rebuildPractice(state.value.sequence.copy(metronome=enabled))}
    fun metronomeBpm(value: Float){val v=value.coerceIn(20f,300f);preferences.metronomeBpm=v;NativeAudio.event(13,0,0,0,v);mutable.update {it.copy(sequence=it.sequence.copy(metronomeBpm=v))}}
    fun metronomeVolume(value: Float){val v=value.coerceIn(0f,1f);preferences.metronomeVolume=v;NativeAudio.event(13,0,1,0,v);mutable.update {it.copy(sequence=it.sequence.copy(metronomeVolume=v))}}
    fun startRecording(){
        if(state.value.sequence.exporting || !state.value.ready || state.value.busy)return
        if(state.value.midiInput=="none"){message("録音するMIDI入力または画面鍵盤を選択してください。");return}
        NativeAudio.event(10,0,0,0,0f);NativeAudio.panic();live.clear();recorder.start()
        mutable.update { it.copy(uiPedal=false,sequence=it.sequence.copy(recording=true,recordingMicros=0),message="録音中。停止するとMIDIライブラリへ保存します。") }
    }
    fun stopRecording(){
        if(!state.value.sequence.recording)return
        val take=recorder.stop();NativeAudio.panic();live.clear()
        mutable.update { it.copy(uiPedal=false,sequence=it.sequence.copy(recording=false)) }
        // Persist the completed take even if closing the notification clears the session's ViewModel.
        executor.execute {
            try {
                val song=take.toSong();val name="Recording " + java.text.SimpleDateFormat("yyyy-MM-dd HH-mm-ss",java.util.Locale.ROOT).format(java.util.Date())
                val entry=midiLibrary.save(name,song)
                if(AudioWorkQueue.isCurrentUi(audioOwner)){
                    selectSong(entry,song)
                    mutable.update {it.copy(message=if(recorder.limitReached())"録音の時間・イベント上限に達したため保存しました。" else "録音をMIDIライブラリへ保存しました。")}
                }
            }catch(error: Exception){mutable.update {it.copy(message="録音を保存できません: ${error.message}")}}
        }
    }
    private fun saveCurrentSound(s: PocketState=state.value){
        val p=s.preset ?: return;val schema=SoundSettings.schema(p);val record=try{soundStore.read(s.selected,schema)}catch(e: Exception){soundStore.backupCorrupt(s.selected,schema);mutable.update {it.copy(message="保存設定の破損を検出し、退避しました")};SoundRecord()};soundStore.write(s.selected,schema,record.copy(last=SoundSettings.capture(p,s.volume,s.voices,s.groups)))
    }
    private fun saveSoundSoon(){soundSaveJob?.cancel();soundSaveJob=viewModelScope.launch {delay(350);withContext(worker){try{saveCurrentSound()}catch(e: Exception){mutable.update {it.copy(message="音源設定を保存できません: ${e.message}")}}}}}
    fun saveSound(name: String)=work {
        require(name.isNotBlank() && name.length<=40);val s=state.value;val p=s.preset ?: return@work;val schema=SoundSettings.schema(p);val record=soundStore.read(s.selected,schema);require(record.favorites.size<32 || record.favorites.any {it.name==name}){"お気に入りは音源ごとに32件までです"};val settings=SoundSettings.capture(p,s.volume,s.voices,s.groups);val favorites=record.favorites.filterNot {it.name==name}+FavoriteSound(name,settings);soundStore.write(s.selected,schema,record.copy(last=settings,favorites=favorites));mutable.update {it.copy(soundFavorites=favorites.map {f->f.name},message="お気に入り「$name」を保存しました")}
    }
    fun recallSound(name: String)=work {val s=state.value;val p=s.preset ?: return@work;val saved=soundStore.read(s.selected,SoundSettings.schema(p)).favorites.first {it.name==name}.settings;require(saved.valid(p)){"保存設定と音源の構成が一致しません"};loadInternal(s.library.first {it.id==s.selected},saved);mutable.update {it.copy(message="お気に入り「$name」を読み込みました")} }
    fun resetSound()=work {val s=state.value;loadInternal(s.library.first {it.id==s.selected},factory=true);mutable.update {it.copy(message="音源の初期値へ戻しました")} }
    fun deleteSound(name: String)=work {val s=state.value;val p=s.preset ?: return@work;val schema=SoundSettings.schema(p);val record=soundStore.read(s.selected,schema);val favorites=record.favorites.filterNot {it.name==name};soundStore.write(s.selected,schema,record.copy(favorites=favorites));mutable.update {it.copy(soundFavorites=favorites.map {f->f.name})} }
    fun diagnosticMode(mode: Int){if(mode !in 0..2)return;NativeAudio.event(12,0,mode,0,0f);mutable.update { it.copy(diagnosticMode=mode) }}
    fun startDiagnosis(){val s=state.value;diagnostics.start(System.nanoTime(),"${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, Android ${android.os.Build.VERSION.RELEASE}, app ${dev.pianopocket.BuildConfig.VERSION_NAME}\npreset=${s.preset?.name}, Output=${s.volume}, voices=${s.voices}, memoryMiB=${s.memoryMiB}, selectedOutputId=${s.device}, availableOutputs=${s.outputs.joinToString { it.label }}");mutable.update { it.copy(diagnosticRecording=true) }}
    fun stopDiagnosis(){diagnostics.stop();mutable.update { it.copy(diagnosticRecording=false) }}
    fun diagnosticReport()=diagnostics.report()
    fun cancelExport()=ExportCoordinator.cancel()
    private fun exportRequest(uri: Uri?,format: ExportFormat,rate: Int,tail: Int,bitrate: Int,video: VideoOptions,preview: Boolean): ExportRequest {
        val s=state.value;val seq=s.sequence;val original=seq.song ?: error("MIDIを選択してください")
        val options=seq.exportSelection;val speed=if(options.usePlayback)seq.speed else 1f
        val start=if(options.loopOnly)seq.loopA else 0;val end=if(options.loopOnly)seq.loopB else original.duration
        val source=SongExportSettings.apply(original,speed,if(options.usePlayback)seq.mutedTracks else emptySet(),start,end,(seq.trackInstruments.mapValues {(_,id)->trackBanks.firstOrNull {it.entry.id==id}?.preset?.articulations?.map {it.key}.orEmpty().toSet()} + (-1 to s.preset?.articulations?.map {it.key}.orEmpty().toSet())))
        val previewPosition=((seq.position-start).coerceAtLeast(0)/speed).toLong()
        val song=if(preview)PreviewSong.slice(source,previewPosition)else source
        val preset=if(format.bits>0)s.preset ?: error("音源を読み込んでください")else null
        val entry=if(preset!=null)s.library.first {it.id==s.selected}else null
        return ExportRequest(uri,format,rate,tail,bitrate,video,song,s.sequence.title,s.memoryMiB,s.volume,s.voices,
            preset?.regions?.map {r->ExportRegion(repository.resolve(checkNotNull(entry),r.path).path,r.ints.copyOf(),r.floats.copyOf())}.orEmpty(),
            preset?.effects?.map {fx->ExportEffect(fx.type,fx.params.copyOf(),if(fx.ir.isBlank())""else repository.resolve(checkNotNull(entry),fx.ir).path)}.orEmpty(),
            preset?.velocityCurves?.mapValues {it.value.copyOf()}.orEmpty(),s.groups.toList(),preset?.controls?.flatMap {c->c.bindings.filter {it.type=="master"}.map {b->bindingValue(b,c,c.value)}}.orEmpty(),s.sequence.noteLabels,s.sequence.viewport,s.sequence.lookAhead,preview,
            TrackRouting.plan(s.selected,seq.trackInstruments).tracks,if(preset==null)emptyList()else trackBanks.map {bank->dev.pianopocket.video.ExportBank(bank.bank,bank.preset.regions.map {r->val mapped=TrackRouting.region(r,bank.offset,bank.bank);ExportRegion(repository.resolve(bank.entry,r.path).path,mapped.ints,mapped.floats)},bank.preset.effects.map {fx->ExportEffect(fx.type,fx.params.copyOf(),if(fx.ir.isBlank())""else repository.resolve(bank.entry,fx.ir).path)},bank.preset.velocityCurves.mapKeys {it.key+bank.offset}.mapValues {it.value.copyOf()},bank.groups.mapIndexed {i,v->bank.offset+i to v},bank.master)},normalize=options.normalize)
    }
    fun export(uri: Uri,format: ExportFormat,rate: Int,tail: Int,bitrate: Int,video: VideoOptions=VideoOptions())=work {
        try{diagnosticMode(0);stopDiagnosis();saveCurrentSound();NativeAudio.event(10,0,0,0,0f);NativeAudio.panic();live.clear();ExportCoordinator.submit(getApplication(),exportRequest(uri,format,rate,tail,bitrate,video,false))}
        catch(e: Exception){runCatching {android.provider.DocumentsContract.deleteDocument(getApplication<Application>().contentResolver,uri)};throw e}
    }
    fun exportSelection(value: ExportSelection){preferences.exportSelection=value;mutable.update {it.copy(sequence=it.sequence.copy(exportSelection=value))}}
    fun previewVideo(rate: Int,bitrate: Int,video: VideoOptions)=work {diagnosticMode(0);stopDiagnosis();saveCurrentSound();NativeAudio.event(10,0,0,0,0f);NativeAudio.panic();live.clear();ExportCoordinator.submit(getApplication(),exportRequest(null,ExportFormat.MP4,rate,1,bitrate,video,true))}
    fun closePreview()=ExportCoordinator.clearPreview()
    override fun onCleared() { midi.close();val snapshot=state.value;executor.execute {runCatching {saveCurrentSong(snapshot);saveCurrentSound(snapshot)};if(AudioWorkQueue.isCurrentUi(audioOwner)){NativeAudio.stop();NativeAudio.abort()} };super.onCleared() }
}

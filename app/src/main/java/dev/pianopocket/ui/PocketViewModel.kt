package dev.pianopocket.ui

import android.app.Application
import android.media.AudioManager
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.pianopocket.audio.NativeAudio
import dev.pianopocket.library.*
import dev.pianopocket.midi.*
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
    val soundFavorites: List<String> = emptyList(),
    val stableAudio: Boolean = false,
    val diagnosticMode: Int = 0, val diagnosticRecording: Boolean = false,
    val targetFps: Int = 60,
    val showFps: Boolean = false,
    val aacAvailable: Boolean = false, val videoOptions: List<VideoOptions> = emptyList(),
    val landscapePerformance: Boolean = true, val uiPedal: Boolean = false,
    val library: List<LibraryEntry> = emptyList(), val selected: String = "", val preset: Preset? = null,
    val busy: Boolean = false, val message: String = "準備中", val midi: String = "MIDI未接続",
    val stats: List<Double> = List(18){0.0}, val groups: List<Float> = emptyList(), val volume: Float = .7f,
    val voices: Int = 512, val memoryMiB: Int = 256, val bursts: Int = 2,
    val ble: List<BleMidiDevice> = emptyList(), val outputs: List<OutputDevice> = emptyList(), val device: Int = 0,
    val channel: Int = -1, val ready: Boolean = false, val processMiB: Float = 0f,
    val inputs: List<MidiInput> = emptyList(), val midiInput: String = "all", val sequence: SequenceState = SequenceState()
)
class PocketViewModel(app: Application):AndroidViewModel(app) {
    private val executor=AudioWorkQueue.executor
    private val audioOwner=AudioWorkQueue.newUiOwner()
    private val worker=executor.asCoroutineDispatcher()
    private val repository=LibraryRepository(app)
    private val preferences=Preferences(app)
    private val soundStore=SoundSettingsStore(File(app.filesDir,"sound-settings"))
    private var soundSaveJob: Job?=null
    private val midiLibrary=MidiLibrary(app)
    private val recorder=MidiRecorder()
    private val diagnostics=dev.pianopocket.audio.DiagnosticSession()
    private val live=LiveNotes()
    private val mutable=MutableStateFlow(PocketState(stableAudio=preferences.stableAudio,targetFps=preferences.targetFps,showFps=preferences.showFps,landscapePerformance=preferences.landscapePerformance,voices=preferences.voiceLimit,memoryMiB=preferences.memoryMiB,bursts=preferences.bursts, sequence=SequenceState(exportSelection=preferences.exportSelection,noteLabels=preferences.noteLabels,rollVisible=preferences.rollVisible,lookAhead=preferences.lookAhead)))
    val state=mutable.asStateFlow()
    @Volatile private var foreground=false;private var initialized=false
    private val midi=MidiController(app,{ text->mutable.update { it.copy(midi=text) } },{ list->mutable.update { it.copy(ble=list) } },{ list->
        val removed=state.value.inputs.any { old->list.none { it.id==old.id } }
        if(removed){live.clear();if(state.value.sequence.recording)viewModelScope.launch { stopRecording() }}
        mutable.update { it.copy(inputs=list,midiInput=if(it.midiInput !in listOf("all","none","screen") && list.none { d->d.id==it.midiInput })"none" else it.midiInput) }
    },::receiveMidi)
    init {
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
                val resume=previousRunning && !result.running && result.previewPath==null || previousPreview!=null && result.previewPath==null && !result.running
                mutable.update { it.copy(uiPedal=if(result.running)false else it.uiPedal,busy=if(result.running)true else if(previousRunning)false else it.busy,ready=if(result.running || result.previewPath!=null)false else it.ready,message=if(result.id>0)result.message else it.message,sequence=it.sequence.copy(exporting=result.running,exportProgress=result.progress,exportPhase=result.phase,exportEtaSeconds=result.etaSeconds,previewPath=result.previewPath,playing=if(result.running)false else it.sequence.playing)) }
                previousRunning=result.running;previousPreview=result.previewPath
                if(resume && foreground)work {openAudioWorker(true);mutable.update {it.copy(message=result.message)}}
            }
        }
        viewModelScope.launch {
            while(isActive){delay(100);if(foreground && !displayFramesActive)displayFrame()}
        }
        viewModelScope.launch {
            while(isActive){delay(500);if(foreground)withContext(worker){val values=NativeAudio.stats().toList();val pss=android.os.Debug.getPss()/1024f;val recording=diagnostics.capture(System.nanoTime(),state.value.diagnosticMode,values);mutable.update { it.copy(stats=values,processMiB=pss,diagnosticRecording=recording) };refreshOutputs()}}
        }
    }
    @Volatile var displayFramesActive=false
    fun targetFps(value: Int){if(value !in listOf(30,60,90,120))return;preferences.targetFps=value;mutable.update { it.copy(targetFps=value) }}
    fun displayFrame() {
        if(!foreground)return
        val transport=NativeAudio.transport();val snapshot=live.snapshot()
        mutable.update { s->s.copy(message=if(transport[2]>0)"MIDIのイベント密度が処理上限を超えたため、再生を停止しました。"else s.message,sequence=s.sequence.copy(position=(transport[0]*1000000).toLong(),playing=!s.sequence.exporting && s.sequence.previewPath==null && transport[1]>0,live=snapshot,recordingMicros=if(s.sequence.recording)recorder.elapsed()else s.sequence.recordingMicros,exportProgress=if(s.sequence.exporting)ExportCoordinator.status.value.progress else s.sequence.exportProgress)) }
        if(state.value.sequence.recording && !recorder.isRecording())stopRecording()
    }
    private fun work(action: suspend () -> Unit) { viewModelScope.launch { withContext(worker) { try { action() } catch(e: Exception) { NativeAudio.abort();mutable.update { it.copy(busy=false,message=e.message ?: e.javaClass.simpleName) } } catch(e: OutOfMemoryError) { NativeAudio.abort();mutable.update { it.copy(busy=false,message="メモリ不足。音源を減らすかメモリ設定を下げて再試行してください。") } } } } }
    fun foreground(active: Boolean) {
        if(foreground==active)return
        foreground=active
        if(!active){diagnosticMode(0);stopDiagnosis();if(state.value.sequence.recording)stopRecording()}
        if(active){if(!ExportCoordinator.running && ExportCoordinator.status.value.previewPath==null)work {openAudioWorker()} }
        else work {soundSaveJob?.cancel();saveCurrentSound();NativeAudio.event(10,0,0,0,0f);live.clear();NativeAudio.panic();NativeAudio.stop();mutable.update {it.copy(ready=false)}}
    }
    private fun openAudioWorker(forceReload: Boolean=false) {
        if(!foreground || ExportCoordinator.running || ExportCoordinator.status.value.previewPath!=null)return
        check(NativeAudio.start(state.value.device,state.value.bursts)){"オーディオ出力を開始できません"}
        if(!initialized){mutable.update {it.copy(busy=true,message="内蔵ピアノを準備しています")};val entries=repository.ensureBundled();mutable.update {it.copy(library=entries,busy=false)};initialized=true}
        val entry=state.value.library.firstOrNull {it.id==(state.value.selected.ifBlank {preferences.lastInstrument})} ?: state.value.library.firstOrNull()
        if(entry!=null && (state.value.preset==null || forceReload || ExportCoordinator.performanceUnloaded))loadInternal(entry)else mutable.update {it.copy(ready=true)}
        if(state.value.sequence.song==null)state.value.sequence.library.firstOrNull {it.id==preferences.lastMidi}?.let {selectSong(it,midiLibrary.load(it))}
    }
    private fun refreshOutputs() {
        val devices=getApplication<Application>().getSystemService(AudioManager::class.java).getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        mutable.update { it.copy(outputs=listOf(OutputDevice(0,"システム既定"))+devices.map { d->OutputDevice(d.id,d.productName.toString()) }) }
    }
    fun load(entry: LibraryEntry) = work { loadInternal(entry) }
    private fun loadInternal(entry: LibraryEntry,override: SoundSettings?=null,factory: Boolean=false) {
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
        NativeAudio.commit()
        ExportCoordinator.performanceRestored()
        val voices=(saved?.voices ?: preferences.voiceLimit).coerceIn(32,1024)
        val output=saved?.output ?: .7f
        NativeAudio.event(8,0,voices,0,0f);NativeAudio.event(4,0,0,0,output)
        initialGroups.forEachIndexed { i,v -> NativeAudio.event(6,0,i,0,v) }
        configured.controls.filter { c->c.bindings.any { it.type=="master" } }.forEach { applyControl(it,it.value) }
        if(state.value.sequence.song!=null)NativeAudio.seekTimeline(state.value.sequence.position)
        repository.used(entry);preferences.lastInstrument=entry.id
        if(!foreground)NativeAudio.stop()
        mutable.update { it.copy(preset=configured,selected=entry.id,busy=false,groups=initialGroups,voices=voices,volume=output,soundFavorites=record.favorites.map { it.name },ready=foreground,message="${preset.regions.size} zones · ${preset.warnings.size} 件の互換性警告") }
        saveCurrentSound()
    }
    fun importArchive(uri: Uri)=work { mutable.update { it.copy(busy=true) };val imported=repository.importArchive(uri){p->mutable.update { it.copy(message=p) } };mutable.update { it.copy(library=repository.entries(),busy=false,message="${imported.size} プリセットをインポートしました。ライブラリで選択してください。") } }
    fun importPreset(uri: Uri, tree: Uri)=work { mutable.update { it.copy(busy=true) };val imported=repository.importPreset(uri,tree){p->mutable.update { it.copy(message=p) } };mutable.update { it.copy(library=repository.entries(),busy=false) };loadInternal(imported.first()) }
    fun delete(entry: LibraryEntry)=work { if(state.value.library.any { it.id==state.value.selected && it.root==entry.root }) { NativeAudio.panic();NativeAudio.stop();mutable.update { it.copy(preset=null,selected="",ready=false) } };soundSaveJob?.cancel();state.value.library.filter {it.root==entry.root}.forEach {soundStore.delete(it.id)};repository.delete(entry);mutable.update { it.copy(library=repository.entries(),message="音源フォルダーを削除しました") } }
    fun rescan()=work { mutable.update { it.copy(library=repository.rescan(),message="再スキャン完了") } }
    fun restoreBundled()=work { mutable.update {it.copy(busy=true)};val entry=repository.installBundled().first();mutable.update {it.copy(library=repository.entries(),busy=false)};loadInternal(entry) }
    fun message(text: String){mutable.update { it.copy(message=text) }}
    fun volume(value: Float){NativeAudio.event(4,0,0,0,value);mutable.update { it.copy(volume=value) };saveSoundSoon()}
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
    private fun receiveMidi(status: Int,a: Int,b: Int,timestamp: Long) {
        if(!foreground || state.value.sequence.exporting || !state.value.ready)return
        when(status and 0xf0){
            0x90 -> if(b==0)NativeAudio.noteOff(a,status and 15)else NativeAudio.noteOn(a,b,status and 15)
            0x80 -> NativeAudio.noteOff(a,status and 15)
            0xb0 -> NativeAudio.cc(a,b,status and 15)
            0xe0 -> NativeAudio.event(3,status and 15,0,0,((b*128+a)-8192)/8192f*2)
        }
        live.event(status,a,b);recorder.capture(status,a,b,timestamp)
    }
    fun key(note: Int,velocity: Int) {
        if(!state.value.ready || state.value.sequence.exporting)return
        if(velocity>0)NativeAudio.noteOn(note,velocity)else NativeAudio.noteOff(note)
        live.event(if(velocity>0)0x90 else 0x80,note,velocity,16)
        if(state.value.midiInput in listOf("all","screen"))recorder.capture(if(velocity>0)0x9f else 0x8f,note,velocity)
    }
    fun showFps(value: Boolean){preferences.showFps=value;mutable.update { it.copy(showFps=value) }}
    fun landscapePerformance(value: Boolean){preferences.landscapePerformance=value;mutable.update { it.copy(landscapePerformance=value) }}
    fun pedal(down: Boolean){mutable.update { it.copy(uiPedal=down) };val value=if(down)127 else 0;NativeAudio.cc(64,value);live.event(0xb0,64,value,16);if(state.value.midiInput in listOf("all","screen"))recorder.capture(0xbf,64,value)}
    fun panic(){diagnosticMode(0);mutable.update { it.copy(uiPedal=false) };if(state.value.sequence.recording)stopRecording();NativeAudio.event(10,0,0,0,0f);NativeAudio.panic();live.clear()}
    fun input(id: String){mutable.update { it.copy(uiPedal=false) };if(state.value.sequence.recording)stopRecording();NativeAudio.panic();live.clear();midi.selected=if(id=="screen")"none" else id;mutable.update { it.copy(midiInput=id) }}
    fun fullKeyboard(value: Boolean){mutable.update { it.copy(sequence=it.sequence.copy(viewport=if(value)KeyboardViewport()else KeyboardViewport.middleTwoOctaves())) }}
    fun zoomKeyboard(factor: Float,anchor: Float,pan: Float){mutable.update { it.copy(sequence=it.sequence.copy(viewport=it.sequence.viewport.transform(factor,anchor,pan))) }}
    fun shiftKeyboard(direction: Int){mutable.update { it.copy(sequence=it.sequence.copy(viewport=it.sequence.viewport.shiftOctave(direction))) }}
    fun noteLabels(value: Boolean){preferences.noteLabels=value;mutable.update { it.copy(sequence=it.sequence.copy(noteLabels=value)) }}
    fun rollVisible(value: Boolean){preferences.rollVisible=value;mutable.update { it.copy(sequence=it.sequence.copy(rollVisible=value)) }}
    fun lookAhead(value: Float){preferences.lookAhead=value;mutable.update { it.copy(sequence=it.sequence.copy(lookAhead=value)) }}
    private fun arrays(song: MidiSong,muted: Set<Int> = emptySet()): Pair<LongArray,IntArray> {
        val messages=Practice.messages(song,muted)
        val times=LongArray(messages.size){messages[it].micros};val data=IntArray(times.size*3)
        messages.forEachIndexed { i,e->data[i*3]=e.status;data[i*3+1]=e.a;data[i*3+2]=e.b };return times to data
    }
    private fun selectSong(entry: MidiEntry,song: MidiSong){
        val (times,data)=arrays(song);NativeAudio.timeline(times,data,song.duration);if(!foreground)NativeAudio.stop()
        preferences.lastMidi=entry.id
        mutable.update { it.copy(sequence=it.sequence.copy(library=midiLibrary.entries(),song=song,selected=entry.id,title=entry.name,playing=false,position=0,speed=1f,loopA=0,loopB=song.duration,looping=false,hiddenTracks=emptySet(),mutedTracks=emptySet()),message="${song.notes.size} notes · ${"%.1f".format(song.bpm)} BPM${if(song.warnings.isEmpty())"" else " · 互換性警告あり"}") }
    }
    fun importMidi(uri: Uri)=work { if(state.value.sequence.recording)stopRecording();mutable.update { it.copy(busy=true) };val (entry,song)=midiLibrary.import(uri);selectSong(entry,song);mutable.update { it.copy(busy=false) } }
    fun selectMidi(entry: MidiEntry)=work { selectSong(entry,midiLibrary.load(entry)) }
    fun deleteMidi(entry: MidiEntry)=work { if(state.value.sequence.selected==entry.id){NativeAudio.timeline(longArrayOf(),intArrayOf(),0);mutable.update { it.copy(sequence=it.sequence.copy(song=null,selected="",title="MIDIを選択",playing=false,position=0)) } };midiLibrary.delete(entry);mutable.update { it.copy(sequence=it.sequence.copy(library=midiLibrary.entries())) } }
    fun play()=work {
        val seq=state.value.sequence;val song=seq.song ?: return@work
        if(seq.recording || seq.exporting || !state.value.ready)return@work
        if(!seq.playing && seq.position>=song.duration)NativeAudio.seekTimeline(0)
        NativeAudio.event(10,0,if(seq.playing)0 else 1,0,0f)
    }
    fun seek(micros: Long)=work { val resume=state.value.sequence.playing;NativeAudio.seekTimeline(micros.coerceIn(0,state.value.sequence.song?.duration ?: 0));if(resume)NativeAudio.event(10,0,1,0,0f) }
    fun speed(value: Float){if(value !in Practice.speeds)return;NativeAudio.event(11,0,0,0,value);mutable.update {it.copy(sequence=it.sequence.copy(speed=value))}}
    fun markA()=work {val seq=state.value.sequence;val duration=seq.song?.duration ?: return@work;require(duration>=100000){"リピートには0.1秒以上必要です"};val a=seq.position.coerceIn(0,duration-100000);val b=if(seq.loopB>a+100000)seq.loopB else duration;configurePracticeLoop(a,b,seq.looping)}
    fun markB()=work {val seq=state.value.sequence;require(seq.position>=seq.loopA+100000){"BはAから0.1秒以上後に設定してください"};configurePracticeLoop(seq.loopA,seq.position,seq.looping)}
    fun looping(enabled: Boolean)=work {val seq=state.value.sequence;configurePracticeLoop(seq.loopA,seq.loopB,enabled)}
    private fun configurePracticeLoop(a: Long,b: Long,enabled: Boolean){val duration=state.value.sequence.song?.duration ?: return;require(!enabled || a>=0 && b<=duration && b-a>=100000){"リピート区間が不正です"};NativeAudio.practiceLoop(a,b,enabled);if(!foreground)NativeAudio.stop();mutable.update {it.copy(sequence=it.sequence.copy(loopA=a,loopB=b,looping=enabled))}}
    fun trackVisible(track: Int,visible: Boolean){val seq=state.value.sequence;if(track !in 0 until (seq.song?.tracks ?: 0))return;mutable.update {it.copy(sequence=it.sequence.copy(hiddenTracks=if(visible)it.sequence.hiddenTracks-track else it.sequence.hiddenTracks+track))}}
    fun trackMuted(track: Int,muted: Boolean)=work {
        val seq=state.value.sequence;val song=seq.song ?: return@work;require(track in 0 until song.tracks);val tracks=if(muted)seq.mutedTracks+track else seq.mutedTracks-track;val (times,data)=arrays(song,tracks);NativeAudio.timeline(times,data,song.duration);NativeAudio.event(11,0,0,0,seq.speed);NativeAudio.practiceLoop(seq.loopA,seq.loopB,seq.looping);NativeAudio.seekTimeline(seq.position);if(seq.playing)NativeAudio.event(10,0,1,0,0f);if(!foreground)NativeAudio.stop();mutable.update {it.copy(sequence=it.sequence.copy(mutedTracks=tracks))}
    }
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
        work {val song=take.toSong();val name="Recording " + java.text.SimpleDateFormat("yyyy-MM-dd HH-mm-ss",java.util.Locale.ROOT).format(java.util.Date());val entry=midiLibrary.save(name,song);selectSong(entry,song);mutable.update { it.copy(message=if(recorder.limitReached())"録音の時間・イベント上限に達したため保存しました。" else "録音をMIDIライブラリへ保存しました。") }}
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
        val s=state.value;val source=s.sequence.song ?: error("MIDIを選択してください");val song=if(preview)PreviewSong.slice(source,s.sequence.position)else source
        val preset=if(format.bits>0)s.preset ?: error("音源を読み込んでください")else null
        val entry=if(preset!=null)s.library.first {it.id==s.selected}else null
        return ExportRequest(uri,format,rate,tail,bitrate,video,song,s.sequence.title,s.memoryMiB,s.volume,s.voices,
            preset?.regions?.map {r->ExportRegion(repository.resolve(checkNotNull(entry),r.path).path,r.ints.copyOf(),r.floats.copyOf())}.orEmpty(),
            preset?.effects?.map {fx->ExportEffect(fx.type,fx.params.copyOf(),if(fx.ir.isBlank())""else repository.resolve(checkNotNull(entry),fx.ir).path)}.orEmpty(),
            preset?.velocityCurves?.mapValues {it.value.copyOf()}.orEmpty(),s.groups.toList(),preset?.controls?.flatMap {c->c.bindings.filter {it.type=="master"}.map {b->bindingValue(b,c,c.value)}}.orEmpty(),s.sequence.noteLabels,s.sequence.viewport,s.sequence.lookAhead,preview)
    }
    fun export(uri: Uri,format: ExportFormat,rate: Int,tail: Int,bitrate: Int,video: VideoOptions=VideoOptions())=work {
        try{diagnosticMode(0);stopDiagnosis();saveCurrentSound();NativeAudio.event(10,0,0,0,0f);NativeAudio.panic();live.clear();ExportCoordinator.submit(getApplication(),exportRequest(uri,format,rate,tail,bitrate,video,false))}
        catch(e: Exception){runCatching {android.provider.DocumentsContract.deleteDocument(getApplication<Application>().contentResolver,uri)};throw e}
    }
    fun exportSelection(value: ExportSelection){preferences.exportSelection=value;mutable.update {it.copy(sequence=it.sequence.copy(exportSelection=value))}}
    fun previewVideo(rate: Int,bitrate: Int,video: VideoOptions)=work {diagnosticMode(0);stopDiagnosis();saveCurrentSound();NativeAudio.event(10,0,0,0,0f);NativeAudio.panic();live.clear();ExportCoordinator.submit(getApplication(),exportRequest(null,ExportFormat.MP4,rate,1,bitrate,video,true))}
    fun closePreview()=ExportCoordinator.clearPreview()
    override fun onCleared() { midi.close();val snapshot=state.value;executor.execute {runCatching {saveCurrentSound(snapshot)};if(AudioWorkQueue.isCurrentUi(audioOwner)){NativeAudio.stop();NativeAudio.abort()} };super.onCleared() }
}

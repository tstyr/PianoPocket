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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.Executors
import dev.pianopocket.sequencer.*
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

data class OutputDevice(val id: Int, val label: String)
data class PocketState(
    val library: List<LibraryEntry> = emptyList(), val selected: String = "", val preset: Preset? = null,
    val busy: Boolean = false, val message: String = "準備中", val midi: String = "MIDI未接続",
    val stats: List<Double> = List(15){0.0}, val groups: List<Float> = emptyList(), val volume: Float = .7f,
    val voices: Int = 80, val memoryMiB: Int = 256, val bursts: Int = 2,
    val ble: List<BleMidiDevice> = emptyList(), val outputs: List<OutputDevice> = emptyList(), val device: Int = 0,
    val channel: Int = -1, val ready: Boolean = false, val processMiB: Float = 0f,
    val inputs: List<MidiInput> = emptyList(), val midiInput: String = "all", val sequence: SequenceState = SequenceState()
)
class PocketViewModel(app: Application):AndroidViewModel(app) {
    private val executor=Executors.newSingleThreadExecutor()
    private val worker=executor.asCoroutineDispatcher()
    private val repository=LibraryRepository(app)
    private val preferences=Preferences(app)
    private val midiLibrary=MidiLibrary(app)
    private val recorder=MidiRecorder()
    private val live=LiveNotes()
    private val exportCancelled=AtomicBoolean(false)
    private val mutable=MutableStateFlow(PocketState(voices=preferences.voiceLimit,memoryMiB=preferences.memoryMiB,bursts=preferences.bursts, sequence=SequenceState(noteLabels=preferences.noteLabels,rollVisible=preferences.rollVisible,lookAhead=preferences.lookAhead)))
    val state=mutable.asStateFlow()
    @Volatile private var foreground=false;private var initialized=false
    private val midi=MidiController(app,{ text->mutable.update { it.copy(midi=text) } },{ list->mutable.update { it.copy(ble=list) } },{ list->
        val removed=state.value.inputs.any { old->list.none { it.id==old.id } }
        if(removed){live.clear();if(state.value.sequence.recording)viewModelScope.launch { stopRecording() }}
        mutable.update { it.copy(inputs=list,midiInput=if(it.midiInput !in listOf("all","none","screen") && list.none { d->d.id==it.midiInput })"none" else it.midiInput) }
    },::receiveMidi)
    init {
        work {
            val pid=android.os.Process.myPid().toString()
            getApplication<Application>().cacheDir.listFiles().orEmpty().filter { it.name.startsWith("render-") && it.extension=="wav" && it.name.split('-').getOrNull(1)!=pid }.forEach { it.delete() }
            mutable.update { it.copy(sequence=it.sequence.copy(library=midiLibrary.entries())) }
        }
        viewModelScope.launch {
            while(isActive){
                delay(33)
                if(foreground){
                    val transport=NativeAudio.transport();val snapshot=live.snapshot()
                    mutable.update { s->s.copy(message=if(transport[2]>0)"MIDIのイベント密度が処理上限を超えたため、再生を停止しました。" else s.message,sequence=s.sequence.copy(position=(transport[0]*1000000).toLong(),playing=transport[1]>0,live=snapshot,recordingMicros=if(s.sequence.recording)recorder.elapsed()else s.sequence.recordingMicros,exportProgress=if(s.sequence.exporting)NativeAudio.exportProgress()else s.sequence.exportProgress)) }
                    if(state.value.sequence.recording && !recorder.isRecording())stopRecording()
                }
            }
        }
        viewModelScope.launch {
            while(isActive){delay(500);if(foreground)withContext(worker){val values=NativeAudio.stats().toList();val pss=android.os.Debug.getPss()/1024f;mutable.update { it.copy(stats=values,processMiB=pss) };refreshOutputs()}}
        }
    }
    private fun work(action: suspend () -> Unit) { viewModelScope.launch { withContext(worker) { try { action() } catch(e: Exception) { NativeAudio.abort();mutable.update { it.copy(busy=false,message=e.message ?: e.javaClass.simpleName) } } catch(e: OutOfMemoryError) { NativeAudio.abort();mutable.update { it.copy(busy=false,message="メモリ不足。音源を減らすかメモリ設定を下げて再試行してください。") } } } } }
    fun foreground(active: Boolean) {
        if(foreground==active)return
        foreground=active
        if(!active && state.value.sequence.recording)stopRecording()
        work {
            if(!active){NativeAudio.event(10,0,0,0,0f);live.clear();NativeAudio.panic();NativeAudio.stop();mutable.update { it.copy(ready=false) };return@work}
            check(NativeAudio.start(state.value.device,state.value.bursts)) { "オーディオ出力を開始できません" }
            if(!initialized){var entries=repository.entries();if(entries.isEmpty())entries=repository.installDemo();mutable.update { it.copy(library=entries) };initialized=true}
            val entry=state.value.library.firstOrNull { it.id==(state.value.selected.ifBlank { preferences.lastInstrument }) } ?: state.value.library.firstOrNull()
            if(entry!=null && state.value.preset==null)loadInternal(entry) else mutable.update { it.copy(ready=true) }
            if(state.value.sequence.song==null)state.value.sequence.library.firstOrNull { it.id==preferences.lastMidi }?.let { selectSong(it,midiLibrary.load(it)) }
        }
    }
    private fun refreshOutputs() {
        val devices=getApplication<Application>().getSystemService(AudioManager::class.java).getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        mutable.update { it.copy(outputs=listOf(OutputDevice(0,"システム既定"))+devices.map { d->OutputDevice(d.id,d.productName.toString()) }) }
    }
    fun load(entry: LibraryEntry) = work { loadInternal(entry) }
    private fun loadInternal(entry: LibraryEntry) {
        NativeAudio.event(10,0,0,0,0f);live.clear()
        mutable.update { it.copy(busy=true,message="XMLと参照WAVを確認中") }
        val preset=repository.parse(entry)
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
        configured.effects.forEach { fx -> NativeAudio.effect(fx.type,fx.params,if(fx.ir.isBlank())"" else repository.resolve(entry,fx.ir).path) }
        NativeAudio.commit()
        val voices=minOf(state.value.voices,preset.voiceLimit ?: state.value.voices).coerceIn(32,128)
        NativeAudio.event(8,0,voices,0,0f);NativeAudio.event(4,0,0,0,state.value.volume)
        initialGroups.forEachIndexed { i,v -> NativeAudio.event(6,0,i,0,v) }
        preset.controls.filter { c->c.bindings.any { it.type=="master" } }.forEach { applyControl(it,it.value) }
        repository.used(entry);preferences.lastInstrument=entry.id
        if(!foreground)NativeAudio.stop()
        mutable.update { it.copy(preset=configured,selected=entry.id,busy=false,groups=initialGroups,voices=voices,ready=foreground,message="${preset.regions.size} zones · ${preset.warnings.size} 件の互換性警告") }
    }
    fun importArchive(uri: Uri)=work { mutable.update { it.copy(busy=true) };val imported=repository.importArchive(uri){p->mutable.update { it.copy(message=p) } };mutable.update { it.copy(library=repository.entries(),busy=false,message="${imported.size} プリセットをインポートしました。ライブラリで選択してください。") } }
    fun importPreset(uri: Uri, tree: Uri)=work { mutable.update { it.copy(busy=true) };val imported=repository.importPreset(uri,tree){p->mutable.update { it.copy(message=p) } };mutable.update { it.copy(library=repository.entries(),busy=false) };loadInternal(imported.first()) }
    fun delete(entry: LibraryEntry)=work { if(state.value.library.any { it.id==state.value.selected && it.root==entry.root }) { NativeAudio.panic();NativeAudio.stop();mutable.update { it.copy(preset=null,selected="",ready=false) } };repository.delete(entry);mutable.update { it.copy(library=repository.entries(),message="音源フォルダーを削除しました") } }
    fun rescan()=work { mutable.update { it.copy(library=repository.rescan(),message="再スキャン完了") } }
    fun message(text: String){mutable.update { it.copy(message=text) }}
    fun volume(value: Float){NativeAudio.event(4,0,0,0,value);mutable.update { it.copy(volume=value) }}
    fun group(index: Int,value: Float){NativeAudio.event(6,0,index,0,value);mutable.update { s->s.copy(groups=s.groups.mapIndexed { i,v->if(i==index)value else v }) }}
    fun fx(index: Int, slot: Int, value: Float){NativeAudio.event(7,0,index,slot,value);mutable.update { s->s.copy(preset=s.preset?.copy(effects=s.preset.effects.mapIndexed { i,e->if(i==index)e.copy(params=e.params.copyOf().also { it[slot]=value }) else e })) }}
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
    }}
    fun voices(value: Int){preferences.voiceLimit=value;NativeAudio.event(8,0,value,0,0f);mutable.update { it.copy(voices=value) }}
    fun impulse(menuIndex: Int,optionIndex: Int)=work {
        val preset=state.value.preset ?: return@work;val menu=preset.menus[menuIndex];val choice=menu.options[optionIndex]
        val entry=state.value.library.first { it.id==state.value.selected };val fx=preset.effects[choice.effectIndex]
        mutable.update { it.copy(busy=true,message="IRを準備中: ${choice.name}") }
        NativeAudio.replaceImpulse(choice.effectIndex,repository.resolve(entry,choice.path).path,fx.params)
        mutable.update { s->s.copy(busy=false,message="IR: ${choice.name}",preset=s.preset?.copy(effects=s.preset.effects.mapIndexed { i,e->if(i==choice.effectIndex)e.copy(ir=choice.path)else e },menus=s.preset.menus.mapIndexed { i,m->if(i==menuIndex)m.copy(selected=optionIndex)else m })) }
    }
    fun memory(value: Int){preferences.memoryMiB=value;mutable.update { it.copy(memoryMiB=value,message="メモリ上限は次回の音源読み込みから適用されます") }}
    fun audio(device: Int=state.value.device,bursts: Int=state.value.bursts)=work { preferences.bursts=bursts;mutable.update { it.copy(device=device,bursts=bursts) };NativeAudio.panic();check(NativeAudio.start(device,bursts)) { "オーディオ経路を開けません" };state.value.library.firstOrNull { it.id==state.value.selected }?.let { loadInternal(it) } }
    fun channel(value: Int){if(state.value.sequence.recording)stopRecording();live.clear();NativeAudio.panic();midi.channel=value;mutable.update { it.copy(channel=value) }}
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
    fun pedal(down: Boolean){val value=if(down)127 else 0;NativeAudio.cc(64,value);live.event(0xb0,64,value,16);if(state.value.midiInput in listOf("all","screen"))recorder.capture(0xbf,64,value)}
    fun panic(){if(state.value.sequence.recording)stopRecording();NativeAudio.event(10,0,0,0,0f);NativeAudio.panic();live.clear()}
    fun input(id: String){if(state.value.sequence.recording)stopRecording();NativeAudio.panic();live.clear();midi.selected=if(id=="screen")"none" else id;mutable.update { it.copy(midiInput=id) }}
    fun fullKeyboard(value: Boolean){mutable.update { it.copy(sequence=it.sequence.copy(fullKeyboard=value)) }}
    fun octave(value: Int){mutable.update { it.copy(sequence=it.sequence.copy(octave=value)) }}
    fun noteLabels(value: Boolean){preferences.noteLabels=value;mutable.update { it.copy(sequence=it.sequence.copy(noteLabels=value)) }}
    fun rollVisible(value: Boolean){preferences.rollVisible=value;mutable.update { it.copy(sequence=it.sequence.copy(rollVisible=value)) }}
    fun lookAhead(value: Float){preferences.lookAhead=value;mutable.update { it.copy(sequence=it.sequence.copy(lookAhead=value)) }}
    private fun arrays(song: MidiSong): Pair<LongArray,IntArray> {
        val times=LongArray(song.messages.size){song.messages[it].micros};val data=IntArray(times.size*3)
        song.messages.forEachIndexed { i,e->data[i*3]=e.status;data[i*3+1]=e.a;data[i*3+2]=e.b };return times to data
    }
    private fun selectSong(entry: MidiEntry,song: MidiSong){
        val (times,data)=arrays(song);NativeAudio.timeline(times,data,song.duration);if(!foreground)NativeAudio.stop()
        preferences.lastMidi=entry.id
        mutable.update { it.copy(sequence=it.sequence.copy(library=midiLibrary.entries(),song=song,selected=entry.id,title=entry.name,playing=false,position=0),message="${song.notes.size} notes · ${"%.1f".format(song.bpm)} BPM${if(song.warnings.isEmpty())"" else " · 互換性警告あり"}") }
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
    fun startRecording(){
        if(state.value.sequence.exporting || !state.value.ready || state.value.busy)return
        if(state.value.midiInput=="none"){message("録音するMIDI入力または画面鍵盤を選択してください。");return}
        NativeAudio.event(10,0,0,0,0f);NativeAudio.panic();live.clear();recorder.start()
        mutable.update { it.copy(sequence=it.sequence.copy(recording=true,recordingMicros=0),message="録音中。停止するとMIDIライブラリへ保存します。") }
    }
    fun stopRecording(){
        if(!state.value.sequence.recording)return
        val take=recorder.stop();NativeAudio.panic();live.clear()
        mutable.update { it.copy(sequence=it.sequence.copy(recording=false)) }
        work {val song=take.toSong();val name="Recording " + java.text.SimpleDateFormat("yyyy-MM-dd HH-mm-ss",java.util.Locale.ROOT).format(java.util.Date());val entry=midiLibrary.save(name,song);selectSong(entry,song);mutable.update { it.copy(message=if(recorder.limitReached())"録音の時間・イベント上限に達したため保存しました。" else "録音をMIDIライブラリへ保存しました。") }}
    }
    fun cancelExport(){exportCancelled.set(true);NativeAudio.cancelExport()}
    fun export(uri: Uri,format: ExportFormat,rate: Int,tail: Int)=work {
        val snapshot=state.value;val song=snapshot.sequence.song ?: error("MIDIを選択してください")
        val context=getApplication<Application>();val temp=File(context.cacheDir,"render-${android.os.Process.myPid()}-${System.nanoTime()}.wav")
        exportCancelled.set(false)
        mutable.update { it.copy(sequence=it.sequence.copy(exporting=true,exportProgress=0f),busy=true,message="${format.label}を書き出し中") }
        NativeAudio.event(10,0,0,0,0f);NativeAudio.panic();live.clear()
        try {
            if(format.bits==0){context.contentResolver.openOutputStream(uri,"wt")?.use { MidiFileCodec.write(song,it,if(format==ExportFormat.MIDI0)0 else 1) } ?: error("保存先を開けません")}
            else {
                val preset=snapshot.preset ?: error("音源を読み込んでください")
                val entry=snapshot.library.first { it.id==snapshot.selected }
                val expected=(song.duration/1000000.0+tail)*rate*2*(format.bits/8)
                require(context.cacheDir.usableSpace>expected+128*1048576){"音声書き出し用の空き容量が不足しています"}
                NativeAudio.exportBegin(snapshot.memoryMiB,rate)
                preset.regions.forEach { r->NativeAudio.region(repository.resolve(entry,r.path).path,r.ints,r.floats) }
                preset.velocityCurves.forEach { (g,c)->NativeAudio.velocityCurve(g,c) }
                preset.effects.forEach { fx->NativeAudio.effect(fx.type,fx.params,if(fx.ir.isBlank())""else repository.resolve(entry,fx.ir).path) }
                NativeAudio.exportControl(4,0,0,snapshot.volume);NativeAudio.exportControl(8,snapshot.voices,0,0f)
                snapshot.groups.forEachIndexed { g,v->NativeAudio.exportControl(6,g,0,v) }
                preset.controls.forEach { c->c.bindings.filter { it.type=="master" }.forEach { b->NativeAudio.exportControl(9,0,0,bindingValue(b,c,c.value)) } }
                check(!exportCancelled.get()){ "書き出しをキャンセルしました" };val (times,data)=arrays(song);NativeAudio.exportWav(temp.path,times,data,song.duration,format.bits,tail)
                mutable.update { it.copy(message="保存先へ音声をコピー中") }
                context.contentResolver.openOutputStream(uri,"wt")?.use { out->temp.inputStream().use { input->
                    val buffer=ByteArray(65536);while(true){check(!exportCancelled.get()){ "書き出しをキャンセルしました" };val n=input.read(buffer);if(n<0)break;out.write(buffer,0,n)}
                } } ?: error("保存先を開けません")
            }
            mutable.update { it.copy(message="${format.label}を書き出しました。${if(format.bits==0)"音源はMIDIに含まれません。" else "現在の音源・ミキサー・エフェクトを反映しています。"}") }
        }catch(e: Throwable){NativeAudio.abort();runCatching { android.provider.DocumentsContract.deleteDocument(context.contentResolver,uri) };throw e}
        finally{temp.delete();mutable.update { it.copy(busy=false,sequence=it.sequence.copy(exporting=false)) }}
    }
    override fun onCleared() { cancelExport();midi.close();executor.execute { NativeAudio.stop();NativeAudio.abort() };executor.shutdown();super.onCleared() }
}

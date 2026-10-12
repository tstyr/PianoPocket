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

data class OutputDevice(val id: Int, val label: String)
data class PocketState(
    val library: List<LibraryEntry> = emptyList(), val selected: String = "", val preset: Preset? = null,
    val busy: Boolean = false, val message: String = "準備中", val midi: String = "MIDI未接続",
    val stats: List<Double> = List(12){0.0}, val groups: List<Float> = emptyList(), val volume: Float = .7f,
    val voices: Int = 80, val memoryMiB: Int = 256, val bursts: Int = 2,
    val ble: List<BleMidiDevice> = emptyList(), val outputs: List<OutputDevice> = emptyList(), val device: Int = 0,
    val channel: Int = -1, val ready: Boolean = false, val processMiB: Float = 0f
)
class PocketViewModel(app: Application):AndroidViewModel(app) {
    private val executor=Executors.newSingleThreadExecutor()
    private val worker=executor.asCoroutineDispatcher()
    private val repository=LibraryRepository(app)
    private val preferences=Preferences(app)
    private val mutable=MutableStateFlow(PocketState(voices=preferences.voiceLimit,memoryMiB=preferences.memoryMiB,bursts=preferences.bursts))
    val state=mutable.asStateFlow()
    private var foreground=false;private var initialized=false
    private val midi=MidiController(app,{ text->mutable.update { it.copy(midi=text) } },{ list->mutable.update { it.copy(ble=list) } })
    init {
        viewModelScope.launch {
            while(isActive){delay(500);if(foreground)withContext(worker){val values=NativeAudio.stats().toList();val pss=android.os.Debug.getPss()/1024f;mutable.update { it.copy(stats=values,processMiB=pss) };refreshOutputs()}}
        }
    }
    private fun work(action: suspend () -> Unit) { viewModelScope.launch { withContext(worker) { try { action() } catch(e: Exception) { NativeAudio.abort();mutable.update { it.copy(busy=false,message=e.message ?: e.javaClass.simpleName) } } catch(e: OutOfMemoryError) { NativeAudio.abort();mutable.update { it.copy(busy=false,message="メモリ不足。音源を減らすかメモリ設定を下げて再試行してください。") } } } } }
    fun foreground(active: Boolean) {
        foreground=active
        work {
            if(!active){NativeAudio.panic();NativeAudio.stop();mutable.update { it.copy(ready=false) };return@work}
            check(NativeAudio.start(state.value.device,state.value.bursts)) { "オーディオ出力を開始できません" }
            if(!initialized){var entries=repository.entries();if(entries.isEmpty())entries=repository.installDemo();mutable.update { it.copy(library=entries) };initialized=true}
            val entry=state.value.library.firstOrNull { it.id==(state.value.selected.ifBlank { preferences.lastInstrument }) } ?: state.value.library.firstOrNull()
            if(entry!=null)loadInternal(entry)
        }
    }
    private fun refreshOutputs() {
        val devices=getApplication<Application>().getSystemService(AudioManager::class.java).getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        mutable.update { it.copy(outputs=listOf(OutputDevice(0,"システム既定"))+devices.map { d->OutputDevice(d.id,d.productName.toString()) }) }
    }
    fun load(entry: LibraryEntry) = work { loadInternal(entry) }
    private fun loadInternal(entry: LibraryEntry) {
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
    fun channel(value: Int){NativeAudio.panic();midi.channel=value;mutable.update { it.copy(channel=value) }}
    fun scanBluetooth()=midi.scanBluetooth()
    fun bluetooth(address: String)=midi.connectBluetooth(address)
    override fun onCleared() { midi.close();executor.execute { NativeAudio.stop();NativeAudio.abort() };executor.shutdown();super.onCleared() }
}

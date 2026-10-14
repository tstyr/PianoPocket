package dev.pianopocket.midi

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.media.midi.*
import android.os.*
import dev.pianopocket.audio.NativeAudio
import java.util.UUID
import java.lang.ref.WeakReference

data class MidiInput(val id: String, val label: String)
data class MidiOutput(val id: String, val label: String)
data class BleMidiDevice(val address: String, val name: String)
class MidiController(
    private val context: Context,
    private val status: (String)->Unit,
    private val discovered: (List<BleMidiDevice>)->Unit,
    private val inputs: (List<MidiInput>)->Unit,
    private val receive: (Int,Int,Int,Long,Boolean)->Unit,
    private val outputs: (List<MidiOutput>,String,String)->Unit
) {
    // One process-wide consumer for the native SPSC output queue, including across
    // ViewModel replacement. Device and port ownership also lives on this worker.
    companion object {
        private val ioThread by lazy {HandlerThread("PianoPocket MIDI I/O").apply {start()}}
        private val io by lazy {Handler(ioThread.looper)}
        private var ownerReference: WeakReference<MidiController>?=null
        private var owner: MidiController?
            get()=ownerReference?.get()
            set(value){ownerReference=value?.let {WeakReference(it)}}
    }
    private val manager=context.getSystemService(MidiManager::class.java)
    private val handler=io
    private data class Open(val device: MidiDevice,val ports: List<Pair<Int,MidiOutputPort>>)
    private val open=mutableMapOf<Int,Open>()
    private val pending=mutableSetOf<Int>()
    private val ble=linkedMapOf<String,BleMidiDevice>()
    @Volatile private var closed=false
    @Volatile var channel=-1
    @Volatile var selected="all"
    @Volatile private var outputDevice=-1
    private var selectedOutput="none"
    private var outputPort: MidiInputPort?=null
    private var writer: MidiOutputWriter?=null
    private var performance=false
    private var thru=false
    private var screenChannel=15
    private var scanning=false
    private var outputMessage="MIDI出力オフ"
    private val buffer=LongArray(2048)
    private val poll=object:Runnable {
        override fun run() {
            if(closed || owner!==this@MidiController)return
            if(writer!=null && performance) {
                try {
                    val count=NativeAudio.drainMidi(buffer)
                    for(i in 0 until count) {
                        val s=buffer[i*4].toInt();val a=buffer[i*4+1].toInt();val b=buffer[i*4+2].toInt();val time=buffer[i*4+3]
                        when(s) {
                            0x100 -> writer?.silence(a,b!=0,time)
                            0x101 -> {detachOutput("MIDI出力の処理が追いつかなかったため停止しました");break}
                            else -> writer?.message(s,a,b,time)
                        }
                    }
                }catch(e:Exception){detachOutput("MIDI出力を停止しました: ${e.message ?: "接続エラー"}")}
            }
            handler.postDelayed(this,if(writer!=null && performance)2 else 50)
        }
    }
    private val callback=object:MidiManager.DeviceCallback() {
        override fun onDeviceAdded(info: MidiDeviceInfo) {connect(info)}
        override fun onDeviceRemoved(info: MidiDeviceInfo) {
            if(closed || owner!==this@MidiController)return
            pending.remove(info.id)
            if(outputDevice==info.id)detachOutput("MIDI出力先が切断されました")
            open.remove(info.id)?.let {entry->
                entry.ports.forEach {runCatching {it.second.close()}}
                runCatching {entry.device.close()}
                if(entry.ports.isNotEmpty())NativeAudio.panic()
            }
            report()
        }
    }
    init {
        handler.post {
            if(!closed){owner?.shutdown();owner=this;NativeAudio.midiOutput(false,false,15)
                try {manager?.registerDeviceCallback(callback,handler);manager?.devices?.forEach(::connect)}
                catch(e:Exception){status("MIDI初期化: ${e.message}")}
                report();handler.post(poll)
            }
        }
    }
    private fun connect(info: MidiDeviceInfo) {
        if(closed || info.inputPortCount+info.outputPortCount==0 || info.id in open || !pending.add(info.id))return
        try {manager?.openDevice(info,{device->
            val requested=pending.remove(info.id)
            if(device==null){status("MIDI接続失敗");return@openDevice}
            if(!requested || closed || owner!==this){device.close();return@openDevice}
            try {attach(device)}catch(e:Exception){runCatching {device.close()};status("MIDI接続: ${e.message}")}
        },handler)}catch(e:Exception){pending.remove(info.id);status("MIDI接続: ${e.message}")}
    }
    private fun attach(device: MidiDevice) {
        if(device.info.id in open){device.close();return}
        val ports=(0 until device.info.outputPortCount).mapNotNull {index->
            device.openOutputPort(index)?.also {port->
                var packetTime=0L
                val source="${device.info.id}:$index"
                val decoder=MidiByteDecoder {s,a,b->
                    if(closed || selected!="all" && selected!=source)return@MidiByteDecoder
                    if(channel>=0 && channel!=(s and 15))return@MidiByteDecoder
                    // Never echo a device's own returned messages back to it.
                    receive(s,a,b,packetTime,device.info.id!=outputDevice)
                }
                port.connect(object:MidiReceiver(){override fun onSend(msg:ByteArray,offset:Int,count:Int,timestamp:Long){packetTime=timestamp;decoder.feed(msg,offset,count)}})
            }?.let {index to it}
        }
        open[device.info.id]=Open(device,ports);report()
    }
    private fun label(device:MidiDevice,index:Int,input:Boolean):String {
        val name=device.info.properties.getString(MidiDeviceInfo.PROPERTY_NAME) ?: "MIDI"
        val type=if(input)MidiDeviceInfo.PortInfo.TYPE_INPUT else MidiDeviceInfo.PortInfo.TYPE_OUTPUT
        val port=device.info.ports.firstOrNull {it.type==type && it.portNumber==index}?.name?.takeIf {it.isNotBlank()} ?: "Port ${index+1}"
        return "$name · $port"
    }
    private fun report() {
        val available=open.values.flatMap {entry->entry.ports.map {(index,_)->MidiInput("${entry.device.info.id}:$index",label(entry.device,index,false))}}
        if(selected !in listOf("all","none") && available.none {it.id==selected}){selected="none";NativeAudio.panic()}
        inputs(available)
        val destinations=open.values.flatMap {entry->(0 until entry.device.info.inputPortCount).map {index->MidiOutput("${entry.device.info.id}:$index",label(entry.device,index,true))}}
        outputs(destinations,selectedOutput,outputMessage)
        status(if(open.isEmpty())"MIDI未接続"else open.values.joinToString(" · "){it.device.info.properties.getString(MidiDeviceInfo.PROPERTY_NAME) ?: "MIDI"})
    }
    private fun updateOutput(){if(owner===this){NativeAudio.midiOutput(outputPort!=null && performance,thru,screenChannel);handler.removeCallbacks(poll);if(!closed)handler.post(poll)}}
    private fun detachOutput(message:String) {
        if(owner===this)NativeAudio.midiOutput(false,false,screenChannel)
        runCatching {writer?.silence()};runCatching {outputPort?.close()}
        writer=null;outputPort=null;selectedOutput="none";outputDevice=-1;outputMessage=message;report()
    }
    fun selectOutput(id:String) {handler.post {
        if(closed || owner!==this)return@post
        detachOutput("MIDI出力オフ")
        if(id=="none")return@post
        val parts=id.split(':');val deviceId=parts.getOrNull(0)?.toIntOrNull();val portNumber=parts.getOrNull(1)?.toIntOrNull()
        val entry=deviceId?.let {open[it]}
        if(entry==null || portNumber==null || parts.size!=2 || portNumber !in 0 until entry.device.info.inputPortCount){outputMessage="MIDI出力先が見つかりません";report();return@post}
        try {
            val port=entry.device.openInputPort(portNumber) ?: error("出力ポートを開けません。他のアプリで使用中の可能性があります。")
            outputPort=port;writer=MidiOutputWriter({bytes,count,time->port.send(bytes,0,count,time)},{port.flush()})
            selectedOutput=id;outputDevice=deviceId!!;outputMessage="出力: ${label(entry.device,portNumber,true)}"
            writer?.silence();updateOutput();report()
        }catch(e:Exception){detachOutput("MIDI出力: ${e.message}")}
    }}
    fun outputOptions(thruEnabled:Boolean,keyboardChannel:Int){handler.post {
        if(closed || owner!==this)return@post
        runCatching {writer?.silence()};thru=thruEnabled;screenChannel=keyboardChannel.coerceIn(0,15);updateOutput()
    }}
    fun performanceActive(active:Boolean){handler.post {
        if(closed || owner!==this || performance==active)return@post
        if(!active)runCatching {writer?.silence()}
        performance=active;updateOutput()
    }}
    private val scanCallback=object:ScanCallback() {
        override fun onScanResult(callbackType:Int,result:ScanResult){handler.post {
            if(!closed)try {val d=result.device;ble[d.address]=BleMidiDevice(d.address,result.scanRecord?.deviceName ?: "Bluetooth MIDI");discovered(ble.values.toList())}catch(_:SecurityException){status("Bluetooth権限がありません")}
        }}
        override fun onScanFailed(errorCode:Int){handler.post {scanning=false;status("Bluetooth検索失敗: $errorCode")}}
    }
    fun scanBluetooth(){handler.post {
        if(closed)return@post
        if(!hasPermission()){status("Bluetooth権限を許可してください");return@post}
        try {
            val scanner=context.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner ?: run {status("Bluetooth LEを利用できません");return@post}
            stopScan();ble.clear();discovered(emptyList());scanning=true
            scanner.startScan(listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(UUID.fromString("03b80e5a-ede8-4b33-a751-6ce34ec4c700"))).build()),ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),scanCallback)
            handler.postDelayed({stopScan()},15000)
        }catch(_:SecurityException){scanning=false;status("Bluetooth検索の権限がありません")}
        catch(e:Exception){scanning=false;status("Bluetooth検索: ${e.message}")}
    }}
    fun connectBluetooth(address:String){handler.post {
        if(closed || !hasPermission())return@post
        try {val d=context.getSystemService(BluetoothManager::class.java).adapter.getRemoteDevice(address)
            manager?.openBluetoothDevice(d,{device->if(device!=null && !closed && owner===this)attach(device)else {device?.close();status("Bluetooth MIDI接続失敗")}},handler)
        }catch(e:Exception){status("Bluetooth MIDI: ${e.message}")}
    }}
    private fun hasPermission()=if(Build.VERSION.SDK_INT>=31)context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED && context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED else context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
    private fun stopScan(){if(scanning){try {context.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner?.stopScan(scanCallback)}catch(_:SecurityException){};scanning=false}}
    private fun shutdown() {
        closed=true;stopScan();runCatching {manager?.unregisterDeviceCallback(callback)};handler.removeCallbacks(poll);pending.clear()
        detachOutput("MIDI出力オフ")
        open.values.forEach {entry->entry.ports.forEach {runCatching {it.second.close()}};runCatching {entry.device.close()}};open.clear()
        if(owner===this)owner=null
    }
    fun close(){closed=true;handler.post {shutdown()}}
}

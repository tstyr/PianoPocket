package dev.pianopocket.midi

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.media.midi.*
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import dev.pianopocket.audio.NativeAudio
import java.util.UUID

data class BleMidiDevice(val address: String, val name: String)
class MidiController(private val context: Context, private val status: (String)->Unit, private val discovered: (List<BleMidiDevice>)->Unit) {
    private val manager=context.getSystemService(MidiManager::class.java)
    private val handler=Handler(Looper.getMainLooper())
    private data class Open(val device: MidiDevice, val ports: List<MidiOutputPort>)
    private val open=mutableMapOf<Int,Open>();private val pending=mutableSetOf<Int>()
    private val ble=linkedMapOf<String,BleMidiDevice>()
    private var scanning=false;private var closed=false
    @Volatile var channel=-1
    private val callback=object:MidiManager.DeviceCallback() {
        override fun onDeviceAdded(info: MidiDeviceInfo) { connect(info) }
        override fun onDeviceRemoved(info: MidiDeviceInfo) { pending.remove(info.id);open.remove(info.id)?.let { it.ports.forEach { p -> p.close() };it.device.close();NativeAudio.panic() };report() }
    }
    init { manager?.registerDeviceCallback(callback,handler);manager?.devices?.forEach(::connect);report() }
    private fun connect(info: MidiDeviceInfo) {
        if(info.outputPortCount==0 || info.id in open || !pending.add(info.id) || closed)return
        manager?.openDevice(info,{ device ->
            pending.remove(info.id)
            if(device==null){status("MIDI接続失敗");return@openDevice}
            if(closed){device.close();return@openDevice}
            attach(device)
        },handler)
    }
    private fun attach(device: MidiDevice) {
        val ports=(0 until device.info.outputPortCount).mapNotNull { index -> device.openOutputPort(index)?.also { port ->
            val decoder=MidiByteDecoder { s,a,b ->
                val ch=s and 15;if(channel>=0 && channel!=ch)return@MidiByteDecoder
                when(s and 0xf0) {
                    0x90 -> if(b==0)NativeAudio.noteOff(a,ch) else NativeAudio.noteOn(a,b,ch)
                    0x80 -> NativeAudio.noteOff(a,ch)
                    0xb0 -> NativeAudio.cc(a,b,ch)
                    0xe0 -> NativeAudio.event(3,ch,0,0,((b*128+a)-8192)/8192f*2)
                }
            }
            port.connect(object:MidiReceiver() { override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) { decoder.feed(msg,offset,count) } })
        } }
        open.remove(device.info.id)?.let { it.ports.forEach { p->p.close() };it.device.close() }
        if(ports.isEmpty()){device.close();status("MIDI入力ポートがありません")}else{open[device.info.id]=Open(device,ports);report()}
    }
    private fun report() { status(if(open.isEmpty()) "MIDI未接続" else open.values.joinToString(" · ") { it.device.info.properties.getString(MidiDeviceInfo.PROPERTY_NAME) ?: "MIDI keyboard" }) }
    private val scanCallback=object:ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            try { val d=result.device;ble[d.address]=BleMidiDevice(d.address,result.scanRecord?.deviceName ?: "Bluetooth MIDI");discovered(ble.values.toList()) } catch(e:SecurityException){status("Bluetooth権限がありません")}
        }
        override fun onScanFailed(errorCode: Int) { scanning=false;status("Bluetooth検索失敗: $errorCode") }
    }
    fun scanBluetooth() {
        if(!hasPermission()){status("Bluetooth権限を許可してください");return}
        try {
            val scanner=context.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner ?: run { status("Bluetooth LEを利用できません");return }
            stopScan();ble.clear();discovered(emptyList());scanning=true
            scanner.startScan(listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(UUID.fromString("03b80e5a-ede8-4b33-a751-6ce34ec4c700"))).build()),ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),scanCallback)
            handler.postDelayed({stopScan()},15000)
        }catch(e:SecurityException){scanning=false;status("Bluetooth検索の権限がありません")}
        catch(e:Exception){scanning=false;status("Bluetooth検索: ${e.message}")}
    }
    fun connectBluetooth(address: String) {
        if(!hasPermission())return
        try { val d=context.getSystemService(BluetoothManager::class.java).adapter.getRemoteDevice(address);manager?.openBluetoothDevice(d,{ device -> if(device!=null && !closed)attach(device) else {device?.close();status("Bluetooth MIDI接続失敗")} },handler) }catch(e:Exception){status("Bluetooth MIDI: ${e.message}")}
    }
    private fun hasPermission()=if(Build.VERSION.SDK_INT>=31) context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED && context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED else context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
    fun stopScan() { if(scanning){try{context.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner?.stopScan(scanCallback)}catch(_:SecurityException){};scanning=false} }
    fun close() { closed=true;stopScan();manager?.unregisterDeviceCallback(callback);handler.removeCallbacksAndMessages(null);open.values.forEach { it.ports.forEach { p->p.close() };it.device.close() };open.clear();NativeAudio.panic() }
}

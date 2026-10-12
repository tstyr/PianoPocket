package dev.pianopocket.sequencer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class MidiEntry(val id: String,val name: String,val imported: Long,val bytes: Long,val bpm: Double,val duration: Long)
class MidiLibrary(private val context: Context) {
    private val home=File(context.filesDir,"midi").apply { mkdirs() }
    init {home.listFiles().orEmpty().filter { it.name.endsWith(".pending") }.forEach { it.deleteRecursively() }}
    fun entries(): List<MidiEntry> = home.listFiles().orEmpty().filter { it.extension=="json" }.mapNotNull { file -> runCatching {
        val j=JSONObject(file.readText());val id=file.nameWithoutExtension
        require(File(home,"$id.mid").isFile)
        MidiEntry(id,j.getString("name"),j.getLong("imported"),j.getLong("bytes"),j.getDouble("bpm"),j.getLong("duration"))
    }.getOrNull() }.sortedByDescending { it.imported }
    fun load(entry: MidiEntry)=File(home,"${entry.id}.mid").inputStream().use(MidiFileCodec::read)
    fun import(uri: Uri): Pair<MidiEntry,MidiSong> {
        val name=context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { c->if(c.moveToFirst())c.getString(0)else null } ?: "Imported MIDI"
        require(MidiFilePolicy.accepts(name)){"追加できるMIDIファイルは .mid / .midi のみです。"}
        val song=context.contentResolver.openInputStream(uri)?.use(MidiFileCodec::read) ?: error("MIDIを開けません")
        return save(name,song) to song
    }
    fun save(name: String,song: MidiSong): MidiEntry {
        require(entries().size<256){"MIDIライブラリは256件までです。不要なファイルを削除してください。"}
        val id=UUID.randomUUID().toString();val pending=File(home,"$id.pending");val midi=File(home,"$id.mid");val metadata=File(home,"$id.json")
        try {
            pending.outputStream().use { out->MidiFileCodec.write(song,out,1);out.fd.sync() };require(pending.length()<=MidiFileCodec.MAX_BYTES){"正規化後のMIDIが16 MiBを超えています"};require(pending.renameTo(midi))
            val entry=MidiEntry(id,name.take(160),System.currentTimeMillis(),midi.length(),song.bpm,song.duration)
            val j=JSONObject().put("name",entry.name).put("imported",entry.imported).put("bytes",entry.bytes).put("bpm",entry.bpm).put("duration",entry.duration)
            pending.outputStream().use { out->out.write(j.toString().toByteArray());out.fd.sync() };require(pending.renameTo(metadata))
            return entry
        }catch(e: Throwable){pending.delete();midi.delete();metadata.delete();throw e}
    }
    fun delete(entry: MidiEntry){File(home,"${entry.id}.json").delete();File(home,"${entry.id}.mid").delete()}
}

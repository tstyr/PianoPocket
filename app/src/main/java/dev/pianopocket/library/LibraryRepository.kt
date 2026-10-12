package dev.pianopocket.library

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.UUID

data class LibraryEntry(val id: String, val root: String, val preset: String, val name: String, val bytes: Long, val lastUsed: Long = 0)
class LibraryRepository(private val context: Context) {
    private val home = File(context.filesDir, "instruments").apply { mkdirs() }
    private val index = File(home, "library.json")
    init { home.listFiles().orEmpty().filter { it.isDirectory && it.name.endsWith(".pending") }.forEach { it.deleteRecursively() };File(home,"library.tmp").delete() }
    fun entries(): List<LibraryEntry> = if (!index.exists()) emptyList() else {
        val a = JSONArray(index.readText())
        (0 until a.length()).map { a.getJSONObject(it) }.map { LibraryEntry(it.getString("id"), it.getString("root"), it.getString("preset"), it.getString("name"), it.getLong("bytes"), it.optLong("lastUsed")) }
            .filter { LibraryFilePolicy.isPreset(it.preset) && File(home, "${it.root}/${it.preset}").isFile }
    }
    private fun save(entries: List<LibraryEntry>) {
        val a=JSONArray(); entries.forEach { e -> a.put(JSONObject().put("id",e.id).put("root",e.root).put("preset",e.preset).put("name",e.name).put("bytes",e.bytes).put("lastUsed",e.lastUsed)) }
        val temp=File(home,"library.tmp");temp.outputStream().use { it.write(a.toString(2).toByteArray());it.fd.sync() }
        require(temp.renameTo(index)) { "Cannot save library index" }
    }
    fun root(entry: LibraryEntry) = File(home,entry.root)
    fun presetFile(entry: LibraryEntry) = File(root(entry),entry.preset)
    fun resolve(entry: LibraryEntry, relative: String): File {
        val base=presetFile(entry).parentFile!!
        val file=File(base,DspresetParser.safeRelative(relative)).canonicalFile
        require(file.path.startsWith(root(entry).canonicalPath + File.separator) && file.isFile) { "Missing reference: $relative" }
        return file
    }
    fun parse(entry: LibraryEntry): Preset = presetFile(entry).inputStream().use { DspresetParser().parse(it,entry.name) }.also { preset ->
        preset.regions.map { it.path }.distinct().forEach { resolve(entry,it) }
        preset.effects.filter { it.ir.isNotEmpty() }.forEach { resolve(entry,it.ir) }
        preset.menus.flatMap { it.options }.forEach { resolve(entry,it.path) }
        preset.warnings.forEach { Log.w("PianoPocket.XML",it) }
    }
    fun used(entry: LibraryEntry) { save(entries().map { if(it.id==entry.id) it.copy(lastUsed=System.currentTimeMillis()) else it }) }
    fun delete(entry: LibraryEntry) { save(entries().filter { it.root!=entry.root });root(entry).deleteRecursively() }
    fun rescan(): List<LibraryEntry> {
        val old=runCatching { entries() }.getOrDefault(emptyList()).associateBy { "${it.root}/${it.preset}" }
        val list=home.listFiles().orEmpty().filter { it.isDirectory && !it.name.endsWith(".pending") }.flatMap { folder ->
            val bytes=folder.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            folder.walkTopDown().filter { it.isFile && LibraryFilePolicy.isPreset(it.relativeTo(folder).invariantSeparatorsPath) }.map { file ->
                val path=file.relativeTo(folder).invariantSeparatorsPath
                old["${folder.name}/$path"] ?: LibraryEntry(UUID.randomUUID().toString(),folder.name,path,file.nameWithoutExtension,bytes)
            }.toList()
        };save(list);return list
    }
    fun importArchive(uri: Uri, progress: (String) -> Unit): List<LibraryEntry> = transaction { dir ->
        context.contentResolver.openInputStream(uri)?.use { SafeArchive.extract(it,dir,progress=progress) } ?: error("Cannot open archive")
    }
    fun installDemo(): List<LibraryEntry> = transaction { dir ->
        val wav=File(dir,"PocketTone.wav")
        java.io.DataOutputStream(wav.outputStream().buffered()).use { out ->
            fun int(value: Int){out.writeInt(Integer.reverseBytes(value))}
            fun short(value: Int){out.writeShort(java.lang.Short.reverseBytes(value.toShort()).toInt())}
            val frames=48000*4
            out.writeBytes("RIFF");int(36+frames*4);out.writeBytes("WAVEfmt ");int(16);short(1);short(2);int(48000);int(192000);short(4);short(16);out.writeBytes("data");int(frames*4)
            for(i in 0 until frames){val t=i/48000.0;val v=(kotlin.math.sin(2*Math.PI*261.625565*t)+.3*kotlin.math.sin(2*Math.PI*523.25113*t))*kotlin.math.exp(-t*1.7)*.25;short((v*32767).toInt());short((v*32767).toInt())}
        }
        File(dir,"Pocket Tone — Demo.dspreset").writeText("""<DecentSampler name="Pocket Tone — 自作デモ"><groups><group name="Demo"><sample path="PocketTone.wav" rootNote="60" loNote="24" hiNote="108" attack="0.003" release="0.3" /></group></groups><effects><effect type="reverb" roomSize="0.65" damping="0.35" wetLevel="0.2" /></effects></DecentSampler>""")
    }
    fun importPreset(uri: Uri, tree: Uri, progress: (String) -> Unit): List<LibraryEntry> = transaction { dir ->
        val presetBytes=context.contentResolver.openInputStream(uri)?.use { limitedBytes(it,4*1024*1024) } ?: error("Cannot open preset")
        val preset=DspresetParser().parse(presetBytes.inputStream(),"Imported instrument")
        val root=DocumentFile.fromTreeUri(context,tree) ?: error("Cannot open sample directory")
        File(dir,"Instrument.dspreset").writeBytes(presetBytes)
        val refs=(preset.regions.map { it.path }+preset.effects.map { it.ir }.filter { it.isNotBlank() }+preset.menus.flatMap { it.options }.map { it.path }).distinct()
        require(refs.size<=4096) { "Reference file count exceeded" }
        var total=0L
        refs.forEachIndexed { i,path ->
            val clean=DspresetParser.safeRelative(path)
            var doc=root
            clean.split('/').forEach { part -> doc=doc.findFile(part) ?: error("選択フォルダーに参照ファイルがありません: $path") }
            require(doc.isFile) { "Reference is not a file" }
            val target=File(dir,clean);target.parentFile!!.mkdirs();progress("コピー ${i+1}/${refs.size}: ${doc.name}")
            context.contentResolver.openInputStream(doc.uri)?.use { input -> target.outputStream().use { out -> copyBounded(input,512L*1024*1024) { b,n -> out.write(b,0,n);total+=n;require(total<=8L*1024*1024*1024 && dir.usableSpace>128L*1024*1024) { "Import storage limit exceeded" } } } } ?: error("Cannot read $path")
        }
    }
    private fun transaction(copy: (File) -> Unit): List<LibraryEntry> {
        val id=UUID.randomUUID().toString();val stage=File(home,"$id.pending").apply { mkdirs() };val destination=File(home,id)
        try {
            copy(stage)
            val presets=stage.walkTopDown().filter { it.isFile && LibraryFilePolicy.isPreset(it.relativeTo(stage).invariantSeparatorsPath) }.toList()
            require(presets.isNotEmpty() && presets.size<=128) { "No .dspreset found, or too many presets" }
            // Validate syntax now; unsupported variants remain visible with an error on selection.
            presets.forEach { require(it.length()<=4*1024*1024) { "Preset too large" } }
            val bytes=stage.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            val added=presets.map { LibraryEntry(UUID.randomUUID().toString(),id,it.relativeTo(stage).invariantSeparatorsPath,it.nameWithoutExtension,bytes) }
            require(stage.renameTo(destination)) { "Import commit failed" };save(entries()+added);return added
        } catch(e: Throwable) { stage.deleteRecursively();destination.deleteRecursively();throw e }
    }
    companion object {
        internal fun copyBounded(input: InputStream, max: Long, write: (ByteArray,Int)->Unit): Long {
            val b=ByteArray(65536);var total=0L
            while(true) { val n=input.read(b);if(n<0)break;total+=n;require(total<=max) { "File size limit exceeded" };write(b,n) };return total
        }
        private fun limitedBytes(input: InputStream, max: Int): ByteArray { val out=java.io.ByteArrayOutputStream();copyBounded(input,max.toLong()) { b,n -> out.write(b,0,n) };return out.toByteArray() }
    }
}

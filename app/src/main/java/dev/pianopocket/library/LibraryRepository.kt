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

data class LibraryEntry(val id: String, val root: String, val preset: String, val name: String, val bytes: Long, val lastUsed: Long = 0,val decodedBytes: Long=0)
class LibraryRepository(private val context: Context) {
    private val home = File(context.filesDir, "instruments").apply { mkdirs() }
    private val index = File(home, "library.json")
    private val decoded=DecodedSamples(File(context.filesDir,"decoded-samples"))
    init { home.listFiles().orEmpty().filter { it.isDirectory && it.name.endsWith(".pending") && it.absolutePath !in activeStages }.forEach { it.deleteRecursively() } }
    fun entries(): List<LibraryEntry> = if (!index.exists()) emptyList() else {
        val a = JSONArray(index.readText())
        (0 until a.length()).map { a.getJSONObject(it) }.map { LibraryEntry(it.getString("id"), it.getString("root"), it.getString("preset"), it.getString("name"), it.getLong("bytes"), it.optLong("lastUsed")) }
            .filter { LibraryFilePolicy.isPreset(it.preset) && File(home, "${it.root}/${it.preset}").isFile }
            .map {it.copy(decodedBytes=File(context.filesDir,"decoded-samples/${it.root}").walkTopDown().filter {file->file.isFile}.sumOf {file->file.length()})}
    }
    private fun save(entries: List<LibraryEntry>) {
        val a=JSONArray(); entries.forEach { e -> a.put(JSONObject().put("id",e.id).put("root",e.root).put("preset",e.preset).put("name",e.name).put("bytes",e.bytes).put("lastUsed",e.lastUsed)) }
        val temp=File(home,"library.tmp");temp.outputStream().use { it.write(a.toString(2).toByteArray());it.fd.sync() }
        require(temp.renameTo(index)) { "Cannot save library index" }
    }
    fun root(entry: LibraryEntry) = File(home,entry.root)
    fun presetFile(entry: LibraryEntry) = File(root(entry),entry.preset)
    fun resolve(entry: LibraryEntry, relative: String): File {
        return decoded.resolve(source(entry,relative),entry.root)
    }
    private fun source(entry: LibraryEntry, relative: String): File {
        val base=if(entry.preset.endsWith(".sfz",true))root(entry)else presetFile(entry).parentFile!!
        val file=File(base,DspresetParser.safeRelative(relative)).canonicalFile
        require(file.path.startsWith(root(entry).canonicalPath + File.separator) && file.isFile) { "Missing reference: $relative" }
        return file
    }
    fun parse(entry: LibraryEntry, progress: (String)->Unit = {}): Preset = (if(entry.preset.endsWith(".sfz",true)) {
        SfzParser().parse(readSfz(presetFile(entry)),entry.name,entry.preset) { relative -> readSfz(source(entry,relative)) }
    } else presetFile(entry).inputStream().use { DspresetParser().parse(it,entry.name) }).let {preset -> val warningFile=File(presetFile(entry).path+".warnings");preset.copy(warnings=preset.warnings+if(warningFile.isFile && warningFile.length()<=65536)warningFile.readLines()else emptyList())}.also { preset ->
        val refs=preset.regions.map {it.path}.distinct()
        refs.forEachIndexed { index,path -> progress("サンプル準備 ${index+1}/${refs.size}");resolve(entry,path) }
        preset.effects.filter { it.ir.isNotEmpty() }.forEach { resolve(entry,it.ir) }
        preset.menus.flatMap { it.options }.forEach { resolve(entry,it.path) }
        preset.warnings.forEach { Log.w("PianoPocket.Import",it) }
    }
    fun used(entry: LibraryEntry) { save(entries().map { if(it.id==entry.id) it.copy(lastUsed=System.currentTimeMillis()) else it }) }
    fun delete(entry: LibraryEntry) { save(entries().filter { it.root!=entry.root });root(entry).deleteRecursively();decoded.delete(entry.root) }
    fun rescan(): List<LibraryEntry> {
        val old=runCatching { entries() }.getOrDefault(emptyList()).associateBy { "${it.root}/${it.preset}" }
        val list=home.listFiles().orEmpty().filter { it.isDirectory && !it.name.endsWith(".pending") }.flatMap { folder ->
            val bytes=folder.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            folder.walkTopDown().filter { it.isFile && LibraryFilePolicy.isPreset(it.relativeTo(folder).invariantSeparatorsPath) }.map { file ->
                val path=file.relativeTo(folder).invariantSeparatorsPath
                old["${folder.name}/$path"] ?: LibraryEntry(UUID.randomUUID().toString(),folder.name,path,displayName(file),bytes)
            }.toList()
        };save(list);return list
    }
    fun importArchive(uri: Uri, progress: (String) -> Unit): List<LibraryEntry> = transaction { dir ->
        context.contentResolver.openInputStream(uri)?.use { SafeArchive.extract(it,dir,progress=progress) } ?: error("Cannot open archive")
    }
    fun findDownloaded(id: String): LibraryEntry? = entries().firstOrNull {entry->File(root(entry),"download-id.txt").run {isFile && length()<128 && readText().trim()==id}}
    fun importDownloaded(input: InputStream,id: String,expandedBytes: Long,progress: (String)->Unit): List<LibraryEntry> = transaction {dir->
        SafeArchive.extract(input,dir,SafeArchive.Limits(files=128,total=expandedBytes+1048576),progress)
        require(File(dir,"download-id.txt").run {isFile && length()<128 && readText().trim()==id}) {"音源パッケージの識別子が一致しません"}
        progress("音源の追加を確定します")
    }
    fun ensureBundled(): List<LibraryEntry> {
        val prefs=context.getSharedPreferences("library",Context.MODE_PRIVATE)
        if(!prefs.getBoolean("pocketUprightV1",false) || entries().isEmpty()) {
            installBundled();prefs.edit().putBoolean("pocketUprightV1",true).apply()
        }
        return entries()
    }
    fun installBundled(): List<LibraryEntry> {
        entries().firstOrNull { File(root(it),"bundled-id.txt").run { isFile && readText().trim()=="pocket-upright-v1" } }?.let { return listOf(it) }
        return transaction { dir ->
            fun copy(asset: String, target: File) {
                val children=context.assets.list(asset).orEmpty()
                if(children.isNotEmpty()){target.mkdirs();children.forEach { copy("$asset/$it",File(target,it)) }}
                else context.assets.open(asset).use { packed ->
                    val compressed=asset.endsWith(".wav.pack")
                    val destination=if(compressed)File(target.parentFile,target.name.removeSuffix(".pack"))else target
                    val input=if(compressed)java.util.zip.GZIPInputStream(packed)else packed
                    input.use { stream -> destination.outputStream().use { out -> copyBounded(stream,16L*1024*1024) { b,n -> out.write(b,0,n) } } }
                }
            }
            copy("instruments/PocketUpright",dir)
        }
    }
    fun importPreset(uri: Uri, tree: Uri, progress: (String) -> Unit): List<LibraryEntry> {
      var primary=""
      return transaction { dir ->
        val presetBytes=context.contentResolver.openInputStream(uri)?.use { limitedBytes(it,4*1024*1024) } ?: error("Cannot open preset")
        val root=DocumentFile.fromTreeUri(context,tree) ?: error("Cannot open sample directory")
        fun document(relative: String): DocumentFile {
            var doc=root
            DspresetParser.safeRelative(relative).split('/').forEach { part -> doc=doc.findFile(part) ?: error("選択フォルダーに参照ファイルがありません: $relative") }
            require(doc.isFile) { "Reference is not a file" };return doc
        }
        val originalName=DocumentFile.fromSingleUri(context,uri)?.name ?: "Instrument.dspreset"
        val sfz=originalName.endsWith(".sfz",true)
        val filename=DspresetParser.safeRelative(originalName).also { require('/' !in it) { "Invalid preset filename" } }
        primary=filename
        File(dir,filename).writeBytes(presetBytes)
        val preset=if(sfz)SfzParser().parse(presetBytes.toString(Charsets.UTF_8),"Imported instrument",filename) { path ->
            val doc=document(path)
            val bytes=context.contentResolver.openInputStream(doc.uri)?.use { limitedBytes(it,SfzParser.MAX_TEXT) } ?: error("Cannot read $path")
            val target=File(dir,path);require(target.canonicalFile!=File(dir,filename).canonicalFile) { "SFZ include overlaps main preset" }
            target.parentFile!!.mkdirs();target.writeBytes(bytes);bytes.toString(Charsets.UTF_8)
        }else DspresetParser().parse(presetBytes.inputStream(),"Imported instrument")
        val refs=(preset.regions.map { it.path }+preset.effects.map { it.ir }.filter { it.isNotBlank() }+preset.menus.flatMap { it.options }.map { it.path }).distinct()
        require(refs.size<=4096) { "Reference file count exceeded" }
        var total=0L
        refs.forEachIndexed { i,path ->
            val clean=DspresetParser.safeRelative(path)
            val doc=document(clean)
            val target=File(dir,clean);target.parentFile!!.mkdirs();progress("コピー ${i+1}/${refs.size}: ${doc.name}")
            context.contentResolver.openInputStream(doc.uri)?.use { input -> target.outputStream().use { out -> copyBounded(input,512L*1024*1024) { b,n -> out.write(b,0,n);total+=n;require(total<=8L*1024*1024*1024 && dir.usableSpace>128L*1024*1024) { "Import storage limit exceeded" } } } } ?: error("Cannot read $path")
        }
      }.sortedBy { it.preset!=primary }
    }
    fun importSoundFont(uri: Uri,progress: (String)->Unit): List<LibraryEntry> = transaction(progress) {dir ->
        val file=File(dir,"Imported.sf2")
        context.contentResolver.openInputStream(uri)?.use {input->file.outputStream().use {out->copyBounded(input,512L*1024*1024){b,n->out.write(b,0,n)}}} ?: error("SoundFontを開けません")
        progress("SoundFontを展開しています")
    }
    private fun displayName(file: File): String {val sidecar=File(file.path+".name");return if(sidecar.isFile && sidecar.length()<=4096)sidecar.readText().take(160)else file.nameWithoutExtension}
    private fun transaction(progress: (String)->Unit = {},copy: (File) -> Unit): List<LibraryEntry> {
        val id=UUID.randomUUID().toString();val stage=File(home,"$id.pending");activeStages.add(stage.absolutePath);stage.mkdirs();val destination=File(home,id)
        try {
            copy(stage)
            val fonts=stage.walkTopDown().filter {it.isFile && it.extension.equals("sf2",true)}.toList();require(fonts.size<=32){"SoundFontは1回につき32ファイルまでです"};var expandedFonts=0L
            fonts.forEach {source ->
                val target=File(source.parentFile,source.name+".pocket");require(!target.exists()){ "SoundFontの展開先が重複しています" };SoundFontReader.import(source,target,progress,2L*1024*1024*1024-expandedFonts);expandedFonts+=target.walkTopDown().filter {it.isFile && it.extension=="wav"}.sumOf {it.length()-44}
            }
            val presets=stage.walkTopDown().filter { it.isFile && LibraryFilePolicy.isPreset(it.relativeTo(stage).invariantSeparatorsPath) }.toList()
            require(presets.isNotEmpty() && presets.size<=2048) { "対応する音源がないか、音色数が上限を超えています" }
            // Validate syntax now; unsupported variants remain visible with an error on selection.
            presets.forEach { require(it.length()<=4*1024*1024) { "Preset too large" } }
            val bytes=stage.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            val added=presets.map { LibraryEntry(UUID.randomUUID().toString(),id,it.relativeTo(stage).invariantSeparatorsPath,displayName(it),bytes) }
            require(stage.renameTo(destination)) { "Import commit failed" };save(entries()+added);return added
        } catch(e: Throwable) { stage.deleteRecursively();destination.deleteRecursively();throw e }
        finally {activeStages.remove(stage.absolutePath)}
    }
    companion object {
        private val activeStages=java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        private fun readSfz(file: File): String = file.inputStream().use { limitedBytes(it,SfzParser.MAX_TEXT).toString(Charsets.UTF_8) }
        internal fun copyBounded(input: InputStream, max: Long, write: (ByteArray,Int)->Unit): Long {
            val b=ByteArray(65536);var total=0L
            while(true) { val n=input.read(b);if(n<0)break;total+=n;require(total<=max) { "File size limit exceeded" };write(b,n) };return total
        }
        private fun limitedBytes(input: InputStream, max: Int): ByteArray { val out=java.io.ByteArrayOutputStream();copyBounded(input,max.toLong()) { b,n -> out.write(b,0,n) };return out.toByteArray() }
    }
}

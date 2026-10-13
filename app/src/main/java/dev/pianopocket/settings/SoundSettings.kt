package dev.pianopocket.settings

import dev.pianopocket.library.Preset
import java.io.*
import java.security.MessageDigest

data class SoundSettings(val output: Float,val voices: Int,val groups: List<Float>,val controls: List<Float>,val effects: List<List<Float>>,val menus: List<Int>) {
    fun valid(p: Preset): Boolean=output.isFinite() && output in 0f..1f && voices in 32..1024 &&
        groups.size==p.groups.size && groups.all { it.isFinite() && it in 0f..4f } &&
        controls.size==p.controls.size && controls.withIndex().all { (i,v)->v.isFinite() && v in p.controls[i].min..p.controls[i].max } &&
        effects.size==p.effects.size && effects.all { it.size==5 && it.all { v->v.isFinite() && kotlin.math.abs(v)<=1000000 } } &&
        menus.size==p.menus.size && menus.withIndex().all { (i,v)->v in p.menus[i].options.indices }
    companion object {
        fun capture(p: Preset,output: Float,voices: Int,groups: List<Float>)=SoundSettings(output,voices,groups.map { it.coerceIn(0f,4f) },p.controls.map { it.value },p.effects.map { it.params.toList() },p.menus.map { it.selected })
        fun schema(p: Preset): String {
            val descriptor=buildString {
                p.groups.forEach { appendLine("group:$it") };p.effects.forEach { appendLine("effect:${it.type}:${it.name}") }
                p.controls.forEach { c->appendLine("control:${c.name}:${c.min}:${c.max}:${c.bindings}") }
                p.menus.forEach { appendLine("menu:${it.name}:${it.options}") }
            }
            return hash(descriptor)
        }
        internal fun hash(value: String)=MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString(""){"%02x".format(it.toInt() and 255)}
    }
}
data class FavoriteSound(val name: String,val settings: SoundSettings)
data class SoundRecord(val last: SoundSettings?=null,val favorites: List<FavoriteSound> = emptyList())

/** Bounded, versioned binary format usable in host tests; atomic app-private persistence. */
class SoundSettingsStore(private val home: File) {
    init {home.mkdirs()}
    private fun file(id: String,schema: String)=File(home,SoundSettings.hash(id)+"-$schema.sound")
    fun read(id: String,schema: String): SoundRecord {
        require(schema.matches(Regex("[0-9a-f]{64}")))
        val file=file(id,schema);if(!file.exists())return SoundRecord()
        require(file.length()<=131072){"保存設定が大きすぎます"};return decode(file.readBytes())
    }
    fun write(id: String,schema: String,record: SoundRecord) {
        require(schema.matches(Regex("[0-9a-f]{64}")));val destination=file(id,schema);val temp=File(home,destination.name+".tmp")
        try {FileOutputStream(temp).use { raw->raw.write(encode(record));raw.fd.sync() };check(temp.renameTo(destination)){"音源設定を保存できません"}}finally{temp.delete()}
    }
    fun delete(id: String){val prefix=SoundSettings.hash(id)+"-";home.listFiles().orEmpty().filter { it.name.startsWith(prefix) }.forEach { it.delete() }}
    fun backupCorrupt(id: String,schema: String){val source=file(id,schema);if(source.exists())check(source.renameTo(File(home,source.name+".${java.util.UUID.randomUUID()}.broken"))){"壊れた保存設定を退避できません"}}
    companion object {
        fun encode(record: SoundRecord): ByteArray {
            require(record.favorites.size<=32 && record.favorites.map { it.name }.distinct().size==record.favorites.size)
            val buffer=ByteArrayOutputStream();DataOutputStream(buffer).use { out->
                fun settings(s: SoundSettings) {
                    require(s.output.isFinite() && s.output in 0f..1f && s.voices in 32..1024)
                    fun floats(list: List<Float>,limit: Int){require(list.size<=limit && list.all { it.isFinite() });out.writeInt(list.size);list.forEach(out::writeFloat)}
                    out.writeFloat(s.output);out.writeInt(s.voices);floats(s.groups,256);floats(s.controls,512);require(s.effects.size<=8);out.writeInt(s.effects.size);s.effects.forEach { require(it.size==5);floats(it,5) }
                    require(s.menus.size<=64);out.writeInt(s.menus.size);s.menus.forEach { require(it in 0..4095);out.writeInt(it) }
                }
                out.writeInt(0x50505331);out.writeBoolean(record.last!=null);record.last?.let(::settings);out.writeInt(record.favorites.size)
                record.favorites.forEach { require(it.name.isNotBlank() && it.name.length<=40);out.writeUTF(it.name);settings(it.settings) }
            };return buffer.toByteArray().also { require(it.size<=131072) }
        }
        fun decode(bytes: ByteArray): SoundRecord {
            require(bytes.size<=131072)
            DataInputStream(bytes.inputStream()).use { input->
                fun count(limit: Int)=input.readInt().also { require(it in 0..limit) }
                fun floats(limit: Int)=List(count(limit)){input.readFloat().also { require(it.isFinite()) }}
                fun settings(): SoundSettings {
                    val output=input.readFloat();val voices=input.readInt();require(output.isFinite() && output in 0f..1f && voices in 32..1024)
                    val groups=floats(256);val controls=floats(512);val effects=List(count(8)){floats(5).also { require(it.size==5) }};val menus=List(count(64)){input.readInt().also { require(it in 0..4095) }}
                    return SoundSettings(output,voices,groups,controls,effects,menus)
                }
                require(input.readInt()==0x50505331){"保存設定のバージョンが不明です"};val last=if(input.readBoolean())settings()else null
                val favorites=List(count(32)){val name=input.readUTF();require(name.isNotBlank() && name.length<=40);FavoriteSound(name,settings())}
                require(input.read()==-1 && favorites.map { it.name }.distinct().size==favorites.size){"保存設定の形式が不正です"}
                return SoundRecord(last,favorites)
            }
        }
    }
}

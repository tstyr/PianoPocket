package dev.pianopocket.tools

import dev.pianopocket.library.*
import java.io.File
import kotlin.math.pow

/** Opt-in production-parser export for privately supplied instruments. Never embeds library XML/audio. */
fun main(args: Array<String>) {
    require(args.size in 2..3) { "usage: PresetProbe <instrument-directory> <report-directory> [owned-archive]" }
    val root=File(args[0]).canonicalFile;val output=File(args[1]).apply { mkdirs() }
    val reports=mutableListOf<Map<String,Any?>>()
    if(args.size==3) {
        val stage=File(output,"archive-import-test").apply { mkdirs() }
        try { File(args[2]).inputStream().use { SafeArchive.extract(it,stage) }
            val entries=stage.walkTopDown().filter { it.isFile && LibraryFilePolicy.isPreset(it.relativeTo(stage).invariantSeparatorsPath) }.map { it.name }.sorted().toList()
            File(output,"archive-import-report.json").writeText(json(mapOf("status" to "passed","presets" to entries,"presetCount" to entries.size,"expandedBytes" to stage.walkTopDown().filter { it.isFile }.sumOf { it.length() })))
            println("Production SafeArchive extracted full archive: ${entries.size} genuine presets")
        }finally {stage.deleteRecursively()}
    }
    root.walkTopDown().filter { it.isFile && LibraryFilePolicy.isPreset(it.relativeTo(root).invariantSeparatorsPath) }.sortedBy { it.name }.forEach { file ->
        try {
            val sfz=file.extension.equals("sfz",true)
            val preset=if(sfz)dev.pianopocket.library.SfzParser().parse(file.readText(),file.nameWithoutExtension,file.relativeTo(root).invariantSeparatorsPath) { relative ->
                val included=File(root,DspresetParser.safeRelative(relative)).canonicalFile;require(included.toPath().startsWith(root.toPath()) && included.length()<=4*1024*1024);included.readText()
            }else file.inputStream().use { DspresetParser().parse(it,file.nameWithoutExtension) }
            fun ref(path: String):String {val resolved=File(if(sfz)root else file.parentFile,DspresetParser.safeRelative(path)).canonicalFile;require(resolved.toPath().startsWith(root.toPath()) && resolved.isFile) { "Missing/unsafe file: $path" };return resolved.path}
            val groups=preset.groupVolumes.toMutableList();val effects=preset.effects.map { it.copy(params=it.params.copyOf()) }.toMutableList();var master=1f
            preset.controls.forEach { c->c.bindings.forEach { b->var v=if(b.outMin.isNaN() || b.outMax.isNaN())c.value else b.outMin+(c.value-c.min)/(c.max-c.min)*(b.outMax-b.outMin);if(b.decibels)v=10f.pow(v/20)
                when(b.type){"group"->groups[b.index]=v;"master"->master=v;else->effects[b.index].params[b.slot]=v}
            } }
            preset.menus.forEach { menu->menu.options.forEach { ref(it.path) };val choice=menu.options[menu.selected];effects[choice.effectIndex]=effects[choice.effectIndex].copy(ir=choice.path) }
            File(output,file.name+".mapping").bufferedWriter().use { out->
                out.appendLine("H\t${file.name}")
                preset.regions.forEach { r->out.appendLine("R\t${ref(r.path)}\t${r.ints.joinToString(",")}\t${r.floats.joinToString(",")}") }
                preset.velocityCurves.forEach { (i,c)->out.appendLine("V\t$i\t${c.joinToString(",")}") }
                groups.forEachIndexed { i,g->out.appendLine("G\t$i\t$g") };out.appendLine("M\t$master");out.appendLine("L\t${preset.voiceLimit ?: 80}")
                effects.forEach { e->out.appendLine("F\t${e.type}\t${e.params.joinToString(",")}\t${if(e.ir.isEmpty())""else ref(e.ir)}") }
            }
            reports+=mapOf("preset" to file.name,"status" to "parsed","regions" to preset.regions.size,"uniqueSamples" to preset.regions.map { it.path }.distinct().size,"groups" to preset.groups,"groupLevels" to groups,"velocityCurveGroups" to preset.velocityCurves.keys.toList(),"controls" to preset.controls.map { mapOf("name" to it.name,"value" to it.value,"targets" to it.bindings.map { b->"${b.type}:${b.index}:${b.slot}" }) },"irMenus" to preset.menus.map { m->mapOf("name" to m.name,"selected" to m.selected,"options" to m.options.map { it.name }) },"effectTypes" to effects.map { it.name },"effectValues" to effects.map { it.params.toList() },"voiceLimit" to preset.voiceLimit,"warnings" to preset.warnings)
        }catch(e:Exception){reports+=mapOf("preset" to file.name,"status" to "rejected","error" to e.message)}
    }
    File(output,"production-parser-report.json").writeText(json(reports)+"\n")
    reports.forEach { println("${it["preset"]}: ${it["status"]} ${it["error"] ?: "regions="+it["regions"]}") }
}
private fun json(v: Any?):String=when(v){null->"null";is String->"\""+v.flatMap { c->when(c){'"'->"\\\"";'\\'->"\\\\";'\n'->"\\n";'\r'->"\\r";'\t'->"\\t";else->if(c.code<32)"\\u%04x".format(c.code)else c.toString()}.toList() }.joinToString("")+"\"";is Map<*,*>->v.entries.joinToString(",","{","}"){json(it.key.toString())+":"+json(it.value)};is Iterable<*>->v.joinToString(",","[","]"){json(it)};else->v.toString()}

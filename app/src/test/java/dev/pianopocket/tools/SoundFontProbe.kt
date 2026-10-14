package dev.pianopocket.tools
import dev.pianopocket.library.SoundFontReader
import dev.pianopocket.library.SfzParser
import java.io.File
fun main(args:Array<String>){
    val destination=File(args[1]);val entries=SoundFontReader.import(File(args[0]),destination,decodeVorbis=if(args.size<3)null else {source,target->
        val process=ProcessBuilder(args[2],source.path,target.path).redirectErrorStream(true).start();val output=process.inputStream.bufferedReader().readText();check(process.waitFor()==0){output}
    })
    var regions=0;val samples=mutableSetOf<String>()
    entries.forEach {entry->val preset=SfzParser().parse(entry.file.readText(),entry.name);regions+=preset.regions.size;
        File(entry.file.path+".mapping").writeText(preset.regions.joinToString("\n") {"R\t${File(entry.file.parentFile,it.path).absolutePath}\t${it.ints.joinToString(",")}\t${it.floats.joinToString(",")}"}+"\n")
        preset.regions.forEach {require(File(entry.file.parentFile,it.path).isFile);samples+=it.path}}
    println("SoundFont validation: presets=${entries.size}, regions=$regions, samples=${samples.size}")
}

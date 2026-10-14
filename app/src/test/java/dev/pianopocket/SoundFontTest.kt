package dev.pianopocket

import dev.pianopocket.library.*
import org.junit.Assert.*
import org.junit.Test
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files

/** Self-authored SoundFont; contains only generated sine samples. */
object SoundFontFixture {
    private fun bytes(size: Int,write:(ByteBuffer)->Unit)=ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN).also(write).array()
    private fun name(b: ByteBuffer,text: String){b.put(text.toByteArray().copyOf(20))}
    private fun chunk(tag:String,body:ByteArray)=tag.toByteArray()+bytes(4){it.putInt(body.size)}+body+if(body.size%2==1)byteArrayOf(0)else byteArrayOf()
    fun bank(sampleEnd: Int=512,rom: Boolean=false): ByteArray {
        val info=chunk("LIST","INFO".toByteArray()+chunk("ifil",bytes(4){it.putShort(2).putShort(4)}))
        val smpl=bytes((512+46)*2){b->repeat(512){i->b.putShort((kotlin.math.sin(i*2*Math.PI/64)*10000).toInt().toShort())};repeat(46){b.putShort(0)}}
        val sdta=chunk("LIST","sdta".toByteArray()+chunk("smpl",smpl))
        val phdr=bytes(38*3){b->for((index,title) in listOf("Piano A","Piano B","EOP").withIndex()){name(b,title);b.putShort(index.toShort()).putShort(0).putShort(index.toShort()).putInt(0).putInt(0).putInt(0)}}
        val pbag=bytes(12){it.putShort(0).putShort(0).putShort(2).putShort(0).putShort(4).putShort(0)}
        val pgen=bytes(20){it.putShort(51).putShort(12).putShort(41).putShort(0).putShort(48).putShort(60).putShort(41).putShort(0).putShort(0).putShort(0)}
        val inst=bytes(44){name(it,"Piano");it.putShort(0);name(it,"EOI");it.putShort(1)}
        val igen=bytes(28){it.putShort(43).putShort(0x4830).putShort(44).putShort(0x7f01).putShort(34).putShort(-12000).putShort(38).putShort(-1200).putShort(54).putShort(3).putShort(53).putShort(0).putShort(0).putShort(0)}
        val ibag=bytes(8){it.putShort(0).putShort(0).putShort(6).putShort(0)}
        val shdr=bytes(92){b->name(b,"Sine");b.putInt(0).putInt(sampleEnd).putInt(64).putInt(448).putInt(48000).put(60).put(3).putShort(0).putShort(if(rom)0x8001.toShort()else 1);name(b,"EOS");b.putInt(558).putInt(558).putInt(558).putInt(558).putInt(0).put(0).put(0).putShort(0).putShort(0)}
        val pdta=chunk("LIST","pdta".toByteArray()+chunk("phdr",phdr)+chunk("pbag",pbag)+chunk("pmod",ByteArray(10))+chunk("pgen",pgen)+chunk("inst",inst)+chunk("ibag",ibag)+chunk("imod",ByteArray(10))+chunk("igen",igen)+chunk("shdr",shdr))
        return chunk("RIFF","sfbk".toByteArray()+info+sdta+pdta)
    }
}
class SoundFontTest {
    private fun exercise(action:(File,File)->Unit){val home=Files.createTempDirectory("sf2").toFile();try{val file=File(home,"test.sf2").apply {writeBytes(SoundFontFixture.bank())};action(file,File(home,"converted"))}finally{home.deleteRecursively()}}
    @Test fun hydraPresetsMapPitchVelocityLoopsEnvelopeAndGain(){exercise {file,target->
        val imported=SoundFontReader.import(file,target);assertEquals(2,imported.size);assertTrue(imported[0].name.contains("Piano A"))
        val a=SfzParser().parse(imported[0].file.readText(),"A").regions.single()
        assertEquals(48,a.ints[1]);assertEquals(72,a.ints[2]);assertEquals(60,a.ints[3]);assertEquals(64,a.ints[9]);assertEquals(447,a.ints[10])
        assertEquals(1203f,a.floats[2],.001f);assertEquals(.5f,a.floats[6],.0001f)
        val b=SfzParser().parse(imported[1].file.readText(),"B").regions.single();assertEquals(.501187f,b.floats[0],.00001f)
        assertEquals(1,File(target,"samples").listFiles()!!.size)
        val wav=File(target,a.path).readBytes();val data=ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(1,data.getShort(22).toInt());assertEquals(48000,data.getInt(24));assertEquals(512*2,data.getInt(40));assertTrue(data.getShort(44+32).toInt()>9000)
        assertTrue(File(imported[0].file.path+".warnings").readText().contains("近似"))
    }}
    @Test fun rejectsOutOfBoundsSamplesAndRomSamples(){exercise {file,target->
        file.writeBytes(SoundFontFixture.bank(sampleEnd=1000000));assertTrue(runCatching {SoundFontReader.import(file,target)}.isFailure)
        file.writeBytes(SoundFontFixture.bank(rom=true));assertTrue(runCatching {SoundFontReader.import(file,target)}.isFailure)
    }}
    @Test fun rejectsTruncatedRiffAndInvalidVersion(){exercise {file,target->
        file.writeBytes(file.readBytes().dropLast(1).toByteArray());assertTrue(runCatching {SoundFontReader.import(file,target)}.isFailure)
        val bytes=SoundFontFixture.bank();val version=bytes.toString(Charsets.ISO_8859_1).indexOf("ifil");bytes[version+8]=3
        file.writeBytes(bytes);assertTrue(runCatching {SoundFontReader.import(file,target)}.isFailure)
    }}
}

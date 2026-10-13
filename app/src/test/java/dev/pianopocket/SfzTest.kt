package dev.pianopocket

import dev.pianopocket.library.LibraryFilePolicy
import dev.pianopocket.library.SfzParser
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.zip.GZIPInputStream

class SfzTest {
    private fun parse(text: String) = SfzParser().parse(text, "Piano")
    @Test fun inheritedSettingsAndOverrides() {
        val p=parse("""<global> volume=-6 ampeg_release=0.8 <master> tune=12 <group> lovel=1 hivel=64 pan=-20 group_label=Soft
            <region> sample=Soft C4.wav key=C4 ampeg_attack=0.01
            <region> sample=Soft D4.wav key=D4 volume=0
            <group> lovel=65 hivel=127 <region> sample=Hard.wav lokey=0 hikey=127 pitch_keycenter=60""")
        assertEquals(3,p.regions.size);assertEquals(listOf("Soft","Piano 2"),p.groups)
        val a=p.regions[0];assertEquals("Soft C4.wav",a.path);assertEquals(60,a.ints[1]);assertEquals(60,a.ints[3])
        assertEquals(64,a.ints[5]);assertEquals(.501187f,a.floats[0],.000001f);assertEquals(-.2f,a.floats[1]);assertEquals(12f,a.floats[2]);assertEquals(.8f,a.floats[6]);assertEquals(4,a.ints[19])
        assertEquals(1f,p.regions[1].floats[0]);assertTrue(p.warnings.isEmpty())
    }
    @Test fun includesMacrosCommentsAndWindowsPaths() {
        val p=SfzParser().parse("""
            // comment
            #define ${'$'}ROOT C#4
            #define ${'$'}NAME Soft C#4.wav
            <control> default_path=Samples\Piano\
            <global> ampeg_release=0.4 /* multiline
            comment */
            #include "Data/zones.inc"
        ""","Piano","Piano.sfz") { path ->
            assertEquals("Data/zones.inc",path)
            "<region> key=${'$'}ROOT sample=${'$'}NAME"
        }
        assertEquals("Samples/Piano/Soft C#4.wav",p.regions.single().path);assertEquals(61,p.regions[0].ints[3])
    }
    @Test fun parentReferenceInsideImportedRoot() {
        val p=SfzParser().parse("<region> sample=../Samples/a.wav key=60","Piano","Presets/Piano.sfz")
        assertEquals("Samples/a.wav",p.regions.single().path)
        assertEquals("Samples/a.wav",SfzParser.path("Presets","../Samples/a.wav"))
    }
    @Test fun oneShotContinuousAndInclusiveEnd() {
        val p=parse("<region> sample=a.wav key=60 loop_mode=one_shot end=999 <region> sample=b.wav key=61 loop_mode=loop_continuous loop_start=20 loop_end=999")
        assertEquals(5,p.regions[0].ints[19]);assertEquals(1000,p.regions[0].ints[8]);assertEquals(6,p.regions[1].ints[19]);assertEquals(999,p.regions[1].ints[10])
    }
    @Test fun controllerAndCrossfadeMapping() {
        val p=parse("<region> sample=a.wav locc64=64 hicc64=127 pitch_keycenter=-1 transpose=12 amp_veltrack=0 xfade_velcurve=gain xfin_lovel=1 xfin_hivel=64")
        val r=p.regions.single();assertEquals(64,r.ints[11]);assertEquals(64,r.ints[12]);assertEquals(1,r.ints[14]);assertEquals(1200f,r.floats[2]);assertEquals(0f,r.floats[7]);assertEquals(0f,r.floats[8])
    }
    @Test fun unsupportedVariantsDoNotStack() {
        val p=parse("<region> sample=first.wav key=60 <region> sample=roundrobin.wav key=60 seq_position=2 <region> sample=filtered.wav cutoff=8000 fil_type=lpf_2p")
        assertEquals(2,p.regions.size);assertTrue(p.warnings.any {"seq_position" in it});assertTrue(p.warnings.any {"skipped" in it});assertTrue(p.warnings.any {"cutoff" in it})
    }
    @Test fun quotedUnicodeAndMacroExpansion() {
        val p=parse("#define ${'$'}A ${'$'}B\n#define ${'$'}B C4\n<region> key=${'$'}A sample=\"ピアノ 音.wav\" /* x */")
        assertEquals("ピアノ 音.wav",p.regions.single().path);assertEquals(60,p.regions[0].ints[3])
    }
    @Test(expected=IllegalArgumentException::class) fun rootEscapeRejected() { SfzParser.path("Presets","../../secret.wav") }
    @Test(expected=IllegalArgumentException::class) fun absoluteRejected() { parse("<region> sample=C:\\private\\a.wav") }
    @Test(expected=IllegalArgumentException::class) fun includeCycleRejected() { SfzParser().parse("#include \"a.sfz\"","Piano","a.sfz") {error("must not read")} }
    @Test(expected=IllegalArgumentException::class) fun infiniteMacroRejected() { parse("#define ${'$'}X ${'$'}X\n<region> key=${'$'}X sample=a.wav") }
    @Test(expected=IllegalArgumentException::class) fun invalidVelocityRejected() { parse("<region> sample=a.wav lovel=100 hivel=20") }
    @Test(expected=IllegalArgumentException::class) fun enormousSourceRejected() { parse(" ".repeat(SfzParser.MAX_TEXT+1)) }
    @Test fun sfzLibraryAndSidecars() {assertTrue(LibraryFilePolicy.isPreset("Piano.SFZ"));assertFalse(LibraryFilePolicy.isPreset("__MACOSX/._Piano.sfz"));assertFalse(LibraryFilePolicy.isPreset("Piano.sf2"))}
    @Test fun bundledPianoCoversAll88KeysAndVelocities() {
        val bank=File("src/main/assets/instruments/PocketUpright")
        val p=SfzParser().parse(File(bank,"Pocket Upright.sfz").readText(),"Pocket Upright")
        assertEquals(36,p.regions.size);assertEquals(3,p.groups.size);assertTrue(p.warnings.toString(),p.warnings.isEmpty())
        for(note in 21..108)for(vel in 1..127)assertEquals("$note / $vel",1,p.regions.count {note in it.ints[1]..it.ints[2] && vel in it.ints[4]..it.ints[5]})
        p.regions.forEach { r ->
            GZIPInputStream(File(bank,r.path+".pack").inputStream()).use { input ->
                val header=ByteArray(44);var n=0;while(n<header.size){val read=input.read(header,n,header.size-n);assertTrue(read>0);n+=read}
                assertEquals("RIFF",String(header,0,4));assertEquals("WAVE",String(header,8,4))
                assertEquals(2,header[22].toInt());assertEquals(16,header[34].toInt())
            }
        }
        assertTrue(File(bank,"LICENSE.txt").readText().startsWith("CC0 1.0 Universal"))
    }
}

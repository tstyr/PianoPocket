package dev.pianopocket
import dev.pianopocket.library.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
class SoundFont3Test {
    private fun exercise(action:(File,File)->Unit){val home=Files.createTempDirectory("sf3").toFile();try{action(File(home,"test.sf3"),File(home,"decoded"))}finally{home.deleteRecursively()}}
    @Test fun byteOffsetsDecodeOnceAndReuseFloatWavAcrossPresets(){exercise {source,target->
        val payload=byteArrayOf(79,103,103,83,5,6,7,8);source.writeBytes(SoundFontFixture.compressed(payload));var calls=0
        val imported=SoundFontReader.import(source,target,decodeVorbis={packed,wav->assertArrayEquals(payload,packed.readBytes());calls++;wav.writeBytes(SoundFontFixture.decodedWave())})
        assertEquals(1,calls);assertEquals(2,imported.size)
        val r=SfzParser().parse(imported.first().file.readText(),"A").regions.single();assertEquals(64,r.ints[9]);assertEquals(447,r.ints[10])
        assertArrayEquals(SoundFontFixture.decodedWave(),File(target,r.path).readBytes());assertFalse(File(target,"samples").listFiles()!!.any {it.extension=="ogg"})
    }}
    @Test fun boundsAndMissingDecoderRejectBeforeAudioLoading(){exercise {source,target->
        source.writeBytes(SoundFontFixture.compressed(badOffset=true));assertTrue(runCatching {SoundFontReader.import(source,target,decodeVorbis={_,_->error("must not decode")})}.isFailure)
        source.writeBytes(SoundFontFixture.compressed());assertTrue(runCatching {SoundFontReader.import(source,target)}.isFailure)
    }}
    @Test fun invalidDecodedWavAndExpansionBudgetAreRejected(){exercise {source,target->
        source.writeBytes(SoundFontFixture.compressed())
        assertTrue(runCatching {SoundFontReader.import(source,target,decodeVorbis={_,wav->wav.writeBytes(ByteArray(80))})}.isFailure)
        assertTrue(runCatching {SoundFontReader.import(source,target,maxExpandedBytes=100,decodeVorbis={_,wav->wav.writeBytes(SoundFontFixture.decodedWave())})}.isFailure)
        assertFalse(File(target,"samples").listFiles().orEmpty().any {it.extension=="ogg"})
    }}
}

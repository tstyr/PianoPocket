package dev.pianopocket
import dev.pianopocket.library.*
import dev.pianopocket.sequencer.*
import dev.pianopocket.settings.*
import org.junit.Assert.*
import org.junit.Test
class TrackRoutingTest {
    @Test fun sourcesAreSharedAndMainSoundUsesBankZero(){
        val routing=TrackRouting.plan("piano",mapOf(3 to "strings",1 to "piano",2 to "strings",4 to "drums"))
        assertEquals(listOf("strings","drums"),routing.instruments);assertEquals(mapOf(1 to 0,2 to 1,3 to 1,4 to 2),routing.tracks)
    }
    @Test fun oldDecentMappingAndSfzCurvesRemainValidWhenMovedToAnotherBank(){
        val old=Region("a.wav",intArrayOf(0,21,108,60,1,127,0,0,-1,-1,-1,-1,0,127,-1,-1,-1,-1,1),floatArrayOf(1f,0f,0f,.002f,0f,1f,.1f,1f,1f))
        val mapped=TrackRouting.region(old,5,2);assertEquals(32,mapped.ints.size);assertEquals(5,mapped.ints[0]);assertEquals(2,mapped.ints[31]);assertEquals(-1,mapped.ints[26]);assertEquals(1,mapped.ints[20]);assertEquals(1f,mapped.floats[10]);assertEquals(0,old.ints[0])
        val sfz=SfzParser().parse("<region> sample=a.wav amp_velcurve_64=0.3 sw_last=24","A").regions.single()
        val moved=TrackRouting.region(sfz,0,1);assertEquals(24,moved.ints[26]);assertArrayEquals(sfz.floats,moved.floats,0f)
    }
    @Test fun assignmentsPersistAndVersionOneFilesStillOpen(){
        val settings=SongSettings(trackInstruments=mapOf(1 to "strings",2 to "drums"))
        assertEquals(settings,SongSettingsStore.decode(SongSettingsStore.encode(settings)))
        val encoded=SongSettingsStore.encode(SongSettings());encoded[3]=0x31
        assertEquals(SongSettings(),SongSettingsStore.decode(encoded.copyOf(encoded.size-4)))
    }
    @Test fun limitsRejectTooManyBanksOrGroups(){
        assertTrue(runCatching {TrackRouting.plan("main",(0..8).associateWith {"$it"})}.isFailure)
        assertTrue(runCatching {TrackRouting.plan("main",mapOf(256 to "piano"))}.isFailure)
        val region=SfzParser().parse("<region> sample=a.wav","A").regions.single()
        assertTrue(runCatching {TrackRouting.region(region,256,1)}.isFailure)
    }
}

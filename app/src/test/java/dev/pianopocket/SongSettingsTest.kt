package dev.pianopocket

import dev.pianopocket.settings.*
import dev.pianopocket.sequencer.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class SongSettingsTest {
    private val settings = SongSettings("piano-a", 3, .75f, 800000, 100000, 900000, true,
        KeyboardViewport.middleTwoOctaves(), setOf(1, 2), setOf(0))
    @Test fun settingsSurviveRestartAndSongsStaySeparate() {
        val home=Files.createTempDirectory("songs").toFile()
        try {
            SongSettingsStore(home).write("song-a",settings)
            SongSettingsStore(home).write("song-b",settings.copy(instrument="piano-b",transpose=-12))
            assertEquals(settings,SongSettingsStore(home).read("song-a"))
            assertEquals(-12,SongSettingsStore(home).read("song-b")!!.transpose)
            SongSettingsStore(home).delete("song-a")
            assertNull(SongSettingsStore(home).read("song-a"))
            assertNotNull(SongSettingsStore(home).read("song-b"))
        } finally {home.deleteRecursively()}
    }
    @Test fun restoreBoundsPositionLoopsAndTracksWithoutAutoPlaying() {
        val song=MidiSong.recorded(emptyList(),200000)
        val restored=settings.restore(SequenceState(song=song,playing=true))
        assertEquals(200000L,restored.position);assertEquals(200000L,restored.loopB)
        assertTrue(restored.looping);assertFalse(restored.playing)
        assertEquals(emptySet<Int>(),restored.mutedTracks);assertEquals(setOf(0),restored.hiddenTracks)
        val short=settings.restore(SequenceState(song=song.copy(duration=50000)))
        assertFalse(short.looping);assertEquals(50000L,short.loopA)
    }
    @Test fun corruptOrFutureFilesAreBackedUpAndOriginalMidiCanStillOpen() {
        val home=Files.createTempDirectory("songs").toFile()
        try {
            val store=SongSettingsStore(home);store.write("song",settings)
            home.listFiles()!!.single().writeBytes(byteArrayOf(1,2,3))
            assertNull(store.read("song"));assertTrue(home.listFiles()!!.single().name.endsWith(".broken"))
            store.write("song",settings);assertEquals(settings,store.read("song"))
        } finally {home.deleteRecursively()}
    }
    @Test fun rejectsInvalidValuesAndTrailingOrTruncatedData() {
        val invalid=listOf(settings.copy(speed=Float.NaN),settings.copy(speed=7f),settings.copy(transpose=25),
            settings.copy(position=-1),settings.copy(viewport=KeyboardViewport(0f)),settings.copy(mutedTracks=setOf(-1)))
        invalid.forEach {assertTrue(runCatching {SongSettingsStore.encode(it)}.isFailure)}
        val encoded=SongSettingsStore.encode(settings)
        assertTrue(runCatching {SongSettingsStore.decode(encoded+byteArrayOf(0))}.isFailure)
        assertTrue(runCatching {SongSettingsStore.decode(encoded.copyOf(encoded.size-1))}.isFailure)
    }
}

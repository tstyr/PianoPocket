package dev.pianopocket

import dev.pianopocket.settings.OutputMode
import org.junit.Assert.*
import org.junit.Test

class OutputModeTest {
    @Test fun localAndExternalAreIndependentAndKeepTheSavedVolume() {
        assertEquals(.73f,OutputMode.INTERNAL.localVolume(.73f,true),0f)
        assertFalse(OutputMode.INTERNAL.sendsMidi(true))
        assertEquals(0f,OutputMode.EXTERNAL.localVolume(.73f,true),0f)
        assertTrue(OutputMode.EXTERNAL.sendsMidi(true))
        assertEquals(.73f,OutputMode.BOTH.localVolume(.73f,true),0f)
        assertTrue(OutputMode.BOTH.sendsMidi(true))
    }
    @Test fun disconnectedOutputFallsBackToLocalSoundInEveryMode() {
        OutputMode.entries.forEach { mode ->
            assertEquals(.6f,mode.localVolume(.6f,false),0f)
            assertFalse(mode.sendsMidi(false))
        }
    }
}

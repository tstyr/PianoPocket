package dev.pianopocket.sequencer

import java.util.Locale

/** SAF filters by MIME; check the provider's display name as well before opening the stream. */
object MidiFilePolicy {
    val mimeTypes get()=arrayOf("audio/midi","audio/x-midi","audio/mid","audio/x-mid","application/x-midi","application/x-mid")
    fun accepts(name: String): Boolean {
        val dot=name.lastIndexOf('.')
        return dot>0 && name.substring(dot+1).lowercase(Locale.ROOT) in setOf("mid","midi")
    }
}

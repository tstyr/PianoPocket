package dev.pianopocket.midi

/** Stateful MIDI 1.0 decoder: running status, fragmented packets, realtime interleaving and SysEx. */
class MidiByteDecoder(private val emit: (status: Int, first: Int, second: Int) -> Unit) {
    private var status=0;private var first=0;private var count=0;private var sysex=false
    fun feed(bytes: ByteArray, offset: Int, size: Int) {
        require(offset>=0 && size>=0 && offset+size<=bytes.size)
        for(i in offset until offset+size) {
            val b=bytes[i].toInt() and 255
            if(b>=0xf8) continue
            if(b>=0x80) {
                if(b==0xf0) { sysex=true;status=0;count=0;continue }
                if(b==0xf7) { sysex=false;status=0;count=0;continue }
                sysex=false;status=b;count=0
                if(b>=0xf0) status=0 // Ignore system-common messages; they cancel running status.
                continue
            }
            if(sysex || status==0)continue
            val type=status and 0xf0
            val length=if(type==0xc0 || type==0xd0)1 else 2
            if(count==0){first=b;count=1}
            if(length==1){emit(status,first,0);count=0}
            else if(count==2){emit(status,first,b);count=0}
            else count=2
        }
    }
}

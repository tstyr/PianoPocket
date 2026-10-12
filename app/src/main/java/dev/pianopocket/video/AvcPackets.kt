package dev.pianopocket.video

/** Android AVC encoders supply Annex B configuration/packets; ISO BMFF stores length-prefixed NALs. */
object AvcPackets {
    fun nals(bytes: ByteArray): List<ByteArray> {
        fun prefix(i: Int): Int=when {
            i+3<=bytes.size && bytes[i]==0.toByte() && bytes[i+1]==0.toByte() && bytes[i+2]==1.toByte()->3
            i+4<=bytes.size && bytes[i]==0.toByte() && bytes[i+1]==0.toByte() && bytes[i+2]==0.toByte() && bytes[i+3]==1.toByte()->4
            else->0
        }
        val starts=mutableListOf<Pair<Int,Int>>();var i=0
        while(i<bytes.size){val p=prefix(i);if(p>0){starts+=i to p;i+=p}else i++}
        if(starts.isEmpty())return emptyList()
        require(bytes.take(starts.first().first).all { it==0.toByte() }) { "Invalid AVC start code" }
        return starts.mapIndexed { index,s->bytes.copyOfRange(s.first+s.second,starts.getOrNull(index+1)?.first ?: bytes.size) }.filter { it.isNotEmpty() }
    }
    fun configuration(parts: List<ByteArray>): ByteArray {
        // An encoder may already return an AVCDecoderConfigurationRecord.
        if(parts.size==1 && parts[0].size>=7 && parts[0][0]==1.toByte())return parts[0].copyOf()
        val all=parts.flatMap(::nals);val sps=all.filter { it[0].toInt() and 31==7 };val pps=all.filter { it[0].toInt() and 31==8 }
        require(sps.isNotEmpty() && pps.isNotEmpty() && sps.size<=31 && pps.size<=255 && sps[0].size>=4){"AVC encoder did not provide SPS/PPS"}
        val bytes=java.io.ByteArrayOutputStream();val out=java.io.DataOutputStream(bytes)
        out.write(byteArrayOf(1,sps[0][1],sps[0][2],sps[0][3],0xff.toByte(),(0xe0 or sps.size).toByte()))
        sps.forEach { require(it.size<=65535);out.writeShort(it.size);out.write(it) };out.writeByte(pps.size)
        pps.forEach { require(it.size<=65535);out.writeShort(it.size);out.write(it) }
        return bytes.toByteArray()
    }
    fun sample(bytes: ByteArray): ByteArray {
        // A valid length prefix can itself resemble 00 00 01. Check the entire packet first.
        var position=0;var valid=bytes.isNotEmpty()
        while(valid && position<bytes.size){
            if(position+4>bytes.size){valid=false;break}
            var length=0;repeat(4){length=(length shl 8) or (bytes[position++].toInt() and 255)}
            if(length<=0 || length>bytes.size-position){valid=false;break};position+=length
        }
        if(valid && position==bytes.size)return bytes
        val packets=nals(bytes)
        require(packets.isNotEmpty()){"Invalid AVC packet"}
        val buffer=java.io.ByteArrayOutputStream();val out=java.io.DataOutputStream(buffer)
        packets.forEach { out.writeInt(it.size);out.write(it) };return buffer.toByteArray()
    }
}

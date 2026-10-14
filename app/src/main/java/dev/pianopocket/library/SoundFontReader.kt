package dev.pianopocket.library

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow

/** SoundFont 2.04 RIFF/hydra reader. Sample I/O runs only during preparation. */
object SoundFontReader {
    private fun prefix(file: File,count: Int): ByteArray=RandomAccessFile(file,"r").use {input->ByteArray(count).also {input.readFully(it)}}
    data class Chunk(val offset: Long,val size: Long)
    private data class Header(val name: String,val first: Int,val bank: Int=0,val program: Int=0)
    private data class SampleHeader(val start: Long,val end: Long,val loopStart: Long,val loopEnd: Long,
        val rate: Int,val root: Int,val correction: Int,val link: Int,val type: Int)
    data class Imported(val file: File,val name: String,val bank: Int,val program: Int)
    fun import(source: File,target: File,progress: (String)->Unit = {},maxExpandedBytes: Long=2L*1024*1024*1024,decodeVorbis: ((File,File)->Unit)?=null): List<Imported> {
        require(source.length() in 12..(512L*1024*1024)) { "SoundFontのファイルサイズが不正です" }
        check(target.isDirectory || target.mkdirs())
        RandomAccessFile(source,"r").use { input ->
            fun tag(): String {val bytes=ByteArray(4);input.readFully(bytes);return String(bytes,Charsets.US_ASCII)}
            fun u32(): Long = Integer.reverseBytes(input.readInt()).toLong() and 0xffffffffL
            require(tag()=="RIFF") { "SoundFontはRIFF形式である必要があります" }
            val end=u32()+8;require(end==input.length() && tag()=="sfbk") { "SoundFont RIFFヘッダーが不正です" }
            val chunks=mutableMapOf<String,Chunk>();var count=0
            fun scan(start: Long,finish: Long,parent: String) {
                var position=start
                while(position<finish){
                    require(finish-position>=8 && ++count<=1024) { "SoundFontのチャンク数・サイズが不正です" }
                    input.seek(position);val id=tag();val size=u32();val body=position+8
                    require(size<=finish-body) { "SoundFontチャンクがファイル範囲を超えています" }
                    if(id=="LIST"){
                        require(size>=4);val list=tag()
                        if(list in setOf("INFO","sdta","pdta"))scan(body+4,body+size,list)
                    }else if(parent.isNotEmpty()){
                        require(chunks.put("$parent/$id",Chunk(body,size))==null) { "SoundFontチャンクが重複しています" }
                    }
                    position=body+size+(size and 1)
                }
                require(position==finish)
            }
            scan(12,end,"")
            fun data(key: String,width: Int,limit: Int): ByteBuffer {
                val c=chunks[key] ?: error("SoundFontに${key}がありません")
                require(c.size%width==0L && c.size/width in 1..limit.toLong()) { "SoundFontの${key}が不正です" }
                val bytes=ByteArray(c.size.toInt());input.seek(c.offset);input.readFully(bytes)
                return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            }
            val version=data("INFO/ifil",4,1);require(version.short.toInt() in 2..3) { "SoundFont 2 / 3のみ対応しています" }
            val smpl=chunks["sdta/smpl"] ?: error("SoundFontにサンプルがありません")
            require(smpl.size>0)
            val warnings=linkedSetOf("SoundFontのエンベロープ・ベロシティ・フィルターは近似処理です。既定の変調、LFO、内蔵コーラス・リバーブは再現しません。")
            if(chunks.containsKey("sdta/sm24"))warnings+="SoundFontの24bit拡張は下位8bitを省き、16bitで展開します。"
            for(key in listOf("pdta/pmod","pdta/imod"))if((chunks[key]?.size ?: 0)>10)warnings+="SoundFontのカスタムモジュレーターは未対応です。"
            fun name(b: ByteBuffer): String {val bytes=ByteArray(20);b.get(bytes);return String(bytes.takeWhile {it!=0.toByte()}.toByteArray(),Charsets.ISO_8859_1).trim()}
            val ph=data("pdta/phdr",38,2049);val presets=mutableListOf<Header>()
            while(ph.hasRemaining()){val n=name(ph);val program=ph.short.toInt() and 65535;val bank=ph.short.toInt() and 65535;val first=ph.short.toInt() and 65535;ph.position(ph.position()+12);presets+=Header(n,first,bank,program)}
            val ih=data("pdta/inst",22,4097);val instruments=mutableListOf<Header>()
            while(ih.hasRemaining())instruments+=Header(name(ih),ih.short.toInt() and 65535)
            val sh=data("pdta/shdr",46,8193);val samples=mutableListOf<SampleHeader>()
            while(sh.hasRemaining()){name(sh);samples+=SampleHeader(sh.int.toLong() and 0xffffffffL,sh.int.toLong() and 0xffffffffL,
                sh.int.toLong() and 0xffffffffL,sh.int.toLong() and 0xffffffffL,sh.int,sh.get().toInt() and 255,
                sh.get().toInt(),sh.short.toInt() and 65535,sh.short.toInt() and 65535)}
            require(presets.size>1 && instruments.size>1 && samples.size>1)
            data("pdta/pmod",10,65536);data("pdta/imod",10,65536)
            val pbag=data("pdta/pbag",4,65536);val ibag=data("pdta/ibag",4,65536)
            val pgen=data("pdta/pgen",4,65536);val igen=data("pdta/igen",4,65536)
            fun zones(headers: List<Header>,bags: ByteBuffer,gens: ByteBuffer,selector: Int): List<List<Map<Int,Int>>> {
                val indices=mutableListOf<Int>()
                while(bags.hasRemaining()){indices+=bags.short.toInt() and 65535;bags.short}
                require(indices.zipWithNext().all {it.first<=it.second} && indices.last()<gens.limit()/4)
                require(headers.first().first==0 && headers.zipWithNext().all {it.first.first<=it.second.first} && headers.last().first==indices.lastIndex)
                return headers.dropLast(1).mapIndexed {index,h ->
                    var global=emptyMap<Int,Int>();val result=mutableListOf<Map<Int,Int>>()
                    for(z in h.first until headers[index+1].first){
                        val values=linkedMapOf<Int,Int>()
                        for(g in indices[z] until indices[z+1]){val opcode=gens.getShort(g*4).toInt() and 65535;val amount=gens.getShort(g*4+2).toInt();require(opcode in 0..60 && values.put(opcode,amount)==null)}
                        if(selector !in values){require(z==h.first) {"SoundFontのグローバルゾーンの位置が不正です"};global=values}
                        else result+=global+values
                    }
                    result
                }
            }
            val pzones=zones(presets,pbag,pgen,41);val izones=zones(instruments,ibag,igen,53)
            val supported=setOf(0,1,2,3,4,8,9,12,17,33,34,35,36,37,38,41,43,44,45,48,50,51,52,53,54,56,57,58)
            val sampleDir=File(target,"samples").apply {mkdirs()};val extracted=mutableMapOf<String,String>();var expanded=0L
            data class Decoded(val file: File,val sample: SampleHeader,val channels: Int,val bits: Int,val format: Int)
            val decoded=mutableMapOf<Int,Decoded>()
            fun unpack(sampleId: Int): Decoded = decoded.getOrPut(sampleId){
                require(decoded.size<4096);val s=samples[sampleId]
                require(s.type and 0x8000==0 && s.type and 15 in setOf(1,2,4) && s.start>=0 && s.end>s.start && s.end<=smpl.size){"SF3の圧縮サンプル範囲が不正です"}
                val decoder=decodeVorbis ?: error("SF3のVorbisデコーダーを使用できません")
                val packed=File(sampleDir,"compressed-$sampleId.ogg");val wav=File(sampleDir,"decoded-$sampleId.wav")
                require(sampleDir.usableSpace>minOf(512L*1024*1024,maxExpandedBytes-expanded).coerceAtLeast(0)+128L*1024*1024){"SF3の展開用空き容量が不足しています"}
                try {
                    // MuseScore SF3 uses an exclusive byte end; tolerate the inclusive variant only on decode failure.
                    val last=s.end;input.seek(smpl.offset+s.start)
                    packed.outputStream().use {out->val buffer=ByteArray(65536);var remaining=last-s.start;while(remaining>0){val n=minOf(remaining,buffer.size.toLong()).toInt();input.readFully(buffer,0,n);out.write(buffer,0,n);remaining-=n}}
                    require(prefix(packed,4).contentEquals("OggS".toByteArray())){"SF3はOGG Vorbisサンプルのみ対応しています"}
                    try {decoder(packed,wav)}catch(first: Exception){
                        if(s.end>=smpl.size)throw first
                        wav.delete();input.seek(smpl.offset+s.end);packed.appendBytes(byteArrayOf(input.readByte()));decoder(packed,wav)
                    }
                    require(wav.length() in 45..(512L*1024*1024+44))
                    val h=ByteBuffer.wrap(prefix(wav,44)).order(ByteOrder.LITTLE_ENDIAN)
                    require(String(h.array(),0,4)=="RIFF" && String(h.array(),8,8)=="WAVEfmt " && h.getInt(16)==16 && String(h.array(),36,4)=="data")
                    val channels=h.getShort(22).toInt();val rate=h.getInt(24);val bits=h.getShort(34).toInt();val format=h.getShort(20).toInt();val bytes=h.getInt(40).toLong() and 0xffffffffL
                    require(channels in 1..2 && rate in 8000..192000 && bits in listOf(16,24,32) && format in listOf(1,3) && (format!=3 || bits==32) && bytes==wav.length()-44 && bytes%(channels*(bits/8))==0L)
                    if(s.type and 15 !=1)require(channels==1){"SF3の左右サンプルにはモノラルVorbisが必要です"}
                    expanded+=bytes;require(expanded<=maxExpandedBytes){"SF3の展開サイズが上限を超えています"}
                    if(rate!=s.rate)warnings+="SF3のサンプルレートはVorbisの実データを使用します。"
                    Decoded(wav,s.copy(start=0,end=bytes/(channels*(bits/8)),rate=rate,type=s.type and 15),channels,bits,format)
                }catch(error: Throwable){wav.delete();throw error}finally{packed.delete()}
            }
            fun wave(sampleId: Int,startOffset: Long,endOffset: Long): String {
                require(sampleId in 0 until samples.lastIndex)
                val raw=samples[sampleId]
                if(raw.type and 0x10!=0){
                    val d=unpack(sampleId);val frames=d.sample.end;val length=frames+endOffset-startOffset
                    require(startOffset>=0 && length>0 && frames+endOffset<=frames && startOffset+length<=frames){"SF3のPCM再生範囲が不正です"}
                    if(startOffset==0L && endOffset==0L)return "samples/${d.file.name}"
                    val key="sf3-$sampleId-$startOffset-$endOffset"
                    return extracted.getOrPut(key){
                        require(extracted.size<4096);val stride=d.channels*(d.bits/8);val bytes=length*stride;expanded+=bytes
                        require(expanded<=maxExpandedBytes && sampleDir.usableSpace>bytes+128L*1024*1024)
                        val trimmed=File(sampleDir,"trim-${extracted.size}.wav")
                        RandomAccessFile(d.file,"r").use {pcm->
                            val h=ByteArray(44);pcm.readFully(h);val header=ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN);header.putInt(4,(bytes+36).toInt());header.putInt(40,bytes.toInt())
                            pcm.seek(44+startOffset*stride);trimmed.outputStream().use {out->out.write(h);val buffer=ByteArray(65536);var remaining=bytes;while(remaining>0){val n=minOf(remaining,buffer.size.toLong()).toInt();pcm.readFully(buffer,0,n);out.write(buffer,0,n);remaining-=n}}
                        };"samples/${trimmed.name}"
                    }
                }
                require(smpl.size%2==0L)
                val original=samples[sampleId];require(original.type and 0x8010==0) { "ROM・圧縮SoundFontサンプルは未対応です" }
                require(original.type in setOf(1,2,4)) { "SoundFontのサンプル種別が未対応です" }
                val linkedStereo=original.type in setOf(2,4)
                val stereo=false // Preserve each SF2 zone's pan/envelope instead of duplicating its stereo partner.
                val left: SampleHeader;val right: SampleHeader
                if(linkedStereo){require(original.link in 0 until samples.lastIndex);val linked=samples[original.link]
                    require(linked.link==sampleId && linked.type==(if(original.type==2)4 else 2) && linked.rate==original.rate && linked.end-linked.start==original.end-original.start) {"SoundFontのステレオリンクが不正です"}
                    left=original;right=original;warnings+="SoundFontの左右サンプルは各ゾーンのパンで再生します。左右共有のピッチ変調は未対応です。"
                }else {left=original;right=original}
                val length=original.end-original.start+endOffset-startOffset
                require(length in 1..(64L*1024*1024) && original.rate in 8000..192000 && (original.root in 0..127 || original.root==255))
                for(s in listOf(left,right))require(s.start+startOffset>=0 && s.start+startOffset+length<=smpl.size/2)
                val key="${if(stereo)minOf(sampleId,original.link)else sampleId}-$startOffset-$endOffset"
                return extracted.getOrPut(key){
                    require(extracted.size<4096);val channels=if(stereo)2 else 1;val bytes=length*channels*2
                    expanded+=bytes;require(expanded<=maxExpandedBytes && sampleDir.usableSpace>bytes+128L*1024*1024) {"SoundFontの展開容量が不足しています"}
                    val file=File(sampleDir,"${extracted.size}.wav")
                    file.outputStream().use {out ->
                        val header=ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
                        header.put("RIFF".toByteArray()).putInt((36+bytes).toInt()).put("WAVEfmt ".toByteArray()).putInt(16).putShort(1).putShort(channels.toShort())
                            .putInt(original.rate).putInt(original.rate*channels*2).putShort((channels*2).toShort()).putShort(16).put("data".toByteArray()).putInt(bytes.toInt());out.write(header.array())
                        val a=ByteArray(8192);val b=ByteArray(8192);val combined=ByteArray(16384);var frame=0L
                        while(frame<length){val n=minOf(4096,length-frame).toInt();input.seek(smpl.offset+(left.start+startOffset+frame)*2);input.readFully(a,0,n*2)
                            if(stereo){input.seek(smpl.offset+(right.start+startOffset+frame)*2);input.readFully(b,0,n*2);repeat(n){i->combined[i*4]=a[i*2];combined[i*4+1]=a[i*2+1];combined[i*4+2]=b[i*2];combined[i*4+3]=b[i*2+1]};out.write(combined,0,n*4)}else out.write(a,0,n*2)
                            frame+=n
                        }
                    };"samples/${file.name}"
                }
            }
            val imported=mutableListOf<Imported>();var totalRegions=0
            pzones.forEachIndexed {pi,presetZones ->
                val text=StringBuilder();val localWarnings=linkedSetOf<String>()
                for(p in presetZones){val instrument=p.getValue(41) and 65535;require(instrument in izones.indices)
                    for(i in izones[instrument]){
                        require(++totalRegions<=100000) {"SoundFontのリージョン数が多すぎます"}
                        fun value(op: Int,default: Int=0)= (i[op] ?: default)+(p[op] ?: 0)
                        fun range(op: Int): Pair<Int,Int> {val a=i[op] ?: 0x7f00;val b=p[op] ?: 0x7f00;return maxOf(a and 255,b and 255) to minOf((a ushr 8) and 255,(b ushr 8) and 255)}
                        val keys=range(43);val velocities=range(44);if(keys.first>keys.second || velocities.first>velocities.second)continue
                        require(keys.first in 0..127 && keys.second in 0..127 && velocities.first in 0..127 && velocities.second in 0..127)
                        val sid=i.getValue(53) and 65535;require(sid in 0 until samples.lastIndex);val sample=if(samples[sid].type and 0x10!=0)unpack(sid).sample else samples[sid]
                        val start=value(0).toLong()+value(4).toLong()*32768;val endOffset=value(1).toLong()+value(12).toLong()*32768
                        val path=wave(sid,start,endOffset);val length=sample.end-sample.start+endOffset-start
                        val mode=(i[54] ?: 0) and 3
                        val ls=sample.loopStart-sample.start+value(2)+value(45).toLong()*32768-start
                        val le=sample.loopEnd-sample.start+value(3)+value(50).toLong()*32768-start-1
                        if(mode==1 || mode==3)require(ls>=0 && le>ls && le<length) {"SoundFontのループ範囲が不正です"}
                        fun seconds(op: Int)=if(value(op,-12000)<=-32768)0.0 else 2.0.pow(value(op,-12000)/1200.0).coerceIn(0.0,if(op==38)120.0 else 60.0)
                        (p.keys+i.keys).filter {it !in supported}.forEach {localWarnings+="SoundFont generator $it は未対応です。"}
                        if(value(33,-12000)>-12000 || value(35,-12000)>-12000)localWarnings+="SoundFontのエンベロープDelay・Holdは未対応です。"
                        text.append("<region> sample=$path group=${i[57] ?: 0} off_by=${i[57] ?: 0} off_mode=fast lokey=${keys.first} hikey=${keys.second} lovel=${velocities.first} hivel=${velocities.second}")
                        text.append(" pitch_keycenter=${i[58]?.takeIf {it in 0..127} ?: sample.root.takeIf {it in 0..127} ?: 60} transpose=${value(51).coerceIn(-120,120)} tune=${value(52)+sample.correction} pitch_keytrack=${value(56,100)}")
                        text.append(" volume=${-value(48).coerceIn(0,1440)/10.0} pan=${value(17).coerceIn(-500,500)/5.0}")
                        text.append(" ampeg_attack=${seconds(34)} ampeg_decay=${seconds(36)} ampeg_sustain=${100*10.0.pow(-value(37).coerceIn(0,1440)/200.0)} ampeg_release=${seconds(38)}")
                        text.append(" cutoff=${(8.176*2.0.pow(value(8,13500)/1200.0)).coerceIn(10.0,200000.0)} resonance=${value(9).coerceIn(0,400)/10.0}")
                        if(mode==1 || mode==3)text.append(" loop_mode=${if(mode==1)"loop_continuous"else "loop_sustain"} loop_start=$ls loop_end=$le")
                        text.append('\n')
                    }
                }
                if(text.isNotEmpty()){
                    val h=presets[pi];val file=File(target,"${h.bank}-${h.program}-$pi.sfz");require(text.length<=SfzParser.MAX_TEXT)
                    file.writeText(text.toString());val display="${h.name.ifBlank {"SoundFont"}} · Bank ${h.bank} / ${h.program+1}"
                    File(target,file.name+".name").writeText(display)
                    File(target,file.name+".warnings").writeText((warnings+localWarnings).take(100).joinToString("\n"))
                    imported+=Imported(file,display,h.bank,h.program);progress("SoundFont音色 ${pi+1}/${pzones.size}: ${h.name}")
                }
            }
            require(imported.isNotEmpty()) {"SoundFontに再生可能な音色がありません"}
            return imported
        }
    }
}

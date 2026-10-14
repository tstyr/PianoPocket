package dev.pianopocket.sequencer
import dev.pianopocket.library.Region

data class BankRouting(val tracks: Map<Int,Int>,val instruments: List<String>)
object TrackRouting {
    fun plan(main: String,choices: Map<Int,String>): BankRouting {
        require(choices.keys.all {it in 0..255} && choices.values.all {it.isNotBlank() && it.length<=256})
        val ids=choices.toSortedMap().values.filter {it!=main}.distinct()
        require(ids.size<=8){"トラック用の音源は8種類までです。同じ音源は複数トラックで共有できます。"}
        return BankRouting(choices.mapValues {(_,id)->if(id==main)0 else ids.indexOf(id)+1},ids)
    }
    fun region(source: Region,offset: Int,bank: Int): Region {
        require(bank in 1..8 && offset>=0 && source.ints[0]+offset<256)
        val ints=IntArray(32);source.ints.copyInto(ints)
        if(source.ints.size<24){ints[20]=1;ints[21]=1}
        if(source.ints.size<31)for(index in 24..27)ints[index]=-1
        ints[0]+=offset;ints[31]=bank
        val floats=if(source.floats.size==9)source.floats+floatArrayOf(0f,1f,20000f,0f)else source.floats.copyOf()
        return Region(source.path,ints,floats)
    }
}

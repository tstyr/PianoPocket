package dev.pianopocket.ui

data class KeyShape(val note: Int,val left: Float,val width: Float,val black: Boolean)
class KeyLayout(low: Int,high: Int) {
    private fun black(n: Int)=n%12 in listOf(1,3,6,8,10)
    val whites=(low..high).filter { !black(it) }
    val shapes: List<KeyShape>
    init {
        val w=1f/whites.size
        val white=whites.mapIndexed { i,n->KeyShape(n,i*w,w,false) }
        val dark=(low..high).filter(::black).map { n ->val previous=whites.indexOfLast { it<n };KeyShape(n,(previous+1)*w-w*.31f,w*.62f,true) }
        shapes=white+dark
    }
    fun hit(x: Float,y: Float): Int? {
        if(x<0 || x>=1 || y<0 || y>=1)return null
        if(y<.62f)shapes.lastOrNull { it.black && x>=it.left && x<it.left+it.width }?.let { return it.note }
        return shapes.firstOrNull { !it.black && x>=it.left && x<it.left+it.width }?.note
    }
}
fun solfege(note: Int): String = arrayOf("ド","ド♯","レ","レ♯","ミ","ファ","ファ♯","ソ","ソ♯","ラ","ラ♯","シ")[note.mod(12)]

fun noteLabelLines(note: Int): List<String> = listOf(solfege(note))

package dev.pianopocket.video

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import dev.pianopocket.sequencer.KeyboardViewport
import dev.pianopocket.sequencer.MidiSong
import dev.pianopocket.ui.KeyLayout
import dev.pianopocket.ui.solfege

/** Deterministic, opaque roll/key frames with physical key lengths, independent of UI FPS. */
internal class VideoRollRenderer(private val song: MidiSong,val width: Int,val height: Int,private val labels: Boolean,private val viewport: KeyboardViewport,lookAhead: Float): AutoCloseable {
    val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
    private val canvas=Canvas(bitmap);private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val layout=KeyLayout(21,108);private val keys=layout.shapes.associateBy { it.note }
    private val span=(lookAhead.coerceIn(1f,12f)*1000000).toLong()
    private val rollHeight=height*.76f;private val keyHeight=height-rollHeight
    private val colors=intArrayOf(0xff86bca7.toInt(),0xff84a8d0.toInt(),0xffc5a4cd.toInt(),0xffcbb579.toInt(),0xffc78d8b.toInt())
    private fun rect(l: Float,t: Float,r: Float,b: Float,color: Int){paint.style=Paint.Style.FILL;paint.color=color;canvas.drawRect(l,t,r,b,paint)}
    private fun label(key: Int,x: Float,top: Float,w: Float,h: Float,black: Boolean=false) {
        if(w<=3 || h<14)return
        val text=solfege(key);paint.typeface=Typeface.DEFAULT_BOLD;paint.textAlign=Paint.Align.CENTER;paint.textScaleX=1f
        paint.textSize=minOf(height/36f,(w-4)*.98f,(h-5)/1.2f);paint.textScaleX=minOf(1f,(w-4)/paint.measureText(text).coerceAtLeast(1f))
        val baseline=top+h-(if(black)5f else 6f)-paint.fontMetrics.descent
        paint.style=Paint.Style.STROKE;paint.strokeWidth=maxOf(1f,height/720f);paint.strokeJoin=Paint.Join.ROUND;paint.color=0xff111821.toInt();canvas.drawText(text,x,baseline,paint)
        paint.style=Paint.Style.FILL;paint.color=Color.WHITE;canvas.drawText(text,x,baseline,paint);paint.textScaleX=1f
    }
    fun draw(micros: Long) {
        canvas.drawColor(0xff1d2732.toInt());paint.strokeWidth=1f
        layout.shapes.filter { !it.black }.forEach { key->val x=viewport.screen(key.left)*width;paint.color=0xff2b3540.toInt();canvas.drawLine(x,0f,x,rollHeight,paint) }
        fun y(t: Long)=rollHeight-((t-micros).toDouble()/span*rollHeight).toFloat()
        for(second in (micros/1000000)..((micros+span)/1000000)){paint.color=0xff2a3540.toInt();canvas.drawLine(0f,y(second*1000000),width.toFloat(),y(second*1000000),paint)}
        canvas.save();canvas.clipRect(0f,0f,width.toFloat(),rollHeight)
        song.keyIndex.visible(micros,micros+span){ n->
            keys[n.key]?.let { key->
                val x=viewport.screen(key.left)*width+1;val w=(key.width*viewport.zoom*width-2).coerceAtLeast(2f)
                if(x+w>=0 && x<width){val top=y(n.keyEnd);val h=(y(n.start)-top).coerceAtLeast(3f);paint.color=colors[n.track%colors.size];paint.style=Paint.Style.FILL;val radius=minOf(4f,w*.15f);canvas.drawRoundRect(x,top,x+w,top+h,radius,radius,paint);if(labels)label(n.key,x+w/2,top,w,h)}
            }
        };canvas.restore()
        val held=HashSet<Int>();song.keyIndex.visible(micros,micros){if(micros<it.keyEnd)held+=it.key}
        canvas.save();canvas.clipRect(0f,rollHeight,width.toFloat(),height.toFloat())
        layout.shapes.forEach { key->
            val x=viewport.screen(key.left)*width;val w=key.width*viewport.zoom*width;val h=if(key.black)keyHeight*.62f else keyHeight
            if(x+w>=0 && x<width){val down=key.note in held;val color=if(key.black){if(down)0xff488a77.toInt()else 0xff10141a.toInt()}else{if(down)0xffa8d4bf.toInt()else 0xffe4e6e7.toInt()};rect(x+1,rollHeight,x+w-1,rollHeight+h,color);if(labels)label(key.note,x+w/2,rollHeight,w,h,key.black)}
        };canvas.restore()
        rect(0f,rollHeight-2,width.toFloat(),rollHeight,colors[0])
        if(song.sustainIndex.active(micros)){paint.color=0xffffd54f.toInt();paint.textSize=height/32f;paint.typeface=Typeface.DEFAULT_BOLD;paint.textAlign=Paint.Align.LEFT;canvas.drawText("SUS",12f,rollHeight-12f,paint)}
    }
    override fun close(){bitmap.recycle()}
}

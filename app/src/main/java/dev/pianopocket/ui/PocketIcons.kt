package dev.pianopocket.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

// Original vector drawings, with 48dp targets and accessible names / long-press hints.
enum class PocketIcon { Back, Forward, Play, Pause, Stop, Record, Export, Mixer, Settings, Midi, Library, Close, More, Zoom, Pedal, Roll, Info, Eye, Undo, Redo, Delete }
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PocketIconButton(icon: PocketIcon,label: String,modifier: Modifier=Modifier,enabled: Boolean=true,selected: Boolean=false,onClick:()->Unit) {
    TooltipBox(positionProvider=TooltipDefaults.rememberPlainTooltipPositionProvider(),tooltip={PlainTooltip { Text(label) }},state=rememberTooltipState()) {
        IconButton(onClick=onClick,enabled=enabled,modifier=modifier.size(48.dp).semantics { contentDescription=label;this.selected=selected },colors=IconButtonDefaults.iconButtonColors(containerColor=if(selected)MaterialTheme.colorScheme.primary.copy(alpha=.16f)else Color.Transparent,contentColor=if(icon==PocketIcon.Record)Color(0xffe4a098)else MaterialTheme.colorScheme.onSurface)) {
            val color=LocalContentColor.current
            Canvas(Modifier.size(24.dp)) {
                val unit=size.width/24f
                fun point(x: Float,y: Float)=Offset(x*unit,y*unit)
                fun line(x: Float,y: Float,xx: Float,yy: Float)=drawLine(color,point(x,y),point(xx,yy),1.7f*unit,StrokeCap.Round)
                fun rect(x: Float,y: Float,w: Float,h: Float,fill: Boolean=false)=drawRect(color,point(x,y),Size(w*unit,h*unit),style=if(fill)androidx.compose.ui.graphics.drawscope.Fill else Stroke(1.7f*unit))
                when(icon) {
                    PocketIcon.Undo, PocketIcon.Redo -> {
                        val flip=icon==PocketIcon.Redo
                        fun xp(x: Float)=if(flip)24f-x else x
                        line(xp(4f),8f,xp(13f),8f);line(xp(4f),8f,xp(8f),4f);line(xp(4f),8f,xp(8f),12f)
                        drawArc(color,if(flip)90f else -90f,if(flip)-180f else 180f,false,point(if(flip)5f else 11f,8f),Size(8*unit,10*unit),style=Stroke(1.7f*unit))
                        line(xp(15f),18f,xp(9f),18f)
                    }
                    PocketIcon.Delete -> {line(5f,6f,19f,6f);line(9f,3f,15f,3f);rect(7f,6f,10f,15f);line(10f,10f,10f,17f);line(14f,10f,14f,17f)}
                    PocketIcon.Eye -> {
                        drawPath(Path().apply {moveTo(2*unit,12*unit);cubicTo(7*unit,3*unit,17*unit,3*unit,22*unit,12*unit);cubicTo(17*unit,21*unit,7*unit,21*unit,2*unit,12*unit);close()},color,style=Stroke(1.7f*unit))
                        drawCircle(color,3*unit,point(12f,12f),style=Stroke(1.7f*unit))
                    }
                    PocketIcon.Back -> {line(18f,12f,5f,12f);line(5f,12f,11f,6f);line(5f,12f,11f,18f)}
                    PocketIcon.Forward -> {line(6f,12f,19f,12f);line(19f,12f,13f,6f);line(19f,12f,13f,18f)}
                    PocketIcon.Play -> drawPath(Path().apply{moveTo(8*unit,5*unit);lineTo(20*unit,12*unit);lineTo(8*unit,19*unit);close()},color)
                    PocketIcon.Pause -> {rect(6f,5f,4f,14f,true);rect(14f,5f,4f,14f,true)}
                    PocketIcon.Stop -> rect(6f,6f,12f,12f,true)
                    PocketIcon.Record -> drawCircle(color,7*unit,point(12f,12f),style=if(selected)androidx.compose.ui.graphics.drawscope.Fill else Stroke(2*unit))
                    PocketIcon.Export -> {line(12f,15f,12f,3f);line(12f,3f,7f,8f);line(12f,3f,17f,8f);line(5f,13f,5f,20f);line(5f,20f,19f,20f);line(19f,20f,19f,13f)}
                    PocketIcon.Mixer -> {for(x in listOf(6f,12f,18f))line(x,3f,x,21f);for((x,y) in listOf(6f to 8f,12f to 16f,18f to 10f))drawCircle(color,2.5f*unit,point(x,y))}
                    PocketIcon.Settings -> {drawCircle(color,5*unit,point(12f,12f),style=Stroke(2*unit));drawCircle(color,1.6f*unit,point(12f,12f));repeat(8){val a=it*kotlin.math.PI/4;line((12+8*kotlin.math.cos(a)).toFloat(),(12+8*kotlin.math.sin(a)).toFloat(),(12+10*kotlin.math.cos(a)).toFloat(),(12+10*kotlin.math.sin(a)).toFloat())}}
                    PocketIcon.Midi -> {rect(3f,5f,18f,14f);for(x in listOf(7f,12f,17f))line(x,12f,x,19f);for(x in listOf(7f,12f,17f))rect(x-1f,5f,2f,7f,true)}
                    PocketIcon.Library -> {rect(3f,7f,18f,13f);line(3f,7f,3f,4f);line(3f,4f,10f,4f);line(10f,4f,13f,7f)}
                    PocketIcon.Close -> {line(6f,6f,18f,18f);line(6f,18f,18f,6f)}
                    PocketIcon.More -> for(y in listOf(5f,12f,19f))drawCircle(color,1.8f*unit,point(12f,y))
                    PocketIcon.Zoom -> {rect(3f,5f,14f,14f);line(7f,12f,13f,12f);if(!selected)line(10f,9f,10f,15f);line(17f,17f,21f,21f)}
                    PocketIcon.Pedal -> {line(4f,20f,20f,20f);line(5f,18f,10f,5f);line(10f,5f,17f,5f);line(17f,5f,20f,18f);line(5f,18f,20f,18f)}
                    PocketIcon.Roll -> {rect(3f,2f,18f,20f);rect(6f,5f,3f,5f,true);rect(11f,10f,3f,6f,true);rect(16f,4f,2f,9f,true)}
                    PocketIcon.Info -> {drawCircle(color,9*unit,point(12f,12f),style=Stroke(1.7f*unit));line(12f,11f,12f,17f);drawCircle(color,1f*unit,point(12f,7f))}
                }
            }
        }
    }
}

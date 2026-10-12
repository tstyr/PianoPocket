package dev.pianopocket

import dev.pianopocket.sequencer.KeyboardViewport
import dev.pianopocket.ui.KeyLayout
import org.junit.Assert.*
import org.junit.Test

class ViewportTest {
    @Test fun pinchKeepsTheNoteUnderItsMovingCenter() {
        val before=KeyboardViewport(2f,.2f)
        val after=before.transform(1.5f,.5f,.08f)
        assertEquals(before.world(.5f),after.world(.58f),.000001f)
        assertEquals(3f,after.zoom,0f)
    }
    @Test fun zoomedDrawingAndTouchUseTheSameKeysAndClipScreenEdges() {
        val keyboard=KeyLayout(21,108)
        for(view in listOf(KeyboardViewport(),KeyboardViewport.middleTwoOctaves(),KeyboardViewport(6.5f,.4f))) {
            for(key in keyboard.shapes) {
                val x=view.screen(key.left+key.width/2)
                if(x in 0f..<1f)assertEquals("note ${key.note}, zoom ${view.zoom}",key.note,keyboard.hit(x,if(key.black).2f else .9f,view))
            }
            assertNull(keyboard.hit(-.01f,.9f,view));assertNull(keyboard.hit(1.01f,.9f,view))
            assertNull(keyboard.hit(.5f,-.01f,view));assertNull(keyboard.hit(.5f,1f,view))
        }
        val middle=KeyboardViewport.middleTwoOctaves()
        assertEquals(60,keyboard.hit(.001f,.9f,middle));assertEquals(83,keyboard.hit(.999f,.9f,middle))
    }
    @Test fun zoomAndPanStayInsideTheReal88KeyRangeAndReset() {
        val max=KeyboardViewport().transform(1000f,.5f,0f)
        assertEquals(6.5f,max.zoom,0f)
        val left=max.transform(1f,.5f,100f);val right=max.transform(1f,.5f,-100f)
        assertEquals(0f,left.start,0f);assertEquals(right.maxStart,right.start,0f)
        assertEquals(KeyboardViewport(),right.transform(.001f,.2f,0f))
        assertEquals(max,max.transform(Float.NaN,.5f,0f));assertEquals(max,max.transform(-1f,.5f,0f))
        assertEquals(max.zoom,max.shiftOctave(1).zoom,0f)
        assertEquals(0f,left.shiftOctave(-1).start,0f);assertEquals(right.maxStart,right.shiftOctave(1).start,0f)
    }
}

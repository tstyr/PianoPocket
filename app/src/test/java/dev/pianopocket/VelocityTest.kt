package dev.pianopocket
import dev.pianopocket.library.DspresetParser
import org.junit.Test
import org.junit.Assert.*

class VelocityTest {
    @Test fun perVoiceTablesTagsAndIrMenus() {
        val xml="""<DecentSampler><ui><tags><tag name="all" polyphony="70" /></tags><tab><control label="Mic" value="0.3"><binding type="amp" level="tag" identifier="mic" parameter="AMP_VOLUME" /></control><control label="Master" maxValue="1.5" value="1"><binding type="amp" level="instrument" parameter="AMP_VOLUME" /></control><menu value="2"><option name="A"><binding type="effect" level="instrument" position="0" parameter="FX_IR_FILE" translation="fixed_value" translationValue="IR/a.wav" /></option><option name="B"><binding type="effect" level="instrument" position="0" parameter="FX_IR_FILE" translation="fixed_value" translationValue="IR/b.wav" /></option></menu></tab></ui><groups tags="all"><group tags="mic"><sample path="a.wav" rootNote="60" /></group><group tags="mic"><sample path="b.wav" rootNote="60" /></group></groups><modulators><midiVelocity scope="voice" modAmount="1"><binding type="amp" level="group" groupIndex="0" parameter="AMP_VOLUME" modBehavior="multiply" translation="table" translationTable="0,1;0.5,0.5;1,0" /><binding type="amp" level="group" groupIndex="1" parameter="AMP_VOLUME" modBehavior="multiply" translation="table" translationTable="0,0;1,1" /></midiVelocity></modulators><effects><effect type="convolution" irFile="IR/a.wav" /><effect type="gain" level="12" /></effects></DecentSampler>"""
        val p=DspresetParser().parse(xml.byteInputStream(),"Test")
        assertEquals(2,p.controls[0].bindings.size);assertEquals("master",p.controls[1].bindings.single().type)
        assertEquals(70,p.voiceLimit);assertEquals(1,p.menus.single().selected);assertEquals("IR/b.wav",p.menus.single().options[1].path)
        val low=p.velocityCurves.getValue(0);val high=p.velocityCurves.getValue(1)
        for(v in 0..127){assertEquals(1-v/127f,low[v],.00001f);assertEquals(v/127f,high[v],.00001f);assertEquals(1f,low[v]+high[v],.00001f)}
        assertEquals(3.9810717f,p.effects[1].params[1],.00001f)
        assertFalse(p.warnings.any { "Unsupported velocity" in it || "Unsupported binding" in it })
    }
    @Test fun integralDecimalFramesAccepted(){val p=DspresetParser().parse("""<DecentSampler><groups><group><sample path="a.wav" rootNote="2" start="0.000" end="120872.000" onLoCC64="64" onHiCC64="127" loNote="1" hiNote="1" /></group></groups></DecentSampler>""".byteInputStream(),"Test");assertEquals(120872,p.regions.single().ints[8]);assertEquals(2,p.regions.single().ints[6])}
    @Test(expected=IllegalArgumentException::class) fun unorderedVelocityTableRejected(){DspresetParser().parse("""<DecentSampler><groups><group><sample path="a.wav" /></group></groups><modulators><midiVelocity scope="voice"><binding type="amp" level="group" groupIndex="0" parameter="AMP_VOLUME" modBehavior="multiply" translation="table" translationTable="0,0;1,1;0.5,0.5" /></midiVelocity></modulators></DecentSampler>""".byteInputStream(),"Test")}
}

package dev.pianopocket
import dev.pianopocket.library.DspresetParser
import dev.pianopocket.midi.MidiByteDecoder
import org.junit.Test
import org.junit.Assert.*

class ParserTest {
    private fun parse(body: String)=DspresetParser().parse(body.byteInputStream(),"Test")
    @Test fun inheritsRangesButMultipliesVolume() {
        val p=parse("""<DecentSampler><groups loNote="36" hiNote="84" volume="0.5" pan="-50"><group volume="0.5" rootNote="60" release="0.4"><sample path="Samples/C4.wav" volume="-6dB" pan="0" loVel="32" hiVel="96" /></group></groups></DecentSampler>""")
        val r=p.regions.single();assertEquals(36,r.ints[1]);assertEquals(84,r.ints[2]);assertEquals(60,r.ints[3]);assertEquals(32,r.ints[4]);assertEquals(.2505936f,r.floats[0],.00001f);assertEquals(.5f,p.groupVolumes[0]);assertEquals(0f,r.floats[1]);assertEquals(.4f,r.floats[6])
    }
    @Test fun releaseAndPedalAreExplicit() {
        val p=parse("""<DecentSampler><groups><group trigger="release"><sample path="release.wav" rootNote="60" /></group><group onLoCC64="64" onHiCC64="127"><sample path="down.wav" rootNote="60" /></group></groups></DecentSampler>""")
        assertEquals(1,p.regions[0].ints[6]);assertEquals(2,p.regions[1].ints[6])
    }
    @Test fun effectOrderAndBinding() {
        val p=parse("""<DecentSampler><ui><tab><control label="Wet" value="0.3"><binding type="effect" level="instrument" effectIndex="1" parameter="FX_REVERB_WET_LEVEL" translation="linear" /></control></tab></ui><groups><group><sample path="a.wav" rootNote="60" /></group></groups><effects><effect type="gain" level="0.5" /><effect type="reverb" roomSize="0.8" damping="0.2" wetLevel="0.4" enabled="false" /></effects></DecentSampler>""")
        assertEquals(listOf(6,0),p.effects.map { it.type });assertEquals(.8f,p.effects[1].params[1]);assertEquals(0f,p.effects[1].params[4]);assertEquals(1,p.controls.single().bindings.single().index)
    }
    @Test fun legacyLowpassHasOfficialDefaultsAndPreservesParameters() {
        fun preset(type: String, attributes: String)=parse("""<DecentSampler><groups><group><sample path="a.wav" rootNote="60" /></group></groups><effects><effect type="$type" $attributes /></effects></DecentSampler>""")
        for(attributes in listOf("", "frequency=\"1750\" resonance=\"1.2\" enabled=\"false\"")) {
            val current=preset("lowpass",attributes).effects.single()
            val legacy=preset("lowpass_4pl",attributes).effects.single()
            assertEquals(3,legacy.type)
            assertEquals("lowpass",legacy.name)
            assertArrayEquals(current.params,legacy.params,0f)
        }
        assertArrayEquals(floatArrayOf(0f,22000f,.7f,1f,1f),preset("lowpass_4pl","").effects.single().params,0f)
    }
    @Test fun legacyLowpassPreservesChainOrderAndOfficialBindings() {
        val p=parse("""<DecentSampler><ui><tab>
            <control label="Tone" value="0.5" minValue="0" maxValue="1"><binding type="effect" level="instrument" effectIndex="1" parameter="FX_FILTER_FREQUENCY" translation="linear" translationOutputMin="100" translationOutputMax="8000" /></control>
            <control label="Resonance" value="0.7"><binding type="effect" level="instrument" effectIndex="1" parameter="FX_FILTER_RESONANCE" translation="linear" /></control>
            <control label="On" value="1"><binding type="effect" level="instrument" effectIndex="1" parameter="ENABLED" translation="linear" /></control>
            </tab></ui><groups><group><sample path="a.wav" rootNote="60" /></group></groups>
            <effects><effect type="gain" level="-6" /><effect type="lowpass_4pl" frequency="2000" resonance="0.8" /><effect type="gain" level="-3" /></effects></DecentSampler>""")
        assertEquals(listOf(6,3,6),p.effects.map {it.type})
        assertEquals(2000f,p.effects[1].params[1],0f)
        assertEquals(.8f,p.effects[1].params[2],0f)
        assertEquals(listOf(1,2,4),p.controls.map {it.bindings.single().slot})
        assertTrue(p.controls.all {it.bindings.single().index==1})
        assertEquals(100f,p.controls.first().bindings.single().outMin,0f)
        assertEquals(8000f,p.controls.first().bindings.single().outMax,0f)
        assertTrue(p.warnings.none {"Unsupported binding" in it || "lowpass_4pl" in it})
    }
    @Test fun unknownFilterIsStillRejectedWithItsName() {
        try {
            parse("""<DecentSampler><groups><group><sample path="a.wav" /></group></groups><effects><effect type="lowpass_8pl" /></effects></DecentSampler>""")
            fail("An undocumented filter must not silently become lowpass")
        }catch(e: IllegalStateException) {
            assertTrue(e.message.orEmpty().contains("lowpass_8pl"))
            assertTrue(e.message.orEmpty().contains("未対応"))
        }
    }
    @Test fun unknownFeaturesWarn() { val p=parse("""<DecentSampler><groups><group seqMode="round_robin"><sample path="a.wav" rootNote="60" mystery="x" /></group></groups><modulators><lfo /></modulators></DecentSampler>""");assertTrue(p.warnings.any { "mystery" in it });assertTrue(p.warnings.any { "lfo" in it }) }
    @Test(expected=IllegalArgumentException::class) fun traversalRejected(){DspresetParser.safeRelative("Samples/../../secret.wav")}
    @Test(expected=IllegalArgumentException::class) fun absoluteRejected(){DspresetParser.safeRelative("C:\\samples\\a.wav")}
    @Test(expected=IllegalStateException::class) fun doctypeRejected(){parse("""<!DOCTYPE DecentSampler [<!ENTITY e SYSTEM "file:///etc/passwd">]><DecentSampler><groups /></DecentSampler>""")}
    @Test(expected=IllegalArgumentException::class) fun invalidRangeRejected(){parse("""<DecentSampler><groups><group><sample path="a.wav" loNote="70" hiNote="60" /></group></groups></DecentSampler>""")}
    @Test fun midiFragmentedRunningStatusRealtimeAndZeroVelocity(){
        val messages=mutableListOf<List<Int>>();val d=MidiByteDecoder { s,a,b->messages+=listOf(s,a,b) }
        d.feed(byteArrayOf(0x90.toByte(),60),0,2);d.feed(byteArrayOf(0xf8.toByte(),100,61,0,0xb1.toByte(),64,127),0,7)
        assertEquals(listOf(listOf(0x90,60,100),listOf(0x90,61,0),listOf(0xb1,64,127)),messages)
    }
    @Test fun midiSysexDoesNotLeakNotes(){val messages=mutableListOf<Int>();val d=MidiByteDecoder { s,_,_->messages+=s };d.feed(byteArrayOf(0xf0.toByte(),60,100,0xf8.toByte(),0xf7.toByte(),60,100,0x81.toByte(),60,0),0,10);assertEquals(listOf(0x81),messages)}
}

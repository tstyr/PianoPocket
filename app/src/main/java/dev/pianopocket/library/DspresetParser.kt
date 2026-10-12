package dev.pianopocket.library

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import kotlin.math.pow

data class Region(val path: String, val ints: IntArray, val floats: FloatArray)
data class Effect(val type: Int, val name: String, val params: FloatArray, val ir: String = "", val gainDecibels: Boolean = false)
data class Binding(val type: String, val index: Int, val slot: Int, val outMin: Float, val outMax: Float, val decibels: Boolean = false)
data class Control(val name: String, val min: Float, val max: Float, var value: Float, val bindings: List<Binding>)
data class IrChoice(val name: String, val effectIndex: Int, val path: String)
data class IrMenu(val name: String, val options: List<IrChoice>, val selected: Int)
data class Preset(val name: String, val regions: List<Region>, val groups: List<String>, val effects: List<Effect>, val controls: List<Control>, val warnings: List<String>, val groupVolumes: List<Float>, val velocityCurves: Map<Int,FloatArray> = emptyMap(), val menus: List<IrMenu> = emptyList(), val voiceLimit: Int? = null)

/** Portable XML mapping parser. Rendering never invokes this class. */
class DspresetParser {
    private data class Node(val name: String, val a: Map<String, String>, val children: MutableList<Node> = mutableListOf())
    private val warnings = linkedSetOf<String>()
    private val mappingAttrs = setOf("path", "name", "tags", "rootNote", "loNote", "hiNote", "loVel", "hiVel", "volume", "pan", "globalVolume", "groupVolume", "globalPan", "groupPan", "tuning", "groupTuning", "globalTuning", "attack", "decay", "sustain", "release", "ampVelTrack", "pitchKeyTrack", "trigger", "start", "end", "loopStart", "loopEnd", "loopEnabled", "loopDuringRelease", "enabled", "onLoCC64", "onHiCC64", "loCC64", "hiCC64")
    fun parse(input: InputStream, fallbackName: String): Preset {
        warnings.clear()
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, "UTF-8")
        val stack = ArrayDeque<Node>(); var root: Node? = null; var count = 0
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.DOCDECL -> error("DOCTYPE / external entities are forbidden")
                XmlPullParser.START_TAG -> {
                    require(++count <= 30000 && stack.size < 32) { "XML complexity limit exceeded" }
                    val node = Node(parser.name, (0 until parser.attributeCount).associate { parser.getAttributeName(it) to parser.getAttributeValue(it) })
                    if (stack.isEmpty()) { require(root == null); root = node } else stack.last().children += node
                    stack.addLast(node)
                }
                XmlPullParser.END_TAG -> stack.removeLast()
            }
            parser.nextToken()
        }
        val doc = requireNotNull(root); require(doc.name == "DecentSampler") { "Not a Decent Sampler preset" }
        val groupsNode = doc.children.singleOrNull { it.name == "groups" } ?: error("Exactly one <groups> is required")
        val groupNodes = groupsNode.children.filter { it.name == "group" }
        require(groupNodes.size in 1..256) { "Group limit: 256" }
        val regions = mutableListOf<Region>()
        val labels = groupNodes.mapIndexed { i, n -> n.a["name"] ?: "Group ${i + 1}" }
        val groupVolumes = groupNodes.map { gain(it.a["groupVolume"] ?: it.a["volume"] ?: "1") }
        val tags=groupNodes.map { ((groupsNode.a["tags"] ?: "")+","+(it.a["tags"] ?: "")).split(',').map(String::trim).filter(String::isNotEmpty).toSet() }
        val velocityCurves=mutableMapOf<Int,FloatArray>()
        doc.children.filter { it.name=="modulators" }.flatMap { it.children }.forEach { mod ->
            if(mod.name!="midiVelocity" || mod.a["scope"]!="voice" || number(mod.a["modAmount"],1f)!=1f || mod.a["enabled"]=="false") { warnings+="Unsupported modulator: <${mod.name}> ${mod.a}";return@forEach }
            checkAttrs(mod,setOf("scope","modAmount","enabled"))
            mod.children.forEach { binding ->
                val a=binding.a;val group=(a["groupIndex"] ?: a["position"])?.toIntOrNull()
                if(binding.name!="binding" || a["type"]!="amp" || a["level"]!="group" || a["parameter"]!="AMP_VOLUME" || a["modBehavior"]!="multiply" || a["translation"]!="table" || group==null || group !in groupNodes.indices) { warnings+="Unsupported velocity binding: $a";return@forEach }
                checkAttrs(binding,setOf("type","level","groupIndex","position","parameter","modBehavior","translation","translationTable"))
                val points=(a["translationTable"] ?: error("Missing velocity table")).split(';').map { pair ->
                    val values=pair.split(',');require(values.size==2) { "Invalid velocity table" };number(values[0],0f) to number(values[1],0f)
                }
                require(points.size in 2..256 && points.zipWithNext().all { it.first.first<it.second.first } && points.all { it.second in 0f..8f }) { "Invalid velocity table range/order" }
                val curve=velocityCurves.getOrPut(group){FloatArray(128){1f}}
                for(v in 0..127){val x=v/127f;val upper=points.indexOfFirst { it.first>=x };val y=when { upper==0 -> points.first().second;upper<0 -> points.last().second;else -> {val lo=points[upper-1];val hi=points[upper];lo.second+(x-lo.first)/(hi.first-lo.first)*(hi.second-lo.second)} };curve[v]*=y}
            }
        }
        val globalGain = gain(groupsNode.a["globalVolume"] ?: groupsNode.a["volume"] ?: "1")
        checkAttrs(groupsNode, mappingAttrs)
        groupNodes.forEachIndexed { index, group ->
            checkAttrs(group, mappingAttrs)
            if (group.children.any { it.name == "effects" }) warnings += "Group $index effects are unsupported; only the instrument effect chain is rendered."
            group.children.filter { it.name == "sample" }.forEach { sample ->
                checkAttrs(sample, mappingAttrs)
                val a = groupsNode.a + group.a + sample.a
                fun num(key: String, default: Float) = number(a[key], default)
                fun integer(key: String, default: Int) = a[key]?.let { integerNumber(it,key) } ?: default
                fun note(key: String, default: Int) = a[key]?.let(::noteNumber) ?: default
                val trigger = when (a["trigger"] ?: "attack") { "attack" -> 0; "release" -> 1; else -> { warnings += "Unsupported trigger '${a["trigger"]}': sample ${sample.a["path"]} skipped"; return@forEach } }
                val pedalLow = a["onLoCC64"]?.toIntOrNull(); val pedalHigh = a["onHiCC64"]?.toIntOrNull()
                val resolvedTrigger = if (pedalLow != null && pedalHigh != null) {
                    if (pedalLow >= 64 && pedalHigh == 127) 2 else if (pedalLow == 0 && pedalHigh <= 63) 3 else { warnings += "CC64 trigger range $pedalLow..$pedalHigh unsupported; region skipped"; return@forEach }
                } else trigger
                if(resolvedTrigger>=2)warnings+="CC64 sample triggers are debounced at the 64 boundary; continuous pedal messages do not repeatedly retrigger noise."
                val low = note("loNote", 0); val high = note("hiNote", 127); val rootNote = note("rootNote", 60)
                val loVel = integer("loVel", 0); val hiVel = integer("hiVel", 127)
                require(low in 0..127 && high in low..127 && rootNote in 0..127 && loVel in 0..127 && hiVel in loVel..127) { "Invalid zone range" }
                val loop = a["loopEnabled"] == "true"
                if (loop && (!a.containsKey("loopStart") || !a.containsKey("loopEnd"))) warnings += "Embedded WAV loop markers are unsupported; explicit loopStart/loopEnd required. Loop disabled."
                val explicitLoop = loop && a.containsKey("loopStart") && a.containsKey("loopEnd")
                if (explicitLoop && a["loopDuringRelease"] != "false") warnings += "Loops stop on release in this version; Decent Sampler's default loopDuringRelease=true differs."
                val sampleGain = gain(sample.a["volume"] ?: "1")
                val pan = (num("pan", 0f) + number(groupsNode.a["globalPan"], 0f) + number(group.a["groupPan"], 0f)).coerceIn(-100f, 100f) / 100
                val tuning = (num("tuning", 0f) + number(groupsNode.a["globalTuning"], 0f) + number(group.a["groupTuning"], 0f)) * 100
                val cc = if (a.containsKey("loCC64") || a.containsKey("hiCC64")) 64 else -1
                val path = sample.a["path"] ?: error("Missing sample path")
                safeRelative(path)
                val ints = intArrayOf(index, low, high, rootNote, loVel, hiVel, resolvedTrigger, integer("start", 0), integer("end", -1), if (explicitLoop) integer("loopStart", -1) else -1, if (explicitLoop) integer("loopEnd", -1) else -1, cc, integer("loCC64", 0), integer("hiCC64", 127), -1, -1, -1, -1, if (a["enabled"] == "false") 0 else 1)
                val floats = floatArrayOf(globalGain * sampleGain, pan, tuning, num("attack", .003f).coerceIn(0f, 60f), num("decay", .1f).coerceIn(0f, 60f), num("sustain", 1f).coerceIn(0f, 1f), num("release", .25f).coerceIn(0f, 120f), num("ampVelTrack", 1f).coerceIn(0f, 1f), num("pitchKeyTrack", 1f).coerceIn(0f, 1f))
                regions += Region(path, ints, floats)
                require(regions.size <= 16000) { "Region limit exceeded" }
            }
        }
        warnings += "ADSR curves and reverb/delay/chorus algorithms are approximations; no bit-identical Decent Sampler claim."
        val effectsNode = doc.children.firstOrNull { it.name == "effects" }
        val effects = effectsNode?.children?.filter { it.name == "effect" }?.map { parseEffect(it) }.orEmpty()
        require(effects.size <= 8) { "Maximum 8 global effects" }
        val controls = mutableListOf<Control>()
        val menus=mutableListOf<IrMenu>()
        fun scan(n: Node) {
            if (n.name in setOf("control", "labeled-knob")) {
                val min = number(n.a["minValue"], 0f); val max = number(n.a["maxValue"], 1f)
                val bs = n.children.filter { it.name == "binding" }.flatMap { binding ->
                    if(binding.a["type"]=="amp" && binding.a["level"]=="tag" && binding.a["parameter"]=="AMP_VOLUME") {
                        val target=binding.a["identifier"];val matches=tags.indices.filter { target in tags[it] }
                        if(matches.isEmpty())warnings+="Binding tag not found: $target"
                        matches.mapNotNull { i->parseBinding(Node("binding",binding.a-"identifier"+mapOf("level" to "group","groupIndex" to "$i")),effects,groupNodes.size) }
                    }else listOfNotNull(parseBinding(binding,effects,groupNodes.size))
                }
                if (bs.isNotEmpty() && max > min) controls += Control(n.a["label"] ?: n.a["name"] ?: "Control ${controls.size+1}", min, max, number(n.a["value"], min).coerceIn(min,max), bs)
            }
            if(n.name=="menu") {
                val options=n.children.filter { it.name=="option" }.mapNotNull { option ->
                    val bindings=option.children.filter { it.name=="binding" }
                    val b=bindings.singleOrNull();val a=b?.a.orEmpty();val index=(a["effectIndex"] ?: a["position"])?.toIntOrNull() ?: -1
                    if(b==null || a["type"]!="effect" || a["level"]!="instrument" || a["parameter"]!="FX_IR_FILE" || a["translation"]!="fixed_value" || index !in effects.indices || effects[index].type!=7) { warnings+="Unsupported menu option: ${option.a}";null }
                    else {val path=a["translationValue"] ?: error("Missing IR menu path");safeRelative(path);IrChoice(option.a["name"] ?: path,index,path)}
                }
                if(options.size==n.children.count { it.name=="option" } && options.isNotEmpty()) {
                    val selection=(n.a["value"]?.let { integerNumber(it,"menu value") } ?: 1)-1
                    require(selection in options.indices) { "IR menu selection out of range" }
                    menus+=IrMenu(n.a["label"] ?: n.a["tooltip"] ?: "Impulse response",options,selection)
                }
            }
            n.children.forEach(::scan)
        }
        doc.children.filter { it.name == "ui" }.forEach(::scan)
        val structural = setOf("DecentSampler", "groups", "group", "sample", "effects", "effect", "ui", "tab", "control", "labeled-knob", "binding","menu","option","tags","tag","modulators","midiVelocity")
        fun unknown(n: Node) { if (n.name !in structural) warnings += "Unsupported XML element <${n.name}>"; n.children.forEach(::unknown) }
        unknown(doc)
        checkAttrs(doc, setOf("minVersion", "pluginVersion", "name"))
        require(regions.isNotEmpty()) { "No supported sample regions" }
        val polyphony=doc.children.filter { it.name=="ui" }.flatMap { it.children }.filter { it.name=="tags" }.flatMap { it.children }.mapNotNull { tag ->
            val value=tag.a["polyphony"]?.let { integerNumber(it,"polyphony") }
            if(value!=null && tag.a["name"] in tags.flatten().toSet())value else null
        }.minOrNull()
        if(polyphony!=null)warnings+="XML tag polyphony=$polyphony counts tagged notes in the original; it is not used to reduce the app sample-voice budget. Tag-specific pools are not reproduced."
        return Preset(doc.a["name"] ?: fallbackName, regions, labels, effects, controls, warnings.toList(), groupVolumes,velocityCurves,menus,polyphony)
    }
    private fun checkAttrs(n: Node, known: Set<String>) { n.a.keys.filter { it !in known }.forEach { warnings += "Unsupported ${n.name} attribute '$it' = '${n.a[it]}'" } }
    private fun number(s: String?, default: Float): Float = if (s == null) default else s.toFloatOrNull()?.takeIf { it.isFinite() } ?: error("Invalid number: $s")
    private fun gain(s: String): Float = if (s.endsWith("dB", true)) 10f.pow(number(s.dropLast(2), 0f) / 20f).coerceIn(0f, 8f) else number(s, 1f).coerceIn(0f, 8f)
    private fun integerNumber(value: String,key: String):Int {
        val number=value.toDoubleOrNull() ?: error("Invalid integer $key")
        require(number.isFinite() && number>=Int.MIN_VALUE && number<=Int.MAX_VALUE && number==kotlin.math.floor(number)) { "Non-integral/out-of-range $key" }
        return number.toInt()
    }
    private fun noteNumber(s: String): Int {
        s.toIntOrNull()?.let { return it }
        val m = Regex("([A-Ga-g])([#b]?)(-?\\d+)").matchEntire(s) ?: error("Invalid MIDI note: $s")
        val pitch = mapOf("C" to 0, "D" to 2, "E" to 4, "F" to 5, "G" to 7, "A" to 9, "B" to 11).getValue(m.groupValues[1].uppercase())
        return (m.groupValues[3].toInt()+1)*12 + pitch + when(m.groupValues[2]) { "#" -> 1; "b" -> -1; else -> 0 }
    }
    private fun parseEffect(n: Node): Effect {
        val t = n.a["type"] ?: error("Missing effect type")
        fun f(k: String, d: Float) = number(n.a[k], d)
        val enabled = if (n.a["enabled"] == "false") 0f else 1f
        val p = when (t) {
            "reverb" -> floatArrayOf(f("wetLevel", 0f), f("roomSize", .7f), f("damping", .3f), 0f, enabled)
            "delay" -> { require(n.a["delayTimeFormat"] != "musical_time") { "Tempo-synced delay unsupported" }; floatArrayOf(f("wetLevel", .5f), f("delayTime", .7f), f("feedback", .2f), f("stereoOffset", 0f), enabled) }
            "chorus" -> floatArrayOf(f("mix", .5f), f("modDepth", .2f), f("modRate", .2f), 0f, enabled)
            "gain" -> floatArrayOf(0f, if(n.a["levelUnit"]=="linear") gain(n.a["level"] ?: "0.5") else gain((n.a["level"] ?: "-6").removeSuffix("dB")+"dB"), 0f, 0f, enabled)
            "convolution" -> floatArrayOf(f("mix", .5f), 0f, 0f, 0f, enabled)
            "lowpass", "highpass", "bandpass", "peak", "notch", "low_shelf", "high_shelf" -> floatArrayOf(0f, f("frequency", 22000f), f(if(t=="notch")"Q" else "q", f("resonance", .7f)), if(n.a["gainUnit"] == "decibels") 10f.pow(f("gain",0f)/20) else f("gain",1f), enabled)
            else -> { warnings += "Unsupported effect '$t': import rejected to avoid pretending effect compatibility"; error(warnings.last()) }
        }
        val types = listOf("reverb", "delay", "chorus", "lowpass", "highpass", "bandpass", "gain", "convolution", "peak", "notch", "low_shelf", "high_shelf")
        val known = when(t) { "reverb" -> setOf("wetLevel","roomSize","damping"); "delay" -> setOf("wetLevel","delayTime","feedback","stereoOffset","delayTimeFormat"); "chorus" -> setOf("mix","modDepth","modRate"); "gain" -> setOf("level","levelUnit"); "convolution" -> setOf("mix","irFile"); else -> setOf("frequency","resonance","q","Q","gain","gainUnit") }
        checkAttrs(n, known + setOf("type","enabled"))
        if(t=="delay" && (p[1] !in 0f..20f || p[2] !in 0f..1f || p[3] !in -10f..10f)) error("Delay parameters outside documented range")
        if(t=="delay" && p[2]>=.995f){p[2]=.995f;warnings += "Delay feedback capped at 0.995 for stability"}
        if(t=="convolution") {
            safeRelative(n.a["irFile"] ?: error("Missing IR file"))
            warnings += "Convolution IRの音量をPianoPocket用に補正します。純正のIR正規化とは未比較です。"
        }
        return Effect(types.indexOf(t), t, p, n.a["irFile"] ?: "", n.a["gainUnit"]=="decibels")
    }
    private fun parseBinding(n: Node, effects: List<Effect>, groups: Int): Binding? {
        val a=n.a; val translation=a["translation"] ?: "linear"
        val group=a["level"]=="group" && a["type"]=="amp"
        val master=a["level"]=="instrument" && a["type"]=="amp" && a["parameter"]=="AMP_VOLUME"
        val index=(a[if(group)"groupIndex" else "effectIndex"] ?: a["position"])?.toIntOrNull() ?: 0
        val parameter=a["parameter"] ?: ""
        val slots=mapOf("FX_REVERB_WET_LEVEL" to 0,"FX_REVERB_ROOM_SIZE" to 1,"FX_REVERB_DAMPING" to 2,"FX_DELAY_WET_LEVEL" to 0,"FX_DELAY_TIME" to 1,"FX_DELAY_FEEDBACK" to 2,"FX_DELAY_STEREO_OFFSET" to 3,"FX_CHORUS_MIX" to 0,"FX_CHORUS_MOD_DEPTH" to 1,"FX_CHORUS_MOD_RATE" to 2,"FX_FILTER_FREQUENCY" to 1,"FX_FILTER_RESONANCE" to 2,"FX_FILTER_Q" to 2,"FX_FILTER_GAIN" to 3,"FX_GAIN" to 1,"FX_CONVOLUTION_MIX" to 0,"ENABLED" to 4)
        val official=mapOf("FX_MIX" to 0,"FX_WET_LEVEL" to 0,"FX_MOD_DEPTH" to 1,"FX_MOD_RATE" to 2,"FX_FEEDBACK" to 2,"FX_STEREO_OFFSET" to 3,"LEVEL" to 1)
        val slot=if((group || master) && parameter=="AMP_VOLUME")0 else (official+slots)[parameter]
        if(translation!="linear" || a.containsKey("tags") || a.containsKey("translationTable") || slot==null || (group && index !in 0 until groups) || (!group && !master && (a["type"]!="effect" || a["level"]!="instrument" || index !in effects.indices))) {
            warnings += "Unsupported binding: $a";return null
        }
        checkAttrs(n,setOf("type","level","groupIndex","effectIndex","position","parameter","translation","translationOutputMin","translationOutputMax"))
        return Binding(if(group)"group" else if(master)"master" else "effect",if(master)0 else index,slot,number(a["translationOutputMin"],Float.NaN),number(a["translationOutputMax"],Float.NaN),!group && !master && parameter=="FX_FILTER_GAIN" && effects[index].gainDecibels)
    }
    companion object {
        fun safeRelative(path: String): String {
            val clean=path.replace('\\','/')
            require(clean.isNotBlank() && !clean.startsWith('/') && !clean.contains(':') && !clean.contains('\u0000') && clean.split('/').none { it==".." || it.isEmpty() }) { "Unsafe relative path: $path" }
            val normalized=clean.split('/').filter { it!="." }.joinToString("/")
            require(normalized.isNotEmpty()) { "Empty relative path" }
            return normalized
        }
    }
}

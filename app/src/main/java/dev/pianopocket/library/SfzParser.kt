package dev.pianopocket.library

import kotlin.math.pow

/** SFZ text is prepared on the import worker, never on the audio thread. */
class SfzParser {
    private val warnings = linkedSetOf<String>()
    private val supported = setOf("sample", "key", "lokey", "hikey", "pitch_keycenter", "lovel", "hivel",
        "volume", "amplitude", "pan", "transpose", "tune", "pitch_keytrack", "amp_veltrack",
        "ampeg_attack", "ampeg_decay", "ampeg_sustain", "ampeg_release", "offset", "end",
        "loop_start", "loop_end", "loop_mode", "trigger", "group_label", "region_label", "label",
        "locc64", "hicc64", "xfade_velcurve", "xfin_lovel", "xfin_hivel", "xfout_lovel", "xfout_hivel",
        "seq_length", "seq_position", "lorand", "hirand", "cutoff", "resonance", "fil_type")

    fun parse(text: String, name: String, file: String = "Instrument.sfz", include: (String) -> String = { error("Missing SFZ include: $it") }): Preset {
        warnings.clear()
        val main = path("", file)
        val base = main.substringBeforeLast('/', "")
        val macros = mutableMapOf<String, String>()
        var sourceBytes = 0L
        var includes = 0
        fun expand(value: String): String {
            var result = value
            repeat(16) {
                val next = Regex("\\$[A-Za-z_][A-Za-z0-9_]*").replace(result) { match -> macros[match.value] ?: error("Undefined SFZ macro: ${match.value}") }
                require(next.length <= MAX_TEXT) { "SFZ macro expansion limit exceeded" }
                if (next == result) {
                    require(!Regex("\\$[A-Za-z_]").containsMatchIn(next)) { "Cyclic SFZ macro" }
                    return next
                }
                result = next
            }
            error("SFZ macro nesting limit exceeded")
        }
        fun preprocess(source: String, current: String, stack: Set<String>): String {
            require(stack.size <= 16 && source.toByteArray().size <= MAX_TEXT) { "SFZ include size/depth limit exceeded" }
            sourceBytes += source.toByteArray().size
            require(sourceBytes <= 16L * MAX_TEXT) { "SFZ source size limit exceeded" }
            val out = StringBuilder()
            stripComments(source).lineSequence().forEach { line ->
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("#define ") || trimmed.startsWith("#define\t") -> {
                        val match = Regex("#define\\s+(\\$[A-Za-z_][A-Za-z0-9_]*)\\s+(.+)").matchEntire(trimmed)
                            ?: error("Invalid SFZ #define")
                        require(macros.size < 256 || match.groupValues[1] in macros) { "Too many SFZ macros" }
                        macros[match.groupValues[1]] = match.groupValues[2]
                    }
                    trimmed.startsWith("#include") -> {
                        val match = Regex("#include\\s+\"([^\"]+)\"").matchEntire(trimmed) ?: error("Invalid SFZ #include")
                        val target = path(current.substringBeforeLast('/', ""), expand(match.groupValues[1]))
                        require(target !in stack && ++includes <= 64) { "Cyclic SFZ include or include count exceeded" }
                        out.append(preprocess(include(target), target, stack + target)).append('\n')
                    }
                    trimmed.startsWith('#') -> error("Unsupported SFZ directive: ${trimmed.take(80)}")
                    else -> out.append(expand(line)).append('\n')
                }
                require(out.length <= MAX_TEXT * 2) { "Expanded SFZ size limit exceeded" }
            }
            return out.toString()
        }

        val source = preprocess(text.removePrefix("\uFEFF"), main, setOf(main))
        val regions = mutableListOf<Region>()
        val groups = mutableListOf<String>()
        var global = linkedMapOf<String, String>()
        var master = linkedMapOf<String, String>()
        var group = linkedMapOf<String, String>()
        var region = linkedMapOf<String, String>()
        var control = linkedMapOf<String, String>()
        var scope = ""
        var groupIndex = -1
        fun flush() {
            if (scope != "region") return
            val attributes = global + master + group + region
            if (groupIndex < 0) {
                require(groups.size < 256) { "SFZ group limit exceeded" }
                groupIndex = groups.size
                groups += group["group_label"] ?: "Piano ${groups.size + 1}"
            }
            require(regions.size < 30000) { "SFZ region limit exceeded" }
            toRegion(attributes, groupIndex, base, control["default_path"] ?: "")?.let { regions += it }
        }
        tokenize(source) { header, key, value ->
            if (header != null) {
                flush()
                scope = header
                when (header) {
                    "global" -> { global = linkedMapOf(); master = linkedMapOf(); group = linkedMapOf(); groupIndex = -1 }
                    "master" -> { master = linkedMapOf(); group = linkedMapOf(); groupIndex = -1 }
                    "group" -> { group = linkedMapOf(); groupIndex = -1 }
                    "region" -> region = linkedMapOf()
                    "control" -> control = linkedMapOf()
                    else -> warn("SFZ <$header> is unsupported")
                }
            } else {
                val target = when (scope) { "global" -> global; "master" -> master; "group" -> group; "region" -> region; "control" -> control; else -> null }
                if (target == null) warn("SFZ $key outside a supported header")
                else {
                    require(target.size < 512 || key in target) { "SFZ opcode count exceeded" }
                    target[key!!] = value!!
                    if (scope == "control") { if (key != "default_path") warn("SFZ <control> $key is unsupported") }
                    else if (key !in supported && !key.matches(Regex("amp_velcurve_\\d{1,3}"))) warn("SFZ opcode $key is unsupported")
                }
            }
        }
        flush()
        require(regions.isNotEmpty()) { "SFZに再生可能なサンプルリージョンがありません。" }
        return Preset(name, regions, groups, emptyList(), emptyList(), warnings.toList(), groups.map { 1f })
    }

    private fun toRegion(a: Map<String, String>, group: Int, base: String, defaultPath: String): Region? {
        val sample = a["sample"] ?: error("SFZ region is missing sample")
        checkRelative(sample)
        if(defaultPath.isNotEmpty())checkRelative(defaultPath)
        if (sample.startsWith('*') || sample.substringAfterLast('.').lowercase() !in setOf("wav","flac","ogg")) {
            warn("SFZ sample $sample skipped: only WAV, FLAC and OGG Vorbis files are supported")
            return null
        }
        fun number(key: String, fallback: Float, low: Float, high: Float): Float {
            val value = a[key]?.let { it.toFloatOrNull() ?: error("Invalid SFZ $key: $it") } ?: fallback
            require(value.isFinite() && value in low..high) { "SFZ $key out of range" }
            return value
        }
        fun integer(key: String, fallback: Int, low: Int = 0, high: Int = Int.MAX_VALUE - 1): Int {
            val value = a[key]?.let { it.toIntOrNull() ?: error("Invalid SFZ $key: $it") } ?: fallback
            require(value in low..high) { "SFZ $key out of range" }
            return value
        }
        fun note(key: String, fallback: Int): Int = a[key]?.let(::noteNumber) ?: fallback
        val key = note("key", 60)
        val low = note("lokey", if ("key" in a) key else 0)
        val high = note("hikey", if ("key" in a) key else 127)
        val root = note("pitch_keycenter", key)
        val lovel = integer("lovel", 0, 0, 127); val hivel = integer("hivel", 127, lovel, 127)
        require(low in 0..127 && high in low..127 && root in -1..127) { "Invalid SFZ key range" }
        val trigger = when (a["trigger"] ?: "attack") {
            "attack" -> 0
            "release" -> { warn("SFZ release samples use attack velocity and fire after sustain is released; release attenuation (rt_decay) is unsupported"); 1 }
            else -> { warn("SFZ trigger=${a["trigger"]}: region skipped"); return null }
        }
        // Selecting all unsupported random/sequence variants would stack them and distort the sound.
        if (a.keys.any { it.startsWith("sw_") || it.matches(Regex("(?:lo|hi)cc(?!64$)\\d+")) || it.startsWith("on_lo") || it.startsWith("on_hi") }) {
            warn("SFZ $sample skipped: unsupported keyswitch or controller selection")
            return null
        }
        val loop = a["loop_mode"] ?: "no_loop"
        require(loop in setOf("no_loop", "one_shot", "loop_sustain", "loop_continuous")) { "Unsupported SFZ loop_mode=$loop" }
        val looping = loop.startsWith("loop_")
        val explicit = looping && "loop_start" in a && "loop_end" in a
        if (looping && !explicit) warn("SFZ $sample: embedded WAV loop markers are unsupported; explicit loop_start / loop_end required")
        val start = integer("offset", 0)
        val end = if ("end" in a) integer("end", 0, start) else -1 // Native end is inclusive too.
        val loopStart = if (explicit) integer("loop_start", 0, start) else -1
        val loopEnd = if (explicit) integer("loop_end", 0, loopStart + 1) else -1
        val cc = if ("locc64" in a || "hicc64" in a) 64 else -1
        val ccLow = integer("locc64", 0, 0, 127); val ccHigh = integer("hicc64", 127, ccLow, 127)
        val velocity = number("amp_veltrack", 100f, -100f, 100f)
        val sequenceLength=integer("seq_length",1,0,1024).coerceAtLeast(1)
        val sequencePosition=integer("seq_position",1,1,sequenceLength)
        val randomLow=number("lorand",0f,0f,1f);val randomHigh=number("hirand",1f,randomLow,1f)
        require(randomHigh>randomLow) { "Empty SFZ random range" }
        val filter=if(a.keys.any {it in setOf("cutoff","resonance","fil_type")})when(a["fil_type"] ?: "lpf_2p") {
            "lpf_1p"->1;"lpf_2p"->2;"hpf_1p"->3;"hpf_2p"->4;"bpf_2p"->5;"brf_2p"->6
            else->{warn("SFZ filter ${a["fil_type"]} is unsupported; filter bypassed");0}
        }else 0
        if(filter>0)warn("SFZ filter uses a biquad approximation; resonance-to-Q mapping can differ between players")
        val points=sortedMapOf(0 to 0f,127 to 1f)
        a.filterKeys {it.startsWith("amp_velcurve_")}.forEach { (key,value)->
            val index=key.removePrefix("amp_velcurve_").toIntOrNull() ?: error("Invalid velocity curve index")
            val gain=value.toFloatOrNull() ?: error("Invalid velocity curve amplitude")
            require(index in 0..127 && gain.isFinite() && gain in 0f..1f);points[index]=gain
        }
        val customVelocity=a.keys.any {it.startsWith("amp_velcurve_")}
        if (a.keys.any { it.startsWith("xfin_") || it.startsWith("xfout_") } && a["xfade_velcurve"] != "gain") warn("SFZ velocity crossfade uses a linear gain curve; power curve is approximated")
        val flags = 4 or (if (loop == "one_shot") 1 else 0) or (if (loop == "loop_continuous") 2 else 0)
        val ints = intArrayOf(group, low, high, root.coerceAtLeast(0), lovel, hivel, trigger, start, end, loopStart, loopEnd,
            cc, ccLow, ccHigh, integer("xfin_lovel", -1, -1, 127), integer("xfin_hivel", -1, -1, 127),
            integer("xfout_lovel", -1, -1, 127), integer("xfout_hivel", -1, -1, 127), 1, flags,sequenceLength,sequencePosition,filter,if(customVelocity)1 else 0)
        val gain = 10.0.pow(number("volume", 0f, -144f, 48f) / 20.0).toFloat() * number("amplitude", 100f, 0f, 100f) / 100f
        val floats = floatArrayOf(gain, number("pan", 0f, -100f, 100f) / 100f,
            number("tune", 0f, -1200f, 1200f) + number("transpose", 0f, -127f, 127f) * 100f,
            number("ampeg_attack", 0f, 0f, 60f), number("ampeg_decay", 0f, 0f, 60f),
            number("ampeg_sustain", 100f, 0f, 100f) / 100f, number("ampeg_release", .001f, 0f, 120f),
            velocity / 100f, if (root == -1) 0f else number("pitch_keytrack", 100f, -1200f, 1200f) / 100f,
            randomLow,randomHigh,number("cutoff",20000f,0f,200000f),number("resonance",0f,0f,40f))
        val curve=if(customVelocity)FloatArray(128) {v->val lo=points.entries.last {it.key<=v};val hi=points.entries.first {it.key>=v};if(lo.key==hi.key)lo.value else lo.value+(hi.value-lo.value)*(v-lo.key)/(hi.key-lo.key)}else FloatArray(0)
        return Region(path(base, defaultPath.replace('\\', '/').let { if (it.isEmpty() || it.endsWith('/')) it else "$it/" } + sample), ints, floats+curve)
    }

    private fun warn(message: String) { if (warnings.size < 100) warnings += message else warnings += "Further SFZ warnings omitted" }

    companion object {
        const val MAX_TEXT = 4 * 1024 * 1024
        /** Paths are relative to the imported instrument root; parents may never escape that root. */
        fun path(base: String, relative: String): String {
            val clean = relative.replace('\\', '/')
            checkRelative(relative)
            val parts = ArrayDeque<String>()
            (if (base.isEmpty()) clean else "$base/$clean").split('/').forEach { part ->
                when (part) { "", "." -> Unit; ".." -> { require(parts.isNotEmpty()) { "SFZ reference escapes instrument root" }; parts.removeLast() }; else -> parts.addLast(part) }
            }
            require(parts.isNotEmpty()) { "Empty SFZ path" }
            return parts.joinToString("/")
        }
        private fun checkRelative(relative: String) {
            val clean=relative.replace('\\','/')
            require(clean.isNotBlank() && !clean.startsWith('/') && ':' !in clean && '\u0000' !in clean) { "Unsafe SFZ path: $relative" }
        }
        fun noteNumber(value: String): Int {
            value.toIntOrNull()?.let { return it }
            val m = Regex("([a-gA-G])([#b]?)(-?\\d+)").matchEntire(value) ?: error("Invalid SFZ note: $value")
            val semitone = mapOf('c' to 0, 'd' to 2, 'e' to 4, 'f' to 5, 'g' to 7, 'a' to 9, 'b' to 11).getValue(m.groupValues[1].lowercase()[0])
            val octave = m.groupValues[3].toIntOrNull() ?: error("Invalid SFZ octave")
            require(octave in -1..9)
            return (octave + 1) * 12 + semitone + when (m.groupValues[2]) { "#" -> 1; "b" -> -1; else -> 0 }
        }
        private fun stripComments(text: String): String {
            val out = StringBuilder(); var i = 0; var quoted = false
            while (i < text.length) {
                when {
                    text[i] == '"' -> { quoted = !quoted; out.append(text[i++]) }
                    !quoted && text.startsWith("//", i) -> { while (i < text.length && text[i] != '\n') i++; out.append(' ') }
                    !quoted && text.startsWith("/*", i) -> {
                        i += 2
                        while (i < text.length && !text.startsWith("*/", i)) { if (text[i] == '\n') out.append('\n'); i++ }
                        require(i < text.length) { "Unterminated SFZ comment" }; i += 2; out.append(' ')
                    }
                    else -> out.append(text[i++])
                }
            }
            require(!quoted) { "Unterminated SFZ string" }
            return out.toString()
        }
        private fun tokenize(text: String, accept: (String?, String?, String?) -> Unit) {
            val opcode = Regex("[A-Za-z_][A-Za-z_0-9]*\\s*=")
            var i = 0; var tokens = 0
            while (i < text.length) {
                if (text[i].isWhitespace()) { i++; continue }
                require(++tokens <= 100000) { "SFZ token limit exceeded" }
                if (text[i] == '<') {
                    val end = text.indexOf('>', i + 1); require(end > i) { "Unclosed SFZ header" }
                    val header = text.substring(i + 1, end).trim().lowercase()
                    require(header.matches(Regex("[a-z_]+"))) { "Invalid SFZ header" }
                    accept(header, null, null); i = end + 1; continue
                }
                val match = opcode.matchAt(text, i) ?: error("Invalid SFZ syntax near: ${text.substring(i).take(60)}")
                val key = match.value.substringBefore('=').trim().lowercase(); i = match.range.last + 1
                while (i < text.length && text[i].isWhitespace()) i++
                val start = i
                val value: String
                if (i < text.length && text[i] == '"') {
                    val end = text.indexOf('"', i + 1); require(end >= 0) { "Unterminated SFZ value" }
                    value = text.substring(i + 1, end); i = end + 1
                } else {
                    while (i < text.length && text[i] != '<') {
                        if (text[i].isWhitespace()) {
                            var next = i; while (next < text.length && text[next].isWhitespace()) next++
                            if (next == text.length || text[next] == '<' || opcode.matchAt(text, next) != null) break
                            i = next; continue
                        }
                        i++
                    }
                    value = text.substring(start, i).trim()
                }
                require(value.isNotEmpty() && value.length <= 4096) { "Invalid SFZ value for $key" }
                accept(null, key, value)
            }
        }
    }
}

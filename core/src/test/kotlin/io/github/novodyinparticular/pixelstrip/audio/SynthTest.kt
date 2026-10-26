package io.github.novodyinparticular.pixelstrip.audio

import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders the engines and music offline. Checks the pitch follows the RPM at the firing
 * frequency and nothing clips or goes NaN, and writes build/audio-preview.wav to listen to.
 */
class SynthTest {
    private val rate = 22050

    /** Fundamental by autocorrelation over a window, searching periods for 20..400 Hz. */
    private fun pitch(x: FloatArray, from: Int, len: Int): Float {
        var best = 0
        var bestR = -1.0
        for (lag in rate / 400..rate / 20) {
            var r = 0.0
            var e = 0.0
            for (i in from until from + len) {
                r += x[i] * x[i + lag]
                e += x[i + lag] * x[i + lag]
            }
            val nr = r / (e + 1e-9)
            if (nr > bestR) {
                bestR = nr
                best = lag
            }
        }
        return rate.toFloat() / best
    }

    private fun sweep(layout: String, cylinders: Int, seconds: Float, out: MutableList<Float>): List<Pair<Float, Float>> {
        val s = Synth(rate)
        s.musicVolume = 0f
        val e = s.engines[0]
        e.active = true
        e.layout = layout
        e.cylinders = cylinders
        e.throttle = 1f
        e.sound = EngineSound(loud = 0.5f)
        val buf = FloatArray(256)
        val all = ArrayList<Float>()
        val n = (seconds * rate).toInt()
        var i = 0
        val marks = ArrayList<Pair<Float, Int>>()
        while (i < n) {
            val t = i.toFloat() / n
            e.rpm = 1500f + 5000f * t
            if ((i / 256) % 40 == 0) marks.add(e.rpm to all.size)
            s.render(buf, buf.size)
            buf.forEach { all.add(it) }
            i += buf.size
        }
        out.addAll(all)
        val arr = all.toFloatArray()
        return marks.filter { it.second + 3000 < arr.size }.map { (rpm, at) -> rpm to pitch(arr, at, 1500) }
    }

    @Test
    fun enginesTrackRpmAndMusicPlays() {
        val wav = ArrayList<Float>()
        for ((layout, cyl, firingsPerRev) in listOf(Triple("v8", 8, 4f), Triple("i4", 4, 2f), Triple("rotary", 2, 2f), Triple("flat4", 4, 2f), Triple("flat6", 6, 3f), Triple("v12", 12, 6f))) {
            val marks = sweep(layout, cyl, 3f, wav)
            println("$layout: " + marks.joinToString { (rpm, hz) -> "%.0f rpm -> %.0f Hz (firing %.0f)".format(rpm, hz, rpm / 60f * firingsPerRev) })
            // the measured pitch should rise with the revs and sit on the firing frequency or a sub-harmonic of the cycle
            assertTrue(marks.last().second > marks.first().second * 1.5f, "$layout pitch should rise with RPM")
        }
        val s = Synth(rate)
        s.musicVolume = 0.6f
        s.music.song = "title"
        val buf = FloatArray(rate * 6)
        s.render(buf, buf.size)
        buf.forEach { wav.add(it) }
        val peak = wav.maxOf { abs(it) }
        println("peak %.3f over %d samples".format(peak, wav.size))
        assertTrue(wav.none { it.isNaN() }, "no NaNs")
        assertTrue(peak < 1f && peak > 0.05f, "audible and never clipping")
        writeWav(File("../build/audio-preview.wav"), wav)
    }

    private fun writeWav(f: File, x: List<Float>) {
        f.parentFile.mkdirs()
        DataOutputStream(FileOutputStream(f)).use { o ->
            fun le32(v: Int) = o.write(byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte()))
            fun le16(v: Int) = o.write(byteArrayOf(v.toByte(), (v shr 8).toByte()))
            o.writeBytes("RIFF"); le32(36 + x.size * 2); o.writeBytes("WAVEfmt ")
            le32(16); le16(1); le16(1); le32(rate); le32(rate * 2); le16(2); le16(16)
            o.writeBytes("data"); le32(x.size * 2)
            for (v in x) le16((v.coerceIn(-1f, 1f) * 32000).toInt())
        }
    }

    /** High-frequency energy (first difference) of a held engine note: a harder, raspier sound scores higher. */
    private fun edge(vtec: Float, rpm: Float): Float {
        val s = Synth(rate)
        s.musicVolume = 0f
        val e = s.engines[0]
        e.active = true
        e.layout = "i4"
        e.cylinders = 4
        e.throttle = 1f
        e.rpm = rpm
        e.vtecRpm = vtec
        val buf = FloatArray(256)
        var sum = 0.0
        var last = 0f
        repeat(rate / 256) { k ->
            s.render(buf, buf.size)
            if (k > rate / 512) for (x in buf) { sum += (x - last) * (x - last); last = x }   // after it has settled
        }
        return sum.toFloat()
    }

    @Test
    fun theCamCrossoverIsAudible() {
        val plain = edge(0f, 7000f)
        val onCam = edge(5500f, 7000f)
        val belowCam = edge(5500f, 4000f) / edge(0f, 4000f)
        println("vtec at 7000 rpm: %.1fx the edge of the same engine without it; below the crossover %.2fx".format(onCam / plain, belowCam))
        assertTrue(onCam > plain * 1.2f, "past the crossover the note should harden")
        assertTrue(belowCam in 0.8f..1.25f, "below it nothing changes")
    }

    // --- every engine type and every mod has to be heard ----------------------------------------------

    /** Renders one engine held at [rpm] for [seconds], after [settle] seconds to let it settle. */
    private fun hold(rpm: Float, seconds: Float = 1f, settle: Float = 0.4f, set: EngineVoice.() -> Unit): FloatArray {
        val s = Synth(rate)
        s.musicVolume = 0f
        val e = s.engines[0]
        e.active = true
        e.rpm = rpm
        e.throttle = 1f
        e.set()
        s.render(FloatArray((settle * rate).toInt()), (settle * rate).toInt())
        val out = FloatArray((seconds * rate).toInt())
        s.render(out, out.size)
        return out
    }

    private fun rms(x: FloatArray) = Math.sqrt(x.sumOf { (it * it).toDouble() } / x.size).toFloat()

    /** Energy at one frequency (Goertzel). */
    private fun at(x: FloatArray, hz: Float): Float {
        val w = 2.0 * Math.PI * hz / rate
        val c = 2.0 * Math.cos(w)
        var s1 = 0.0
        var s2 = 0.0
        for (v in x) {
            val s0 = v + c * s1 - s2
            s2 = s1
            s1 = s0
        }
        return (Math.sqrt(s1 * s1 + s2 * s2 - c * s1 * s2) / x.size).toFloat()
    }

    /** How uneven the firings are: spread of the loudness from one engine cycle to the next. */
    private fun irregularity(x: FloatArray, rpm: Float, firingsPerRev: Float): Float {
        val period = (rate * 60f / rpm / firingsPerRev).toInt().coerceAtLeast(1)
        val peaks = (0 until x.size / period).map { i -> (0 until period).maxOf { abs(x[i * period + it]) } }
        val mean = peaks.average()
        return (Math.sqrt(peaks.sumOf { (it - mean) * (it - mean) } / peaks.size) / mean).toFloat()
    }

    private fun highs(x: FloatArray): Float {
        var e = 0.0
        for (i in 1 until x.size) e += (x[i] - x[i - 1]).toDouble().let { it * it }
        return Math.sqrt(e / x.size).toFloat() / rms(x)
    }

    @Test
    fun everyLayoutFiresAtItsFiringFrequency() {
        val cases = listOf(
            Triple("i4", 4, 2f), Triple("i6", 6, 3f), Triple("flat6", 6, 3f), Triple("v8", 8, 4f), Triple("v10", 10, 5f), Triple("v12", 12, 6f),
            Triple("rotary", 2, 2f), Triple("rotary", 3, 3f), Triple("rotary", 4, 4f),
        )
        for ((layout, cyl, perRev) in cases) {
            val rpm = 3000f
            val x = hold(rpm) { this.layout = layout; cylinders = cyl; idleRpm = 800f }
            val firing = rpm / 60f * perRev
            // the firing frequency stands out from its neighbours (patterns add the cycle's sub-harmonics underneath)
            val peak = at(x, firing)
            val beside = maxOf(at(x, firing * 0.83f), at(x, firing * 1.17f))
            println("%-7s %2d: firing %4.0f Hz, %.4f vs %.4f beside it".format(layout, cyl, firing, peak, beside))
            assertTrue(peak > beside * 2f, "$layout/$cyl should sound at its firing frequency, $firing Hz")
        }
    }

    @Test
    fun aRotaryBrapsAtIdleAndBuzzesUpTop() {
        val rotary = { lope: Float, rpm: Float -> hold(rpm, 2f) { layout = "rotary"; cylinders = 2; idleRpm = 850f; throttle = 0.05f; sound = EngineSound(lope = lope) } }
        val i4 = irregularity(hold(850f, 2f) { layout = "i4"; cylinders = 4; idleRpm = 850f; throttle = 0.05f }, 850f, 2f)
        val stock = irregularity(rotary(0f, 850f), 850f, 2f)
        val bridge = irregularity(rotary(0.7f, 850f), 850f, 2f)
        val peri = irregularity(rotary(1f, 850f), 850f, 2f)
        val high = irregularity(rotary(1f, 8000f), 8000f, 2f)
        println("idle irregularity: i4 %.3f, rotary %.3f, bridge port %.3f, peripheral %.3f; peripheral at 8000 rpm %.3f".format(i4, stock, bridge, peri, high))
        assertTrue(stock > i4 * 1.5f, "a rotary never idles smooth")
        assertTrue(bridge > stock && peri > bridge, "each port stage idles rougher")
        assertTrue(high < peri * 0.5f, "and it smooths into a buzz up top")
        assertTrue(highs(rotary(0f, 8000f)) > highs(rotary(0f, 2500f)), "sharper and buzzier with revs")
    }

    @Test
    fun bigCamsLopeAtIdle() {
        val v8 = { lope: Float -> irregularity(hold(800f, 2f) { layout = "v8"; cylinders = 8; idleRpm = 800f; throttle = 0.05f; sound = EngineSound(lope = lope) }, 800f, 0.5f) }
        println("v8 idle lope: stock %.3f, race cam %.3f".format(v8(0f), v8(1f)))
        assertTrue(v8(1f) > v8(0f) * 1.3f)
    }

    @Test
    fun exhaustAndIntakeMods() {
        val i4 = { snd: EngineSound -> hold(6000f) { layout = "i4"; cylinders = 4; sound = snd } }
        val stock = i4(EngineSound.STOCK)
        val catBack = i4(EngineSound(loud = 0.3f))
        val straight = i4(EngineSound(loud = 0.75f, rasp = 0.5f))
        val headers = i4(EngineSound(rasp = 0.7f))
        println("rms stock %.3f cat-back %.3f straight %.3f; edge stock %.2f headers %.2f".format(rms(stock), rms(catBack), rms(straight), highs(stock), highs(headers)))
        assertTrue(rms(catBack) > rms(stock) && rms(straight) > rms(catBack), "louder from cat-back to straight pipe")
        assertTrue(highs(headers) > highs(stock), "headers add rasp")
        val growlHz = 260f + 6000f * 0.11f
        assertTrue(at(i4(EngineSound(growl = 1f)), growlHz) > at(stock, growlHz) * 1.3f, "an open intake growls")
    }

    @Test
    fun boostSounds() {
        val turbo = { snd: EngineSound, set: EngineVoice.() -> Unit -> hold(6000f) { layout = "i4"; cylinders = 4; turbo = true; boost = 1f; sound = snd; set() } }
        val small = turbo(EngineSound(whistle = 0f)) {}
        val big = turbo(EngineSound(whistle = 1f)) {}
        assertTrue(at(big, 6200f) > at(small, 7100f) * 1.5f, "a bigger turbo whistles lower and louder")
        assertTrue(highs(turbo(EngineSound(screamer = 1f)) {}) > highs(small), "the screamer pipe screams")
        // lifting off with boost: the valve, or the flutter with none
        val lift = { type: Int ->
            val s = Synth(rate)
            s.musicVolume = 0f
            val e = s.engines[0]
            e.active = true; e.layout = "i4"; e.cylinders = 4; e.turbo = true; e.boost = 1f; e.rpm = 6000f; e.throttle = 1f
            e.sound = EngineSound(bovType = type)
            s.render(FloatArray(rate / 2), rate / 2)
            e.throttle = 0f
            FloatArray(rate / 2).also { s.render(it, it.size) }
        }
        val vta = lift(1)
        val recirc = lift(2)
        val none = lift(0)
        println("lift: vta edge %.2f, recirc %.2f; flutter at 1540 Hz none %.5f vs vta %.5f".format(highs(vta), highs(recirc), at(none, 1540f), at(vta, 1540f)))
        assertTrue(highs(vta) > highs(recirc), "vent-to-atmosphere hisses, recirculating doesn't")
        assertTrue(at(none, 1540f) > at(vta, 1540f) * 2f, "no valve: the compressor flutters")
        // superchargers: roots whine locked to the crank, a centrifugal siren far above it
        val blown = { type: Int -> hold(6000f) { layout = "v8"; cylinders = 8; blower = 0.6f; redline = 6500f; sound = EngineSound(blowerType = type) } }
        assertTrue(at(blown(1), 6000f / 60f * 3.3f) > at(blown(3), 6000f / 60f * 3.3f) * 2f, "roots whine")
        assertTrue(at(blown(3), 6000f / 60f * 14f) > at(blown(1), 6000f / 60f * 14f) * 2f, "centrifugal siren")
    }

    @Test
    fun twoStepAndLimiterBangs() {
        val staged = { two: Boolean, al: Boolean -> hold(5000f, 2f) { layout = "i4"; cylinders = 4; turbo = true; twoStep = two; antiLag = al } }
        val quiet = staged(false, false)
        val twoStep = staged(true, false)
        val antiLag = staged(true, true)
        println("staged rms: no 2-step %.3f, 2-step %.3f, anti-lag %.3f".format(rms(quiet), rms(twoStep), rms(antiLag)))
        assertTrue(rms(twoStep) > rms(quiet) * 1.1f && rms(antiLag) > rms(twoStep), "pops on the 2-step, more on anti-lag")
        // the limiter bounces: the level pulses about 14 times a second
        val env = { x: FloatArray -> FloatArray(x.size / 64) { i -> (0 until 64).maxOf { abs(x[i * 64 + it]) } } }
        val on = hold(7000f, 2f) { layout = "i4"; cylinders = 4; limiter = true }
        val off = hold(7000f, 2f) { layout = "i4"; cylinders = 4 }
        val envRate = rate / 64f
        val bounce = { e: FloatArray -> val w = 2.0 * Math.PI * 14.0 / envRate; var re = 0.0; var im = 0.0; e.forEachIndexed { i, v -> re += v * Math.cos(w * i); im += v * Math.sin(w * i) }; Math.hypot(re, im) / e.size }
        assertTrue(bounce(env(on)) > bounce(env(off)) * 3.0, "the limiter bounces")
    }

    /** A listening reel, build/engine-demo.wav: each sound the parts make, one after another (timeline printed). */
    @Test
    fun writesADemoReel() {
        val reel = ArrayList<Float>()
        val marks = ArrayList<String>()
        fun clip(label: String, seconds: Float, set: EngineVoice.(Float) -> Unit) {
            marks.add("%5.1f s  %s".format(reel.size / rate.toFloat(), label))
            val s = Synth(rate)
            s.musicVolume = 0f
            val e = s.engines[0]
            e.active = true
            val n = (seconds * rate).toInt()
            val buf = FloatArray(256)
            var i = 0
            while (i < n) {
                e.set(i / n.toFloat())
                s.render(buf, buf.size)
                buf.forEach { reel.add(it) }
                i += buf.size
            }
        }
        fun sweep(e: EngineVoice, t: Float, from: Float, to: Float) { e.rpm = from + (to - from) * t; e.throttle = 1f }
        clip("i4, stock, idle then a pull", 4f) { t -> layout = "i4"; cylinders = 4; idleRpm = 800f; redline = 7200f; if (t < 0.3f) { rpm = 800f; throttle = 0.05f } else sweep(this, (t - 0.3f) / 0.7f, 2000f, 7200f) }
        clip("Honda VTEC: watch it harden at 5,500", 4f) { t -> layout = "i4"; cylinders = 4; vtecRpm = 5500f; redline = 8400f; sweep(this, t, 3000f, 8400f); sound = EngineSound(loud = 0.3f, growl = 0.5f) }
        clip("i4 straight pipe, headers, intake: lift-off crackle at the end", 4f) { t -> layout = "i4"; cylinders = 4; redline = 7500f; sound = EngineSound(loud = 0.75f, rasp = 1.2f, growl = 0.7f); if (t < 0.7f) sweep(this, t / 0.7f, 3000f, 7300f) else { rpm = 7300f - (t - 0.7f) * 9000f; throttle = 0f } }
        clip("V8 stock idle", 2f) { _ -> layout = "v8"; cylinders = 8; idleRpm = 750f; rpm = 750f; throttle = 0.05f }
        clip("V8 race cam idle: lumpy", 2.5f) { _ -> layout = "v8"; cylinders = 8; idleRpm = 1000f; rpm = 1000f; throttle = 0.05f; sound = EngineSound(lope = 1f, loud = 0.75f, rasp = 0.7f) }
        clip("V8 roots blower pull", 3f) { t -> layout = "v8"; cylinders = 8; blower = 0.6f; redline = 6500f; sweep(this, t, 2000f, 6500f); sound = EngineSound(blowerType = 1, loud = 0.5f) }
        clip("V8 centrifugal pull: the siren", 3f) { t -> layout = "v8"; cylinders = 8; blower = 0.6f; redline = 6500f; sweep(this, t, 2000f, 6500f); sound = EngineSound(blowerType = 3, loud = 0.5f) }
        clip("rotary stock idle", 2.5f) { _ -> layout = "rotary"; cylinders = 2; idleRpm = 850f; rpm = 850f; throttle = 0.05f }
        clip("rotary bridge port idle: brap brap brap", 3f) { _ -> layout = "rotary"; cylinders = 2; idleRpm = 1050f; rpm = 1050f; throttle = 0.05f; sound = EngineSound(lope = 0.7f, loud = 0.6f, rasp = 0.4f) }
        clip("rotary peripheral port: idle, then the buzz to 9,500", 4f) { t -> layout = "rotary"; cylinders = 2; idleRpm = 1250f; redline = 9500f; sound = EngineSound(lope = 1f, loud = 0.75f, rasp = 0.5f); if (t < 0.4f) { rpm = 1250f; throttle = 0.05f } else sweep(this, (t - 0.4f) / 0.6f, 3000f, 9500f) }
        clip("3-rotor pull", 3f) { t -> layout = "rotary"; cylinders = 3; redline = 8000f; sweep(this, t, 2500f, 8000f); sound = EngineSound(loud = 0.6f) }
        clip("4-rotor pull", 3f) { t -> layout = "rotary"; cylinders = 4; redline = 9500f; sweep(this, t, 2500f, 9500f); sound = EngineSound(loud = 0.75f) }
        clip("flat-6 turbo pull", 3f) { t -> layout = "flat6"; cylinders = 6; turbo = true; boost = t; redline = 6800f; sweep(this, t, 2500f, 6800f); sound = EngineSound(whistle = 0.5f, loud = 0.3f) }
        clip("V10 pull", 3f) { t -> layout = "v10"; cylinders = 10; redline = 6000f; sweep(this, t, 2000f, 6000f); sound = EngineSound(loud = 0.4f) }
        clip("V12 pull", 3f) { t -> layout = "v12"; cylinders = 12; redline = 7500f; sweep(this, t, 2500f, 7500f) }
        for ((label, type) in listOf("big turbo, vent-to-atmosphere BOV" to 1, "big turbo, recirculating BOV" to 2, "big turbo, no BOV: flutter" to 0)) {
            clip(label, 2.5f) { t -> layout = "i6"; cylinders = 6; turbo = true; boost = 1f; rpm = 6500f; throttle = if (t < 0.5f) 1f else 0f; sound = EngineSound(whistle = 1f, bovType = type) }
        }
        clip("big turbo with a wastegate screamer", 2.5f) { _ -> layout = "i6"; cylinders = 6; turbo = true; boost = 1f; rpm = 6500f; throttle = 1f; sound = EngineSound(whistle = 1f, screamer = 1f) }
        clip("staged on the 2-step", 3f) { _ -> layout = "i4"; cylinders = 4; rpm = 5000f; throttle = 1f; twoStep = true; sound = EngineSound(loud = 0.75f) }
        clip("staged on anti-lag", 3f) { _ -> layout = "i4"; cylinders = 4; turbo = true; boost = 0.6f; rpm = 5000f; throttle = 1f; twoStep = true; antiLag = true; sound = EngineSound(loud = 0.75f, whistle = 0.8f) }
        clip("on the rev limiter", 2.5f) { _ -> layout = "i6"; cylinders = 6; rpm = 7000f; throttle = 1f; limiter = true; sound = EngineSound(loud = 0.5f) }
        println(marks.joinToString("\n"))
        assertTrue(reel.none { it.isNaN() } && reel.maxOf { abs(it) } < 1f)
        writeWav(File("../build/engine-demo.wav"), reel)
        File("../build/engine-demo.txt").writeText(marks.joinToString("\n") + "\n")
    }
}

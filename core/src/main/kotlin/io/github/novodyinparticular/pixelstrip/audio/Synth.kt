package io.github.novodyinparticular.pixelstrip.audio

import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Everything the game hears, synthesized per sample: two engines, one-shot effects, the crowd and
 * the chiptune music. The game thread only writes the @Volatile parameters and queues effects;
 * [render] runs on the audio thread (or in a test, offline).
 */
class Synth(val rate: Int) {
    private var seed = 0x2545F491

    /** White noise in -1..1 (a cheap LCG; fine for sound). */
    fun noise(): Float {
        seed = seed * 1664525 + 1013904223
        return ((seed ushr 9) / 4194304f) - 1f
    }

    val engines = Array(2) { EngineVoice(this) }
    val music = Music(this)
    private val queue = ConcurrentLinkedQueue<Sfx>()
    private val active = ArrayList<Sfx>()

    @Volatile var crowd = 0f
    @Volatile var master = 0.8f
    @Volatile var musicVolume = 0.5f

    private var crowdLp = 0f
    private var crowdLevel = 0f
    private var musicLevel = 0.5f

    fun play(kind: Sfx.Kind, volume: Float = 1f) {
        queue.add(Sfx(kind, volume))
    }

    fun render(out: FloatArray, n: Int) {
        while (true) active.add(queue.poll() ?: break)
        for (i in 0 until n) {
            var s = 0f
            for (e in engines) s += e.next()
            val it = active.iterator()
            while (it.hasNext()) {
                val fx = it.next()
                s += fx.next(this)
                if (fx.done) it.remove()
            }
            // crowd: low-passed noise that swells with the action
            crowdLevel += (crowd - crowdLevel) * 0.0002f
            crowdLp += (noise() - crowdLp) * 0.08f
            s += crowdLp * crowdLevel * 0.35f
            musicLevel += (musicVolume - musicLevel) * 0.0005f
            s += music.next() * musicLevel
            s *= master
            out[i] = s / (1f + abs(s))       // soft clip: never harsh, never over 1
        }
    }

    /** Two-pole state-variable filter (Chamberlin), low and band outputs. */
    class Svf {
        var low = 0f
        var band = 0f
        fun run(input: Float, cutoffHz: Float, q: Float, rate: Int): Float {
            val f = (2.0 * sin(PI * min(cutoffHz, rate * 0.24f) / rate)).toFloat()
            low += f * band
            val high = input - low - q * band
            band += f * high
            return low
        }
    }

    companion object {
        fun noteHz(midi: Float) = (440.0 * 2.0.pow((midi - 69.0) / 12.0)).toFloat()
    }
}

/**
 * One engine: a pulse per cylinder firing (so the pitch is exactly the firing frequency),
 * uneven for a crossplane V8, through a resonant low-pass that opens with the throttle; exhaust
 * noise riding the pulses; turbo whistle and blow-off, supercharger whine, limiter cut, nitrous
 * hiss and tyre squeal on top.
 */
class EngineVoice(private val synth: Synth) {
    @Volatile var active = false
    @Volatile var rpm = 800f
    @Volatile var redline = 6500f
    @Volatile var throttle = 0f
    @Volatile var limiter = false
    @Volatile var layout = "v8"
    @Volatile var cylinders = 8
    @Volatile var turbo = false
    @Volatile var boost = 0f
    @Volatile var blower = 0f
    @Volatile var exhaust = 0f        // 0 stock .. 1 race exhaust
    @Volatile var nitrous = false
    @Volatile var slip = 0f
    @Volatile var volume = 1f

    private val rate = synth.rate
    private var r = 800f
    private var thr = 0f
    private var bst = 0f
    private var level = 0f
    private var phase = 0.0
    private var whistle = 0.0
    private var whine = 0.0
    private var squeal = 0.0
    private var t = 0.0
    private val filter = Synth.Svf()
    private val tyre = Synth.Svf()
    private var dcIn = 0f
    private var dcOut = 0f
    private var blowOff = 0f
    private var lastThrottle = 0f
    private var hissLp = 0f

    private fun pattern(): FloatArray = when (layout) {
        "v8" -> V8
        "v6" -> V6
        "v10" -> V10
        "rotary" -> ROTARY
        "i6" -> I6
        else -> I4
    }

    fun next(): Float {
        val target = if (active) volume else 0f
        level += (target - level) * 0.0008f
        if (level < 0.0005f && !active) return 0f
        r += (rpm - r) * 0.004f
        thr += (throttle - thr) * 0.003f
        bst += (boost - bst) * 0.002f
        t += 1.0 / rate

        val amps = pattern()
        // a piston engine fires every cylinder once per two revs; a rotary fires each rotor once per rev
        val firings = cylinders
        val cycleHz = r / 60.0 / (if (layout == "rotary") 1.0 else 2.0)
        phase += cycleHz / rate
        if (phase >= 1.0) phase -= 1.0
        val pos = phase * firings
        val k = pos.toInt().coerceIn(0, firings - 1)
        val local = (pos - k).toFloat()
        val decay = if (layout == "rotary") 3.5f else 5f + r / 1500f
        var pulse = exp(-local * decay) * amps[k % amps.size]
        var s = pulse + synth.noise() * pulse * (0.25f + 0.5f * exhaust) * (0.3f + thr)
        if (limiter) s *= if (synth.noise() > 0.3f) 0.15f else 0.5f            // ignition cut crackle

        val cutoff = 180f + r * 0.32f + thr * 900f + exhaust * 700f
        val q = 0.55f - exhaust * 0.25f
        var y = filter.run(s, cutoff, q, rate) + filter.band * (0.15f + 0.35f * exhaust)
        // DC blocker
        val dc = y - dcIn + 0.995f * dcOut
        dcIn = y
        dcOut = dc
        y = dc * (0.35f + 0.65f * thr) * (0.8f + 0.5f * exhaust)

        if (turbo) {
            whistle += (2600.0 + bst * 4800.0) / rate
            y += sin(2 * PI * whistle).toFloat() * bst * bst * 0.05f
            if (lastThrottle - throttle > 0.4f && bst > 0.35f) blowOff = bst     // lift with boost: pssshh
        }
        lastThrottle = throttle
        if (blowOff > 0.001f) {
            hissLp += (synth.noise() - hissLp) * 0.6f
            y += (synth.noise() - hissLp) * blowOff * 0.3f
            blowOff *= 0.99975f
        }
        if (blower > 0f) {
            whine += r / 60.0 * 3.3 / rate
            y += (sin(2 * PI * whine) + 0.4 * sin(4 * PI * whine)).toFloat() * blower * 0.035f * (0.4f + thr)
        }
        if (nitrous) y += (synth.noise() - hissLp * 0.5f) * 0.06f
        if (slip > 0.02f) {
            squeal += 1.0 / rate
            val fc = 1300f + 260f * sin(squeal * 9.0).toFloat()
            tyre.run(synth.noise(), fc, 0.08f, rate)
            y += tyre.band * min(1f, slip) * 0.35f
        }
        return y * level
    }

    companion object {
        // relative strength of each firing in the cycle: the crossplane V8's lope, even inline sixes
        val V8 = floatArrayOf(1f, 0.6f, 0.92f, 0.7f, 0.96f, 0.58f, 0.86f, 0.74f)
        val V6 = floatArrayOf(1f, 0.82f, 0.96f, 0.85f, 1f, 0.8f)
        val V10 = floatArrayOf(1f, 0.78f, 0.95f, 0.72f, 1f, 0.84f, 0.9f, 0.8f, 1f, 0.7f)
        val I6 = floatArrayOf(1f, 0.94f, 0.97f, 0.95f, 1f, 0.93f)
        val I4 = floatArrayOf(1f, 0.9f, 0.98f, 0.88f)
        val ROTARY = floatArrayOf(1f, 1f, 1f, 1f)
    }
}

/** One-shot effects. */
class Sfx(private val kind: Kind, private val volume: Float) {
    enum class Kind { CLUNK, POP, BLIP, BEEP, CHIME, CHEER, WHOOSH, SLAM }

    private var n = 0
    private var phase = 0.0
    private var lp = 0f
    var done = false
        private set

    private val length = when (kind) {
        Kind.CLUNK -> 0.09f
        Kind.POP -> 0.07f
        Kind.BLIP -> 0.05f
        Kind.BEEP -> 0.12f
        Kind.CHIME -> 0.45f
        Kind.CHEER -> 2.2f
        Kind.WHOOSH -> 0.45f
        Kind.SLAM -> 0.35f
    }

    fun next(s: Synth): Float {
        val t = n.toFloat() / s.rate
        n++
        if (t >= length) {
            done = true
            return 0f
        }
        val env = 1f - t / length
        val y = when (kind) {
            Kind.CLUNK -> {
                phase += (90.0 - t * 400.0) / s.rate
                sin(2 * PI * phase).toFloat() * env * env * 0.5f + (if (n < 60) s.noise() * 0.3f else 0f)
            }
            Kind.POP -> {
                lp += (s.noise() - lp) * 0.3f
                phase += 110.0 / s.rate
                (lp * 0.9f + sin(2 * PI * phase).toFloat() * 0.6f) * env * env
            }
            Kind.BLIP -> {
                phase += (if (t < length / 2) 880.0 else 1320.0) / s.rate
                (if (phase % 1.0 < 0.25) 0.18f else -0.18f) * env
            }
            Kind.BEEP -> {
                phase += 1000.0 / s.rate
                sin(2 * PI * phase).toFloat() * 0.2f * env
            }
            Kind.CHIME -> {
                phase += (if (t < 0.12f) 1568.0 else 2093.0) / s.rate
                sin(2 * PI * phase).toFloat() * 0.18f * env
            }
            Kind.CHEER -> {
                lp += (s.noise() - lp) * 0.15f
                val swell = if (t < 0.4f) t / 0.4f else env
                lp * 0.6f * swell
            }
            Kind.WHOOSH -> {
                lp += (s.noise() - lp) * (0.02f + 0.3f * (t / length))
                lp * 0.5f * sin(PI * t / length).toFloat()
            }
            Kind.SLAM -> {
                lp += (s.noise() - lp) * 0.12f
                phase += (60.0 - t * 40.0) / s.rate
                (lp * 0.6f + sin(2 * PI * phase).toFloat() * 0.8f) * env * env * env
            }
        }
        return y * volume
    }
}

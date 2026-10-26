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
    /** Rain ambience level 0..1. */
    @Volatile var rain = 0f
    private var rainLevel = 0f
    private var rainHp = 0f
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
            rainLevel += (rain - rainLevel) * 0.0002f
            if (rainLevel > 0.001f) {
                val nz = noise()
                rainHp += (nz - rainHp) * 0.35f
                s += (nz - rainHp) * rainLevel * 0.22f
            }
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

/** One-shot effects. */
class Sfx(private val kind: Kind, private val volume: Float) {
    enum class Kind { CLUNK, POP, BLIP, BEEP, CHIME, CHEER, WHOOSH, SLAM, THUNDER }

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
        Kind.THUNDER -> 2.6f
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
            Kind.THUNDER -> {
                // a crack, then a long low roll
                lp += (s.noise() - lp) * (if (t < 0.08f) 0.5f else 0.02f)
                lp * (if (t < 0.08f) 1.2f else 0.9f) * env * (0.7f + 0.3f * sin(t * 13.0).toFloat())
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

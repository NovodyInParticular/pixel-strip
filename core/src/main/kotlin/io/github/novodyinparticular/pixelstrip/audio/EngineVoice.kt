package io.github.novodyinparticular.pixelstrip.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * One engine, synthesized per sample from the live physics and the parts on the car.
 *
 * The core is a pulse per firing, so the pitch is exactly the firing frequency: a four-stroke
 * fires each cylinder once every two revs, a rotary each rotor once per rev. Each layout has its
 * own pattern of pulse strengths (the crossplane V8's burble, a boxer's unequal headers, even
 * sixes and twelves). The pulses go through a resonant low-pass that opens with rpm and throttle.
 *
 * The parts are heard through [EngineSound]: exhaust volume and rasp, a lumpy idle from big cams
 * (or a rotary's ports), intake growl, turbo whistle by size, the blow-off valve (or the flutter
 * with none), a wastegate screamer, the supercharger by type, 2-step and anti-lag bangs while
 * staged, crackle on a lift with a loud exhaust, and the limiter bouncing at redline.
 */
class EngineVoice(private val synth: Synth) {
    @Volatile var active = false
    @Volatile var rpm = 800f
    @Volatile var idleRpm = 800f
    @Volatile var redline = 6500f
    @Volatile var throttle = 0f
    @Volatile var limiter = false
    @Volatile var layout = "v8"
    @Volatile var cylinders = 8
    /** Cam crossover rpm (0 = none): above it, on the throttle, the intake howl hardens. */
    @Volatile var vtecRpm = 0f
    @Volatile var turbo = false
    @Volatile var boost = 0f
    /** Supercharger boost share (0 = none). */
    @Volatile var blower = 0f
    @Volatile var nitrous = false
    @Volatile var slip = 0f
    @Volatile var volume = 1f
    /** Staged on the 2-step: held at the launch rpm with the gas down. [antiLag]: and the turbo kept lit. */
    @Volatile var twoStep = false
    @Volatile var antiLag = false
    @Volatile var sound = EngineSound.STOCK

    private val rate = synth.rate
    private var r = 800f
    private var thr = 0f
    private var bst = 0f
    private var level = 0f
    private var phase = 0.0
    private var lastK = -1
    private var fireAmp = 1f
    private var whistle = 0.0
    private var whine = 0.0
    private var squeal = 0.0
    private var surge = 0.0
    private val filter = Synth.Svf()
    private val tyre = Synth.Svf()
    private val intake = Synth.Svf()
    private val scream = Synth.Svf()
    private var dcIn = 0f
    private var dcOut = 0f
    private var blowOff = 0f
    private var flutter = 0f
    private var lastThrottle = 0f
    private var hissLp = 0f
    private var cam = 0f
    private var lope = 0.0
    private var bang = 0f
    private var bangLp = 0f
    private var thump = 0.0
    private var crackle = 0f
    private var bounce = 0.0

    private fun pattern(): FloatArray = when (layout) {
        "v8" -> V8
        "v6" -> V6
        "v10" -> V10
        "v12" -> V12
        "rotary" -> ROTARY
        "i6" -> I6
        "flat4" -> FLAT4
        "flat6" -> FLAT6
        "flat12" -> FLAT12
        else -> I4
    }

    /** Extra filter brightness (Hz) per layout: air-cooled sixes and twelves scream, boxers and big rotaries rumble. */
    private fun bright(): Float = when (layout) {
        "flat6" -> 500f
        "v12", "flat12" -> 750f
        "v10" -> 200f
        "flat4" -> -120f
        "rotary" -> -160f * (cylinders - 2)
        else -> 0f
    }

    /** Exhaust rasp the layout has before any parts. */
    private fun baseRasp(): Float = when (layout) {
        "flat6" -> 0.3f
        "flat4" -> 0.2f
        "v12", "flat12" -> 0.12f
        "rotary" -> 0.15f
        else -> 0f
    }

    /** How choppy the idle is: big cams, or a rotary (which is never smooth down there, ported or not). */
    private fun roughness() = sound.lope + if (layout == "rotary") 0.3f else 0f

    fun next(): Float {
        val target = if (active) volume else 0f
        level += (target - level) * 0.0008f
        if (level < 0.0005f && !active) return 0f
        r += (rpm - r) * 0.004f
        thr += (throttle - thr) * 0.003f
        bst += (boost - bst) * 0.002f
        val snd = sound
        val rotary = layout == "rotary"

        // --- the firing pulses ---------------------------------------------------------------
        val amps = pattern()
        val firings = cylinders
        val cycleHz = r / 60.0 / (if (rotary) 1.0 else 2.0)
        phase += cycleHz / rate
        if (phase >= 1.0) phase -= 1.0
        val pos = phase * firings
        val k = pos.toInt().coerceIn(0, firings - 1)
        val local = (pos - k).toFloat()
        // near idle a lumpy cam or a ported rotary fires unevenly: a weak one, a hard one (brap, brap)
        val idleness = (1f - (r - idleRpm) / IDLE_BAND).coerceIn(0f, 1f)
        if (k != lastK) {
            lastK = k
            val rough = roughness() * idleness
            fireAmp = (1f + (synth.noise() * 0.5f) * rough).coerceAtLeast(0.15f)
            if (synth.noise() * 0.5f + 0.5f < rough * 0.22f) fireAmp *= 0.25f              // a near-miss
        }
        // a rotary's pulses sharpen with revs: mellow brap at idle, a hard buzz up top
        val decay = if (rotary) 3f + r / 900f else 5f + r / 1500f
        var pulse = exp(-local * decay) * amps[k % amps.size] * fireAmp
        if (layout == "flat4" || (layout == "v8" && snd.lope > 0.4f && idleness > 0f)) {
            // unequal-length headers, or a big cam's lope: the pulses bunch, a burble at half the firing rate
            lope += cycleHz / rate
            val depth = if (layout == "flat4") 0.35f else 0.45f * snd.lope * idleness
            pulse *= 1f - depth * 0.6f + depth * sin(2 * PI * lope).toFloat()
        }
        // the cam switching over: an audible step up in rasp and brightness, on the gas
        val camTarget = if (vtecRpm > 0f && r > vtecRpm && thr > 0.5f) 1f else 0f
        cam += (camTarget - cam) * 0.003f
        val rasp = baseRasp() + snd.rasp * 0.6f + 0.25f * cam
        var s = pulse + synth.noise() * pulse * (0.25f + 0.45f * snd.loud + rasp) * (0.3f + thr)

        // the limiter: ignition cut and back, bouncing ~14 times a second, crackling
        if (limiter) {
            bounce += 14.0 / rate
            if (bounce >= 1.0) bounce -= 1.0
            s *= if (bounce < 0.45) 0.12f + (if (synth.noise() > 0.6f) 0.6f else 0f) else 1f
        } else bounce = 0.0

        val cutoff = 180f + r * (if (rotary) 0.4f else 0.32f) + thr * 900f + snd.loud * 700f + bright() + cam * 800f
        val q = 0.55f - snd.loud * 0.22f - cam * 0.12f
        var y = filter.run(s, cutoff, q, rate) + filter.band * (0.15f + 0.3f * snd.loud + (if (rotary) 0.1f * (cylinders - 2) else 0f))
        val dc = y - dcIn + 0.995f * dcOut                   // DC blocker
        dcIn = y
        dcOut = dc
        y = dc * (0.35f + 0.65f * thr) * (0.8f + 0.45f * snd.loud)

        // --- induction: a growl that rides the throttle -------------------------------------------
        if (snd.growl > 0f) {
            intake.run(synth.noise() * (0.4f + pulse), 260f + r * 0.11f, 0.35f, rate)
            y += intake.band * snd.growl * thr * 0.22f
        }

        // --- boost ------------------------------------------------------------------------------
        if (turbo) {
            // a bigger turbo whistles lower and louder
            whistle += (2900.0 - 900.0 * snd.whistle + bst * 4200.0) / rate
            y += sin(2 * PI * whistle).toFloat() * bst * bst * (0.035f + 0.045f * snd.whistle)
            val lift = lastThrottle - throttle > 0.4f && bst > 0.35f
            if (lift) when (snd.bovType) {
                1 -> blowOff = bst                        // vent-to-atmosphere: PSSSH
                2 -> blowOff = bst * 0.3f                 // recirculating: a soft sigh
                else -> flutter = bst                     // nowhere to go: the compressor stalls, stu-tu-tu
            }
            if (snd.screamer > 0f && bst > 0.8f && thr > 0.6f) {
                // the wastegate venting straight to the air: a harsh scream on top of everything
                scream.run(synth.noise(), 1800f + bst * 900f, 0.06f, rate)
                y += scream.band * (bst - 0.8f) * 5f * snd.screamer * 0.18f
            }
        }
        lastThrottle = throttle
        if (blowOff > 0.001f) {
            hissLp += (synth.noise() - hissLp) * 0.6f
            y += (synth.noise() - hissLp) * blowOff * 0.3f
            blowOff *= 0.99975f
        }
        if (flutter > 0.001f) {
            surge += 22.0 / rate
            val chop = if ((surge % 1.0) < 0.5) 1f else 0.2f
            y += sin(2 * PI * surge * 70.0).toFloat() * flutter * chop * 0.07f
            flutter *= 0.99985f
        }
        if (blower > 0f) {
            val centrifugal = snd.blowerType == 3
            if (centrifugal) {
                // an impeller geared way up: a siren that climbs and gets louder with rpm
                whine += r / 60.0 * 14.0 / rate
                val k2 = (r / redline).coerceIn(0f, 1f)
                y += sin(2 * PI * whine).toFloat() * k2 * k2 * 0.06f * (0.4f + thr)
            } else {
                // roots and twin-screw lobes: a gear whine locked to the crank
                whine += r / 60.0 * (if (snd.blowerType == 2) 4.1 else 3.3) / rate
                y += (sin(2 * PI * whine) + 0.4 * sin(4 * PI * whine)).toFloat() * blower * 0.035f * (0.4f + thr)
            }
        }

        // --- bangs: the 2-step and anti-lag while staged, crackle on a lift with a loud pipe -------
        val bangRate = when {
            twoStep && antiLag -> 16f
            twoStep -> 9f
            crackle > 0f -> 7f * crackle
            else -> 0f
        }
        if (bangRate > 0f && synth.noise() * 0.5f + 0.5f < bangRate / rate) {
            bang = if (twoStep) 1f else 0.45f + 0.4f * crackle
            thump = 0.0
        }
        if (crackle > 0f) crackle = max(0f, crackle - 1.6f / rate)
        if (lastThrottle < 0.1f && thr > 0.5f && r > idleRpm + 2000f && (snd.loud + snd.rasp) > 0.9f) crackle = min(1f, (snd.loud + snd.rasp) * 0.5f)
        if (bang > 0.001f) {
            // a crack of noise over a low thump, unburnt fuel lighting in the pipe
            bangLp += (synth.noise() - bangLp) * 0.35f
            thump += 55.0 / rate
            val loudness = if (antiLag) 2.2f else 1.5f
            y += (bangLp * 1.4f + sin(2 * PI * thump).toFloat() * 0.8f) * bang * loudness * (0.6f + 0.4f * sound.loud)
            bang *= 0.9986f
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
        /** Rpm above idle where a lumpy idle has smoothed out. */
        const val IDLE_BAND = 1500f
        // relative strength of each firing in the cycle: the crossplane V8's lope, even inline sixes
        val V8 = floatArrayOf(1f, 0.6f, 0.92f, 0.7f, 0.96f, 0.58f, 0.86f, 0.74f)
        val V6 = floatArrayOf(1f, 0.82f, 0.96f, 0.85f, 1f, 0.8f)
        val V10 = floatArrayOf(1f, 0.78f, 0.95f, 0.72f, 1f, 0.84f, 0.9f, 0.8f, 1f, 0.7f)
        val I6 = floatArrayOf(1f, 0.94f, 0.97f, 0.95f, 1f, 0.93f)
        val I4 = floatArrayOf(1f, 0.9f, 0.98f, 0.88f)
        val ROTARY = floatArrayOf(1f, 1f, 1f, 1f)
        val V12 = floatArrayOf(1f, 0.95f, 0.98f, 0.93f, 1f, 0.96f, 0.97f, 0.94f, 1f, 0.95f, 0.98f, 0.92f)
        val FLAT12 = floatArrayOf(1f, 0.97f, 0.99f, 0.96f, 1f, 0.98f, 0.99f, 0.96f, 1f, 0.97f, 0.99f, 0.95f)
        val FLAT4 = floatArrayOf(1f, 0.55f, 0.9f, 0.5f)
        val FLAT6 = floatArrayOf(1f, 0.9f, 0.96f, 0.88f, 1f, 0.92f)
    }
}

/**
 * How a build sounds, summed from the sound tags of the parts that work on it (tools/make_parts.py):
 * exhaust [loud] and [rasp], cam or port [lope], intake [growl], turbo [whistle] size, a wastegate
 * [screamer], the blow-off valve type (0 none: flutter, 1 vent-to-atmosphere, 2 recirculating) and
 * the supercharger type (1 roots, 2 twin-screw, 3 centrifugal).
 */
data class EngineSound(
    val loud: Float = 0f, val rasp: Float = 0f, val lope: Float = 0f, val growl: Float = 0f,
    val whistle: Float = 0f, val screamer: Float = 0f, val bovType: Int = 0, val blowerType: Int = 0,
) {
    companion object {
        val STOCK = EngineSound()

        /** [def] is the effective car (after any swap), so a piston part on a rotary stays quiet too. */
        fun of(c: io.github.novodyinparticular.pixelstrip.data.PartCatalog, parts: Map<String, Int>,
               def: io.github.novodyinparticular.pixelstrip.data.CarDef): EngineSound {
            var loud = 0f
            var rasp = 0f
            var lope = 0f
            var growl = 0f
            var whistle = 0f
            var screamer = 0f
            for (s in c.slots) {
                val fx = io.github.novodyinparticular.pixelstrip.data.Upgrades.working(c, parts, s.id, def)?.effects ?: continue
                loud += fx["loud"] ?: 0f
                rasp += fx["rasp"] ?: 0f
                lope += fx["lope"] ?: 0f
                growl += fx["growl"] ?: 0f
                whistle = max(whistle, fx["whistle"] ?: 0f)
                screamer = max(screamer, fx["screamer"] ?: 0f)
            }
            if (def.stockTurbo && whistle == 0f) whistle = 0.4f            // a factory turbo whistles too
            return EngineSound(
                loud.coerceAtMost(1.3f), rasp.coerceAtMost(1.4f), lope.coerceAtMost(1f), growl.coerceAtMost(1f), whistle, screamer,
                c.fx(parts, "bov", "bovType").toInt(), c.fx(parts, "supercharger", "scType").toInt(),
            )
        }
    }
}

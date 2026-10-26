package io.github.novodyinparticular.pixelstrip.audio

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.AudioDevice
import com.badlogic.gdx.utils.Disposable
import io.github.novodyinparticular.pixelstrip.Settings
import io.github.novodyinparticular.pixelstrip.data.CarDef
import io.github.novodyinparticular.pixelstrip.race.Vehicle

/** Streams the [Synth] to the audio device on its own thread. Stops while the app is paused. */
class AudioEngine(private val settings: Settings) : Disposable {
    val synth = Synth(RATE)
    private var device: AudioDevice? = null
    private var thread: Thread? = null
    @Volatile private var running = false

    fun start() {
        if (running) return
        val dev = runCatching { Gdx.audio.newAudioDevice(RATE, true) }.getOrNull() ?: return
        device = dev
        running = true
        thread = Thread({
            val buf = FloatArray(BUFFER)
            while (running) {
                synth.master = if (settings.sound) 0.8f else 0f
                synth.render(buf, buf.size)
                dev.writeSamples(buf, 0, buf.size)       // blocks until the device wants more
            }
        }, "pixel-strip-audio").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
        device?.dispose()
        device = null
    }

    fun play(kind: Sfx.Kind, volume: Float = 1f) = synth.play(kind, volume)

    fun music(song: String?, volume: Float) {
        synth.music.song = song
        synth.musicVolume = volume
    }

    /** Feeds engine voice [i] from a running car; [sound] is how its parts make it sound (EngineSound.of). */
    fun engine(i: Int, car: Vehicle, sound: EngineSound, volume: Float) {
        val e = synth.engines[i]
        val def = car.def
        voice(e, def, sound, volume)
        e.rpm = car.rpm
        e.throttle = car.throttle
        e.limiter = car.limiter
        e.boost = car.boost
        // held on the 2-step at the launch rpm: bangs (and with anti-lag, the turbo kept lit)
        e.twoStep = car.mode == Vehicle.Mode.HELD && def.launchControl > 0f && car.throttle > 0f
        e.antiLag = e.twoStep && def.antiLag
        e.nitrous = car.nitrousActive && car.throttle > 0f
        e.slip = (car.slipSpeed / 14f).coerceIn(0f, 1f)
    }

    /**
     * An engine revving without a Vehicle (garage, dyno): boost follows rpm and throttle unless the
     * caller knows it; [twoStep] holds it on the launch limiter.
     */
    fun engine(i: Int, def: CarDef, rpm: Float, throttle: Float, sound: EngineSound, volume: Float, limiter: Boolean = rpm >= def.redline - 20f,
               twoStep: Boolean = false) {
        val e = synth.engines[i]
        voice(e, def, sound, volume)
        e.rpm = rpm
        e.throttle = throttle
        e.limiter = limiter
        e.boost = if (def.boostGain > 0f || def.stockTurbo) (throttle * ((rpm - def.boostSpoolRpm) / 1400f)).coerceIn(0f, 1f) else 0f
        e.twoStep = twoStep
        e.antiLag = twoStep && def.antiLag
        e.nitrous = false
        e.slip = 0f
    }

    private fun voice(e: EngineVoice, def: CarDef, sound: EngineSound, volume: Float) {
        e.active = true
        e.layout = def.engineLayout
        e.cylinders = def.cylinders
        e.vtecRpm = def.vtecRpm
        e.idleRpm = def.idleRpm
        e.redline = def.redline
        e.turbo = def.boostGain > 0f || def.stockTurbo
        e.blower = (def.blowerGain * 2f).coerceAtMost(1f)
        e.sound = sound
        e.volume = volume
    }

    fun silenceEngines() = synth.engines.forEach { it.active = false }

    override fun dispose() = stop()

    companion object {
        const val RATE = 22050
        const val BUFFER = 512
    }
}

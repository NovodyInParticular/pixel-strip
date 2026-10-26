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

    /** Feeds engine voice [i] from a running car. */
    fun engine(i: Int, car: Vehicle, exhaustTier: Int, volume: Float) {
        val e = synth.engines[i]
        val def = car.def
        e.active = true
        e.layout = def.engineLayout
        e.cylinders = def.cylinders
        e.vtecRpm = def.vtecRpm
        e.rpm = car.rpm
        e.redline = def.redline
        e.throttle = car.throttle
        e.limiter = car.limiter
        e.turbo = def.boostGain > 0f || def.stockTurbo
        e.boost = car.boost
        e.blower = (def.blowerGain * 2f).coerceAtMost(1f)
        e.exhaust = exhaustTier / 4f
        e.nitrous = car.nitrousActive && car.throttle > 0f
        e.slip = (car.slipSpeed / 14f).coerceIn(0f, 1f)
        e.volume = volume
    }

    /** An engine idling or revving without a Vehicle (garage, dyno). */
    fun engine(i: Int, def: CarDef, rpm: Float, throttle: Float, exhaustTier: Int, volume: Float) {
        val e = synth.engines[i]
        e.active = true
        e.layout = def.engineLayout
        e.cylinders = def.cylinders
        e.vtecRpm = def.vtecRpm
        e.rpm = rpm
        e.redline = def.redline
        e.throttle = throttle
        e.limiter = rpm >= def.redline - 20f
        e.turbo = def.boostGain > 0f || def.stockTurbo
        e.boost = (throttle * ((rpm - 2800f) / 2500f)).coerceIn(0f, 1f)
        e.blower = (def.blowerGain * 2f).coerceAtMost(1f)
        e.exhaust = exhaustTier / 4f
        e.nitrous = false
        e.slip = 0f
        e.volume = volume
    }

    fun silenceEngines() = synth.engines.forEach { it.active = false }

    override fun dispose() = stop()

    companion object {
        const val RATE = 22050
        const val BUFFER = 512
    }
}

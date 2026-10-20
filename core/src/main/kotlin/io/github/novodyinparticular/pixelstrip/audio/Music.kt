package io.github.novodyinparticular.pixelstrip.audio

import kotlin.math.PI
import kotlin.math.sin

/**
 * A four-channel chiptune sequencer: pulse lead, pulse arpeggio, triangle bass, noise drums.
 * Songs are a chord progression, a tempo and a melody motif; the arpeggio, bass line and drums
 * are derived from the chords, with a fill every fourth bar.
 */
class Music(private val synth: Synth) {
    class Song(val bpm: Float, val chords: List<Pair<Int, Boolean>>, val motif: IntArray, val drive: Float)

    @Volatile var song: String? = null
    private var playing: String? = null
    private var s: Song? = null
    private var sample = 0L
    private var step = -1
    private val ph = DoubleArray(4)
    private val freq = FloatArray(4)
    private val env = FloatArray(4)
    private var kick = 0f
    private var kickPh = 0.0
    private var snare = 0f
    private var hat = 0f
    private var drumLp = 0f

    fun next(): Float {
        if (song != playing) {
            playing = song
            s = SONGS[song]
            sample = 0
            step = -1
        }
        val sng = s ?: return 0f
        val stepLen = (synth.rate * 60f / sng.bpm / 4f).toLong()
        val st = (sample / stepLen).toInt()
        if (st != step) {
            step = st
            trigger(sng, st)
        }
        sample++
        var out = 0f
        // lead (25% pulse), arp (50% pulse), bass (triangle)
        for (c in 0..2) {
            if (env[c] < 0.001f) continue
            ph[c] += freq[c] / synth.rate
            val p = ph[c] % 1.0
            val v = when (c) {
                0 -> if (p < 0.25) 1f else -1f
                1 -> if (p < 0.5) 1f else -1f
                else -> (4.0 * kotlin.math.abs(p - 0.5) - 1.0).toFloat()
            }
            out += v * env[c] * VOL[c]
            env[c] *= DECAY[c]
        }
        // drums
        if (kick > 0.001f) {
            kickPh += (45.0 + 90.0 * kick) / synth.rate
            out += sin(2 * PI * kickPh).toFloat() * kick * 0.5f
            kick *= 0.9993f
        }
        if (snare > 0.001f) {
            out += synth.noise() * snare * 0.22f
            snare *= 0.9985f
        }
        if (hat > 0.001f) {
            val n = synth.noise()
            drumLp += (n - drumLp) * 0.5f
            out += (n - drumLp) * hat * 0.12f
            hat *= 0.996f
        }
        return out * 0.6f
    }

    private fun trigger(sng: Song, st: Int) {
        val inBar = st % 16
        val bar = (st / 16) % sng.chords.size
        val fill = (st / 16) % 4 == 3 && inBar >= 12
        val (root, minor) = sng.chords[bar]
        val third = if (minor) 3 else 4
        val chord = intArrayOf(0, third, 7, 12)
        // arpeggio every 16th
        note(1, root + 12 + chord[inBar % 4], 0.6f)
        // bass on the 8ths, octave jump on the off-beats when driving
        if (inBar % 2 == 0) note(2, root - 12 + (if (sng.drive > 0.5f && inBar % 4 == 2) 12 else 0), 1f)
        // lead motif: scale degrees over the chord, -1 = rest
        val m = sng.motif[(st % (sng.motif.size)).coerceAtLeast(0)]
        if (m >= 0) note(0, root + 24 + SCALE[m % SCALE.size] + if (minor && m % 7 == 2) -1 else 0, 1f)
        // drums
        if (inBar % 8 == 0 || (sng.drive > 0.5f && inBar % 8 == 6)) { kick = 1f; kickPh = 0.0 }
        if (inBar % 8 == 4 || (fill && inBar % 2 == 0)) snare = 1f
        if (inBar % 2 == 1 || sng.drive > 0.7f) hat = if (inBar % 4 == 2) 1f else 0.6f
    }

    private fun note(c: Int, midi: Int, vel: Float) {
        freq[c] = Synth.noteHz(midi.toFloat())
        env[c] = vel
    }

    companion object {
        private val VOL = floatArrayOf(0.09f, 0.045f, 0.16f)
        private val DECAY = floatArrayOf(0.99985f, 0.9994f, 0.9997f)
        private val SCALE = intArrayOf(0, 2, 4, 5, 7, 9, 11)

        // root notes as MIDI (C3 = 48); true = minor
        val SONGS = mapOf(
            "title" to Song(124f, listOf(52 to true, 48 to false, 55 to false, 50 to false),
                intArrayOf(0, -1, 2, -1, 4, -1, 2, 3, 4, -1, 6, -1, 4, 2, -1, -1), 0.8f),
            "garage" to Song(94f, listOf(57 to true, 53 to false, 48 to false, 55 to false),
                intArrayOf(4, -1, -1, 2, -1, -1, 0, -1, 2, -1, 4, -1, -1, -1, -1, -1), 0.2f),
            "career" to Song(112f, listOf(50 to true, 58 to false, 53 to false, 48 to false),
                intArrayOf(0, -1, 4, -1, 2, -1, 4, 5, -1, 4, -1, 2, 0, -1, -1, -1), 0.5f),
            "neon_city" to Song(140f, listOf(54 to true, 50 to false, 57 to false, 52 to false),
                intArrayOf(0, 0, -1, 4, -1, 4, 2, -1, 0, -1, 6, -1, 4, -1, 2, -1), 1f),
            "boss" to Song(132f, listOf(52 to true, 52 to true, 48 to false, 50 to false),
                intArrayOf(0, -1, 0, 1, 2, -1, 1, -1, 0, -1, -1, 4, 3, -1, 2, -1), 1f),
        )
    }
}

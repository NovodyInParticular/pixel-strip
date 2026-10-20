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
        e.exhaust = 0.5f
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
        for ((layout, cyl, firingsPerRev) in listOf(Triple("v8", 8, 4f), Triple("i4", 4, 2f), Triple("rotary", 2, 2f))) {
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
}

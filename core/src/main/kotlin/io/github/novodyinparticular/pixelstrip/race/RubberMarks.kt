package io.github.novodyinparticular.pixelstrip.race

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import io.github.novodyinparticular.pixelstrip.world.TrackRenderer.Companion.PX_PER_M
import kotlin.math.floor

/**
 * Rubber laid on the track: one strip per lane, darkened a pixel column at a time wherever a
 * driven tyre spins. A long burnout leaves a black patch in the box; a launch with wheelspin, two
 * fading streaks. Kept for the whole run, on top of the groove: the rubber thousands of earlier
 * passes left in each lane, darkest at the line and fading out down the track, as on a real strip.
 */
class RubberMarks(private val white: Texture) {
    private val start = floor(FROM_M * PX_PER_M).toInt()
    private val strips = Array(2) { FloatArray(((TO_M - FROM_M) * PX_PER_M).toInt()) }

    /** Rubber at world pixel [x] in [lane] (0 near, 1 far); [amount] adds darkness (0..1 is full black). */
    fun lay(lane: Int, x: Float, amount: Float) {
        val i = floor(x).toInt() - start
        val s = strips[lane]
        for (k in -1..1) {
            val j = i + k
            if (j in s.indices) s[j] = (s[j] + amount * (if (k == 0) 1f else 0.5f)).coerceAtMost(1f)
        }
    }

    fun clear() {
        val rng = java.util.Random(7)
        for (s in strips) for (j in s.indices) {
            val m = (j + start) / PX_PER_M
            val groove = when {
                m < -12f -> 0f
                m < 0f -> 0.45f * (m + 12f) / 12f              // the burnout box
                m < 90f -> 0.55f * (1f - m / 90f)              // the launch: darkest off the line
                else -> 0f
            }
            s[j] = (groove * (0.75f + 0.5f * rng.nextFloat())).coerceIn(0f, 1f)
        }
    }

    init {
        clear()
    }

    /** The strip under [lane]'s tyres: [thick] pixels tall at [groundY]; [wet] tracks show it fainter. */
    fun draw(batch: SpriteBatch, camX: Float, screenW: Int, lane: Int, groundY: Float, thick: Float, wet: Boolean) {
        val s = strips[lane]
        val from = (floor(camX).toInt() - start).coerceAtLeast(0)
        val to = (floor(camX).toInt() + screenW - start).coerceAtMost(s.size)
        val k = if (wet) 0.6f else 0.9f
        for (j in from until to) {
            val a = s[j]
            if (a < 0.03f) continue
            val x = floor(j + start - camX)
            batch.setColor(0.01f, 0.01f, 0.02f, a * k)
            batch.draw(white, x, groundY - 2f, 1f, thick)
            // rubbered-in asphalt has a dull shine along its edge
            batch.setColor(0.7f, 0.72f, 0.85f, a * 0.12f)
            batch.draw(white, x, groundY - 2f + thick, 1f, 1f)
        }
        batch.setColor(1f, 1f, 1f, 1f)
    }

    companion object {
        const val FROM_M = -20f
        const val TO_M = 460f
    }
}

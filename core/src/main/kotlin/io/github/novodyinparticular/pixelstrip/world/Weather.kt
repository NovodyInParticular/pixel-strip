package io.github.novodyinparticular.pixelstrip.world

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.MathUtils
import kotlin.math.floor

/**
 * Screen-space weather: slanted rain streaks (with lightning), drifting snow, blowing sand,
 * rising embers. Moves with the camera speed so it whips past when you're flying.
 */
class Weather(private val kind: String?, private val white: Texture) {
    private val n = when (kind) {
        "rain" -> 180
        "snow" -> 140
        "sand" -> 90
        "embers" -> 50
        else -> 0
    }
    private val x = FloatArray(n) { MathUtils.random(0f, 640f) }
    private val y = FloatArray(n) { MathUtils.random(0f, 270f) }
    private val s = FloatArray(n) { MathUtils.random(0.6f, 1.4f) }
    private var t = 0f
    /** 0..1 while lightning flashes. */
    var lightning = 0f
        private set
    /** True on the frame a bolt strikes (for the thunder). */
    var struck = false
        private set

    /** [scroll] = how far the camera moved this frame (pixels), so particles streak past. */
    fun update(dt: Float, scroll: Float, w: Int) {
        t += dt
        struck = false
        lightning = (lightning - dt * 3f).coerceAtLeast(0f)
        if (kind == "rain" && MathUtils.random() < dt / 7f) {
            lightning = 1f
            struck = true
        }
        for (i in 0 until n) {
            when (kind) {
                "rain" -> { x[i] -= (60f * s[i]) * dt + scroll * 0.6f; y[i] -= 420f * s[i] * dt }
                "snow" -> { x[i] -= (12f + MathUtils.sin(t + i) * 10f) * dt + scroll * 0.4f; y[i] -= 26f * s[i] * dt }
                "sand" -> { x[i] -= 180f * s[i] * dt + scroll * 0.5f; y[i] += MathUtils.sin(t * 3f + i) * 8f * dt }
                "embers" -> { x[i] -= 10f * dt + scroll * 0.3f; y[i] += 22f * s[i] * dt }
            }
            if (y[i] < -8f) y[i] += 280f
            if (y[i] > 278f) y[i] -= 280f
            if (x[i] < -8f) x[i] += w + 16f
            if (x[i] > w + 8f) x[i] -= w + 16f
        }
    }

    fun draw(batch: SpriteBatch) {
        for (i in 0 until n) {
            val px = floor(x[i])
            val py = floor(y[i])
            when (kind) {
                "rain" -> {
                    batch.setColor(0.6f, 0.7f, 0.95f, 0.35f * s[i])
                    batch.draw(white, px, py, 1f, 5f * s[i])
                    batch.draw(white, px + 1f, py + 3f, 1f, 3f * s[i])
                }
                "snow" -> {
                    batch.setColor(0.95f, 0.97f, 1f, 0.8f)
                    batch.draw(white, px, py, if (s[i] > 1.1f) 2f else 1f, if (s[i] > 1.1f) 2f else 1f)
                }
                "sand" -> {
                    batch.setColor(0.95f, 0.75f, 0.45f, 0.35f)
                    batch.draw(white, px, py, 6f * s[i], 1f)
                }
                "embers" -> {
                    val flick = 0.6f + 0.4f * MathUtils.sin(t * 9f + i)
                    batch.setColor(1f, 0.55f * flick, 0.15f, 0.9f)
                    batch.draw(white, px, py, 1f, 1f)
                }
            }
        }
        batch.color = Color.WHITE
    }

    /** Embers glow. */
    fun drawGlow(batch: SpriteBatch) {
        if (kind != "embers") return
        for (i in 0 until n) {
            batch.setColor(1f, 0.45f, 0.1f, 0.8f)
            batch.draw(white, floor(x[i]) - 1f, floor(y[i]) - 1f, 3f, 3f)
        }
        batch.color = Color.WHITE
    }
}

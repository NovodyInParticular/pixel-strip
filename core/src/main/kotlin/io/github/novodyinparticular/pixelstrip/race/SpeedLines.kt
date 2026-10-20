package io.github.novodyinparticular.pixelstrip.race

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.MathUtils
import kotlin.math.floor

/** Screen-space streaks that whip past once the car is really moving. */
class SpeedLines(private val white: Texture) {
    private val x = FloatArray(MAX)
    private val y = FloatArray(MAX)
    private val len = FloatArray(MAX)
    private val speed = FloatArray(MAX)
    private val alive = BooleanArray(MAX)
    private var spawn = 0f

    /** [v] in m/s. Lines live in two bands: over the sky and low over the road. */
    fun update(dt: Float, v: Float, screenW: Int) {
        val k = ((v - START) / (FULL - START)).coerceIn(0f, 1f)
        spawn += dt * k * 70f
        while (spawn >= 1f) {
            spawn -= 1f
            val i = alive.indexOfFirst { !it }
            if (i < 0) break
            alive[i] = true
            x[i] = screenW + MathUtils.random(0f, 40f)
            y[i] = if (MathUtils.randomBoolean(0.6f)) MathUtils.random(150f, 255f) else MathUtils.random(4f, 26f)
            len[i] = MathUtils.random(10f, 34f) * (0.5f + k)
            speed[i] = MathUtils.random(700f, 1200f) * (0.6f + k)
        }
        for (i in 0 until MAX) {
            if (!alive[i]) continue
            x[i] -= speed[i] * dt
            if (x[i] + len[i] < 0f) alive[i] = false
        }
    }

    fun draw(batch: SpriteBatch, v: Float) {
        val k = ((v - START) / (FULL - START)).coerceIn(0f, 1f)
        if (k <= 0f) return
        for (i in 0 until MAX) {
            if (!alive[i]) continue
            batch.setColor(0.9f, 0.92f, 1f, 0.18f + 0.25f * k)
            batch.draw(white, floor(x[i]), floor(y[i]), len[i], 1f)
        }
        batch.setColor(1f, 1f, 1f, 1f)
    }

    companion object {
        const val MAX = 48
        const val START = 18f
        const val FULL = 45f
    }
}

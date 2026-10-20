package io.github.novodyinparticular.pixelstrip.race

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.MathUtils
import kotlin.math.floor

/**
 * Pooled particles in world pixels (y up).
 *  SMOKE  grows through the puff sprites, rises, drifts back and lingers; starts tinted by the
 *         lamps behind it (tail-light red) and fades toward the night ambient. Two layers:
 *         behind the car and billowing in front of it.
 *  RUBBER falls.  SPARK bounces and glows.  PURGE is the nitrous purge jet's white puffs.
 *  HAZE   is invisible: it only draws into the heat-distortion buffer.
 */
class Particles(private val smoke: Texture, private val white: Texture, private val haze: Texture, capacity: Int = 900) {
    enum class Kind { SMOKE, RUBBER, SPARK, PURGE, HAZE }

    private class P {
        var kind = Kind.SMOKE
        var x = 0f
        var y = 0f
        var vx = 0f
        var vy = 0f
        var life = 0f
        var maxLife = 1f
        var size0 = 6f
        var size1 = 30f
        val color = Color()
        val color2 = Color()
        var alpha = 1f
        var front = false
        var floor = 0f
        var alive = false
    }

    private val pool = Array(capacity) { P() }
    private var next = 0
    private val sizes = intArrayOf(6, 10, 16, 24, 32)
    private val cell = smoke.height
    private val tmp = Color()

    private fun obtain(): P {
        for (i in pool.indices) {
            val p = pool[(next + i) % pool.size]
            if (!p.alive) {
                next = (next + i + 1) % pool.size
                return p
            }
        }
        val p = pool[next]          // full: recycle the next slot
        next = (next + 1) % pool.size
        return p
    }

    /** Tyre smoke at world (x, y). [heat] 0..1 scales size and lifetime; [lit] is the lamp colour it starts in. */
    fun smoke(x: Float, y: Float, heat: Float, lit: Color, ambient: Color, linger: Float = 1f) {
        val p = obtain()
        p.kind = Kind.SMOKE
        p.alive = true
        p.x = x + MathUtils.random(-4f, 4f)
        p.y = y + MathUtils.random(-1f, 3f)
        p.vx = MathUtils.random(-34f, -6f) * (0.5f + heat)
        p.vy = MathUtils.random(3f, 16f)
        p.life = 0f
        p.maxLife = MathUtils.random(1.8f, 3.6f) * (0.6f + heat * 0.7f) * linger
        p.size0 = MathUtils.random(5f, 10f)
        p.size1 = MathUtils.random(24f, 40f) * (0.7f + heat * 0.6f)
        p.color.set(lit)
        p.color2.set(ambient)
        p.alpha = MathUtils.random(0.5f, 0.85f)
        p.front = MathUtils.random() < 0.4f
    }

    fun rubber(x: Float, y: Float) {
        val p = obtain()
        p.kind = Kind.RUBBER
        p.alive = true
        p.x = x
        p.y = y + 2f
        p.floor = y - 1f
        p.vx = MathUtils.random(-80f, -20f)
        p.vy = MathUtils.random(25f, 70f)
        p.life = 0f
        p.maxLife = 0.9f
        p.color.set(0.06f, 0.05f, 0.1f, 1f)
        p.front = true
    }

    fun spark(x: Float, y: Float) {
        val p = obtain()
        p.kind = Kind.SPARK
        p.alive = true
        p.x = x
        p.y = y
        p.floor = y - 2f
        p.vx = MathUtils.random(-140f, -40f)
        p.vy = MathUtils.random(10f, 70f)
        p.life = 0f
        p.maxLife = MathUtils.random(0.25f, 0.6f)
        p.color.set(1f, MathUtils.random(0.75f, 0.95f), 0.4f, 1f)
        p.front = true
    }

    /** One puff of the nitrous purge: shoots up from the nozzle and spreads. */
    fun purge(x: Float, y: Float) {
        val p = obtain()
        p.kind = Kind.PURGE
        p.alive = true
        p.x = x + MathUtils.random(-1f, 1f)
        p.y = y
        p.vx = MathUtils.random(-10f, 10f)
        p.vy = MathUtils.random(110f, 170f)
        p.life = 0f
        p.maxLife = MathUtils.random(0.6f, 1.1f)
        p.size0 = 4f
        p.size1 = MathUtils.random(16f, 26f)
        p.color.set(0.92f, 0.97f, 1f, 1f)
        p.color2.set(0.7f, 0.8f, 0.95f, 1f)
        p.alpha = 0.9f
        p.front = true
    }

    fun haze(x: Float, y: Float, strength: Float) {
        val p = obtain()
        p.kind = Kind.HAZE
        p.alive = true
        p.x = x + MathUtils.random(-3f, 3f)
        p.y = y + MathUtils.random(-2f, 3f)
        p.vx = MathUtils.random(-30f, -10f)
        p.vy = MathUtils.random(8f, 20f)
        p.life = 0f
        p.maxLife = MathUtils.random(0.4f, 0.8f)
        p.size0 = 10f
        p.size1 = 22f
        p.alpha = strength
    }

    fun update(dt: Float) {
        for (p in pool) {
            if (!p.alive) continue
            p.life += dt
            if (p.life >= p.maxLife) {
                p.alive = false
                continue
            }
            when (p.kind) {
                Kind.SMOKE -> {
                    p.vx *= 1f - dt * 0.7f
                    p.vy = p.vy * (1f - dt * 0.5f) + dt * 3f
                }
                Kind.PURGE -> {
                    p.vx *= 1f - dt * 2f
                    p.vy *= 1f - dt * 3.5f
                }
                Kind.RUBBER, Kind.SPARK -> {
                    p.vy -= 260f * dt
                    if (p.y < p.floor && p.vy < 0f) {
                        p.vy = -p.vy * 0.35f          // bounce off the asphalt
                        p.vx *= 0.6f
                    }
                }
                Kind.HAZE -> p.vy *= 1f - dt
            }
            p.x += p.vx * dt
            p.y += p.vy * dt
        }
    }

    /** Smoke, rubber, sparks and purge, behind ([front] = false) or in front of the cars. */
    fun draw(batch: SpriteBatch, camX: Float, front: Boolean) {
        for (p in pool) {
            if (!p.alive || p.front != front || p.kind == Kind.HAZE) continue
            val t = p.life / p.maxLife
            when (p.kind) {
                Kind.SMOKE, Kind.PURGE -> {
                    val size = p.size0 + (p.size1 - p.size0) * (1f - (1f - t) * (1f - t))
                    var idx = 0
                    while (idx < sizes.size - 1 && sizes[idx] < size) idx++
                    val a = p.alpha * (if (t < 0.08f) t / 0.08f else 1f - (t - 0.08f) / 0.92f)
                    tmp.set(p.color).lerp(p.color2, min1(t * 1.6f))
                    batch.setColor(tmp.r, tmp.g, tmp.b, a)
                    batch.draw(smoke, floor(p.x - camX - cell / 2f), floor(p.y - cell / 2f), idx * cell, 0, cell, cell)
                }
                Kind.RUBBER, Kind.SPARK -> {
                    batch.setColor(p.color.r, p.color.g, p.color.b, 1f)
                    batch.draw(white, floor(p.x - camX), floor(p.y), if (p.kind == Kind.SPARK) 2f else 1f, 1f)
                }
                Kind.HAZE -> {}
            }
        }
        batch.color = Color.WHITE
    }

    /** Sparks and fresh purge into the bloom buffer. */
    fun drawGlow(batch: SpriteBatch, camX: Float) {
        for (p in pool) {
            if (!p.alive) continue
            if (p.kind == Kind.SPARK) {
                batch.setColor(p.color.r, p.color.g * 0.8f, p.color.b * 0.5f, 1f)
                batch.draw(white, floor(p.x - camX) - 1f, floor(p.y) - 1f, 4f, 3f)
            }
        }
        batch.color = Color.WHITE
    }

    /** Heat-haze blobs into the distortion buffer. */
    fun drawHaze(batch: SpriteBatch, camX: Float) {
        for (p in pool) {
            if (!p.alive || p.kind != Kind.HAZE) continue
            val t = p.life / p.maxLife
            val size = p.size0 + (p.size1 - p.size0) * t
            batch.setColor(1f, 1f, 1f, p.alpha * (1f - t))
            batch.draw(haze, p.x - camX - size / 2f, p.y - size / 2f, size, size)
        }
        batch.color = Color.WHITE
    }

    private fun min1(v: Float) = if (v > 1f) 1f else v

    fun clear() = pool.forEach { it.alive = false }
}

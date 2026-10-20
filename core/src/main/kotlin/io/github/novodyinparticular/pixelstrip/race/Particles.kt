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
 *  SPARK bounces and glows.  PURGE is the nitrous purge jet's white puffs.
 *  HAZE   is invisible: it only draws into the heat-distortion buffer.
 *  CONFETTI flutters down after a win.  ROCKET climbs and bursts into STARs (fireworks), which glow.
 */
class Particles(private val smoke: Texture, private val white: Texture, private val haze: Texture, capacity: Int = 900) {
    enum class Kind { SMOKE, SPARK, PURGE, HAZE, CONFETTI, ROCKET, STAR }

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
        /** Drawn in screen pixels (confetti, fireworks), not on the track. */
        var screen = false
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
                p.screen = false
                return p
            }
        }
        val p = pool[next]          // full: recycle the next slot
        next = (next + 1) % pool.size
        p.screen = false
        return p
    }

    /** Tyre smoke at world (x, y). [heat] 0..1 scales size and lifetime; [lit] is the lamp colour it starts in. */
    fun smoke(x: Float, y: Float, heat: Float, lit: Color, ambient: Color, linger: Float = 1f, carry: Float = 0f) {
        val p = obtain()
        p.kind = Kind.SMOKE
        p.alive = true
        p.x = x + MathUtils.random(-4f, 4f)
        p.y = y + MathUtils.random(-1f, 3f)
        p.vx = MathUtils.random(-34f, -6f) * (0.5f + heat) + carry
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

    private val confettiColors = arrayOf(
        Color(0.94f, 0.37f, 0.68f, 1f), Color(0.49f, 0.95f, 0.86f, 1f), Color(1f, 0.91f, 0.56f, 1f),
        Color(0.58f, 0.84f, 0.42f, 1f), Color(1f, 0.6f, 0.24f, 1f), Color(0.6f, 0.45f, 1f, 1f),
    )

    /** A piece of confetti from above the screen, somewhere over screen x [x0]..[x1]. */
    fun confetti(x0: Float, x1: Float, top: Float) {
        val p = obtain()
        p.kind = Kind.CONFETTI
        p.alive = true
        p.screen = true
        p.x = MathUtils.random(x0, x1)
        p.y = top + MathUtils.random(0f, 60f)
        p.vx = MathUtils.random(-14f, 14f)
        p.vy = MathUtils.random(-38f, -22f)
        p.life = 0f
        p.maxLife = MathUtils.random(3f, 5f)
        p.color.set(confettiColors[MathUtils.random(confettiColors.size - 1)])
        p.size0 = MathUtils.random(0f, 6.28f)          // flutter phase
        p.front = true
    }

    /** A firework: climbs from screen (x, y) and bursts near the top of its flight. */
    fun rocket(x: Float, y: Float) {
        val p = obtain()
        p.kind = Kind.ROCKET
        p.alive = true
        p.screen = true
        p.x = x
        p.y = y
        p.vx = MathUtils.random(-12f, 12f)
        p.vy = MathUtils.random(120f, 160f)
        p.life = 0f
        p.maxLife = MathUtils.random(0.7f, 1f)
        p.color.set(confettiColors[MathUtils.random(confettiColors.size - 1)])
        p.front = false
    }

    private fun burst(x: Float, y: Float, color: Color) {
        val n = 48
        for (i in 0 until n) {
            val p = obtain()
            val a = i * 6.2832f / n + MathUtils.random(-0.1f, 0.1f)
            val sp = MathUtils.random(55f, 95f)
            p.kind = Kind.STAR
            p.alive = true
            p.screen = true
            p.x = x
            p.y = y
            p.vx = MathUtils.cos(a) * sp
            p.vy = MathUtils.sin(a) * sp
            p.life = 0f
            p.maxLife = MathUtils.random(0.9f, 1.4f)
            p.color.set(color)
            p.front = false
        }
    }

    private val bursts = ArrayList<P>()

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
                if (p.kind == Kind.ROCKET) bursts.add(p)
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
                Kind.SPARK -> {
                    p.vy -= 260f * dt
                    if (p.y < p.floor && p.vy < 0f) {
                        p.vy = -p.vy * 0.35f          // bounce off the asphalt
                        p.vx *= 0.6f
                    }
                }
                Kind.HAZE -> p.vy *= 1f - dt
                Kind.CONFETTI -> p.vx = MathUtils.sin(p.life * 5f + p.size0) * 18f
                Kind.ROCKET -> p.vy -= 60f * dt
                Kind.STAR -> {
                    p.vx *= 1f - dt * 1.8f
                    p.vy = p.vy * (1f - dt * 1.8f) - 28f * dt
                }
            }
            p.x += p.vx * dt
            p.y += p.vy * dt
        }
        for (r in bursts) burst(r.x, r.y, r.color)
        bursts.clear()
    }

    /** Smoke, rubber, sparks and purge, behind ([front] = false) or in front of the cars. */
    fun draw(batch: SpriteBatch, camX: Float, front: Boolean) {
        for (p in pool) {
            if (!p.alive || p.front != front || p.kind == Kind.HAZE) continue
            val t = p.life / p.maxLife
            val camX = if (p.screen) 0f else camX
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
                Kind.SPARK -> {
                    batch.setColor(p.color.r, p.color.g, p.color.b, 1f)
                    batch.draw(white, floor(p.x - camX), floor(p.y), 2f, 1f)
                }
                Kind.CONFETTI -> {
                    // a flat piece turning over: wide, then edge-on, then wide again
                    val wide = MathUtils.sin(p.life * 7f + p.size0) > 0f
                    batch.setColor(p.color.r, p.color.g, p.color.b, if (t > 0.85f) (1f - t) / 0.15f else 1f)
                    batch.draw(white, floor(p.x - camX), floor(p.y), if (wide) 3f else 1f, if (wide) 2f else 3f)
                }
                Kind.ROCKET -> {
                    batch.setColor(1f, 0.9f, 0.7f, 1f)
                    batch.draw(white, floor(p.x - camX), floor(p.y), 1f, 2f)
                }
                Kind.STAR -> {
                    // white-hot at first, then its colour; a short trail behind it
                    tmp.set(Color.WHITE).lerp(p.color, min1(t * 4f))
                    batch.setColor(tmp.r, tmp.g, tmp.b, 1f - t * t)
                    batch.draw(white, floor(p.x - camX), floor(p.y), 2f, 2f)
                    batch.setColor(p.color.r, p.color.g, p.color.b, (1f - t) * 0.5f)
                    batch.draw(white, floor(p.x - camX - p.vx * 0.05f), floor(p.y - p.vy * 0.05f), 1f, 1f)
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
            if (p.kind == Kind.STAR || p.kind == Kind.ROCKET) {
                val t = p.life / p.maxLife
                val camX = 0f
                batch.setColor(p.color.r, p.color.g, p.color.b, (1f - t) * 0.9f)
                batch.draw(white, floor(p.x - camX) - 1f, floor(p.y) - 1f, 4f, 4f)
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

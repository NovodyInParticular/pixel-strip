package io.github.novodyinparticular.pixelstrip.world

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.MathUtils
import io.github.novodyinparticular.pixelstrip.data.TrackDef
import io.github.novodyinparticular.pixelstrip.gfx.Art
import kotlin.math.floor
import kotlin.math.sin

/**
 * Draws a [TrackDef]: sky, stars, parallax layers (with animated crowds and blinking antenna
 * lights), props along the strip, light pools and the foreground. Everything is in framebuffer
 * pixels; camX is the world x (in pixels) at the left edge of the screen.
 */
class TrackRenderer(val def: TrackDef, private val art: Art) {
    var time = 0f
        private set

    /** World x of the finish line in pixels, set by the race. */
    var finishPx = 0f

    private val white = art.tex("ui/white.png")
    private val flashTex = art.tex("fx/flash.png")
    private val lightTex = art.tex("fx/light_soft.png")
    private val stars = List(def.stars) { Star(MathUtils.random(0f, 640f), MathUtils.random(0f, def.starsMaxTop), MathUtils.random(0f, 6.28f), MathUtils.random(0.6f, 2.4f), MathUtils.random()) }
    private val blinkers: Map<TrackDef.Layer, List<Pair<Float, Float>>> = def.layers.filter { it.blinkers != null }.associateWith { layer ->
        Gdx.files.internal(layer.blinkers!!).readString().lines().filter { it.isNotBlank() }.map {
            val (x, y) = it.trim().split(" ")
            x.toFloat() to y.toFloat()
        }
    }
    private val flashes = ArrayList<Flash>()
    private val crowdLayer = def.layers.firstOrNull { it.frames > 1 }

    private class Star(val x: Float, val top: Float, val phase: Float, val speed: Float, val bright: Float)
    private class Flash(val x: Float, val top: Float, var life: Float)
    /** Flashes queued by [volley], each waiting its turn. */
    private val queued = ArrayList<Pair<Float, Flash>>()

    /** [excitement] 0..1 makes the crowd jump and photographers fire more often. */
    fun update(dt: Float, excitement: Float) {
        time += dt
        flashes.forEach { it.life -= dt }
        flashes.removeAll { it.life <= 0f }
        for (i in queued.indices.reversed()) {
            val (at, f) = queued[i]
            if (time >= at) {
                flashes.add(f)
                queued.removeAt(i)
            }
        }
        val rate = 1.5f + excitement * 10f
        if (crowdLayer != null && MathUtils.random() < rate * dt) {
            flashes.add(Flash(MathUtils.random(0f, 640f), crowdLayer.top + MathUtils.random(4f, 26f), 0.07f))
        }
        this.excitement = excitement
    }

    private var excitement = 0f

    /** A burst of [n] camera flashes from the stands over the next second (the green, the finish). */
    fun volley(n: Int) {
        val l = crowdLayer ?: return
        repeat(n) {
            queued.add((time + MathUtils.random(0f, 0.9f)) to Flash(MathUtils.random(0f, 640f), l.top + MathUtils.random(4f, 26f), 0.07f))
        }
    }

    private fun bottomOf(top: Float, h: Int) = SCREEN_H - top - h

    private inline fun forEachTile(offset: Float, w: Int, screenW: Int, draw: (Float) -> Unit) {
        var x = -(offset - floor(offset / w) * w)
        while (x < screenW) {
            draw(floor(x))
            x += w
        }
    }

    private fun layerOffset(l: TrackDef.Layer, camX: Float) = camX * l.parallax + time * l.drift

    fun drawSky(batch: SpriteBatch, screenW: Int) {
        val sky = art.tex(def.sky)
        forEachTile(0f, sky.width, screenW) { x -> batch.draw(sky, x, bottomOf(0f, sky.height)) }
        for (s in stars) {
            val tw = 0.5f + 0.5f * sin(time * s.speed + s.phase)
            val a = (0.25f + 0.75f * tw) * (0.4f + 0.6f * s.bright)
            batch.setColor(1f, 0.92f + 0.08f * s.bright, 0.85f + 0.15f * tw, a)
            batch.draw(white, floor(s.x % screenW), floor(SCREEN_H - s.top - 1), 1f, 1f)
            if (s.bright > 0.93 && tw > 0.8f) {   // the brightest ones sparkle into a little cross
                batch.setColor(1f, 1f, 1f, a * 0.45f)
                batch.draw(white, floor(s.x % screenW) - 1, floor(SCREEN_H - s.top - 1), 3f, 1f)
                batch.draw(white, floor(s.x % screenW), floor(SCREEN_H - s.top - 2), 1f, 3f)
            }
        }
        batch.color = Color.WHITE
    }

    /** Layers from the back up to and including the road surface, then props on top of them. */
    fun drawWorld(batch: SpriteBatch, camX: Float, screenW: Int) {
        for (l in def.layers) {
            drawLayer(batch, l, camX, screenW)
            if (l.blinkers != null) drawBlinkers(batch, l, camX, screenW, 1f)
            if (l.frames > 1) drawFlashes(batch)
        }
        for (p in def.props) drawProp(batch, p, p.image, camX)
    }

    fun drawForeground(batch: SpriteBatch, camX: Float, screenW: Int) {
        def.foreground?.let { drawLayer(batch, it, camX, screenW) }
    }

    /** Emissive copies of the lit parts, into the bloom buffer. */
    fun drawGlow(batch: SpriteBatch, camX: Float, screenW: Int) {
        for (l in def.layers) {
            if (l.glow != null) drawLayer(batch, l, camX, screenW, l.glow)
            if (l.blinkers != null) drawBlinkers(batch, l, camX, screenW, 3f)
        }
        drawFlashes(batch)
        for (p in def.props) if (p.glow != null) drawProp(batch, p, p.glow, camX)
    }

    /** Additive light pools under the lamps, drawn over the road in the scene buffer. */
    fun drawLights(batch: SpriteBatch, camX: Float, screenW: Int) {
        val pool = def.lampPool ?: return
        val lampLayer = def.layers.firstOrNull { it.lamp != null } ?: return
        val tex = art.tex(lampLayer.image)
        val (lx, ly) = lampLayer.lamp!!
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE)
        forEachTile(layerOffset(lampLayer, camX), tex.width, screenW + 200) { x ->
            val cx = x + lx
            batch.setColor(pool.r, pool.g, pool.b, pool.a)
            batch.draw(lightTex, cx - 90f, bottomOf(def.nearLaneTop + 8f, 0), 180f, 52f)
            batch.setColor(pool.r, pool.g, pool.b, pool.a * 0.35f)       // faint beam under the lamp
            batch.draw(lightTex, cx - 26f, bottomOf(def.nearLaneTop, 0), 52f, SCREEN_H - lampLayer.top - ly - (SCREEN_H - def.nearLaneTop))
        }
        batch.color = Color.WHITE
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
    }

    /** Screen positions of the lamp heads this frame (for the lighting), appended to [out]. */
    fun lampPositions(camX: Float, screenW: Int, out: MutableList<Pair<Float, Float>>) {
        val lampLayer = def.layers.firstOrNull { it.lamp != null } ?: return
        val tex = art.tex(lampLayer.image)
        val (lx, ly) = lampLayer.lamp!!
        forEachTile(layerOffset(lampLayer, camX), tex.width, screenW + 200) { x ->
            out.add(x + lx to SCREEN_H - lampLayer.top - ly)
        }
    }

    private fun drawLayer(batch: SpriteBatch, l: TrackDef.Layer, camX: Float, screenW: Int, image: String = l.image) {
        val tex = art.tex(image)
        val frameH = tex.height / l.frames
        val y = bottomOf(l.top, frameH) - floor(time * l.sink)       // the sun sets as you race
        val offset = layerOffset(l, camX)
        if (!l.tile) {
            batch.draw(tex, floor(l.x - offset), y)
            return
        }
        if (l.frames > 1 && image == l.image) {
            // Animated crowd: each chunk runs its own phase so the stands never move in lockstep
            val chunk = if (l.chunk > 0) l.chunk else tex.width
            val speedUp = 1f + excitement * 1.6f
            forEachTile(offset, tex.width, screenW) { x ->
                var c = 0
                while (c * chunk < tex.width) {
                    val phase = ((c * 7919) % 13) / 13f
                    val f = ((time * speedUp / l.frameTime + phase * l.frames).toInt() + c) % l.frames
                    val frame = if (excitement < 0.3f && f == 2) 0 else f
                    batch.draw(tex, x + c * chunk, y, c * chunk, frame * frameH, chunk, frameH)
                    c++
                }
            }
            return
        }
        forEachTile(offset, tex.width, screenW) { x -> batch.draw(tex, x, y) }
    }

    private fun drawBlinkers(batch: SpriteBatch, l: TrackDef.Layer, camX: Float, screenW: Int, size: Float) {
        val tex = art.tex(l.image)
        val list = blinkers[l] ?: return
        val on = (time * 1.1f) % 1f < 0.45f
        if (!on) return
        forEachTile(layerOffset(l, camX), tex.width, screenW) { x ->
            for ((bx, by) in list) {
                batch.setColor(1f, 0.25f, 0.2f, 1f)
                val px = x + bx - (size - 1) / 2
                val py = bottomOf(l.top + by, 1) - (size - 1) / 2
                batch.draw(white, px, py, size, size)
            }
        }
        batch.color = Color.WHITE
    }

    private fun drawFlashes(batch: SpriteBatch) {
        for (f in flashes) batch.draw(flashTex, floor(f.x), bottomOf(f.top, flashTex.height))
    }

    private fun drawProp(batch: SpriteBatch, p: TrackDef.Prop, image: String, camX: Float) {
        val tex = art.tex(image)
        val worldX = (if (p.atFinish) finishPx else (p.at ?: 0f) * PX_PER_M) + p.dx
        val x = floor((worldX - camX) * p.parallax)
        if (x > -tex.width && x < 700) batch.draw(tex, x, bottomOf(p.top, tex.height))
    }

    companion object {
        const val SCREEN_H = 270f
        /** The car sprite is 96 px for about 5 m of car. */
        const val PX_PER_M = 19.2f
    }
}

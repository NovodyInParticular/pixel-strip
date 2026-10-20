package io.github.novodyinparticular.pixelstrip.race

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.MathUtils
import io.github.novodyinparticular.pixelstrip.gfx.PixelFont
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** Race HUD pieces: the LED tach, pop-up ratings and the timeslip. */
class Hud(private val font: PixelFont, private val white: Texture) {

    // --- Tach --------------------------------------------------------------------------------

    class Zone(val center: Float, val perfect: Float, val good: Float)

    private fun segAngle(i: Int) = MathUtils.degreesToRadians * (START_DEG - (START_DEG - END_DEG) * i / (SEGMENTS - 1f))

    private fun segColor(i: Int, rpm: Float, max: Float, redline: Float, zone: Zone?): Color? {
        val segRpm = max * i / (SEGMENTS - 1f)
        val lit = segRpm <= rpm
        val base = when {
            segRpm >= redline * 0.97f -> RED
            zone != null && kotlin.math.abs(segRpm - zone.center) <= zone.center * zone.perfect -> PERFECT_C
            zone != null && kotlin.math.abs(segRpm - zone.center) <= zone.center * zone.good -> GOOD_C
            else -> CYAN
        }
        return if (lit) base else Color(base).mul(0.22f, 0.22f, 0.3f, 1f)
    }

    /** (cx, cy) is the gauge centre. */
    fun drawTach(batch: SpriteBatch, cx: Float, cy: Float, rpm: Float, redline: Float, zone: Zone?, gear: String, speed: String, limiter: Boolean, time: Float) {
        val max = redline * 1.08f
        batch.setColor(0.03f, 0.02f, 0.08f, 0.82f)
        batch.draw(white, cx - RADIUS - 6f, cy - 18f, RADIUS * 2f + 12f, RADIUS + 26f)
        for (i in 0 until SEGMENTS) {
            val a = segAngle(i)
            val c = segColor(i, rpm, max, redline, zone)!!
            batch.color = c
            val r = if (i % 4 == 0) RADIUS + 1f else RADIUS
            batch.draw(white, floor(cx + cos(a) * r - 1f), floor(cy + sin(a) * r - 1f), 3f, 3f)
        }
        // needle, shaking on the limiter
        val shake = if (limiter) MathUtils.sin(time * 90f) * 0.06f else 0f
        val na = MathUtils.degreesToRadians * (START_DEG - (START_DEG - END_DEG) * (rpm / max).coerceIn(0f, 1f)) + shake
        batch.color = if (limiter) RED else Color.WHITE
        var d = 3f
        while (d < RADIUS - 4f) {
            batch.draw(white, floor(cx + cos(na) * d), floor(cy + sin(na) * d), 1f, 1f)
            d += 0.8f
        }
        batch.color = Color.WHITE
        batch.draw(white, cx - 1f, cy - 1f, 3f, 3f)
        font.drawCentered(batch, gear, cx, cy - 12f, Color.WHITE, 2)
        font.drawCentered(batch, speed, cx, cy + RADIUS - 24f, YELLOW)
    }

    fun drawTachGlow(batch: SpriteBatch, cx: Float, cy: Float, rpm: Float, redline: Float, zone: Zone?) {
        val max = redline * 1.08f
        for (i in 0 until SEGMENTS) {
            if (max * i / (SEGMENTS - 1f) > rpm) continue
            val a = segAngle(i)
            batch.color = segColor(i, rpm, max, redline, zone)!!
            batch.draw(white, floor(cx + cos(a) * RADIUS - 2f), floor(cy + sin(a) * RADIUS - 2f), 5f, 5f)
        }
        batch.color = Color.WHITE
    }

    // --- Pop-up ratings ---------------------------------------------------------------------

    private class Pop(val text: String, val color: Color, var life: Float, val y: Float)
    private val pops = ArrayList<Pop>()

    fun pop(text: String, color: Color, y: Float = 150f) {
        // A new pop takes the base line; the ones still showing step up out of its way
        val shifted = pops.map { Pop(it.text, it.color, it.life, it.y + 18f) }
        pops.clear()
        pops.addAll(shifted)
        pops.add(Pop(text, color, POP_LIFE, y))
    }

    fun drawPops(batch: SpriteBatch, w: Int, dt: Float) {
        val it = pops.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.life -= dt
            if (p.life <= 0f) {
                it.remove()
                continue
            }
            val t = 1f - p.life / POP_LIFE
            val scale = if (t < 0.12f) 3 else 2                     // punch in, then settle
            val rise = floor(t * 14f)
            val c = Color(p.color)
            c.a = if (t > 0.75f) (1f - t) / 0.25f else 1f
            font.drawCentered(batch, p.text, w / 2f, p.y + rise, c, scale, Color(0.04f, 0.03f, 0.09f, c.a))
        }
    }

    // --- Timeslip ----------------------------------------------------------------------------

    class SlipLine(val label: String, val you: String, val them: String)

    /** Vintage paper timeslip printed in from the top. [progress] 0..1 animates it. */
    fun drawTimeslip(batch: SpriteBatch, w: Int, h: Int, title: String, lines: List<SlipLine>, notes: List<String>, stamp: String, stampColor: Color, progress: Float, them: String = "GHOST") {
        val pw = 180f
        val ph = 30f + lines.size * 10f + notes.size * 9f + 34f
        val x = floor(w / 2f - pw / 2f)
        val top = h - 14f
        val shown = ph * progress.coerceIn(0f, 1f)
        val y = floor(top - shown)
        // shadow, paper, torn bottom edge
        batch.setColor(0f, 0f, 0f, 0.45f)
        batch.draw(white, x + 3f, y - 3f, pw, shown)
        batch.color = PAPER
        batch.draw(white, x, y, pw, shown)
        var tx = 0f
        while (tx < pw) {
            val tooth = if (((tx / 3f).toInt() % 2) == 0) 2f else 0f
            batch.color = PAPER
            batch.draw(white, x + tx, y - tooth, 3f, tooth)
            tx += 3f
        }
        batch.color = PAPER_SHADE
        batch.draw(white, x, y + shown - 2f, pw, 2f)
        batch.color = Color.WHITE
        // content scrolls out of the "printer" with the paper
        val baseY = y + shown - ph
        var ly = baseY + ph - 14f
        font.drawCentered(batch, title, x + pw / 2f, ly, INK, 1, null)
        ly -= 9f
        batch.color = INK_FAINT
        batch.draw(white, x + 8f, ly + 3f, pw - 16f, 1f)
        batch.color = Color.WHITE
        ly -= 9f
        font.draw(batch, "YOU", x + 86f, ly, INK, 1, null)
        font.draw(batch, them, x + 130f, ly, INK, 1, null)
        for (l in lines) {
            ly -= 10f
            if (ly < y + 2f) break
            font.draw(batch, l.label, x + 8f, ly, INK, 1, null)
            font.draw(batch, l.you, x + 86f, ly, INK, 1, null)
            font.draw(batch, l.them, x + 130f, ly, INK_FAINT, 1, null)
        }
        ly -= 4f
        for (n in notes) {
            ly -= 9f
            if (ly < y + 2f) break
            font.draw(batch, n, x + 8f, ly, INK, 1, null)
        }
        ly -= 22f
        if (ly > y) {
            batch.color = stampColor
            val sw = font.width(stamp, 2) + 10f
            val sx = floor(x + pw / 2f - sw / 2f)
            batch.draw(white, sx, ly - 3f, sw, 1f)
            batch.draw(white, sx, ly + 15f, sw, 1f)
            batch.draw(white, sx, ly - 3f, 1f, 19f)
            batch.draw(white, sx + sw - 1f, ly - 3f, 1f, 19f)
            batch.color = Color.WHITE
            font.drawCentered(batch, stamp, x + pw / 2f, ly, stampColor, 2, null)
        }
    }

    companion object {
        const val SEGMENTS = 30
        const val RADIUS = 30f
        const val START_DEG = 205f
        const val END_DEG = -25f
        const val POP_LIFE = 1.1f
        val CYAN = Color(0.21f, 0.78f, 0.75f, 1f)
        val GOOD_C = Color(0.4f, 0.9f, 0.35f, 1f)
        val PERFECT_C = Color(1f, 0.62f, 0.85f, 1f)
        val RED = Color(0.95f, 0.26f, 0.22f, 1f)
        val YELLOW = Color(1f, 0.91f, 0.56f, 1f)
        val AMBER = Color(1f, 0.66f, 0.2f, 1f)
        val PAPER = Color(0.95f, 0.92f, 0.82f, 1f)
        val PAPER_SHADE = Color(0.8f, 0.76f, 0.66f, 1f)
        val INK = Color(0.12f, 0.1f, 0.22f, 1f)
        val INK_FAINT = Color(0.36f, 0.34f, 0.5f, 1f)
    }
}

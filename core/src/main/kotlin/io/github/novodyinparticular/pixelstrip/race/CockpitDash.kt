package io.github.novodyinparticular.pixelstrip.race

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.utils.JsonReader
import com.badlogic.gdx.utils.JsonValue
import io.github.novodyinparticular.pixelstrip.data.CarDef
import io.github.novodyinparticular.pixelstrip.gfx.Art
import io.github.novodyinparticular.pixelstrip.gfx.PixelFont
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * The cockpit around the first-person view: the car's hood in its paint, the dashboard of its era
 * (tools/art/cockpit.py) with live gauges, the steering wheel, the shifter, the driver's hands, the
 * pillars and the rearview mirror. The parts show: aftermarket gauges, a nitrous bottle and its arm
 * switch, a race wheel and gloves, a cage.
 *
 * [parts] are the fitted parts (slot -> option) the cockpit cares about.
 */
class CockpitDash(
    private val art: Art, private val white: Texture, private val font: PixelFont, private val def: CarDef,
    private val parts: Map<String, Int>, private val paint: Color,
) {
    private val meta: JsonValue = JsonReader().parse(Gdx.files.internal("data/cockpits.json"))
    private val pixel = TextureRegion(white)

    val style: String = def.cockpit ?: when {
        def.era == "classic" -> "classic"
        def.era == "80s" -> "80s_analog"
        else -> "90s"
    }
    private val exotic = style.startsWith("exotic")
    private val raceInterior = (parts["interior"] ?: 0) >= 2 || (parts["cage"] ?: 0) >= 2
    private val wheelName = when {
        raceInterior -> "race_suede"
        style == "classic" -> "classic_thin"
        style.startsWith("80s") -> "80s_four"
        exotic -> "exotic_small"
        else -> "90s_three"
    }
    private val shifterKind = when {
        (parts["gearbox"] ?: 0) >= 5 -> "sequential"
        exotic -> "gated"
        style == "classic" -> "tall"
        else -> "short"
    }
    private val styleMeta = meta.get("styles").get(style)
    private val dash = art.tex("cockpit/dash_$style.png")
    private val frame = art.tex("cockpit/frame.png")
    private val wheel = art.tex("cockpit/wheel_$wheelName.png")
    private val shifter = art.tex("cockpit/shifter_$shifterKind.png")
    private val handsTex = art.tex(if (raceInterior) "cockpit/hands_gloved.png" else "cockpit/hands.png")
    private val hands = Array(4) { TextureRegion(handsTex, it * 32, 0, 32, 32) }
    private val turbo = def.stockTurbo || (parts["turbo"] ?: 0) > 0
    private val podGauges = style == "90s" || turbo || (parts["ecu"] ?: 0) >= 2
    private val nitrousKit = (parts["nitrous"] ?: 0) > 0
    private val cage = parts["cage"] ?: 0
    /** Red zone of the tach art (as a share of the dial); the dial is scaled so the car's own redline lands there. */
    private val redFrom = if (style == "90s" || style == "exotic_bare") 0.8f else 0.85f
    private val tachMax = def.redline / redFrom

    /** Shift hand: seconds left with the right hand on the knob; switch hand: on the nitrous switch. */
    private var onKnob = 0f
    private var onSwitch = 0f
    fun shifted() {
        onKnob = SHIFT_HAND_S
    }
    fun sprayed() {
        onSwitch = SWITCH_HAND_S
    }

    /** How far ahead of the eyes the nose is, and the hood's height there (m): it sets where the hood ends on screen. */
    private val hoodAheadM = max(0.9f, (def.sprite.frontX - (def.sprite.driver?.first ?: def.sprite.frontX * 0.4f)) / (def.sprite.scale * 18.2f) - 0.6f)

    /** Pixels from the bottom where the dash's top edge sits. */
    fun dashTop() = DASH_TOP

    fun draw(batch: SpriteBatch, w: Int, h: Int, rpm: Float, mph: Float, gear: Int, limiter: Boolean, boost: Float, nitrous: Boolean,
             shiftPoint: Float, dt: Float, time: Float, shakeX: Float, shakeY: Float, horizon: Float, focal: Float) {
        onKnob = max(0f, onKnob - dt)
        onSwitch = max(0f, onSwitch - dt)
        val ox = floor((w - DASH_W) / 2f + shakeX)
        val oy = floor(shakeY)

        drawHood(batch, w, horizon + shakeY, focal, ox)

        // the dash and its gauges
        batch.color = Color.WHITE
        batch.draw(dash, ox, oy)
        fun at(ix: Float, iy: Float) = Pair(ox + ix, oy + DASH_H - iy)
        styleMeta.get("tach")?.takeIf { it.isArray }?.let { g ->
            val (x, y) = at(g.getFloat(0), g.getFloat(1))
            needle(batch, x, y, g.getFloat(2) - 3f, min(1.02f, rpm / tachMax), if (limiter) LIMIT_RED else NEEDLE)
        }
        styleMeta.get("speedo")?.takeIf { it.isArray }?.let { g ->
            val (x, y) = at(g.getFloat(0), g.getFloat(1))
            needle(batch, x, y, g.getFloat(2) - 3f, min(1f, mph / SPEEDO_MAX), NEEDLE)
        }
        styleMeta.get("lcd")?.takeIf { it.isArray }?.let { r -> drawLcd(batch, ox, oy, r, rpm, mph, gear) }
        // the small gauges: oil, water, fuel steady near the middle
        styleMeta.get("small")?.forEach { g ->
            val (x, y) = at(g.getFloat(0), g.getFloat(1))
            needle(batch, x, y, g.getFloat(2) - 2f, 0.45f + 0.03f * (g.getFloat(0) % 3f), NEEDLE)
        }
        // VTEC: the lamp lights over the crossover
        if (def.vtecRpm > 0f) {
            val v = styleMeta.get("vtec")?.takeIf { it.isArray }
            val (x, y) = if (v != null) at(v.getFloat(0), v.getFloat(1)) else at(DASH_W / 2f + 70f, 66f)
            font.draw(batch, "VTEC", x, y, if (rpm > def.vtecRpm) VTEC_ON else VTEC_OFF, 1, null)
        }
        // the shift light up on the binnacle: blinks at the shift point
        if (style == "90s" || style == "80s_digital") {
            val sl = styleMeta.get("shiftLight")
            val (x, y) = at(sl.getFloat(0), sl.getFloat(1))
            val on = rpm >= shiftPoint * 0.97f && (time * 12f).toInt() % 2 == 0
            batch.color = if (on) SHIFT_ON else SHIFT_OFF
            batch.draw(white, x - 4f, y - 2f, 8f, 3f)
        }
        // A-pillar pod: boost and air/fuel
        if (podGauges) drawPod(batch, ox, oy, boost, rpm)
        if (nitrousKit) drawNitrous(batch, ox, oy, nitrous, time)

        // shifter and wheel, then the hands on them
        val sx = ox + DASH_W / 2f + SHIFTER_DX
        batch.color = Color.WHITE
        batch.draw(shifter, sx, oy - 6f)
        val wr = meta.get("wheels").get(wheelName).getFloat("r")
        val wx = ox + DASH_W / 2f - wheel.width / 2f
        val wy = oy + WHEEL_CY - wheel.height / 2f
        batch.draw(wheel, wx, wy)
        val cxw = ox + DASH_W / 2f
        val cyw = oy + WHEEL_CY
        // left hand at ten o'clock, the right at two unless it's on the knob or the switch
        batch.draw(hands[0], cxw - wr * 0.72f - 16f, cyw + wr * 0.62f - 16f)
        val knob = meta.get("shifters").get(shifterKind).get("knob")
        when {
            onSwitch > 0f -> {
                val sw = styleMeta.get("switches")
                val (x, y) = at(sw.getFloat(0), sw.getFloat(1))
                batch.draw(hands[3], x - 16f, y - 10f)
            }
            onKnob > 0f -> batch.draw(hands[2], sx + knob.getFloat(0) - 16f, oy - 6f + knob.getFloat(1) - 14f)
            else -> batch.draw(hands[1], cxw + wr * 0.72f - 16f, cyw + wr * 0.62f - 16f)
        }
        if (cage > 0) drawCage(batch, w, h, oy)
        batch.draw(frame, ox, oy + h - frame.height)
        batch.color = Color.WHITE
    }

    /** The hood from the dash to its front edge, in the car's paint: lit along the middle, darker at the fenders. */
    private fun drawHood(batch: SpriteBatch, w: Int, hz: Float, f: Float, ox: Float) {
        val drop = HOOD_DROP_M
        val front = hz - drop * f / hoodAheadM           // the row where the hood ends
        val base = DASH_TOP - 4f
        if (front <= base) return
        for (row in base.toInt() until front.toInt()) {
            val t = (row - base) / (front - base)
            val half = w * (0.62f - 0.18f * t)
            val c = Color(paint).mul(0.55f + 0.3f * t, 0.55f + 0.3f * t, 0.62f + 0.3f * t, 1f)
            batch.color = c
            batch.draw(white, w / 2f - half, row.toFloat(), half * 2f, 1f)
            batch.color = Color(paint).lerp(Color.WHITE, 0.25f)
            batch.draw(white, w / 2f - half * 0.18f, row.toFloat(), half * 0.36f, 1f)   // the sky's streak down the middle
        }
        if (def.sprite.popup) {
            // pop-up lamp pods at the front corners
            batch.color = Color(paint).mul(0.7f, 0.7f, 0.78f, 1f)
            val half = w * 0.44f
            batch.draw(white, w / 2f - half, front - 2f, 22f, 4f)
            batch.draw(white, w / 2f + half - 22f, front - 2f, 22f, 4f)
        }
        batch.color = Color.WHITE
    }

    private fun needle(batch: SpriteBatch, x: Float, y: Float, len: Float, t: Float, c: Color) {
        val deg = 225f - 270f * t.coerceIn(0f, 1.02f)
        batch.color = c
        batch.draw(pixel, x, y - 0.5f, 0f, 0.5f, len, 1f, 1f, 1f, deg)
        batch.color = NEEDLE_HUB
        batch.draw(white, x - 1f, y - 1f, 2f, 2f)
        batch.color = Color.WHITE
    }

    /** The 80s LCD: a bar tach across the top, the speed and gear in big segments below. */
    private fun drawLcd(batch: SpriteBatch, ox: Float, oy: Float, r: JsonValue, rpm: Float, mph: Float, gear: Int) {
        val x0 = ox + r.getFloat(0)
        val x1 = ox + r.getFloat(2)
        val top = oy + DASH_H - r.getFloat(1)
        val bars = 24
        val bw = (x1 - x0) / bars
        val lit = (rpm / tachMax * bars).toInt()
        for (k in 0 until bars) {
            val red = k >= bars * redFrom
            batch.color = when {
                k >= lit -> LCD_OFF
                red -> LCD_RED
                k > bars * 0.6f -> LCD_AMBER
                else -> LCD_GREEN
            }
            batch.draw(white, x0 + k * bw + 0.5f, top - 4f - k * 0.3f, bw - 1f, 4f + k * 0.3f)
        }
        font.draw(batch, mph.toInt().toString().padStart(3), x0 + 4f, top - 22f, LCD_GREEN, 2, null)
        font.draw(batch, (gear + 1).toString(), x1 - 14f, top - 22f, LCD_AMBER, 2, null)
        batch.color = Color.WHITE
    }

    private fun drawPod(batch: SpriteBatch, ox: Float, oy: Float, boost: Float, rpm: Float) {
        val pod = styleMeta.get("pod")
        val px = ox + pod.getFloat(0)
        val py = oy + DASH_H - pod.getFloat(1) + 4f
        for ((k, v) in listOf(boost, 0.5f + 0.25f * kotlin.math.sin(rpm / 900f)).withIndex()) {
            val gx = px + k * 20f
            batch.color = POD_RIM
            batch.draw(white, gx - 8f, py - 8f, 16f, 16f)
            batch.color = POD_FACE
            batch.draw(white, gx - 7f, py - 7f, 14f, 14f)
            needle(batch, gx, py, 6f, v, if (k == 0) BOOST_NEEDLE else NEEDLE)
        }
        batch.color = Color.WHITE
    }

    private fun drawNitrous(batch: SpriteBatch, ox: Float, oy: Float, spraying: Boolean, time: Float) {
        // the arm switch on the console: a toggle under a red cover, lit when armed
        val sw = styleMeta.get("switches")
        val x = ox + sw.getFloat(0)
        val y = oy + DASH_H - sw.getFloat(1)
        batch.color = SWITCH_BASE
        batch.draw(white, x - 6f, y - 4f, 12f, 8f)
        batch.color = if (spraying || (time * 2f).toInt() % 2 == 0) NOS_LIT else NOS_DIM
        batch.draw(white, x - 2f, y - 1f, 4f, 3f)
        // the bottle strapped in on the passenger side
        batch.color = NOS_BOTTLE
        batch.draw(white, ox + DASH_W / 2f + 190f, oy + 6f, 12f, 40f)
        batch.color = NOS_BOTTLE.cpy().lerp(Color.WHITE, 0.4f)
        batch.draw(white, ox + DASH_W / 2f + 192f, oy + 8f, 2f, 34f)
        batch.color = Color.WHITE
    }

    private fun drawCage(batch: SpriteBatch, w: Int, h: Int, oy: Float) {
        batch.color = CAGE
        val t = if (cage >= 2) 5f else 4f
        // the main hoop's side bars up the pillars, and (full cage) a bar across the windshield's top
        for (k in 0 until h - DASH_TOP.toInt() - 18) {
            val y = oy + DASH_TOP + k
            val inset = 46f + k * 0.28f
            batch.draw(white, inset, y, t, 1f)
            batch.draw(white, w - inset - t, y, t, 1f)
        }
        if (cage >= 2) batch.draw(white, 0f, oy + h - 30f, w.toFloat(), t)
        batch.color = Color.WHITE
    }

    companion object {
        const val DASH_W = 640f
        const val DASH_H = 100f
        const val DASH_TOP = 78f
        const val WHEEL_CY = 28f
        const val SHIFTER_DX = 92f
        const val SPEEDO_MAX = 180f
        const val SHIFT_HAND_S = 0.32f
        const val SWITCH_HAND_S = 0.4f
        /** How far below the eyes the hood's front edge is (m). */
        const val HOOD_DROP_M = 0.36f

        val NEEDLE = Color(1f, 0.45f, 0.25f, 1f)
        val LIMIT_RED = Color(1f, 0.2f, 0.2f, 1f)
        val NEEDLE_HUB = Color(0.7f, 0.7f, 0.75f, 1f)
        val VTEC_ON = Color(1f, 0.6f, 0.15f, 1f)
        val VTEC_OFF = Color(0.22f, 0.18f, 0.16f, 1f)
        val SHIFT_ON = Color(1f, 0.25f, 0.2f, 1f)
        val SHIFT_OFF = Color(0.2f, 0.06f, 0.06f, 1f)
        val LCD_OFF = Color(0.1f, 0.16f, 0.14f, 1f)
        val LCD_GREEN = Color(0.35f, 1f, 0.6f, 1f)
        val LCD_AMBER = Color(1f, 0.75f, 0.2f, 1f)
        val LCD_RED = Color(1f, 0.25f, 0.2f, 1f)
        val POD_RIM = Color(0.42f, 0.42f, 0.5f, 1f)
        val POD_FACE = Color(0.05f, 0.05f, 0.07f, 1f)
        val BOOST_NEEDLE = Color(0.4f, 0.8f, 1f, 1f)
        val SWITCH_BASE = Color(0.15f, 0.14f, 0.18f, 1f)
        val NOS_LIT = Color(0.4f, 0.75f, 1f, 1f)
        val NOS_DIM = Color(0.12f, 0.2f, 0.32f, 1f)
        val NOS_BOTTLE = Color(0.2f, 0.4f, 0.85f, 1f)
        val CAGE = Color(0.28f, 0.28f, 0.34f, 1f)
    }
}

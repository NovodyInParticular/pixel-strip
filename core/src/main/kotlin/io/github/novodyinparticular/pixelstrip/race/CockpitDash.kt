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
 * (tools/art/cockpit.py) with live gauges, the steering wheel, the shifter, the pillars and the
 * rearview mirror. The driver sits on the left, or on the right in the Japanese cars ([CarDef.rhd]):
 * the wheel is ahead of the eyes, nudged toward the door on screen, and the console, the mirror and
 * the hood's middle are off toward the car's centre. The parts show: a nitrous bottle and its arm
 * switch, a race wheel with a race interior, a cage; and the interior mods from the shop's INTERIOR
 * tab ([interior], cosmetic id -> style): an aftermarket tach (monster pedestal, column pod, LED bar),
 * pillar gauges, a shift light, a shift knob, a steering wheel.
 *
 * The tach always marks the window that matters: the launch rpm while staged, each gear's shift
 * point while racing (green dead on, amber close).
 *
 * [parts] are the fitted parts (slot -> option) the cockpit cares about.
 */
class CockpitDash(
    private val art: Art, private val white: Texture, private val font: PixelFont, private val def: CarDef,
    private val parts: Map<String, Int>, private val paint: Color, private val interior: Map<String, String> = emptyMap(),
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
    private val wheelName = when (interior["steering"]) {
        "deep_dish" -> "deep_dish"
        "suede_race" -> "race_suede"
        "wood_classic" -> "classic_thin"
        "drag_small" -> "drag_small"
        else -> null
    } ?: when {
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
    /** +1 when the car's centre is to the driver's right (left-hand drive), -1 when it's to the left. */
    private val side = if (def.rhd) -1f else 1f
    private val styleMeta = meta.get(if (def.rhd) "stylesRhd" else "styles").get(style)
    private val dash = art.tex(if (def.rhd) "cockpit/dash_${style}_rhd.png" else "cockpit/dash_$style.png")
    /** Where the car's middle is across the dash art. */
    private val centreArt = styleMeta.getFloat("centre")
    private val frame = art.tex("cockpit/frame.png")
    private val wheel = art.tex("cockpit/wheel_$wheelName.png")
    private val shifter = art.tex("cockpit/shifter_$shifterKind.png")
    private val podCount = when (interior["gauges"]) {
        "boost" -> 1
        "boost_afr" -> 2
        "triple" -> 3
        else -> 0
    }
    private val tachKind = interior["tach"] ?: "stock"
    private val shiftLight = interior["shiftlight"] ?: "none"
    private val knob = interior["knob"] ?: "stock"
    /** The aftermarket tach's dial: a round ten thousand, or more for the screamers. */
    private val afterMax = max(10000f, kotlin.math.ceil(def.redline * 1.12f / 1000f) * 1000f)
    private val nitrousKit = (parts["nitrous"] ?: 0) > 0
    private val cage = parts["cage"] ?: 0
    /** Red zone of the tach art (as a share of the dial); the dial is scaled so the car's own redline lands there. */
    private val redFrom = if (style == "90s" || style == "exotic_bare") 0.8f else 0.85f
    private val tachMax = def.redline / redFrom

    /** Seconds left of the lever's jolt after a shift. */
    private var nudge = 0f
    fun shifted() {
        nudge = NUDGE_S
    }

    /** Screen x of the dash art's left edge: the wheel sits [WHEEL_SHIFT] toward the driver's door from the middle. */
    private fun originX(w: Int, shakeX: Float) = floor(w / 2f - side * WHEEL_SHIFT - DASH_W / 2f + shakeX)
    /** Screen x of the car's centre line. */
    private fun centreX(w: Int, shakeX: Float) = originX(w, shakeX) + centreArt

    /** How far ahead of the eyes the nose is, and the hood's height there (m): it sets where the hood ends on screen. */
    private val hoodAheadM = max(0.9f, (def.sprite.frontX - (def.sprite.driver?.first ?: def.sprite.frontX * 0.4f)) / (def.sprite.scale * 18.2f) - 0.6f)

    /** Pixels from the bottom where the dash's top edge sits. */
    fun dashTop() = DASH_TOP

    fun draw(batch: SpriteBatch, w: Int, h: Int, rpm: Float, mph: Float, gear: Int, limiter: Boolean, boost: Float, nitrous: Boolean,
             shiftPoint: Float, zone: Hud.Zone?, dt: Float, time: Float, shakeX: Float, shakeY: Float, horizon: Float, focal: Float) {
        nudge = max(0f, nudge - dt)
        val ox = originX(w, shakeX)
        val oy = floor(shakeY)

        drawHood(batch, w, horizon + shakeY, focal, centreX(w, shakeX))

        // the dash and its gauges
        batch.color = Color.WHITE
        batch.draw(dash, ox, oy)
        fun at(ix: Float, iy: Float) = Pair(ox + ix, oy + DASH_H - iy)
        styleMeta.get("tach")?.takeIf { it.isArray }?.let { g ->
            val (x, y) = at(g.getFloat(0), g.getFloat(1))
            if (def.engineLayout == "rotary") rotaryBand(batch, x, y, g.getFloat(2))
            zoneArc(batch, x, y, g.getFloat(2) - 5f, tachMax, zone)
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
        // A-pillar pod: boost, air/fuel, oil pressure
        if (podCount > 0) drawPod(batch, ox, oy, boost, rpm)
        if (nitrousKit) drawNitrous(batch, ox, oy, nitrous, time)

        // aftermarket tach and shift light on top of the column, behind the wheel's rim
        drawAfterTach(batch, ox, oy, rpm, zone, shiftPoint, limiter, time)
        drawShiftLight(batch, ox, oy, rpm, shiftPoint, time, glow = false)

        // the shifter by the console (it jolts back on a shift), its knob, then the wheel
        val sx = ox + DASH_W / 2f + side * SHIFTER_DX - shifter.width / 2f
        val sy = oy - 6f - if (nudge > 0f) 2f else 0f
        batch.color = Color.WHITE
        batch.draw(shifter, sx, sy)
        drawKnob(batch, sx, sy)
        batch.color = Color.WHITE
        batch.draw(wheel, ox + DASH_W / 2f - wheel.width / 2f, oy + WHEEL_CY - wheel.height / 2f)
        // VTEC: the lamp lights over the crossover (drawn over the rim, which would otherwise cross it)
        if (def.vtecRpm > 0f) {
            val v = styleMeta.get("vtec")
            val (x, y) = at(v.getFloat(0), v.getFloat(1))          // the cluster's top-right corner
            font.draw(batch, "VTEC", x, y, if (rpm > def.vtecRpm) VTEC_ON else VTEC_OFF, 1, null)
        }
        if (cage > 0) drawCage(batch, w, h, oy, centreX(w, shakeX))
        batch.draw(frame, floor(centreX(w, shakeX) - frame.width / 2f), oy + h - frame.height)
        batch.color = Color.WHITE
    }

    /** The hood from the dash to its front edge, in the car's paint: lit along the middle, darker at the fenders. */
    private fun drawHood(batch: SpriteBatch, w: Int, hz: Float, f: Float, cx: Float) {
        val drop = HOOD_DROP_M
        val front = hz - drop * f / hoodAheadM           // the row where the hood ends
        val base = DASH_TOP - 4f
        if (front <= base) return
        for (row in base.toInt() until front.toInt()) {
            val t = (row - base) / (front - base)
            val half = w * (0.62f - 0.18f * t)
            val c = Color(paint).mul(0.55f + 0.3f * t, 0.55f + 0.3f * t, 0.62f + 0.3f * t, 1f)
            batch.color = c
            batch.draw(white, cx - half, row.toFloat(), half * 2f, 1f)
            batch.color = Color(paint).lerp(Color.WHITE, 0.25f)
            batch.draw(white, cx - half * 0.18f, row.toFloat(), half * 0.36f, 1f)   // the sky's streak down the middle
        }
        if (def.sprite.popup) {
            // pop-up lamp pods at the front corners
            batch.color = Color(paint).mul(0.7f, 0.7f, 0.78f, 1f)
            val half = w * 0.44f
            batch.draw(white, cx - half, front - 2f, 22f, 4f)
            batch.draw(white, cx + half - 22f, front - 2f, 22f, 4f)
        }
        batch.color = Color.WHITE
    }

    /** A rotary's tach: an amber warm-up band climbing to the redline, the way the factory dials marked it. */
    private fun rotaryBand(batch: SpriteBatch, x: Float, y: Float, r: Float) {
        val from = (def.redline - 1500f) / tachMax
        batch.color = ROTARY_BAND
        var t = from
        while (t < redFrom) {
            val a = Math.toRadians((225f - 270f * t).toDouble())
            batch.draw(white, x + kotlin.math.cos(a).toFloat() * (r - 2f), y + kotlin.math.sin(a).toFloat() * (r - 2f), 1f, 1f)
            t += 0.01f
        }
        batch.color = Color.WHITE
    }

    /** The lit things, for the bloom: needles' tips, the LCD, the shift light, VTEC, the nitrous switch; nitrous washes the dash blue. */
    fun drawGlow(batch: SpriteBatch, w: Int, rpm: Float, nitrous: Boolean, shiftPoint: Float, time: Float, shakeX: Float, shakeY: Float) {
        val ox = originX(w, shakeX)
        val oy = floor(shakeY)
        fun at(ix: Float, iy: Float) = Pair(ox + ix, oy + DASH_H - iy)
        styleMeta.get("lcd")?.takeIf { it.isArray }?.let { r ->
            batch.setColor(0.3f, 1f, 0.55f, 0.35f)
            batch.draw(white, ox + r.getFloat(0), oy + DASH_H - r.getFloat(3), r.getFloat(2) - r.getFloat(0), r.getFloat(3) - r.getFloat(1))
        }
        if (def.vtecRpm > 0f && rpm > def.vtecRpm) {
            val v = styleMeta.get("vtec")
            val (x, y) = at(v.getFloat(0), v.getFloat(1))
            batch.setColor(1f, 0.55f, 0.1f, 0.8f)
            batch.draw(white, x - 2f, y - 2f, 26f, 11f)
        }
        drawShiftLight(batch, ox, oy, rpm, shiftPoint, time, glow = true)
        if (tachKind == "led_bar") drawLedBar(batch, ox, oy, rpm, shiftPoint, time, glow = true)
        if (nitrous) {
            val k = 0.25f + 0.1f * kotlin.math.sin(time * 18f)
            batch.setColor(0.3f, 0.6f, 1f, k)
            batch.draw(white, ox, oy, DASH_W, DASH_TOP + 4f)
        }
        batch.color = Color.WHITE
    }

    // --- rain on the glass, and the wipers clearing it -------------------------------------------
    private val drops = Array(70) { floatArrayOf(kotlin.random.Random.nextFloat(), kotlin.random.Random.nextFloat(), 0f) }   // x, y (0..1), age
    private var wiper = 0f

    /** Wet tracks: drops gather on the windshield and run, two wipers sweep them off. [mph] makes them streak up. */
    fun drawRain(batch: SpriteBatch, w: Int, h: Int, dt: Float, mph: Float) {
        wiper = (wiper + dt / WIPE_S) % 1f
        val swing = if (wiper < 0.5f) wiper * 2f else 2f - wiper * 2f          // 0 -> 1 -> 0
        val bladeDeg = 160f - 140f * swing
        val glassTop = h - 30f
        for (d in drops) {
            d[2] += dt
            val x = d[0] * w
            val y = DASH_TOP + d[1] * (glassTop - DASH_TOP)
            // under a blade's sweep: wiped
            for (px in listOf(w * 0.3f, w * 0.7f)) {
                val ang = Math.toDegrees(kotlin.math.atan2((y - DASH_TOP).toDouble(), (x - px).toDouble())).toFloat()
                if (kotlin.math.abs(ang - bladeDeg) < 6f && kotlin.math.hypot(x - px, y - DASH_TOP) < h * 0.6f) d[2] = 0f
            }
            val a = min(1f, d[2] / 1.2f)
            if (a <= 0.05f) continue
            batch.setColor(0.7f, 0.8f, 1f, 0.35f * a)
            val streak = 1f + mph / 40f
            batch.draw(white, x, y, 1f, streak)
            batch.setColor(0.9f, 0.95f, 1f, 0.5f * a)
            batch.draw(white, x, y + streak, 1f, 1f)
        }
        batch.color = WIPER
        for (px in listOf(w * 0.3f, w * 0.7f)) batch.draw(pixel, px, DASH_TOP, 0f, 1f, h * 0.55f, 2f, 1f, 1f, bladeDeg)
        batch.color = Color.WHITE
    }

    /** The rearview mirror: the strip behind, and the opponent's headlights when you're ahead. [gap] metres back. */
    fun drawMirror(batch: SpriteBatch, w: Int, h: Int, gap: Float?, paintThem: Color, shakeX: Float, shakeY: Float) {
        val m = meta.get("frame").get("mirror")
        val fx = floor(centreX(w, shakeX) - frame.width / 2f)
        val x = fx + m.getFloat(0)
        val y = floor(shakeY) + m.getFloat(1)
        val mw = m.getFloat(2)
        val mh = m.getFloat(3)
        batch.color = MIRROR_SKY
        batch.draw(white, x, y + mh * 0.45f, mw, mh * 0.55f)
        batch.color = MIRROR_ROAD
        batch.draw(white, x, y, mw, mh * 0.45f)
        if (gap != null && gap in 1f..120f) {
            val k = (6f / gap).coerceIn(0.08f, 1.2f)
            val cw = 26f * k
            val ch = 16f * k
            val cx = x + mw * 0.32f                       // in the other lane, on the left behind you
            batch.color = paintThem
            batch.draw(white, cx - cw / 2f, y + mh * 0.45f - ch * 0.2f, cw, ch)
            batch.color = HEADLIGHT
            batch.draw(white, cx - cw / 2f + 1f, y + mh * 0.45f + ch * 0.15f, max(1f, cw * 0.18f), max(1f, ch * 0.18f))
            batch.draw(white, cx + cw / 2f - 1f - max(1f, cw * 0.18f), y + mh * 0.45f + ch * 0.15f, max(1f, cw * 0.18f), max(1f, ch * 0.18f))
        }
        batch.color = Color.WHITE
    }

    /** Before a pink-slip race: the cockpit goes dark... */
    fun drawPinkDark(batch: SpriteBatch, w: Int, h: Int, k: Float) {
        batch.setColor(0f, 0f, 0.02f, 0.55f * k)
        batch.draw(white, 0f, 0f, w.toFloat(), h.toFloat())
        batch.color = Color.WHITE
    }

    /** ...and the track's spotlights sweep across the glass (bloom pass). */
    fun drawPinkSpots(batch: SpriteBatch, w: Int, h: Int, time: Float, k: Float) {
        for (i in 0..1) {
            val sweep = kotlin.math.sin(time * 0.7f + i * 2.2f)
            val x = w * (0.5f + 0.35f * sweep)
            batch.setColor(1f, 0.95f, 0.85f, 0.22f * k)
            for (r in 0 until 6) batch.draw(white, x - 30f + r * 6f - (h - 40f) * 0.15f * sweep, 40f, 12f, h - 60f)
            batch.setColor(1f, 1f, 0.95f, 0.5f * k)
            batch.draw(white, x - 10f, h - 40f, 20f, 14f)
        }
        batch.color = Color.WHITE
    }

    /** The window that matters on a dial of [max] rpm: amber close to it, green dead on. */
    private fun zoneArc(batch: SpriteBatch, x: Float, y: Float, r: Float, max: Float, zone: Hud.Zone?) {
        zone ?: return
        var rr = zone.center * (1f - zone.good)
        val step = max / 400f
        while (rr <= zone.center * (1f + zone.good)) {
            val perfect = kotlin.math.abs(rr - zone.center) <= zone.center * zone.perfect
            batch.color = if (perfect) ZONE_PERFECT else ZONE_GOOD
            val a = Math.toRadians((225f - 270f * (rr / max)).toDouble())
            val cx = x + kotlin.math.cos(a).toFloat() * r
            val cy = y + kotlin.math.sin(a).toFloat() * r
            batch.draw(white, cx - 1f, cy - 1f, 2f, 2f)
            rr += step
        }
        batch.color = Color.WHITE
    }

    /** Screen spot of a point on the dash art, [iy] down from its top (negative: up above the dash, on the column). */
    private fun spot(ox: Float, oy: Float, ix: Float, iy: Float) = Pair(ox + ix, oy + DASH_H - iy)

    /** The aftermarket tach: a monster on a pedestal over the column, a small column pod, or an LED bar on the binnacle. */
    private fun drawAfterTach(batch: SpriteBatch, ox: Float, oy: Float, rpm: Float, zone: Hud.Zone?, shiftPoint: Float, limiter: Boolean, time: Float) {
        when (tachKind) {
            "monster_5in", "column_pod" -> {
                val big = tachKind == "monster_5in"
                val r = if (big) 17f else 10f
                val (x, y) = spot(ox, oy, DASH_W / 2f + (if (big) 0f else side * 36f), if (big) -12f else 6f)
                batch.color = PEDESTAL
                batch.draw(white, x - 3f, y - r - 6f, 6f, 8f)
                disc(batch, x, y, r + 2f, BEZEL_LIT, BEZEL_DARK)
                disc(batch, x, y, r, if (big) TACH_FACE_WHITE else TACH_FACE_BLACK, if (big) TACH_FACE_WHITE else TACH_FACE_BLACK)
                // ticks every thousand, the redline on
                val tick = if (big) TACH_TICK_DARK else TACH_TICK_LIGHT
                var k = 0f
                while (k <= afterMax) {
                    val red = k >= def.redline
                    val a = Math.toRadians((225f - 270f * (k / afterMax)).toDouble())
                    batch.color = if (red) RED_TICK else tick
                    batch.draw(white, x + kotlin.math.cos(a).toFloat() * (r - 2f) - 0.5f, y + kotlin.math.sin(a).toFloat() * (r - 2f) - 0.5f, 1f, 1f)
                    k += 1000f
                }
                zoneArc(batch, x, y, r - 4f, afterMax, zone)
                needle(batch, x, y, r - 2f, min(1.02f, rpm / afterMax), if (limiter) LIMIT_RED else NEEDLE)
                if (big && shiftLight == "none") {
                    // the monster's own shift light on top
                    val on = rpm >= shiftPoint * 0.97f && (time * 12f).toInt() % 2 == 0
                    batch.color = if (on) SHIFT_ON else SHIFT_OFF
                    batch.draw(white, x - 3f, y + r + 2f, 6f, 4f)
                }
            }
            "led_bar" -> drawLedBar(batch, ox, oy, rpm, shiftPoint, time, glow = false)
        }
        batch.color = Color.WHITE
    }

    /** Sixteen LEDs across the top of the binnacle: green, yellow, then red to the shift point, all flashing past it. */
    private fun drawLedBar(batch: SpriteBatch, ox: Float, oy: Float, rpm: Float, shiftPoint: Float, time: Float, glow: Boolean) {
        val (x0, y) = spot(ox, oy, DASH_W / 2f - 40f, -2f)
        val n = 16
        val from = shiftPoint * 0.45f
        val lit = (((rpm - from) / (shiftPoint - from)) * n).toInt().coerceIn(0, n)
        val flash = rpm >= shiftPoint && (time * 14f).toInt() % 2 == 0
        if (!glow) {
            batch.color = LED_HOUSING
            batch.draw(white, x0 - 2f, y - 2f, n * 5f + 3f, 7f)
        }
        for (i in 0 until n) {
            val on = i < lit && (rpm < shiftPoint || flash)
            val c = when {
                rpm >= shiftPoint -> SHIFT_BLUE
                i >= n - 3 -> SHIFT_ON
                i >= n - 7 -> LED_YELLOW
                else -> LED_GREEN
            }
            if (glow && !on) continue
            batch.color = if (on) c else LED_OFF
            batch.draw(white, x0 + i * 5f, y, 4f, 3f)
        }
        batch.color = Color.WHITE
    }

    /** The bought shift light: a single LED on the binnacle, a sequential strip over it, or a big pro light on the dash. */
    private fun drawShiftLight(batch: SpriteBatch, ox: Float, oy: Float, rpm: Float, shiftPoint: Float, time: Float, glow: Boolean) {
        val blink = rpm >= shiftPoint * 0.97f && (time * 12f).toInt() % 2 == 0
        when (shiftLight) {
            "dash_led" -> {
                val sl = styleMeta.get("shiftLight")
                val (x, y) = spot(ox, oy, sl.getFloat(0), sl.getFloat(1))
                if (glow) {
                    if (!blink) return
                    batch.setColor(1f, 0.2f, 0.15f, 1f)
                    batch.draw(white, x - 8f, y - 5f, 16f, 9f)
                } else {
                    batch.color = if (blink) SHIFT_ON else SHIFT_OFF
                    batch.draw(white, x - 4f, y - 2f, 8f, 3f)
                }
            }
            "led_strip" -> {
                val (x0, y) = spot(ox, oy, DASH_W / 2f - 18f, if (tachKind == "led_bar") -10f else -4f)
                val steps = 8
                for (i in 0 until steps) {
                    val at = shiftPoint * (0.86f + 0.02f * i)
                    val on = rpm >= at && (rpm < shiftPoint || blink)
                    val c = if (i < 3) LED_GREEN else if (i < 6) LED_YELLOW else SHIFT_ON
                    if (glow && !on) continue
                    if (!glow) {
                        batch.color = LED_HOUSING
                        batch.draw(white, x0 + i * 5f - 1f, y - 1f, 5f, 5f)
                    }
                    batch.color = if (on) c else LED_OFF
                    batch.draw(white, x0 + i * 5f, y, 3f, 3f)
                }
            }
            "pro_light" -> {
                val (x, y) = spot(ox, oy, DASH_W / 2f + side * 52f, -8f)
                if (glow) {
                    if (!blink) return
                    batch.setColor(1f, 0.2f, 0.15f, 1f)
                    batch.draw(white, x - 10f, y - 10f, 20f, 20f)
                } else {
                    batch.color = PEDESTAL
                    batch.draw(white, x - 2f, y - 12f, 4f, 6f)
                    disc(batch, x, y, 7f, BEZEL_LIT, BEZEL_DARK)
                    disc(batch, x, y, 5f, if (blink) SHIFT_ON else SHIFT_OFF, if (blink) SHIFT_ON else SHIFT_OFF)
                }
            }
        }
        batch.color = Color.WHITE
    }

    /** The knob on the lever, over the shifter art's own: a cue ball, billet aluminium, burnt titanium, a drag T-handle. */
    private fun drawKnob(batch: SpriteBatch, sx: Float, sy: Float) {
        if (knob == "stock") return
        val k = meta.get("shifters").get(shifterKind).get("knob")
        val x = sx + k.getFloat(0)
        val y = sy + k.getFloat(1)
        when (knob) {
            "cue_ball" -> {
                disc(batch, x, y, 7f, CUE_LIT, CUE_DARK)
                batch.color = CUE_SPOT
                batch.draw(white, x - 2f, y, 4f, 4f)
            }
            "billet" -> {
                for (r in 0 until 12) {
                    batch.color = if (r % 4 == 0) BILLET_DARK else if (r > 7) BILLET_LIT else BILLET
                    batch.draw(white, x - 5f, y - 6f + r, 10f, 1f)
                }
            }
            "titanium" -> {
                for (r in -6..6) {
                    val half = kotlin.math.sqrt((36f - r * r).coerceAtLeast(0f))
                    batch.color = TITANIUM[((r + 6) * TITANIUM.size / 13).coerceIn(0, TITANIUM.size - 1)]
                    batch.draw(white, x - half, y + r, half * 2f, 1f)
                }
            }
            "drag_tee" -> {
                batch.color = BILLET
                batch.draw(white, x - 1f, y - 6f, 3f, 10f)
                batch.color = BILLET_LIT
                batch.draw(white, x - 9f, y + 3f, 19f, 4f)
                batch.color = BILLET_DARK
                batch.draw(white, x - 9f, y + 3f, 19f, 1f)
            }
        }
        batch.color = Color.WHITE
    }

    /** A filled disc, lit from the top-left ([lit]) to [dark]. */
    private fun disc(batch: SpriteBatch, x: Float, y: Float, r: Float, lit: Color, dark: Color) {
        val n = r.toInt()
        for (dy in -n..n) {
            val half = kotlin.math.sqrt((r * r - dy * dy).coerceAtLeast(0f))
            batch.color = if (dy > 0) lit else dark
            batch.draw(white, floor(x - half), y + dy, kotlin.math.ceil(half * 2f), 1f)
        }
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
        val readings = listOf(boost, 0.5f + 0.25f * kotlin.math.sin(rpm / 900f), 0.3f + 0.4f * (rpm / def.redline).coerceIn(0f, 1f))
        for ((k, v) in readings.take(podCount).withIndex()) {
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
        val bx = ox + DASH_W / 2f + side * BOTTLE_DX - 6f
        batch.draw(white, bx, oy + 6f, 12f, 40f)
        batch.color = NOS_BOTTLE.cpy().lerp(Color.WHITE, 0.4f)
        batch.draw(white, bx + 2f, oy + 8f, 2f, 34f)
        batch.color = Color.WHITE
    }

    private fun drawCage(batch: SpriteBatch, w: Int, h: Int, oy: Float, cx: Float) {
        batch.color = CAGE
        val t = if (cage >= 2) 5f else 4f
        // the main hoop's side bars up the pillars, and (full cage) a bar across the windshield's top
        for (k in 0 until h - DASH_TOP.toInt() - 18) {
            val y = oy + DASH_TOP + k
            val half = CAGE_HALF - k * 0.28f                 // the hoop's sides, either side of the car's centre
            batch.draw(white, cx - half, y, t, 1f)
            batch.draw(white, cx + half - t, y, t, 1f)
        }
        if (cage >= 2) batch.draw(white, 0f, oy + h - 30f, w.toFloat(), t)
        batch.color = Color.WHITE
    }

    companion object {
        const val DASH_W = 960f
        const val DASH_H = 100f
        const val DASH_TOP = 78f
        const val WHEEL_CY = 28f
        const val SHIFTER_DX = 128f
        const val BOTTLE_DX = 236f
        /** How far toward the door the wheel sits from the screen's middle. */
        const val WHEEL_SHIFT = 60f
        /** Half the main hoop's width at the dash, either side of the car's centre. */
        const val CAGE_HALF = 300f
        const val SPEEDO_MAX = 180f
        const val NUDGE_S = 0.12f
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
        val ROTARY_BAND = Color(1f, 0.6f, 0.15f, 1f)
        val ZONE_GOOD = Color(1f, 0.72f, 0.2f, 1f)
        val ZONE_PERFECT = Color(0.3f, 1f, 0.45f, 1f)
        val PEDESTAL = Color(0.12f, 0.12f, 0.15f, 1f)
        val BEZEL_LIT = Color(0.78f, 0.78f, 0.84f, 1f)
        val BEZEL_DARK = Color(0.42f, 0.42f, 0.5f, 1f)
        val TACH_FACE_WHITE = Color(0.92f, 0.92f, 0.9f, 1f)
        val TACH_FACE_BLACK = Color(0.05f, 0.05f, 0.07f, 1f)
        val TACH_TICK_DARK = Color(0.1f, 0.1f, 0.12f, 1f)
        val TACH_TICK_LIGHT = Color(0.85f, 0.85f, 0.9f, 1f)
        val RED_TICK = Color(0.9f, 0.15f, 0.12f, 1f)
        val LED_HOUSING = Color(0.06f, 0.06f, 0.08f, 1f)
        val LED_OFF = Color(0.14f, 0.13f, 0.16f, 1f)
        val LED_GREEN = Color(0.3f, 1f, 0.4f, 1f)
        val LED_YELLOW = Color(1f, 0.85f, 0.2f, 1f)
        val SHIFT_BLUE = Color(0.35f, 0.6f, 1f, 1f)
        val CUE_LIT = Color(0.96f, 0.95f, 0.9f, 1f)
        val CUE_DARK = Color(0.76f, 0.75f, 0.7f, 1f)
        val CUE_SPOT = Color(0.08f, 0.08f, 0.1f, 1f)
        val BILLET = Color(0.7f, 0.72f, 0.78f, 1f)
        val BILLET_LIT = Color(0.9f, 0.92f, 0.96f, 1f)
        val BILLET_DARK = Color(0.45f, 0.46f, 0.52f, 1f)
        val TITANIUM = listOf(Color(0.3f, 0.25f, 0.6f, 1f), Color(0.4f, 0.35f, 0.8f, 1f), Color(0.55f, 0.4f, 0.75f, 1f),
            Color(0.8f, 0.55f, 0.5f, 1f), Color(0.9f, 0.75f, 0.45f, 1f))
        val WIPER = Color(0.06f, 0.06f, 0.08f, 1f)
        val MIRROR_SKY = Color(0.12f, 0.1f, 0.2f, 1f)
        val MIRROR_ROAD = Color(0.18f, 0.16f, 0.24f, 1f)
        val HEADLIGHT = Color(1f, 0.95f, 0.75f, 1f)
        const val WIPE_S = 1.3f
    }
}

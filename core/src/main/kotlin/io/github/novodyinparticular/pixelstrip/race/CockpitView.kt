package io.github.novodyinparticular.pixelstrip.race

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import io.github.novodyinparticular.pixelstrip.data.TrackDef
import io.github.novodyinparticular.pixelstrip.gfx.Art
import io.github.novodyinparticular.pixelstrip.world.TrackRenderer
import kotlin.math.floor
import kotlin.math.max

/**
 * The first-person view: the strip seen from the driver's seat, drawn the arcade way (Out Run,
 * Pole Position). Every screen row under the horizon is a slice of road at the distance that row
 * sees, so the asphalt bands, lane lines and dashed centre line rush toward you; the trackside
 * objects are billboards scaled by distance, drawn back to front; the opponent is a rear view in the
 * other lane at the real gap between the cars. Presentation only: the race is the same race.
 *
 * World: x (m) ahead along the strip, lane offset (m) to the right of the player's lane centre. The
 * player's lane is on the right, the opponent's on the left.
 */
class CockpitView(private val track: TrackDef, private val art: Art, private val trackView: TrackRenderer, private val white: Texture) {
    /** A billboard: [at] metres along the strip, [side] metres across, [kind] what it is. */
    class Thing(val at: Float, val side: Float, val kind: Kind, val label: String = "")
    enum class Kind { POLE, STAND, WALL, BOARD, TREE, GANTRY, CONE }

    private val ground = GROUND[track.id] ?: Color(0.2f, 0.19f, 0.26f, 1f)
    private val groundB = Color(ground).mul(0.86f, 0.86f, 0.9f, 1f)
    private val asphalt = if (track.weather == "snow") Color(0.36f, 0.36f, 0.42f, 1f) else Color(0.15f, 0.13f, 0.21f, 1f)
    private val asphaltB = Color(asphalt).mul(1.14f, 1.14f, 1.12f, 1f)
    private val fog = Color(track.ambient).lerp(Color(0.25f, 0.2f, 0.35f, 1f), 0.5f)
    private val far = track.layers.firstOrNull { it.image.endsWith("far.png") }
    private val mid = track.layers.firstOrNull { it.image.endsWith("mid.png") }
    private val sun = track.layers.firstOrNull { it.image.endsWith("sun.png") || it.image.endsWith("moon.png") }
    /** Golden-hour tracks: the low sun glares through the windshield. */
    val goldenHour = track.layers.any { it.image.endsWith("sun.png") }

    /** Where the sun (or moon) is on screen: its centre. */
    fun sunAt(w: Int, h: Int): Pair<Float, Float>? {
        val s = sun ?: return null
        val tex = art.tex(s.image)
        // low over the skyline, a little right of the road, sinking as you race like the side view's
        val rise = (SUN_RISE - trackView.time * s.sink).coerceAtLeast(-tex.height / 3f)
        return w * SUN_X to horizon(h) + rise + tex.height / 2f
    }

    /** What lines the strip: a pole every 25 m, stands and a wall, the distance boards, the tree, the finish. */
    fun things(finishM: Float): List<Thing> {
        val out = ArrayList<Thing>()
        var m = -20f
        while (m < finishM + 160f) {
            out.add(Thing(m, RIGHT_EDGE + 1.6f, Kind.POLE))
            out.add(Thing(m + 12.5f, LEFT_EDGE - 1.4f, Kind.POLE))
            out.add(Thing(m, LEFT_EDGE - 5f, Kind.STAND))
            out.add(Thing(m + 12.5f, LEFT_EDGE - 5f, Kind.STAND))
            m += 25f
        }
        for ((at, label) in listOf(18.3f to "60", 100.6f to "330", 201.2f to "1/8", 304.8f to "1000", 402.3f to "1/4")) {
            if (at <= finishM + 1f) out.add(Thing(at, RIGHT_EDGE + 1.2f, Kind.BOARD, label))
        }
        out.add(Thing(0.2f, (LEFT_EDGE + RIGHT_EDGE) / 2f + LANE / 2f, Kind.TREE))     // between the lanes at the line
        out.add(Thing(finishM, 0f, Kind.GANTRY))
        for (k in 0..3) out.add(Thing(-1f + k * 0.6f, RIGHT_EDGE - 0.2f, Kind.CONE))
        return out
    }

    private var stuff: List<Thing> = emptyList()
    private var stuffFor = -1f

    /** Horizon row (from the bottom) and focal length for a screen [h] pixels tall. */
    fun horizon(h: Int) = floor(h * HORIZON)
    fun focal(h: Int) = h * FOCAL

    fun draw(
        batch: SpriteBatch, w: Int, h: Int, playerX: Float, opponentX: Float?, opponent: RearView?, finishM: Float,
        time: Float, shakeX: Float, shakeY: Float,
    ) {
        this.batch = batch
        if (stuffFor != finishM) {
            stuff = things(finishM)
            stuffFor = finishM
        }
        val hz = horizon(h) + shakeY
        val f = focal(h)
        val cx = w / 2f + shakeX

        // sky and the city on the horizon (the side view's own layers, standing on the horizon line)
        trackView.drawSky(batch, w)
        sun?.let { s ->
            val tex = art.tex(s.image)
            val (sx, sy) = sunAt(w, h)!!
            batch.draw(tex, sx - tex.width / 2f, sy - tex.height / 2f)
        }
        for (l in listOfNotNull(far, mid)) {
            val tex = art.tex(l.image)
            val y = hz - 2f - (if (l === mid) 0f else 3f)
            var x = -((time * l.drift * 2f) % tex.width) - tex.width
            while (x < w) {
                batch.draw(tex, x, y)
                x += tex.width
            }
        }

        // the road, one row at a time from the bottom of the screen up to the horizon
        for (row in 0 until hz.toInt()) {
            val d = hz - row
            val z = CAM_H * f / d                              // metres ahead this row sees
            if (z > FAR_M) continue
            val at = playerX + z
            val fogT = (z / FAR_M).coerceIn(0f, 1f)
            val k = f / z                                      // pixels per metre across at this distance
            val band = (floor(at / BAND_M).toInt() and 1) == 0
            val y = row.toFloat()
            fun lx(side: Float) = cx + (side - CAM_SIDE) * k
            // ground either side
            mix(if (band) ground else groundB, fogT)
            batch.draw(white, 0f, y, w.toFloat(), 1f)
            // asphalt across both lanes
            mix(if (band) asphalt else asphaltB, fogT)
            val r0 = lx(LEFT_EDGE)
            val r1 = lx(RIGHT_EDGE)
            batch.draw(white, r0, y, r1 - r0, 1f)
            // edge lines, solid
            mix(EDGE_LINE, fogT)
            val lw = max(1f, 0.15f * k)
            batch.draw(white, r0, y, lw, 1f)
            batch.draw(white, r1 - lw, y, lw, 1f)
            // the centre line between the lanes, dashed
            if ((floor(at / DASH_M).toInt() and 1) == 0) {
                mix(CENTRE_LINE, fogT)
                batch.draw(white, lx(-LANE / 2f) - lw / 2f, y, lw, 1f)
            }
            // start and finish lines across the road
            if (at in -0.3f..0.3f || at in finishM - 0.6f..finishM + 0.6f) {
                mix(if (at > 1f && (floor(at * 4f).toInt() + floor((lx(0f) - r0) / 4f).toInt()) and 1 == 0) Color.BLACK else Color.WHITE, fogT)
                batch.draw(white, r0, y, r1 - r0, 1f)
            }
        }
        // the walls along both sides, far to near so the near rows cover the far ones: each row of road
        // raises the wall's face from its foot at that distance to its top, out to the screen's edge
        for (row in hz.toInt() - 1 downTo 0) {
            val d = hz - row
            val z = CAM_H * f / d
            if (z > FAR_M) continue
            val fogT = (z / FAR_M).coerceIn(0f, 1f)
            val k = f / z
            val panel = (floor((playerX + z) / WALL_PANEL_M).toInt() and 1) == 0
            val wh = WALL_H * k
            val y = row.toFloat()
            val rx = cx + (RIGHT_EDGE + WALL_GAP - CAM_SIDE) * k
            mix(if (panel) WALL else WALL_B, fogT)
            batch.draw(white, rx, y, w - rx, wh)
            mix(WALL_TOP, fogT)
            batch.draw(white, rx, y + wh - max(1f, 0.1f * k), w - rx, max(1f, 0.1f * k))
            val lx = cx + (LEFT_EDGE - WALL_GAP - CAM_SIDE) * k
            mix(if (panel) WALL else WALL_B, fogT)
            batch.draw(white, 0f, y, lx, wh)
            mix(WALL_TOP, fogT)
            batch.draw(white, 0f, y + wh - max(1f, 0.1f * k), lx, max(1f, 0.1f * k))
        }
        batch.color = Color.WHITE

        // billboards, back to front, and the opponent among them
        val ahead = stuff.filter { it.at - playerX > NEAR_M && it.at - playerX < FAR_M }.sortedByDescending { it.at }
        var opponentDrawn = opponentX == null || opponent == null
        for (t in ahead) {
            val z = t.at - playerX
            if (!opponentDrawn && opponentX!! - playerX > z) {
                drawOpponent(batch, opponent!!, opponentX - playerX, cx, hz, f, time)
                opponentDrawn = true
            }
            drawThing(batch, t, z, cx, hz, f)
        }
        if (!opponentDrawn) drawOpponent(batch, opponent!!, opponentX!! - playerX, cx, hz, f, time)
        batch.color = Color.WHITE
    }

    private fun mix(c: Color, fogT: Float) {
        val t = fogT * fogT
        batch?.setColor(c.r + (fog.r - c.r) * t, c.g + (fog.g - c.g) * t, c.b + (fog.b - c.b) * t, 1f)
    }

    private var batch: SpriteBatch? = null

    /** Where a point on the ground [z] m ahead and [side] m across lands, and the pixels per metre there. */
    private fun project(z: Float, side: Float, cx: Float, hz: Float, f: Float): Triple<Float, Float, Float> {
        val k = f / z
        return Triple(cx + (side - CAM_SIDE) * k, hz - CAM_H * k, k)
    }

    private fun drawThing(b: SpriteBatch, t: Thing, z: Float, cx: Float, hz: Float, f: Float) {
        val (x, y, k) = project(z, t.side, cx, hz, f)
        val fogT = (z / FAR_M).coerceIn(0f, 1f)
        when (t.kind) {
            Kind.POLE -> {
                mix(POLE, fogT)
                b.draw(white, x - 0.08f * k, y, max(1f, 0.16f * k), 8f * k)
                mix(LAMP, fogT * 0.5f)
                b.draw(white, x - 0.6f * k, y + 7.8f * k, 1.2f * k, max(1f, 0.25f * k))
            }
            Kind.STAND -> {
                // a stand of crowd rising behind the wall: rows of little heads
                val y = y + WALL_H * k
                val wpx = 12.5f * k
                mix(STAND, fogT)
                b.draw(white, x - wpx, y, wpx, 3.2f * k)
                val rows = 4
                for (r in 0 until rows) {
                    val ry = y + (0.5f + r * 0.7f) * k
                    for (c in 0 until 10) {
                        val hue = CROWD[Math.floorMod(c * 7 + r * 3 + t.at.toInt(), CROWD.size)]
                        mix(hue, fogT)
                        b.draw(white, x - wpx + (c + 0.3f) * wpx / 10f, ry, max(1f, 0.5f * k), max(1f, 0.35f * k))
                    }
                }
            }
            Kind.WALL -> {
                mix(WALL, fogT)
                b.draw(white, x - 6.25f * k, y, 12.5f * k, 0.9f * k)
                mix(WALL_TOP, fogT)
                b.draw(white, x - 6.25f * k, y + 0.8f * k, 12.5f * k, max(1f, 0.12f * k))
            }
            Kind.BOARD -> {
                mix(BOARD, fogT)
                b.draw(white, x - 0.06f * k, y, max(1f, 0.12f * k), 1.6f * k)
                b.draw(white, x - 0.7f * k, y + 1.6f * k, 1.4f * k, 0.8f * k)
            }
            Kind.TREE -> {
                mix(POLE, fogT)
                b.draw(white, x - 0.1f * k, y, max(1f, 0.2f * k), 2.6f * k)
                val lamps = listOf(Color.YELLOW, Color.YELLOW, Color.YELLOW, GREEN_LAMP, RED_LAMP)
                lamps.forEachIndexed { i, c ->
                    mix(c, fogT)
                    b.draw(white, x - 0.25f * k, y + (2.5f - i * 0.4f) * k, 0.5f * k, 0.3f * k)
                }
            }
            Kind.GANTRY -> {
                val (lx, _, _) = project(z, LEFT_EDGE - 0.6f, cx, hz, f)
                val (rx, _, _) = project(z, RIGHT_EDGE + 0.6f, cx, hz, f)
                mix(POLE, fogT)
                b.draw(white, lx, y, max(1f, 0.3f * k), 6f * k)
                b.draw(white, rx, y, max(1f, 0.3f * k), 6f * k)
                b.draw(white, lx, y + 5.2f * k, rx - lx, 0.9f * k)
                mix(Color.WHITE, fogT)
                b.draw(white, lx + (rx - lx) * 0.3f, y + 5.4f * k, (rx - lx) * 0.4f, 0.5f * k)
            }
            Kind.CONE -> {
                mix(CONE, fogT)
                b.draw(white, x - 0.15f * k, y, 0.3f * k, 0.45f * k)
            }
        }
    }

    /** Low sun through the glass: a bloom on the sun, a streak across the screen, a few ghost discs. */
    fun drawGlare(b: SpriteBatch, w: Int, h: Int, time: Float) {
        val (sx, sy) = sunAt(w, h) ?: return
        val flicker = 0.85f + 0.15f * kotlin.math.sin(time * 1.7f)
        b.setColor(1f, 0.8f, 0.5f, 0.55f * flicker)
        b.draw(white, sx - 16f, sy - 16f, 32f, 32f)
        b.setColor(1f, 0.75f, 0.45f, 0.18f * flicker)
        b.draw(white, 0f, sy - 1f, w.toFloat(), 3f)
        val cx = w / 2f
        val cy = h / 2f
        for ((k, a) in listOf(0.4f to 0.16f, 0.9f to 0.1f, 1.4f to 0.08f)) {
            val gx = sx + (cx - sx) * k * 2f
            val gy = sy + (cy - sy) * k * 2f
            b.setColor(1f, 0.6f, 0.3f, a * flicker)
            b.draw(white, gx - 6f * k, gy - 6f * k, 12f * k, 12f * k)
        }
        b.color = Color.WHITE
    }

    /** The opponent's lit tail lamps, for the bloom pass. */
    fun drawOpponentGlow(b: SpriteBatch, w: Int, h: Int, playerX: Float, opponentX: Float, car: RearView, shakeX: Float, shakeY: Float) {
        val z = opponentX - playerX
        if (z < NEAR_M || z > FAR_M) return
        val (x, y, k) = project(z, -LANE, w / 2f + shakeX, horizon(h) + shakeY, focal(h))
        car.drawGlow(b, x, y, k)
    }

    private fun drawOpponent(b: SpriteBatch, car: RearView, z: Float, cx: Float, hz: Float, f: Float, time: Float) {
        if (z < NEAR_M) return
        val (x, y, k) = project(z, -LANE, cx, hz, f)
        car.draw(b, x, y, k, time)
    }

    companion object {
        const val SUN_X = 0.66f
        const val SUN_RISE = 46f
        /** Horizon as a share of the screen height from the bottom, focal length as a share of it. */
        const val HORIZON = 0.56f
        const val FOCAL = 1.25f
        /** The driver's eye height and how far right of the lane centre it sits (left-hand drive: left). */
        const val CAM_H = 1.15f
        const val CAM_SIDE = -0.35f
        const val LANE = 3.6f
        const val RIGHT_EDGE = 1.8f
        const val LEFT_EDGE = -LANE - 1.8f
        const val BAND_M = 6f
        /** The concrete walls: their height, how far off the lane edge, how long a panel is. */
        const val WALL_H = 1.0f
        const val WALL_GAP = 0.6f
        const val WALL_PANEL_M = 4f
        const val DASH_M = 3f
        const val FAR_M = 420f
        const val NEAR_M = 2.2f

        val EDGE_LINE = Color(0.9f, 0.9f, 0.95f, 1f)
        val CENTRE_LINE = Color(1f, 0.82f, 0.3f, 1f)
        val POLE = Color(0.32f, 0.31f, 0.42f, 1f)
        val LAMP = Color(1f, 0.9f, 0.65f, 1f)
        val STAND = Color(0.22f, 0.2f, 0.32f, 1f)
        val WALL = Color(0.55f, 0.55f, 0.62f, 1f)
        val WALL_TOP = Color(0.8f, 0.8f, 0.86f, 1f)
        val WALL_B = Color(0.48f, 0.48f, 0.56f, 1f)
        val BOARD = Color(0.85f, 0.85f, 0.9f, 1f)
        val CONE = Color(1f, 0.5f, 0.15f, 1f)
        val GREEN_LAMP = Color(0.3f, 1f, 0.4f, 1f)
        val RED_LAMP = Color(1f, 0.25f, 0.2f, 1f)
        val CROWD = listOf(Color(0.9f, 0.3f, 0.4f, 1f), Color(0.3f, 0.6f, 0.9f, 1f), Color(0.95f, 0.85f, 0.4f, 1f),
            Color(0.4f, 0.8f, 0.5f, 1f), Color(0.85f, 0.85f, 0.9f, 1f), Color(0.6f, 0.4f, 0.8f, 1f))
        /** The ground beside the strip, per track. */
        val GROUND = mapOf(
            "neon_city" to Color(0.17f, 0.15f, 0.24f, 1f), "desert" to Color(0.78f, 0.55f, 0.35f, 1f),
            "port" to Color(0.2f, 0.22f, 0.27f, 1f), "coast" to Color(0.85f, 0.62f, 0.5f, 1f),
            "airfield" to Color(0.88f, 0.9f, 0.96f, 1f), "speedway" to Color(0.27f, 0.42f, 0.3f, 1f),
            "sanantonio" to Color(0.6f, 0.45f, 0.4f, 1f),
        )
    }
}

/**
 * A car seen from behind, for the cockpit view: paint, glass, tail lamps (lit), the tyres either
 * side, scaled by [k] pixels per metre. Drawn from the car's paint until the rear sprites exist.
 */
class RearView(private val white: Texture, private val paint: Color, private val widthM: Float = 1.78f, private val heightM: Float = 1.3f) {
    var braking = false
    var flame = 0f
    var smoke = 0f

    fun draw(b: SpriteBatch, x: Float, y: Float, k: Float, time: Float) {
        val w = widthM * k
        val h = heightM * k
        val dark = Color(paint).mul(0.55f, 0.55f, 0.65f, 1f)
        val lit = Color(paint).lerp(Color.WHITE, 0.25f)
        // shadow, tyres
        b.setColor(0f, 0f, 0.02f, 0.5f)
        b.draw(white, x - w * 0.55f, y - 0.04f * k, w * 1.1f, 0.12f * k)
        b.setColor(0.04f, 0.04f, 0.06f, 1f)
        b.draw(white, x - w * 0.48f, y, 0.26f * k, 0.6f * k)
        b.draw(white, x + w * 0.48f - 0.26f * k, y, 0.26f * k, 0.6f * k)
        // body, cabin, glass
        b.color = dark
        b.draw(white, x - w / 2f, y + 0.18f * k, w, 0.62f * k)
        b.color = paint
        b.draw(white, x - w / 2f + 0.04f * k, y + 0.3f * k, w - 0.08f * k, 0.48f * k)
        b.color = lit
        b.draw(white, x - w / 2f + 0.04f * k, y + 0.74f * k, w - 0.08f * k, max(1f, 0.05f * k))
        b.color = paint
        b.draw(white, x - w * 0.36f, y + 0.8f * k, w * 0.72f, h - 0.8f * k)
        b.setColor(0.08f, 0.1f, 0.2f, 1f)
        b.draw(white, x - w * 0.31f, y + 0.86f * k, w * 0.62f, (h - 0.98f) * k)
        // tail lamps, lit; brighter on the brakes
        val tl = if (braking) Color(1f, 0.25f, 0.2f, 1f) else Color(0.85f, 0.1f, 0.12f, 1f)
        b.color = tl
        b.draw(white, x - w / 2f + 0.08f * k, y + 0.56f * k, 0.36f * k, max(1f, 0.12f * k))
        b.draw(white, x + w / 2f - 0.44f * k, y + 0.56f * k, 0.36f * k, max(1f, 0.12f * k))
        // plate
        b.setColor(0.85f, 0.85f, 0.8f, 1f)
        b.draw(white, x - 0.25f * k, y + 0.36f * k, 0.5f * k, max(1f, 0.12f * k))
        // flames out the pipes on shifts and nitrous, smoke off the tyres at launch
        if (flame > 0f) {
            b.setColor(1f, 0.55f + 0.3f * (time * 30f % 1f), 0.2f, 0.9f)
            b.draw(white, x + w * 0.3f, y + 0.18f * k, 0.12f * k, -0.25f * k * flame)
        }
        if (smoke > 0f) {
            b.setColor(0.85f, 0.85f, 0.9f, 0.45f * smoke)
            b.draw(white, x - w * 0.75f, y, w * 1.5f, 0.9f * k * smoke)
        }
        b.color = Color.WHITE
    }

    /** The lit pixels for the bloom pass. */
    fun drawGlow(b: SpriteBatch, x: Float, y: Float, k: Float) {
        val w = widthM * k
        b.setColor(1f, 0.2f, 0.15f, if (braking) 0.9f else 0.55f)
        b.draw(white, x - w / 2f + 0.04f * k, y + 0.52f * k, 0.44f * k, max(1f, 0.2f * k))
        b.draw(white, x + w / 2f - 0.48f * k, y + 0.52f * k, 0.44f * k, max(1f, 0.2f * k))
        b.color = Color.WHITE
    }
}

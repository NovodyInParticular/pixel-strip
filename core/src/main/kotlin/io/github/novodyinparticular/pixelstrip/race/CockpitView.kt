package io.github.novodyinparticular.pixelstrip.race

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.MathUtils
import io.github.novodyinparticular.pixelstrip.data.TrackDef
import io.github.novodyinparticular.pixelstrip.gfx.Art
import io.github.novodyinparticular.pixelstrip.gfx.PixelFont
import io.github.novodyinparticular.pixelstrip.world.TrackRenderer
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The first-person view: the strip seen from the driver's seat, drawn the arcade way (Out Run,
 * Pole Position). Every screen row under the horizon is a slice of road at the distance that row
 * sees, so the launch pad's seams, the rubber laid down off the line and the lane lines rush
 * toward you; the trackside is billboards scaled by distance, drawn back to
 * front (the tree with its live bulbs, lamp posts, grandstands, the catch fence, the pits, each
 * track's own scenery); the opponent is a rear view in the other lane at the real gap between the
 * cars. Presentation only: the race is the same race.
 *
 * World: x (m) ahead along the strip, lane offset (m) to the right of the player's lane centre. The
 * player's lane is on the right, the opponent's on the left.
 */
class CockpitView(
    private val track: TrackDef, private val art: Art, private val trackView: TrackRenderer, private val white: Texture,
    private val font: PixelFont, private val tree: Tree,
) {
    /** A billboard: [at] metres along the strip, [side] metres across, [kind] what it is, [v] a per-thing variation 0..1. */
    class Thing(val at: Float, val side: Float, val kind: Kind, val label: String = "", val v: Float = 0f)
    enum class Kind {
        POLE, STAND, FENCE, BOARD, TREE, GANTRY, CONE, TENT, TRAILER, SCOREBOARD, NET,
        NEON, CACTUS, PALM, CONTAINERS, CRANE, HANGAR, RUNWAY_LIGHT, PINE, PICADO,
    }

    private val pixel = TextureRegion(white)
    private val ground = GROUND[track.id] ?: Color(0.2f, 0.19f, 0.26f, 1f)
    private val groundB = Color(ground).mul(0.86f, 0.86f, 0.9f, 1f)
    private val groundSpeck = Color(ground).lerp(Color.WHITE, 0.18f)
    private val snow = track.weather == "snow"
    private val asphalt = if (snow) Color(0.36f, 0.36f, 0.42f, 1f) else Color(0.15f, 0.13f, 0.21f, 1f)
    private val asphaltB = Color(asphalt).mul(1.14f, 1.14f, 1.12f, 1f)
    private val concrete = Color(asphalt).lerp(Color(0.62f, 0.6f, 0.66f, 1f), 0.45f)
    private val concreteB = Color(concrete).mul(0.95f, 0.95f, 0.97f, 1f)
    private val fog = Color(track.ambient).lerp(Color(0.25f, 0.2f, 0.35f, 1f), 0.5f)
    private val far = track.layers.firstOrNull { it.image.endsWith("far.png") }
    private val mid = track.layers.firstOrNull { it.image.endsWith("mid.png") }
    /** The eyes across the lane (m, + right): set from the player's car. */
    var camSide = -SEAT_SIDE
    private val sun = track.layers.firstOrNull { it.image.endsWith("sun.png") || it.image.endsWith("moon.png") }
    /** Golden-hour tracks: the low sun glares through the windshield. */
    val goldenHour = track.layers.any { it.image.endsWith("sun.png") }
    /** Night tracks: the lamps are lit and pool light on the road. */
    private val night = track.id in NIGHT

    /** Where the sun (or moon) is on screen: its centre. */
    fun sunAt(w: Int, h: Int): Pair<Float, Float>? {
        val s = sun ?: return null
        val tex = art.tex(s.image)
        // low over the skyline, a little right of the road, sinking as you race like the side view's
        val rise = (SUN_RISE - trackView.time * s.sink).coerceAtLeast(-tex.height / 3f)
        return w * SUN_X to horizon(h) + rise + tex.height / 2f
    }

    /** What lines the strip: lamp posts, the stands and the fence, the boards, the tree, the pits, the track's own scenery, the finish. */
    fun things(finishM: Float): List<Thing> {
        val out = ArrayList<Thing>()
        val end = finishM + SHUTDOWN_M
        var m = -20f
        var n = 0
        while (m < end) {
            out.add(Thing(m, RIGHT_EDGE + 1.6f, Kind.POLE, v = 1f))
            out.add(Thing(m + 12.5f, LEFT_EDGE - 1.4f, Kind.POLE, v = -1f))
            if (m + 12.5f < finishM + 40f) {
                out.add(Thing(m, LEFT_EDGE - 5f, Kind.STAND, v = hash(n)))
                out.add(Thing(m + 12.5f, LEFT_EDGE - 5f, Kind.STAND, v = hash(n + 500)))
            }
            m += 25f
            n++
        }
        var f = -12f
        while (f < end) {
            out.add(Thing(f, LEFT_EDGE - WALL_GAP, Kind.FENCE, v = -1f))
            out.add(Thing(f, RIGHT_EDGE + WALL_GAP, Kind.FENCE, v = 1f))
            f += FENCE_M
        }
        for ((at, label) in listOf(18.3f to "60'", 100.6f to "330'", 201.2f to "1/8", 304.8f to "1000'", 402.3f to "1/4")) {
            if (at <= finishM + 1f) out.add(Thing(at, RIGHT_EDGE + 1.2f, Kind.BOARD, label))
        }
        out.add(Thing(TREE_M, -LANE / 2f, Kind.TREE))                // between the lanes, just past the line
        out.add(Thing(finishM, 0f, Kind.GANTRY))
        out.add(Thing(finishM + 30f, RIGHT_EDGE + 9f, Kind.SCOREBOARD))
        out.add(Thing(end - 10f, -LANE / 2f, Kind.NET))
        for (k in 0..3) out.add(Thing(-1f + k * 0.6f, RIGHT_EDGE - 0.2f, Kind.CONE))
        // the pits off the right wall: canopies and trailers
        var p = 10f
        var i = 0
        while (p < end) {
            out.add(Thing(p, RIGHT_EDGE + 8f + hash(i * 3) * 4f, if (i % 2 == 0) Kind.TENT else Kind.TRAILER, v = hash(i * 3 + 1)))
            p += 22f + hash(i * 3 + 2) * 20f
            i++
        }
        scenery(out, end)
        return out
    }

    /** Each track's own: what you'd see out there that the others don't have. */
    private fun scenery(out: ArrayList<Thing>, end: Float) {
        fun every(step: Float, from: Float = -10f, body: (Float, Int) -> Unit) {
            var at = from
            var i = 0
            while (at < end + 120f) {
                body(at, i)
                at += step
                i++
            }
        }
        when (track.id) {
            "neon_city" -> every(38f, 15f) { at, i -> out.add(Thing(at, RIGHT_EDGE + 4.5f, Kind.NEON, NEON_WORDS[i % NEON_WORDS.size], hash(i))) }
            "desert" -> every(11f) { at, i -> out.add(Thing(at + hash(i) * 8f, RIGHT_EDGE + 5f + hash(i + 50) * 14f, Kind.CACTUS, v = hash(i + 99))) }
            "coast" -> every(16f) { at, i -> out.add(Thing(at, RIGHT_EDGE + 3.4f + hash(i) * 2f, Kind.PALM, v = hash(i + 7))) }
            "port" -> {
                every(26f, 5f) { at, i -> out.add(Thing(at, RIGHT_EDGE + 6f + hash(i) * 3f, Kind.CONTAINERS, v = hash(i + 3))) }
                every(110f, 40f) { at, i -> out.add(Thing(at, RIGHT_EDGE + 34f, Kind.CRANE, v = hash(i))) }
            }
            "airfield" -> {
                every(85f, 30f) { at, i -> out.add(Thing(at, RIGHT_EDGE + 26f, Kind.HANGAR, v = hash(i))) }
                every(10f) { at, _ -> out.add(Thing(at, RIGHT_EDGE + 3.2f, Kind.RUNWAY_LIGHT)) }
            }
            "speedway" -> every(7f) { at, i -> out.add(Thing(at + hash(i) * 5f, RIGHT_EDGE + 7f + hash(i + 31) * 16f, Kind.PINE, v = hash(i + 61))) }
            "sanantonio" -> {
                every(25f, 6f) { at, i -> out.add(Thing(at, 0f, Kind.PICADO, v = hash(i))) }
                every(19f) { at, i -> out.add(Thing(at, RIGHT_EDGE + 3.6f, Kind.PALM, v = hash(i + 11))) }
            }
        }
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

        drawRoad(batch, w, hz, f, cx, playerX, finishM)
        drawWalls(batch, w, hz, f, cx, playerX)

        // billboards, back to front, and the opponent among them
        val ahead = stuff.filter { it.at - playerX > NEAR_M && it.at - playerX < FAR_M }.sortedByDescending { it.at }
        var opponentDrawn = opponentX == null || opponent == null
        for (t in ahead) {
            val z = t.at - playerX
            if (!opponentDrawn && opponentX!! - playerX > z) {
                drawOpponent(batch, opponent!!, opponentX - playerX, cx, hz, f, time)
                opponentDrawn = true
            }
            drawThing(batch, t, z, cx, hz, f, time)
        }
        if (!opponentDrawn) drawOpponent(batch, opponent!!, opponentX!! - playerX, cx, hz, f, time)
        batch.color = Color.WHITE
    }

    /** The road, one row at a time from the bottom of the screen up to the horizon. */
    private fun drawRoad(batch: SpriteBatch, w: Int, hz: Float, f: Float, cx: Float, playerX: Float, finishM: Float) {
        var lastSeam = Int.MIN_VALUE
        var lastLine = Int.MIN_VALUE
        val sandAt = finishM + SHUTDOWN_M - 30f
        for (row in 0 until hz.toInt()) {
            val d = hz - row
            val z = CAM_H * f / d                              // metres ahead this row sees
            if (z > FAR_M) continue
            val at = playerX + z
            val fogT = (z / FAR_M).coerceIn(0f, 1f)
            val k = f / z                                      // pixels per metre across at this distance
            val band = (floor(at / BAND_M).toInt() and 1) == 0
            val y = row.toFloat()
            fun lx(side: Float) = cx + (side - camSide) * k
            // ground either side, with its grain
            mix(if (band) ground else groundB, fogT)
            batch.draw(white, 0f, y, w.toFloat(), 1f)
            if (k > 4f) {
                val cell = floor(at * 2f).toInt()
                for (j in 0 until 8) {
                    val sx = lx(-40f + hash(cell * 8 + j) * 80f)
                    mixA(if (j % 2 == 0) groundSpeck else groundB, fogT, 0.8f)
                    batch.draw(white, sx, y, 1f, 1f)
                }
            }
            val r0 = lx(LEFT_EDGE)
            val r1 = lx(RIGHT_EDGE)
            // past the shutdown the strip runs out into the sand trap
            if (at > sandAt) {
                mix(SAND, fogT)
                batch.draw(white, r0, y, r1 - r0, 1f)
                continue
            }
            // the concrete launch pad, then asphalt
            val pad = at < PAD_M
            mix(if (pad) (if (band) concrete else concreteB) else (if (band) asphalt else asphaltB), fogT)
            batch.draw(white, r0, y, r1 - r0, 1f)
            if (pad) {
                val seam = floor(at / SEAM_M).toInt()
                if (seam != lastSeam && lastSeam != Int.MIN_VALUE) {
                    mixA(SEAM, fogT, 0.8f)
                    batch.draw(white, r0, y, r1 - r0, 1f)
                }
                lastSeam = seam
            }
            // the rubber laid down in each lane's grooves: dark off the line, gone a few car lengths out
            val rubber = if (at < -6f || at > RUBBER_M) 0f else 0.62f * (1f - at / RUBBER_M).coerceIn(0f, 1f)
            if (rubber > 0f) for (lane in floatArrayOf(0f, -LANE)) {
                for (t in floatArrayOf(-TYRE_TRACK, TYRE_TRACK)) {
                    mixA(RUBBER, fogT, rubber * (1f - fogT))
                    batch.draw(white, lx(lane + t - 0.2f), y, 0.4f * k, 1f)
                }
            }
            // lamp light pooling under each post at night
            if (night) {
                val pr = ((at + 20f) % 25f + 25f) % 25f
                val pl = ((at + 7.5f) % 25f + 25f) % 25f
                val poolR = exp(-(min(pr, 25f - pr) / 5f).let { it * it })
                val poolL = exp(-(min(pl, 25f - pl) / 5f).let { it * it })
                mixA(LAMP, fogT, 0.14f * poolR)
                batch.draw(white, lx(-LANE / 2f), y, r1 - lx(-LANE / 2f), 1f)
                mixA(LAMP, fogT, 0.14f * poolL)
                batch.draw(white, r0, y, lx(-LANE / 2f) - r0, 1f)
            }
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
            // start and finish lines across the road (a row that steps over one paints it)
            val line = (if (at >= 0f) 1 else 0) + (if (at >= finishM) 1 else 0)
            if (lastLine != Int.MIN_VALUE && line != lastLine || at in -0.3f..0.3f || at in finishM - 0.6f..finishM + 0.6f) {
                val checker = at > 1f && (floor(at * 4f).toInt() + floor((lx(0f) - r0) / 4f).toInt()) and 1 == 0
                mix(if (checker) Color.BLACK else Color.WHITE, fogT)
                batch.draw(white, r0, y, r1 - r0, 1f)
            }
            lastLine = line
        }
    }

    /**
     * The walls along both sides, far to near so the near rows cover the far ones: each row of road
     * raises the wall's face from its foot at that distance to its top, out to the screen's edge.
     * Some panels carry a sponsor's colours; on the snowy track the tops are white.
     */
    private fun drawWalls(batch: SpriteBatch, w: Int, hz: Float, f: Float, cx: Float, playerX: Float) {
        for (row in hz.toInt() - 1 downTo 0) {
            val d = hz - row
            val z = CAM_H * f / d
            if (z > FAR_M) continue
            val fogT = (z / FAR_M).coerceIn(0f, 1f)
            val k = f / z
            val at = playerX + z
            val idx = floor(at / WALL_PANEL_M).toInt()
            val panel = (idx and 1) == 0
            val ad = hash(idx * 13 + 5)
            val wh = WALL_H * k
            val y = row.toFloat()
            val top = max(1f, 0.1f * k)
            for (sideSign in intArrayOf(1, -1)) {
                val edge = if (sideSign > 0) cx + (RIGHT_EDGE + WALL_GAP - camSide) * k else cx + (LEFT_EDGE - WALL_GAP - camSide) * k
                val x0 = if (sideSign > 0) edge else 0f
                val ww = if (sideSign > 0) w - edge else edge
                if (ww <= 0f) continue
                mix(if (panel) WALL else WALL_B, fogT)
                batch.draw(white, x0, y, ww, wh)
                if (ad > 0.55f) {
                    val c = ADS[(ad * 97).toInt() % ADS.size]
                    mix(c, fogT)
                    batch.draw(white, x0, y + wh * 0.28f, ww, wh * 0.44f)
                    mix(Color.WHITE, fogT)
                    batch.draw(white, x0, y + wh * 0.46f, ww, max(1f, wh * 0.08f))
                }
                mix(if (snow) SNOW else WALL_TOP, fogT)
                batch.draw(white, x0, y + wh - top, ww, if (snow) top * 2.5f else top)
            }
        }
        batch.color = Color.WHITE
    }

    private fun mix(c: Color, fogT: Float) = mixA(c, fogT, 1f)

    private fun mixA(c: Color, fogT: Float, a: Float) {
        val t = fogT * fogT
        batch?.setColor(c.r + (fog.r - c.r) * t, c.g + (fog.g - c.g) * t, c.b + (fog.b - c.b) * t, a)
    }

    private var batch: SpriteBatch? = null

    /** Where a point on the ground [z] m ahead and [side] m across lands, and the pixels per metre there. */
    private fun project(z: Float, side: Float, cx: Float, hz: Float, f: Float): Triple<Float, Float, Float> {
        val k = f / z
        return Triple(cx + (side - camSide) * k, hz - CAM_H * k, k)
    }

    private fun rect(b: SpriteBatch, x: Float, y: Float, w: Float, h: Float) = b.draw(white, x, y, max(1f, w), max(1f, h))

    private fun line(b: SpriteBatch, x1: Float, y1: Float, x2: Float, y2: Float, t: Float) {
        val dx = x2 - x1
        val dy = y2 - y1
        val len = hypot(dx, dy)
        if (len < 0.5f) return
        b.draw(pixel, x1, y1 - t / 2f, 0f, t / 2f, len, max(1f, t), 1f, 1f, atan2(dy, dx) * MathUtils.radiansToDegrees)
    }

    private fun drawThing(b: SpriteBatch, t: Thing, z: Float, cx: Float, hz: Float, f: Float, time: Float) {
        val (x, y, k) = project(z, t.side, cx, hz, f)
        val fogT = (z / FAR_M).coerceIn(0f, 1f)
        when (t.kind) {
            Kind.POLE -> {
                // a lamp post, its arm reaching out over the road
                mix(POLE, fogT)
                rect(b, x - 0.08f * k, y, 0.16f * k, 8f * k)
                val armEnd = x - t.v * 1.8f * k
                line(b, x, y + 7.9f * k, armEnd, y + 8.2f * k, max(1f, 0.1f * k))
                mix(if (night) LAMP else LAMP_OFF, fogT * 0.5f)
                rect(b, armEnd - 0.4f * k, y + 7.95f * k, 0.8f * k, 0.22f * k)
            }
            Kind.STAND -> drawStand(b, t, x, y, k, fogT, time)
            Kind.FENCE -> {
                // the catch fence on the wall: posts, a top and a middle rail out to the next post
                val (nx, ny, nk) = project(z + FENCE_M, t.side, cx, hz, f)
                val base = y + WALL_H * k
                mix(FENCE, fogT)
                rect(b, x - 0.04f * k, base, 0.08f * k, FENCE_H * k)
                line(b, x, base + FENCE_H * k, nx, ny + (WALL_H + FENCE_H) * nk, max(1f, 0.05f * k))
                mixA(FENCE, fogT, 0.6f)
                line(b, x, base + FENCE_H * 0.5f * k, nx, ny + (WALL_H + FENCE_H * 0.5f) * nk, 1f)
            }
            Kind.BOARD -> {
                mix(POLE, fogT)
                rect(b, x - 0.06f * k, y, 0.12f * k, 1.6f * k)
                mix(BOARD, fogT)
                rect(b, x - 0.8f * k, y + 1.6f * k, 1.6f * k, 0.9f * k)
                val scale = (1.4f * k / (font.width(t.label) + 2)).toInt()
                if (scale >= 1) font.draw(b, t.label, floor(x - font.width(t.label, scale) / 2f), floor(y + 1.75f * k), BOARD_TEXT, scale, null)
            }
            Kind.TREE -> {
                b.color = Color.WHITE
                mix(POLE, fogT)
                rect(b, x - 0.06f * k, y, 0.12f * k, 0.5f * k)
                b.color = Color.WHITE
                tree.drawScaled(b, x, y + 0.4f * k, k / TREE_PX_M)
            }
            Kind.GANTRY -> {
                val (lx, _, _) = project(z, LEFT_EDGE - 0.6f, cx, hz, f)
                val (rx, _, _) = project(z, RIGHT_EDGE + 0.6f, cx, hz, f)
                mix(POLE, fogT)
                rect(b, lx, y, 0.3f * k, 6f * k)
                rect(b, rx, y, 0.3f * k, 6f * k)
                rect(b, lx, y + 5.2f * k, rx - lx, 0.9f * k)
                mix(Color.WHITE, fogT)
                rect(b, lx + (rx - lx) * 0.3f, y + 5.4f * k, (rx - lx) * 0.4f, 0.5f * k)
                val scale = ((rx - lx) * 0.36f / font.width("FINISH")).toInt()
                if (scale >= 1) font.draw(b, "FINISH", floor((lx + rx) / 2f - font.width("FINISH", scale) / 2f), floor(y + 5.45f * k), RED_LAMP, scale, null)
            }
            Kind.CONE -> {
                mix(CONE, fogT)
                rect(b, x - 0.15f * k, y, 0.3f * k, 0.45f * k)
                mix(Color.WHITE, fogT)
                rect(b, x - 0.1f * k, y + 0.22f * k, 0.2f * k, 0.08f * k)
            }
            Kind.TENT -> {
                val c = TENTS[(t.v * 97).toInt() % TENTS.size]
                mix(POLE, fogT)
                rect(b, x - 1.5f * k, y, 0.08f * k, 2.4f * k)
                rect(b, x + 1.42f * k, y, 0.08f * k, 2.4f * k)
                for (s in 0 until 6) {
                    mix(if (s % 2 == 0) c else Color.WHITE, fogT)
                    rect(b, x - 1.6f * k + s * 0.533f * k, y + 2.4f * k, 0.54f * k, 0.5f * k)
                }
                mix(STAND, fogT)                                   // a toolbox and a car's nose under it
                rect(b, x - 1.1f * k, y, 0.6f * k, 0.9f * k)
                mix(c, fogT)
                rect(b, x - 0.2f * k, y, 1.5f * k, 0.7f * k)
            }
            Kind.TRAILER -> {
                mix(TRAILER, fogT)
                rect(b, x - 1.25f * k, y + 0.4f * k, 2.5f * k, 3f * k)
                mix(TENTS[(t.v * 89).toInt() % TENTS.size], fogT)
                rect(b, x - 1.25f * k, y + 1.6f * k, 2.5f * k, 0.35f * k)
                mix(TYRE, fogT)
                rect(b, x - 1.1f * k, y, 0.4f * k, 0.55f * k)
                rect(b, x + 0.7f * k, y, 0.4f * k, 0.55f * k)
            }
            Kind.SCOREBOARD -> {
                mix(POLE, fogT)
                rect(b, x - 3f * k, y, 0.3f * k, 5f * k)
                rect(b, x + 2.7f * k, y, 0.3f * k, 5f * k)
                mix(SCORE_FACE, fogT)
                rect(b, x - 3.6f * k, y + 5f * k, 7.2f * k, 3f * k)
                val scale = (6.4f * k / font.width("PIXEL STRIP")).toInt()
                if (scale >= 1) font.draw(b, "PIXEL STRIP", floor(x - font.width("PIXEL STRIP", scale) / 2f), floor(y + 6.6f * k), SCORE_TEXT, scale, null)
                mix(LCD_AMBER, fogT)
                rect(b, x - 3.2f * k, y + 5.4f * k, 6.4f * k, 0.3f * k)
            }
            Kind.NET -> {
                val (lx, _, _) = project(z, LEFT_EDGE - 0.4f, cx, hz, f)
                val (rx, _, _) = project(z, RIGHT_EDGE + 0.4f, cx, hz, f)
                mix(POLE, fogT)
                rect(b, lx, y, 0.2f * k, 3f * k)
                rect(b, rx, y, 0.2f * k, 3f * k)
                mixA(FENCE, fogT, 0.5f)
                var gx = lx
                while (gx < rx) {
                    rect(b, gx, y, 1f, 3f * k)
                    gx += max(2f, 0.5f * k)
                }
                for (r in 0..5) rect(b, lx, y + r * 0.5f * k, rx - lx, 1f)
            }
            Kind.NEON -> {
                val c = NEONS[(t.v * 97).toInt() % NEONS.size]
                mix(POLE, fogT)
                rect(b, x - 1.6f * k, y, 0.14f * k, 3f * k)
                rect(b, x + 1.46f * k, y, 0.14f * k, 3f * k)
                mix(SCORE_FACE, fogT)
                rect(b, x - 2f * k, y + 3f * k, 4f * k, 1.6f * k)
                mix(c, fogT * 0.4f)
                val e = max(1f, 0.08f * k)
                rect(b, x - 2f * k, y + 3f * k, 4f * k, e)
                rect(b, x - 2f * k, y + 4.6f * k - e, 4f * k, e)
                rect(b, x - 2f * k, y + 3f * k, e, 1.6f * k)
                rect(b, x + 2f * k - e, y + 3f * k, e, 1.6f * k)
                val scale = (3.4f * k / (font.width(t.label) + 2)).toInt()
                if (scale >= 1) font.draw(b, t.label, floor(x - font.width(t.label, scale) / 2f), floor(y + 3.8f * k - 3.5f * scale), c, scale, null)
            }
            Kind.CACTUS -> {
                val hgt = 2.6f + t.v * 2.6f
                mix(CACTUS, fogT)
                rect(b, x - 0.22f * k, y, 0.44f * k, hgt * k)
                rect(b, x - 0.75f * k, y + hgt * 0.45f * k, 0.53f * k, 0.3f * k)
                rect(b, x - 0.75f * k, y + hgt * 0.45f * k, 0.3f * k, hgt * 0.35f * k)
                if (t.v > 0.35f) {
                    rect(b, x + 0.22f * k, y + hgt * 0.6f * k, 0.5f * k, 0.28f * k)
                    rect(b, x + 0.45f * k, y + hgt * 0.6f * k, 0.28f * k, hgt * 0.28f * k)
                }
                mix(CACTUS_LIT, fogT)
                rect(b, x - 0.16f * k, y, 0.1f * k, hgt * k)
            }
            Kind.PALM -> {
                val hgt = 7f + t.v * 3f
                val lean = (t.v - 0.5f) * 1.6f
                mix(TRUNK, fogT)
                var px = x
                var py = y
                for (s in 1..8) {
                    val u = s / 8f
                    val nx = x + lean * u * u * k
                    val ny = y + hgt * u * k
                    line(b, px, py, nx, ny, max(1f, (0.35f - 0.12f * u) * k))
                    px = nx
                    py = ny
                }
                for (fr in 0 until 7) {
                    val a = fr / 6f * 2f - 1f                      // -1..1 across
                    val tipX = px + a * 2.4f * k
                    val tipY = py - (0.6f + abs(a) * 1.3f) * k + sin(time * 1.3f + fr + t.v * 9f) * 0.08f * k
                    mix(if (fr % 2 == 0) FROND else FROND_DARK, fogT)
                    line(b, px, py, (px + tipX) / 2f, py + 0.5f * k, max(1f, 0.16f * k))
                    line(b, (px + tipX) / 2f, py + 0.5f * k, tipX, tipY, max(1f, 0.12f * k))
                }
            }
            Kind.CONTAINERS -> {
                val n = 1 + (t.v * 3f).toInt()
                for (s in 0 until n) {
                    val c = BOXES[((t.v * 53).toInt() + s * 3) % BOXES.size]
                    val by = y + s * 2.6f * k
                    val bx = x - 3f * k + (if (s % 2 == 1) 0.4f * k else 0f)
                    mix(c, fogT)
                    rect(b, bx, by, 6f * k, 2.5f * k)
                    mix(Color(c).mul(0.7f, 0.7f, 0.75f, 1f), fogT)
                    var rx = bx + 0.3f * k
                    if (k > 3f) while (rx < bx + 6f * k) {
                        rect(b, rx, by, max(1f, 0.06f * k), 2.5f * k)
                        rx += 0.4f * k
                    }
                }
            }
            Kind.CRANE -> {
                mix(CRANE, fogT)
                rect(b, x - 6f * k, y, 0.8f * k, 30f * k)
                rect(b, x + 5.2f * k, y, 0.8f * k, 30f * k)
                rect(b, x - 6f * k, y + 22f * k, 12f * k, 1.2f * k)
                rect(b, x - 30f * k, y + 30f * k, 44f * k, 1.4f * k)
                mix(Color.WHITE, fogT)
                rect(b, x - 6f * k, y + 12f * k, 12f * k, 0.6f * k)
            }
            Kind.HANGAR -> {
                val hw = 15f
                for (r in 0 until 20) {
                    val u = r / 19f
                    val half = hw * kotlin.math.sqrt(1f - u * u * 0.9f)
                    mix(if (r < 14) HANGAR else HANGAR_ROOF, fogT)
                    rect(b, x - half * k, y + u * 11f * k, half * 2f * k, 11f * k / 19f + 1f)
                }
                mix(HANGAR_DOOR, fogT)
                rect(b, x - 9f * k, y, 18f * k, 6.5f * k)
                mix(HANGAR, fogT)
                for (dx in 0..5) rect(b, x - 9f * k + dx * 3f * k, y, max(1f, 0.1f * k), 6.5f * k)
            }
            Kind.RUNWAY_LIGHT -> {
                mix(RUNWAY, fogT * 0.4f)
                rect(b, x - 0.12f * k, y, 0.24f * k, 0.3f * k)
            }
            Kind.PINE -> {
                val hgt = 7f + t.v * 6f
                mix(TRUNK, fogT)
                rect(b, x - 0.2f * k, y, 0.4f * k, hgt * 0.25f * k)
                for (tier in 0 until 3) {
                    val base = y + (hgt * 0.2f + tier * hgt * 0.25f) * k
                    val tw = (2.4f - tier * 0.6f) * k
                    val th = hgt * 0.38f * k
                    val rows = max(1, (th / 2f).toInt())
                    for (r in 0 until rows) {
                        val u = r / rows.toFloat()
                        mix(if (r % 3 == 0) PINE_DARK else PINE, fogT)
                        rect(b, x - tw * (1f - u), base + u * th, tw * 2f * (1f - u), th / rows + 1f)
                    }
                }
            }
            Kind.PICADO -> {
                // papel picado strung across the road between the lamp posts
                val left = LEFT_EDGE - 1.4f
                val right = RIGHT_EDGE + 1.6f
                val steps = 24
                var px = 0f
                var py = 0f
                for (s in 0..steps) {
                    val u = s / steps.toFloat()
                    val side = left + (right - left) * u
                    val (sx, sy, _) = project(z, side, cx, hz, f)
                    val hy = sy + (PICADO_H - PICADO_SAG * 4f * u * (1f - u)) * k
                    if (s > 0) {
                        mix(FENCE, fogT)
                        line(b, px, py, sx, hy, 1f)
                        val c = PICADO[(s + (t.v * 7).toInt()) % PICADO.size]
                        mix(c, fogT)
                        val flutter = sin(time * 4f + s * 0.9f) * 0.04f * k
                        rect(b, (px + sx) / 2f - 0.17f * k, (py + hy) / 2f - 0.45f * k + flutter, 0.34f * k, 0.42f * k)
                    }
                    px = sx
                    py = hy
                }
            }
        }
    }

    /** A grandstand behind the wall: tiers of crowd climbing back under a roof on columns. */
    private fun drawStand(b: SpriteBatch, t: Thing, x: Float, y0: Float, k: Float, fogT: Float, time: Float) {
        val y = y0 + WALL_H * k
        val wpx = 12.5f * k
        val x0 = x - wpx
        mix(STAND, fogT)
        rect(b, x0, y, wpx, TIERS * TIER_M * k)
        for (r in 0 until TIERS) {
            val ry = y + r * TIER_M * k
            mix(STAND_RISER, fogT)
            rect(b, x0, ry, wpx, 0.12f * k)
            if (k < 2.5f) {
                // far off: the crowd is a speckled band
                mix(CROWD[(r + (t.v * 9).toInt()) % CROWD.size], fogT)
                rect(b, x0, ry + 0.15f * k, wpx, 0.25f * k)
                continue
            }
            val people = 24
            for (c in 0 until people) {
                val seed = hash(c * 31 + r * 7 + (t.v * 1000).toInt())
                if (seed < 0.12f) continue                           // an empty seat
                val bob = if (k > 6f) sin(time * (4f + seed * 3f) + seed * 20f) * 0.05f * k else 0f
                val px = x0 + (c + 0.25f) * wpx / people
                mix(CROWD[(seed * 61).toInt() % CROWD.size], fogT)
                rect(b, px, ry + 0.12f * k + bob, 0.32f * k, 0.28f * k)
                mix(SKIN[(seed * 37).toInt() % SKIN.size], fogT)
                rect(b, px + 0.06f * k, ry + 0.4f * k + bob, 0.2f * k, 0.2f * k)
            }
        }
        // the roof and its columns
        val roofY = y + (TIERS * TIER_M + 0.6f) * k
        mix(POLE, fogT)
        rect(b, x0 + 0.2f * k, y, 0.18f * k, roofY - y)
        mix(ROOF, fogT)
        rect(b, x0 - 0.3f * k, roofY, wpx + 0.6f * k, 0.45f * k)
        mix(WALL_TOP, fogT)
        rect(b, x0 - 0.3f * k, roofY, wpx + 0.6f * k, max(1f, 0.08f * k))
    }

    /** The lit things, for the bloom: the tree's bulbs, the lamps at night, neon, the runway lights, the scoreboard. */
    fun drawGlow(b: SpriteBatch, w: Int, h: Int, playerX: Float, time: Float, shakeX: Float, shakeY: Float) {
        val hz = horizon(h) + shakeY
        val f = focal(h)
        val cx = w / 2f + shakeX
        for (t in stuff) {
            val z = t.at - playerX
            if (z < NEAR_M || z > FAR_M) continue
            val (x, y, k) = project(z, t.side, cx, hz, f)
            val a = 1f - (z / FAR_M)
            when (t.kind) {
                Kind.TREE -> {
                    b.color = Color.WHITE
                    tree.drawScaled(b, x, y + 0.4f * k, k / TREE_PX_M, bulbsOnly = true)
                }
                Kind.POLE -> if (night) {
                    b.setColor(LAMP.r, LAMP.g, LAMP.b, 0.7f * a)
                    rect(b, x - t.v * 1.8f * k - 0.5f * k, y + 7.9f * k, 1f * k, 0.35f * k)
                }
                Kind.NEON -> {
                    val c = NEONS[(t.v * 97).toInt() % NEONS.size]
                    val buzz = if (hash((time * 8f).toInt() + (t.v * 50).toInt()) < 0.06f) 0.3f else 1f
                    b.setColor(c.r, c.g, c.b, 0.6f * a * buzz)
                    rect(b, x - 2f * k, y + 3f * k, 4f * k, 1.6f * k)
                }
                Kind.RUNWAY_LIGHT -> {
                    b.setColor(RUNWAY.r, RUNWAY.g, RUNWAY.b, 0.9f * a)
                    rect(b, x - 0.25f * k, y, 0.5f * k, 0.4f * k)
                }
                Kind.SCOREBOARD -> {
                    b.setColor(LCD_AMBER.r, LCD_AMBER.g, LCD_AMBER.b, 0.5f * a)
                    rect(b, x - 3.2f * k, y + 5.4f * k, 6.4f * k, 0.3f * k)
                }
                else -> {}
            }
        }
        b.color = Color.WHITE
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

    /** Spreads an integer into 0..1, the same every frame: where the specks, seats and props fall. */
    private fun hash(i: Int): Float {
        var x = i * 374761393 + 668265263
        x = (x xor (x ushr 13)) * 1274126177
        return ((x xor (x ushr 16)) and 0xffff) / 65535f
    }

    companion object {
        const val SUN_X = 0.66f
        const val SUN_RISE = 46f
        /** Horizon as a share of the screen height from the bottom, focal length as a share of it. */
        const val HORIZON = 0.56f
        const val FOCAL = 1.25f
        /** The driver's eye height (m). */
        const val CAM_H = 1.15f
        /** The eyes' offset from the car's centre line (m): a left-hand-drive seat; right-hand drive flips it. */
        const val SEAT_SIDE = 0.35f
        const val LANE = 3.6f
        const val RIGHT_EDGE = 1.8f
        const val LEFT_EDGE = -LANE - 1.8f
        const val BAND_M = 6f
        /** The concrete walls: their height, how far off the lane edge, how long a panel is. */
        const val WALL_H = 1.0f
        const val WALL_GAP = 0.6f
        const val WALL_PANEL_M = 4f
        /** The catch fence on the walls: post spacing and height above the wall. */
        const val FENCE_M = 5f
        const val FENCE_H = 1.8f
        const val DASH_M = 3f
        const val FAR_M = 420f
        const val NEAR_M = 2.2f
        /** The launch pad's concrete and its seams, the rubber laid down off the line, the tyres' grooves. */
        const val PAD_M = 60f
        const val SEAM_M = 4.5f
        const val RUBBER_M = 25f
        const val TYRE_TRACK = 0.78f
        /** Past the finish: the shutdown before the sand trap and the net. */
        const val SHUTDOWN_M = 190f
        /** The tree stands between the lanes this far past the line; its art is drawn at this many pixels per metre. */
        const val TREE_M = 8f
        const val TREE_PX_M = 28.8f
        const val TIERS = 6
        const val TIER_M = 0.62f
        const val PICADO_H = 5.6f
        const val PICADO_SAG = 0.9f

        val NIGHT = setOf("neon_city", "port")
        val NEON_WORDS = listOf("MOTEL", "RAMEN", "24H", "ARCADE", "DINER", "BAR", "TATTOO", "KARAOKE", "PARTS")

        val EDGE_LINE = Color(0.9f, 0.9f, 0.95f, 1f)
        val CENTRE_LINE = Color(1f, 0.82f, 0.3f, 1f)
        val POLE = Color(0.32f, 0.31f, 0.42f, 1f)
        val LAMP = Color(1f, 0.9f, 0.65f, 1f)
        val LAMP_OFF = Color(0.7f, 0.7f, 0.72f, 1f)
        val STAND = Color(0.22f, 0.2f, 0.32f, 1f)
        val STAND_RISER = Color(0.3f, 0.28f, 0.4f, 1f)
        val ROOF = Color(0.26f, 0.25f, 0.36f, 1f)
        val WALL = Color(0.55f, 0.55f, 0.62f, 1f)
        val WALL_TOP = Color(0.8f, 0.8f, 0.86f, 1f)
        val WALL_B = Color(0.48f, 0.48f, 0.56f, 1f)
        val SNOW = Color(0.94f, 0.96f, 1f, 1f)
        val FENCE = Color(0.42f, 0.42f, 0.5f, 1f)
        val BOARD = Color(0.85f, 0.85f, 0.9f, 1f)
        val BOARD_TEXT = Color(0.12f, 0.1f, 0.2f, 1f)
        val CONE = Color(1f, 0.5f, 0.15f, 1f)
        val GREEN_LAMP = Color(0.3f, 1f, 0.4f, 1f)
        val RED_LAMP = Color(1f, 0.25f, 0.2f, 1f)
        val SAND = Color(0.72f, 0.6f, 0.44f, 1f)
        val SEAM = Color(0.3f, 0.29f, 0.36f, 1f)
        val RUBBER = Color(0.04f, 0.03f, 0.06f, 1f)
        val TRAILER = Color(0.86f, 0.86f, 0.9f, 1f)
        val TYRE = Color(0.06f, 0.06f, 0.08f, 1f)
        val SCORE_FACE = Color(0.06f, 0.05f, 0.1f, 1f)
        val SCORE_TEXT = Color(1f, 0.8f, 0.3f, 1f)
        val LCD_AMBER = Color(1f, 0.7f, 0.2f, 1f)
        val CACTUS = Color(0.3f, 0.5f, 0.3f, 1f)
        val CACTUS_LIT = Color(0.45f, 0.66f, 0.38f, 1f)
        val TRUNK = Color(0.45f, 0.33f, 0.24f, 1f)
        val FROND = Color(0.25f, 0.6f, 0.32f, 1f)
        val FROND_DARK = Color(0.15f, 0.42f, 0.25f, 1f)
        val CRANE = Color(0.85f, 0.3f, 0.22f, 1f)
        val HANGAR = Color(0.6f, 0.62f, 0.68f, 1f)
        val HANGAR_ROOF = Color(0.48f, 0.5f, 0.58f, 1f)
        val HANGAR_DOOR = Color(0.36f, 0.38f, 0.45f, 1f)
        val RUNWAY = Color(0.35f, 0.55f, 1f, 1f)
        val PINE = Color(0.16f, 0.36f, 0.24f, 1f)
        val PINE_DARK = Color(0.1f, 0.26f, 0.18f, 1f)
        val ADS = listOf(Color(0.9f, 0.2f, 0.3f, 1f), Color(0.2f, 0.45f, 0.9f, 1f), Color(1f, 0.75f, 0.15f, 1f),
            Color(0.15f, 0.7f, 0.45f, 1f), Color(0.1f, 0.1f, 0.14f, 1f))
        val TENTS = listOf(Color(0.9f, 0.25f, 0.3f, 1f), Color(0.25f, 0.5f, 0.95f, 1f), Color(1f, 0.8f, 0.2f, 1f),
            Color(0.3f, 0.75f, 0.5f, 1f), Color(0.6f, 0.35f, 0.85f, 1f))
        val NEONS = listOf(Color(1f, 0.3f, 0.7f, 1f), Color(0.3f, 0.9f, 1f, 1f), Color(1f, 0.8f, 0.25f, 1f), Color(0.5f, 1f, 0.4f, 1f))
        val BOXES = listOf(Color(0.75f, 0.25f, 0.2f, 1f), Color(0.2f, 0.4f, 0.7f, 1f), Color(0.85f, 0.6f, 0.2f, 1f),
            Color(0.25f, 0.55f, 0.4f, 1f), Color(0.55f, 0.55f, 0.6f, 1f), Color(0.5f, 0.25f, 0.5f, 1f))
        val PICADO = listOf(Color(1f, 0.3f, 0.55f, 1f), Color(0.3f, 0.8f, 1f, 1f), Color(1f, 0.85f, 0.2f, 1f),
            Color(0.45f, 0.95f, 0.4f, 1f), Color(1f, 0.5f, 0.2f, 1f), Color(0.7f, 0.4f, 1f, 1f))
        val CROWD = listOf(Color(0.9f, 0.3f, 0.4f, 1f), Color(0.3f, 0.6f, 0.9f, 1f), Color(0.95f, 0.85f, 0.4f, 1f),
            Color(0.4f, 0.8f, 0.5f, 1f), Color(0.85f, 0.85f, 0.9f, 1f), Color(0.6f, 0.4f, 0.8f, 1f), Color(0.15f, 0.14f, 0.2f, 1f))
        val SKIN = listOf(Color(0.95f, 0.78f, 0.62f, 1f), Color(0.78f, 0.56f, 0.4f, 1f), Color(0.55f, 0.38f, 0.26f, 1f), Color(0.36f, 0.24f, 0.17f, 1f))
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

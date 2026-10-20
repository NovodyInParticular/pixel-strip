package io.github.novodyinparticular.pixelstrip.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.MathUtils
import io.github.novodyinparticular.pixelstrip.PixelStrip
import io.github.novodyinparticular.pixelstrip.data.CarDef
import io.github.novodyinparticular.pixelstrip.data.Tune
import io.github.novodyinparticular.pixelstrip.data.TuneCode
import io.github.novodyinparticular.pixelstrip.data.Upgrades
import io.github.novodyinparticular.pixelstrip.race.DragMath
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min

/**
 * Deep tuning in five pages: gears and final drive; ignition timing, air/fuel, rev limit and launch
 * control; turbo and blower boost and boost by gear; the nitrous shot, timing and ramp; tyre
 * pressure, ride height and shocks. What the parts don't allow shows locked, with what's needed.
 * The neon gear chart (or the power curve) redraws on every change; presets live per car and
 * tunes travel as share codes through the clipboard.
 */
class TuneScreen(game: PixelStrip) : MenuScreen(game) {
    private val save = game.save
    private val owned = save.selectedCar()!!
    private val stock = game.car(owned.carId)
    private val base = Upgrades.baseGears(stock, game.parts, owned.parts)
    private val lim = Upgrades.limits(stock, game.parts, owned.parts)
    private val built = game.effectiveWith(owned.carId, owned.parts, owned.cosmetics, null)
    private val stockFinal = built.finalDrive
    private val bg = art.tex("scenes/garage.png")

    // the draft being edited
    private val t0 = owned.tune
    private val gears = (t0?.gears?.takeIf { it.size == base.size } ?: base).copyOf()
    private var finalDrive = t0?.finalDrive ?: stockFinal
    private var launch = t0?.launchRpm ?: 0f
    private var nosGear = t0?.nitrousGear ?: 0
    private var psi = t0?.tirePressure ?: CarDef.STOCK_PRESSURE
    private var timing = t0?.timing ?: 0f
    private var afr = t0?.afr ?: built.afr
    private var limiter = t0?.limiter ?: lim.limiterMax
    private var boost = t0?.boostPsi ?: built.boostPsi
    private var scPsi = t0?.scPsi ?: (lim.scMax * 0.8f)
    private val byGear = FloatArray(base.size) { t0?.boostByGear?.getOrNull(it) ?: 1f }
    private var shot = t0?.nitrousShot ?: lim.nitrousMax
    private var ramp = t0?.nitrousRamp ?: 0.6f
    private var ride = t0?.rideHeight ?: 0f
    private var shock = t0?.shockRate ?: 0.5f

    private var reveal = 0f
    private var toast = ""
    private var toastTime = 0f
    private val sparks = ArrayList<FloatArray>()   // x, y, vx, vy, life

    /** [lock]: why it can't be changed on this build (shown instead of the slider). */
    private class Param(
        val label: String, val min: Float, val max: Float, val step: Float,
        val get: () -> Float, val set: (Float) -> Unit, val fmt: (Float) -> String, val lock: String? = null,
    )

    private val pages = listOf("GEARS", "ENGINE", "BOOST", "NITROUS", "CHASSIS")
    private var page = 0

    private fun paramsFor(p: String): List<Param> = buildList {
        when (p) {
            "GEARS" -> {
                for (i in base.indices) add(Param(ordinal(i + 1), base[i] * (1 - lim.gearRange), base[i] * (1 + lim.gearRange), 0.01f, { gears[i] }, { gears[i] = it }, { "%.2f".format(it) }))
                add(Param("FINAL", stockFinal * (1 - lim.finalRange), stockFinal * (1 + lim.finalRange), 0.01f, { finalDrive }, { finalDrive = it }, { "%.2f".format(it) }))
            }
            "ENGINE" -> {
                add(Param("TIMING", -lim.timingRange, lim.timingRange, 0.5f, { timing }, { timing = it }, { "%+.1f".format(it) },
                    if (lim.timingRange <= 0f) "NEEDS AN ECU" else null))
                add(Param("AIR/FUEL", Upgrades.AFR_MIN, Upgrades.AFR_MAX, 0.1f, { afr }, { afr = it }, { "%.1f".format(it) },
                    if (!lim.afr) "NEEDS ECU REFLASH" else null))
                add(Param("REV LIMIT", built.peakHpRpm + 200f, lim.limiterMax, 100f, { limiter }, { limiter = it }, { "${it.toInt()}" },
                    if (!lim.limiter) "NEEDS ECU REFLASH" else null))
                add(Param("LAUNCH", 0f, lim.limiterMax, 100f, { launch }, { launch = it }, { if (it < 1f) "OFF" else "${it.toInt()}" },
                    if (!lim.launchControl) "NEEDS ECU REFLASH" else null))
            }
            "BOOST" -> {
                add(Param("TURBO", Upgrades.SPRING_PSI.coerceAtMost(lim.boostMax), lim.boostMax.coerceAtLeast(1f), 0.5f, { boost }, { boost = it }, { "%.1f PSI".format(it) },
                    when { lim.boostMax <= 0f -> "NO TURBO"; !lim.boostAdjustable -> "NEEDS A BOOST CONTROLLER"; else -> null }))
                add(Param("BLOWER", lim.scMax * 0.5f, lim.scMax.coerceAtLeast(1f), 0.5f, { scPsi }, { scPsi = it }, { "%.1f PSI".format(it) },
                    if (lim.scMax <= 0f) "NO SUPERCHARGER" else null))
                for (i in base.indices) add(Param("BOOST ${ordinal(i + 1)}", 0.2f, 1f, 0.05f, { byGear[i] }, { byGear[i] = it }, { "${(it * 100).toInt()}%" },
                    if (!lim.boostByGear) "NEEDS ELECTRONIC CONTROL + STANDALONE" else null))
            }
            "NITROUS" -> {
                val none = if (lim.nitrousMax <= 0f) "NO NITROUS KIT" else null
                add(Param("SHOT", lim.nitrousMax * 0.25f, lim.nitrousMax.coerceAtLeast(1f), 25f, { shot }, { shot = it }, { "${it.toInt()} HP" }, none))
                add(Param("AUTO", 0f, base.size.toFloat(), 1f, { nosGear.toFloat() }, { nosGear = it.toInt() }, { if (it < 1f) "TAP" else "G${it.toInt()}" },
                    none ?: if (built.nitrous.hp <= 0f) "NO NITROUS KIT" else null))
                add(Param("RAMP", 0.1f, 2f, 0.1f, { ramp }, { ramp = it }, { "%.1f S".format(it) }, none ?: if (!lim.progressive) "NEEDS PROGRESSIVE CONTROLLER" else null))
            }
            "CHASSIS" -> {
                add(Param("TYRE PSI", 12f, 40f, 0.5f, { psi }, { psi = it }, { "%.1f".format(it) }))
                add(Param("RIDE HT", -1f, 1f, 0.1f, { ride }, { ride = it }, { if (it < -0.05f) "LOW %.1f".format(-it) else if (it > 0.05f) "HIGH %.1f".format(it) else "STOCK" },
                    if (!lim.rideHeight) "NEEDS COILOVERS" else null))
                add(Param("SHOCKS", 0f, 1f, 0.05f, { shock }, { shock = it }, { "${(it * 100).toInt()}%" },
                    if (!lim.shocks) "NEEDS DOUBLE ADJUSTABLES" else null))
            }
        }
    }

    private var params: List<Param> = paramsFor(pages[0])
    private val minus = List(MAX_ROWS) { i -> button("-", BTN_GREY) { params.getOrNull(i)?.let { nudge(it, -1) } } }
    private val plus = List(MAX_ROWS) { i -> button("+", BTN_GREY) { params.getOrNull(i)?.let { nudge(it, 1) } } }
    private val pageButtons = pages.mapIndexed { i, name -> button(name, Color(BTN_PURPLE)) { page = i; params = paramsFor(name); changed() } }
    private val slots = List(5) { i -> button(if (i == 0) "STOCK" else "$i", Color(BTN_PURPLE)) { loadSlot(i - 1) } }
    private val saveBtn = button("SAVE", BTN_RED, keys = intArrayOf(Input.Keys.S)) { saveSlot() }
    private val share = button("SHARE", BTN_TEAL) { shareCode() }
    private val paste = button("PASTE", BTN_TEAL) { pasteCode() }
    private val back = button("BACK", BTN_GREY, keys = intArrayOf(Input.Keys.B)) { onBack() }
    private val runsBtn = button("RUNS", BTN_PURPLE, keys = intArrayOf(Input.Keys.R)) { game.go(0) { RunLogScreen(game) } }
    private val dynoBtn = button("DYNO", BTN_PURPLE, keys = intArrayOf(Input.Keys.Y)) { game.go(0) { DynoScreen(game) } }

    private fun ordinal(n: Int) = when (n) { 1 -> "1ST"; 2 -> "2ND"; 3 -> "3RD"; else -> "${n}TH" }

    private fun draft() = Tune(
        t0?.name ?: "TUNE", if (gears.contentEquals(base)) null else gears.copyOf(), finalDrive, launch, nosGear, psi,
        boostPsi = if (lim.boostAdjustable) boost else null,
        boostByGear = if (lim.boostByGear && byGear.any { it < 1f }) byGear.copyOf() else null,
        scPsi = if (lim.scMax > 0f) scPsi else null,
        timing = timing,
        afr = if (lim.afr) afr else null,
        limiter = if (lim.limiter && limiter < lim.limiterMax) limiter else null,
        nitrousShot = if (lim.nitrousMax > 0f) shot else null,
        nitrousRamp = ramp,
        rideHeight = ride,
        shockRate = shock,
    )

    private fun changed() {
        reveal = 0f
        sparks.clear()
    }

    private fun nudge(p: Param, d: Int) {
        if (p.lock != null) return say(p.lock)
        p.set((p.get() + d * p.step).coerceIn(p.min, p.max))
        changed()
    }

    private fun say(text: String) {
        toast = text
        toastTime = 2.4f
    }

    private fun loadSlot(i: Int) {
        val t = if (i < 0) null else owned.tunes.getOrNull(i)
        if (i >= 0 && t == null) {
            say("EMPTY SLOT: TUNE, THEN SAVE")
            owned.activeTune = i.coerceAtMost(owned.tunes.size)
        } else {
            owned.activeTune = if (i < 0) -1 else i
            apply(t)
            save.write()
            say(if (t == null) "STOCK SETUP" else "LOADED ${t.name}")
        }
    }

    private fun apply(t: Tune?) {
        val g = t?.gears?.takeIf { it.size == base.size } ?: base
        g.copyInto(gears)
        finalDrive = t?.finalDrive ?: stockFinal
        launch = t?.launchRpm ?: 0f
        nosGear = t?.nitrousGear ?: 0
        psi = t?.tirePressure ?: CarDef.STOCK_PRESSURE
        timing = t?.timing ?: 0f
        afr = t?.afr ?: built.afr
        limiter = t?.limiter ?: lim.limiterMax
        boost = t?.boostPsi ?: built.boostPsi
        scPsi = t?.scPsi ?: (lim.scMax * 0.8f)
        for (i in byGear.indices) byGear[i] = t?.boostByGear?.getOrNull(i) ?: 1f
        shot = t?.nitrousShot ?: lim.nitrousMax
        ramp = t?.nitrousRamp ?: 0.6f
        ride = t?.rideHeight ?: 0f
        shock = t?.shockRate ?: 0.5f
        changed()
    }

    private fun saveSlot() {
        var slot = owned.activeTune
        if (slot < 0) slot = owned.tunes.size
        if (slot >= MAX_PRESETS) {
            say("ALL ${MAX_PRESETS} SLOTS USED: PICK ONE")
            return
        }
        val t = draft().copy(name = "TUNE ${slot + 1}")
        if (slot < owned.tunes.size) owned.tunes[slot] = t else owned.tunes.add(t)
        owned.activeTune = slot
        save.write()
        flash = 0.4f
        flashColor.set(GREEN)
        say("SAVED TO SLOT ${slot + 1}")
    }

    private fun shareCode() {
        val code = TuneCode.encode(owned.carId, draft(), base.size, stockFinal)
        Gdx.app.clipboard.contents = code
        say(code)
    }

    private fun pasteCode() {
        val text = Gdx.app.clipboard.contents ?: ""
        val t = TuneCode.decode(text, owned.carId, base.size)
        if (t == null) {
            say("NO VALID CODE FOR THIS CAR")
            flash = 0.3f
            flashColor.set(RED)
            return
        }
        apply(t)
        say("TUNE IMPORTED: SAVE TO KEEP")
        flash = 0.4f
        flashColor.set(CYAN)
    }

    override fun onBack() = game.go(0) { GarageScreen(game) }

    // --- Layout and input --------------------------------------------------------------------

    private fun rowY(h: Int, i: Int) = h - 88f - i * 15f
    private fun trackX() = 128f
    private val trackW = 68f

    override fun layout(w: Int, h: Int) {
        minus.indices.forEach { i ->
            val show = i < params.size && params[i].lock == null
            minus[i].apply { visible = show; x = 94f; y = rowY(h, i) - 3f; this.w = 14f; this.h = 12f }
            plus[i].apply { visible = show; x = 110f; y = rowY(h, i) - 3f; this.w = 14f; this.h = 12f }
        }
        var px = 8f
        pageButtons.forEachIndexed { i, b ->
            b.w = font.width(pages[i]) + 10f; b.h = 14f; b.x = px; b.y = h - 70f
            px += b.w + 3f
            b.face.set(if (i == page) PINK else BTN_PURPLE)
        }
        var sx = 8f
        slots.forEachIndexed { i, b ->
            b.x = sx; b.y = 36f; b.w = if (i == 0) 44f else 20f; b.h = 16f
            sx += b.w + 3f
            val active = owned.activeTune == i - 1
            b.face.set(if (active) PINK else if (i > 0 && owned.tunes.getOrNull(i - 1) == null) BTN_GREY else BTN_PURPLE)
        }
        saveBtn.apply { x = 8f; y = 10f; this.w = 50f; this.h = 20f }
        share.apply { x = 62f; y = 10f; this.w = 54f; this.h = 20f }
        paste.apply { x = 120f; y = 10f; this.w = 54f; this.h = 20f }
        back.apply { x = w - 64f; y = 10f; this.w = 56f; this.h = 20f }
        dynoBtn.apply { x = w - 116f; y = 10f; this.w = 48f; this.h = 20f }
        runsBtn.apply { x = w - 168f; y = 10f; this.w = 48f; this.h = 20f }
    }

    override fun update(dt: Float) {
        reveal = min(1f, reveal + dt * 1.1f)
        toastTime = (toastTime - dt).coerceAtLeast(0f)
        // dragging on a slider track sets the value directly
        for (p in 0 until 5) {
            if (!Gdx.input.isTouched(p)) continue
            val tx = post.toBufferX(Gdx.input.getX(p))
            val ty = post.toBufferY(Gdx.input.getY(p))
            params.forEachIndexed { i, prm ->
                if (prm.lock != null) return@forEachIndexed
                val y = rowY(post.height, i)
                if (tx in trackX() - 3f..trackX() + trackW + 3f && ty in y - 4f..y + 9f) {
                    val t = ((tx - trackX()) / trackW).coerceIn(0f, 1f)
                    val v = prm.min + (prm.max - prm.min) * t
                    val snapped = (Math.round(v / prm.step) * prm.step).coerceIn(prm.min, prm.max)
                    if (abs(snapped - prm.get()) > prm.step * 0.5f) {
                        prm.set(snapped)
                        changed()
                    }
                }
            }
        }
        // sparks fly off each shift point once the chart has drawn in
        if (reveal >= 1f && MathUtils.random() < 0.25f && shiftDrops.isNotEmpty() && pages[page] in GEAR_PAGES) {
            val (x, y) = shiftDrops[MathUtils.random(shiftDrops.size - 1)]
            repeat(3) { sparks.add(floatArrayOf(x, y, MathUtils.random(-40f, 40f), MathUtils.random(10f, 60f), 0.5f)) }
        }
        sparks.forEach { it[0] += it[2] * dt; it[1] += it[3] * dt; it[3] -= 160f * dt; it[4] -= dt }
        sparks.removeAll { it[4] <= 0f }
    }

    // --- Gear chart --------------------------------------------------------------------------

    private var shiftDrops: List<Pair<Float, Float>> = emptyList()

    private fun chartRect(w: Int, h: Int) = floatArrayOf(206f, 62f, w - 214f, h - 142f)

    private fun drawChart(w: Int, h: Int, glow: Boolean) {
        val def = game.effectiveWith(owned.carId, owned.parts, owned.cosmetics, draft())
        val (cx, cy, cw, ch) = chartRect(w, h).let { Quad(it[0], it[1], it[2], it[3]) }
        val r = def.tireRadiusM
        val redline = def.redline
        val mph = { rpm: Float, g: Int -> rpm / (def.gears[g] * def.finalDrive) * 2f * Math.PI.toFloat() * r / 60f * 2.23694f }
        val vMax = mph(redline, def.gears.size - 1) * 1.05f
        val rMax = redline * 1.08f
        fun px(v: Float) = cx + v / vMax * cw
        fun py(rpm: Float) = cy + rpm / rMax * ch
        if (!glow) {
            panel(cx - 4f, cy - 14f, cw + 8f, ch + 22f, CYAN, 0.86f)
            // power band and redline
            batch.setColor(0.2f, 0.55f, 0.25f, 0.25f)
            batch.draw(white, cx, py(def.peakTorqueRpm), cw, py(def.peakHpRpm) - py(def.peakTorqueRpm))
            batch.setColor(0.9f, 0.2f, 0.2f, 0.5f)
            batch.draw(white, cx, py(redline), cw, 1f)
            batch.color = Color.WHITE
            for (k in 1..(redline / 1000f).toInt()) {
                batch.setColor(0.3f, 0.3f, 0.45f, 0.6f)
                batch.draw(white, cx, floor(py(k * 1000f)), cw, 1f)
                font.draw(batch, "$k", cx - 4f - font.width("$k"), floor(py(k * 1000f)) - 3f, SOFT, 1, null)
            }
            var spd = 20
            while (spd < vMax - 18f) {
                font.draw(batch, "$spd", floor(px(spd.toFloat())) - 6f, cy - 11f, SOFT, 1, null)
                spd += if (vMax > 160f) 40 else 20
            }
            batch.color = Color.WHITE
        }
        // every gear as a dim line, the shift path (best shift points) bright, drawn in over time
        val shifts = DragMath.shiftPoints(def)
        val drops = ArrayList<Pair<Float, Float>>()
        val total = cw * reveal
        for (g in def.gears.indices) {
            val vTop = mph(redline, g)
            var x = cx
            while (x <= px(vTop) && x - cx <= total) {
                val v = (x - cx) / cw * vMax
                val rpm = v / mph(1f, g)
                batch.setColor(GEAR_COLORS[g % GEAR_COLORS.size].r, GEAR_COLORS[g % GEAR_COLORS.size].g, GEAR_COLORS[g % GEAR_COLORS.size].b, if (glow) 0.25f else 0.35f)
                batch.draw(white, floor(x), floor(py(rpm)), 1f, 1f)
                x += 1f
            }
        }
        var vStart = 0f
        for (g in def.gears.indices) {
            val vShift = if (g < def.gears.size - 1) mph(shifts[g], g) else vMax
            val c = GEAR_COLORS[g % GEAR_COLORS.size]
            var x = px(vStart)
            while (x <= px(vShift) && x - cx <= total) {
                val v = (x - cx) / cw * vMax
                val rpm = (v / mph(1f, g)).coerceAtMost(redline)
                batch.color = c
                batch.draw(white, floor(x), floor(py(rpm)), 2f, 2f)
                x += 1f
            }
            if (g < def.gears.size - 1 && px(vShift) - cx <= total) {
                val from = py(shifts[g])
                val to = py(vShift / mph(1f, g + 1))
                batch.color = c
                batch.draw(white, floor(px(vShift)), floor(to), 2f, from - to)
                drops.add(px(vShift) to from)
            }
            vStart = vShift
        }
        batch.color = Color.WHITE
        if (!glow) shiftDrops = drops
        for (s in sparks) {
            batch.setColor(1f, 0.85f, 0.4f, s[4] * 2f)
            batch.draw(white, floor(s[0]), floor(s[1]), 1f, 1f)
        }
        batch.color = Color.WHITE
        if (!glow) {
            font.draw(batch, "MPH", cx + cw - font.width("MPH"), cy - 11f, SOFT, 1, null)
            font.draw(batch, "RPM x1000", cx, cy + ch + 2f, SOFT, 1, null)
        }
    }

    private data class Quad(val a: Float, val b: Float, val c: Float, val d: Float)

    // --- Power chart (engine, boost and nitrous pages) ------------------------------------------

    /** Horsepower and torque across the rev range as tuned, knock- or fuel-limited stretches in red. */
    private fun drawPower(w: Int, h: Int, glow: Boolean) {
        val def = game.effectiveWith(owned.carId, owned.parts, owned.cosmetics, draft())
        val (cx, cy, cw, ch) = chartRect(w, h).let { Quad(it[0], it[1], it[2], it[3]) }
        val rMax = def.redline * 1.05f
        val why = arrayOf(DragMath.Limit.NONE)
        val n = cw.toInt()
        val hp = FloatArray(n)
        val tq = FloatArray(n)
        val held = BooleanArray(n)
        var top = 1f
        for (i in 0 until n) {
            val r = def.idleRpm + (rMax - def.idleRpm) * i / (n - 1)
            if (r > def.redline) continue
            var t = DragMath.crankTorque(def, r, why)
            if (pages[page] == "NITROUS" && def.nitrous.hp > 0f) {
                t = DragMath.limit(def, r, DragMath.torqueAt(def, r), t + def.nitrous.hp * 745.7f / (maxOf(r, 2800f) * 0.10471976f), why)
            }
            tq[i] = t
            hp[i] = t * r / 7127f
            held[i] = why[0] != DragMath.Limit.NONE
            top = maxOf(top, hp[i], tq[i] * 0.74f)
        }
        if (!glow) panel(cx - 4f, cy - 14f, cw + 8f, ch + 22f, CYAN, 0.86f)
        val shown = (n * reveal).toInt()
        for (i in 0 until shown) {
            if (hp[i] <= 0f) continue
            val x = cx + i
            batch.color = if (held[i]) RED else PINK
            batch.draw(white, x, floor(cy + hp[i] / top * ch), 1f, 2f)
            batch.color = ORANGE
            batch.draw(white, x, floor(cy + tq[i] * 0.74f / top * ch), 1f, 1f)
        }
        batch.color = Color.WHITE
        if (!glow) {
            val peak = hp.max()
            font.draw(batch, "${peak.toInt()} HP", cx + 4f, cy + ch - 2f, PINK, 1, null)
            font.draw(batch, "${tq.max().toInt()} NM", cx + 4f, cy + ch - 12f, ORANGE, 1, null)
            if (held.any { it }) font.draw(batch, if (why[0] == DragMath.Limit.FUEL) "LEAN: FUEL LIMIT" else "KNOCK: TIMING PULLED", cx + 4f, cy + ch - 22f, RED, 1, null)
            font.draw(batch, "RPM", cx + cw - font.width("RPM"), cy - 11f, SOFT, 1, null)
        }
    }

    // --- Passes ------------------------------------------------------------------------------

    override fun drawScene(w: Int, h: Int) {
        batch.draw(bg, floor((w - bg.width) / 2f), 0f)
        batch.setColor(0.02f, 0.01f, 0.05f, 0.75f)
        batch.draw(white, 0f, 0f, w.toFloat(), h.toFloat())
        batch.color = Color.WHITE
    }

    override fun drawGlow(w: Int, h: Int) = if (pages[page] in GEAR_PAGES) drawChart(w, h, glow = true) else drawPower(w, h, glow = true)

    override fun drawUi(w: Int, h: Int) {
        font.draw(batch, "TUNING", 8f, h - 24f, PINK, 2)
        font.draw(batch, stock.name.uppercase(), 8f, h - 38f, SOFT)
        cash(w - 8f, h - 20f)
        params.forEachIndexed { i, p ->
            val y = rowY(h, i)
            font.draw(batch, p.label, 8f, y, if (p.lock != null) DIM else SOFT, 1, null)
            if (p.lock != null) {
                font.draw(batch, p.lock, 60f, y, DIM, 1, null)
                return@forEachIndexed
            }
            font.draw(batch, p.fmt(p.get()), 52f, y, Color.WHITE, 1, null)
            // slider track and knob
            batch.setColor(0.15f, 0.12f, 0.25f, 1f)
            batch.draw(white, trackX(), y + 2f, trackW, 3f)
            val t = ((p.get() - p.min) / (p.max - p.min)).coerceIn(0f, 1f)
            batch.color = if (pages[page] == "GEARS" && i < base.size) GEAR_COLORS[i % GEAR_COLORS.size] else YELLOW
            batch.draw(white, floor(trackX() + t * (trackW - 3f)), y, 3f, 7f)
            batch.color = Color.WHITE
        }
        if (pages[page] in GEAR_PAGES) drawChart(w, h, glow = false) else drawPower(w, h, glow = false)
        val def = game.effectiveWith(owned.carId, owned.parts, owned.cosmetics, draft())
        val now = DragMath.perfectPass(def)
        val stockPass = DragMath.perfectPass(game.effectiveWith(owned.carId, owned.parts, owned.cosmetics, null))
        val c = chartRect(w, h)
        val better = now.quarter <= stockPass.quarter
        font.draw(batch, "1/4 %.3f  60' %.3f  %.0f MPH".format(now.quarter, now.sixty, now.trapMph), c[0], 40f, if (better) GREEN else ORANGE, 1, null)
        font.draw(batch, "UNTUNED %.3f".format(stockPass.quarter), c[0], 30f, SOFT, 1, null)
        if (toastTime > 0f) {
            val tw = font.width(toast) + 12f
            panel(w / 2f - tw / 2f, h / 2f - 8f, tw, 18f, YELLOW, 0.92f)
            font.drawCentered(batch, toast, w / 2f, h / 2f - 2f, YELLOW, 1, null)
        }
    }

    companion object {
        const val MAX_PRESETS = 4
        const val MAX_ROWS = 8
        val GEAR_PAGES = setOf("GEARS", "CHASSIS")
        val DIM = Color(0.42f, 0.4f, 0.55f, 1f)
        val GEAR_COLORS = arrayOf(
            Color(1f, 0.37f, 0.68f, 1f), Color(1f, 0.6f, 0.24f, 1f), Color(1f, 0.91f, 0.56f, 1f),
            Color(0.58f, 0.84f, 0.42f, 1f), Color(0.49f, 0.95f, 0.86f, 1f), Color(0.45f, 0.6f, 1f, 1f),
        )
    }
}

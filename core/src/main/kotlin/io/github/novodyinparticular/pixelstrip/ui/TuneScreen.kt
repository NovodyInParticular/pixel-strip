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
 * Deep tuning: every gear, the final drive, launch control, nitrous timing and tyre pressure,
 * within the ranges the installed parts allow. The neon gear chart redraws on every change;
 * presets live per car and tunes travel as share codes through the clipboard.
 */
class TuneScreen(game: PixelStrip) : MenuScreen(game) {
    private val save = game.save
    private val owned = save.selectedCar()!!
    private val stock = game.car(owned.carId)
    private val base = Upgrades.baseGears(stock, game.parts, owned.parts)
    private val ranges = Upgrades.ranges(game.parts, owned.parts)
    private val bg = art.tex("scenes/garage.png")

    // the draft being edited
    private val gears = (owned.tune?.gears?.takeIf { it.size == base.size } ?: base).copyOf()
    private var finalDrive = owned.tune?.finalDrive ?: stock.finalDrive
    private var launch = owned.tune?.launchRpm ?: 0f
    private var nosGear = owned.tune?.nitrousGear ?: 0
    private var psi = owned.tune?.tirePressure ?: CarDef.STOCK_PRESSURE

    private var reveal = 0f
    private var toast = ""
    private var toastTime = 0f
    private val sparks = ArrayList<FloatArray>()   // x, y, vx, vy, life

    private class Param(val label: String, val min: Float, val max: Float, val step: Float, val get: () -> Float, val set: (Float) -> Unit, val fmt: (Float) -> String)

    private val params: List<Param> = buildList {
        for (i in base.indices) add(Param(ordinal(i + 1), base[i] * (1 - ranges.gear), base[i] * (1 + ranges.gear), 0.01f, { gears[i] }, { gears[i] = it }, { "%.2f".format(it) }))
        add(Param("FINAL", stock.finalDrive * (1 - ranges.finalDrive), stock.finalDrive * (1 + ranges.finalDrive), 0.01f, { finalDrive }, { finalDrive = it }, { "%.2f".format(it) }))
        add(Param("LAUNCH", 0f, stock.redline, 100f, { launch }, { launch = it }, { if (it < 1f) "OFF" else "${it.toInt()}" }))
        add(Param("NOS", 0f, base.size.toFloat(), 1f, { nosGear.toFloat() }, { nosGear = it.toInt() }, { if (it < 1f) "TAP" else "G${it.toInt()}" }))
        add(Param("PSI", 12f, 40f, 0.5f, { psi }, { psi = it }, { "%.1f".format(it) }))
    }
    private val minus = params.map { p -> button("-", BTN_GREY) { nudge(p, -1) } }
    private val plus = params.map { p -> button("+", BTN_GREY) { nudge(p, 1) } }
    private val slots = List(5) { i -> button(if (i == 0) "STOCK" else "$i", Color(BTN_PURPLE)) { loadSlot(i - 1) } }
    private val saveBtn = button("SAVE", BTN_RED, keys = intArrayOf(Input.Keys.S)) { saveSlot() }
    private val share = button("SHARE", BTN_TEAL) { shareCode() }
    private val paste = button("PASTE", BTN_TEAL) { pasteCode() }
    private val back = button("BACK", BTN_GREY, keys = intArrayOf(Input.Keys.B)) { onBack() }

    private fun ordinal(n: Int) = when (n) { 1 -> "1ST"; 2 -> "2ND"; 3 -> "3RD"; else -> "${n}TH" }

    private fun draft() = Tune(owned.tune?.name ?: "TUNE", if (gears.contentEquals(base)) null else gears.copyOf(), finalDrive, launch, nosGear, psi)

    private fun changed() {
        reveal = 0f
        sparks.clear()
    }

    private fun nudge(p: Param, d: Int) {
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
        finalDrive = t?.finalDrive ?: stock.finalDrive
        launch = t?.launchRpm ?: 0f
        nosGear = t?.nitrousGear ?: 0
        psi = t?.tirePressure ?: CarDef.STOCK_PRESSURE
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
        val code = TuneCode.encode(owned.carId, draft(), base.size, stock.finalDrive)
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

    private fun rowY(h: Int, i: Int) = h - 70f - i * 15f
    private fun trackX() = 128f
    private val trackW = 68f

    override fun layout(w: Int, h: Int) {
        params.indices.forEach { i ->
            minus[i].apply { x = 94f; y = rowY(h, i) - 3f; this.w = 14f; this.h = 12f }
            plus[i].apply { x = 110f; y = rowY(h, i) - 3f; this.w = 14f; this.h = 12f }
        }
        var x = 8f
        slots.forEachIndexed { i, b ->
            b.x = x; b.y = 36f; b.w = if (i == 0) 44f else 20f; b.h = 16f
            x += b.w + 3f
            val active = owned.activeTune == i - 1
            b.face.set(if (active) PINK else if (i > 0 && owned.tunes.getOrNull(i - 1) == null) BTN_GREY else BTN_PURPLE)
        }
        saveBtn.apply { x = 8f; y = 10f; this.w = 50f; this.h = 20f }
        share.apply { x = 62f; y = 10f; this.w = 54f; this.h = 20f }
        paste.apply { x = 120f; y = 10f; this.w = 54f; this.h = 20f }
        back.apply { x = w - 64f; y = 10f; this.w = 56f; this.h = 20f }
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
        if (reveal >= 1f && MathUtils.random() < 0.25f && shiftDrops.isNotEmpty()) {
            val (x, y) = shiftDrops[MathUtils.random(shiftDrops.size - 1)]
            repeat(3) { sparks.add(floatArrayOf(x, y, MathUtils.random(-40f, 40f), MathUtils.random(10f, 60f), 0.5f)) }
        }
        sparks.forEach { it[0] += it[2] * dt; it[1] += it[3] * dt; it[3] -= 160f * dt; it[4] -= dt }
        sparks.removeAll { it[4] <= 0f }
    }

    // --- Gear chart --------------------------------------------------------------------------

    private var shiftDrops: List<Pair<Float, Float>> = emptyList()

    private fun chartRect(w: Int, h: Int) = floatArrayOf(206f, 62f, w - 214f, h - 120f)

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

    // --- Passes ------------------------------------------------------------------------------

    override fun drawScene(w: Int, h: Int) {
        batch.draw(bg, floor((w - bg.width) / 2f), 0f)
        batch.setColor(0.02f, 0.01f, 0.05f, 0.75f)
        batch.draw(white, 0f, 0f, w.toFloat(), h.toFloat())
        batch.color = Color.WHITE
    }

    override fun drawGlow(w: Int, h: Int) = drawChart(w, h, glow = true)

    override fun drawUi(w: Int, h: Int) {
        font.draw(batch, "TUNING", 8f, h - 24f, PINK, 2)
        font.draw(batch, stock.name.uppercase(), 8f, h - 38f, SOFT)
        cash(w - 8f, h - 20f)
        params.forEachIndexed { i, p ->
            val y = rowY(h, i)
            font.draw(batch, p.label, 8f, y, SOFT, 1, null)
            font.draw(batch, p.fmt(p.get()), 52f, y, Color.WHITE, 1, null)
            // slider track and knob
            batch.setColor(0.15f, 0.12f, 0.25f, 1f)
            batch.draw(white, trackX(), y + 2f, trackW, 3f)
            val t = ((p.get() - p.min) / (p.max - p.min)).coerceIn(0f, 1f)
            batch.color = if (i < base.size) GEAR_COLORS[i % GEAR_COLORS.size] else YELLOW
            batch.draw(white, floor(trackX() + t * (trackW - 3f)), y, 3f, 7f)
            batch.color = Color.WHITE
        }
        drawChart(w, h, glow = false)
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
        val GEAR_COLORS = arrayOf(
            Color(1f, 0.37f, 0.68f, 1f), Color(1f, 0.6f, 0.24f, 1f), Color(1f, 0.91f, 0.56f, 1f),
            Color(0.58f, 0.84f, 0.42f, 1f), Color(0.49f, 0.95f, 0.86f, 1f), Color(0.45f, 0.6f, 1f, 1f),
        )
    }
}

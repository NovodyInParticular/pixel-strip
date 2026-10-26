package io.github.novodyinparticular.pixelstrip.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import io.github.novodyinparticular.pixelstrip.PixelStrip
import io.github.novodyinparticular.pixelstrip.data.Career
import io.github.novodyinparticular.pixelstrip.race.DragMath
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The career map: four glowing districts, a marker per rival, bosses big and pulsing. Beating a
 * boss reveals a barn out past the district's edge with a rare car under a tarp.
 */
class CareerScreen(game: PixelStrip) : MenuScreen(game) {
    private val map = art.tex("scenes/career_map.png")
    private val mapGlow = art.tex("scenes/career_map_glow.png")
    private val career = game.career
    private val save = game.save
    private val all = career.rivals
    private var selected = all.indexOfFirst { it.id !in save.beaten }.let { if (it < 0) all.size - 1 else it }

    private val race = button("RACE", BTN_RED, big = true, keys = intArrayOf(Input.Keys.ENTER)) { start() }
    private val back = button("GARAGE", BTN_GREY, keys = intArrayOf(Input.Keys.B)) { onBack() }
    private val slips = button("PINK SLIP", Color(0.75f, 0.15f, 0.4f, 1f), keys = intArrayOf(Input.Keys.P)) { pinkSlip() }
    private var confirmSlip = 0f

    /** A rematch for cars: only against beaten rivals, never with your last car. Tap twice. */
    private fun pinkSlip() {
        if (rival.id !in save.beaten || save.cars.size < 2) return
        if (confirmSlip <= 0f) {
            confirmSlip = 3f
            return
        }
        val r = rival
        game.go(1) { VsScreen(game, r, pinkSlip = true) }
    }

    private val prev = button("<", BTN_GREY, keys = intArrayOf(Input.Keys.LEFT)) { selected = (selected - 1 + all.size) % all.size }
    private val next = button(">", BTN_GREY, keys = intArrayOf(Input.Keys.RIGHT)) { selected = (selected + 1) % all.size }

    private val rival get() = all[selected]

    /** A barn tapped on the map (null: a rival is selected). Barns show once their boss is beaten. */
    private var barn: Career.Barn? = null
    private val barns get() = career.barns.filter { it.after in save.beaten && it.carId !in save.found }

    private fun openBarn() {
        val b = barn ?: return
        if (!game.commit(io.github.novodyinparticular.pixelstrip.save.Entry("find", game.now(), car = b.carId))) return
        game.save.selected = game.save.cars.last().uid
        game.save.write()
        game.go(2) { PrizeScreen(game, b) }
    }
    override val song = "career"

    private fun start() {
        if (barn != null) return openBarn()
        if (!career.unlocked(save, rival)) {
            flash = 0.3f
            flashColor.set(RED)
            return
        }
        val r = rival
        game.go(if (r.boss) 1 else 0) { VsScreen(game, r) }
    }

    override fun onBack() = game.go(2) { GarageScreen(game) }

    private fun bx(w: Int) = floor((w - map.width) / 2f)
    private fun nodeX(w: Int, r: Career.Rival) = bx(w) + r.x
    private fun nodeY(r: Career.Rival) = 270f - r.y

    override fun layout(w: Int, h: Int) {
        race.apply {
            x = w - 96f; y = 8f; this.w = 88f; this.h = 34f
            label = if (barn != null) "OPEN" else if (career.unlocked(save, rival)) "RACE" else "LOCKED"
        }
        back.apply { x = 8f; y = 8f; this.w = 64f; this.h = 20f }
        slips.apply {
            x = w - 96f; y = 46f; this.w = 88f; this.h = 16f
            visible = barn == null && rival.id in save.beaten && save.cars.size >= 2
            label = if (confirmSlip > 0f) "RISK IT?" else "PINK SLIP"
        }
        prev.apply { x = 78f; y = 8f; this.w = 20f; this.h = 20f }
        next.apply { x = 102f; y = 8f; this.w = 20f; this.h = 20f }
    }

    override fun update(dt: Float) {
        confirmSlip = (confirmSlip - dt).coerceAtLeast(0f)
        if (Gdx.input.justTouched() && !game.transitioning) {
            val tx = post.toBufferX(Gdx.input.x)
            val ty = post.toBufferY(Gdx.input.y)
            all.forEachIndexed { i, r ->
                if (hypot(tx - nodeX(post.width, r), ty - nodeY(r)) < (if (r.boss) 18f else 13f)) {
                    selected = i
                    barn = null
                }
            }
            for (b in barns) if (hypot(tx - (bx(post.width) + b.x), ty - (270f - b.y)) < 14f) barn = b
        }
        if (prev.justPressed || next.justPressed) barn = null
    }

    override fun drawScene(w: Int, h: Int) {
        batch.draw(map, bx(w), 0f)
        drawPaths(w, false)
        for (r in all) drawNode(w, r)
        for (b in barns) drawBarn(w, b)
        // district names
        for (t in career.tiers) {
            val first = t.rivals.first()
            font.draw(batch, t.name, nodeX(w, first) - 30f, nodeY(first) + 22f, classColor(t.name), 1)
        }
    }

    private fun drawPaths(w: Int, glow: Boolean) {
        for ((a, b) in all.zipWithNext()) {
            val lit = a.id in save.beaten
            if (glow && !lit) continue
            val ax = nodeX(w, a); val ay = nodeY(a); val bx = nodeX(w, b); val by = nodeY(b)
            val n = hypot(bx - ax, by - ay).toInt()
            for (i in 0..n step 2) {
                val x = ax + (bx - ax) * i / n
                val y = ay + (by - ay) * i / n
                batch.color = if (lit) GREEN else Color(0.35f, 0.33f, 0.5f, 1f)
                batch.draw(white, floor(x), floor(y), 1f, 1f)
            }
        }
        batch.color = Color.WHITE
    }

    private fun drawNode(w: Int, r: Career.Rival) {
        val size = if (r.boss) 30f else 20f
        val x = floor(nodeX(w, r) - size / 2f)
        val y = floor(nodeY(r) - size / 2f)
        val unlocked = career.unlocked(save, r)
        val beaten = r.id in save.beaten
        val sel = all[selected] == r
        val edge = when {
            sel -> YELLOW
            beaten -> GREEN
            r.boss -> RED
            else -> classColor(career.tierOf(r).name)
        }
        val pulse = if (r.boss && unlocked && !beaten) 0.5f + 0.5f * sin(time * 5f) else 1f
        batch.setColor(edge.r * pulse, edge.g * pulse, edge.b * pulse, 1f)
        batch.draw(white, x - 2f, y - 2f, size + 4f, size + 4f)
        batch.setColor(0.02f, 0.01f, 0.05f, 1f)
        batch.draw(white, x - 1f, y - 1f, size + 2f, size + 2f)
        if (unlocked) {
            batch.color = if (beaten) Color(0.6f, 0.65f, 0.6f, 1f) else Color.WHITE
            batch.draw(art.tex("portraits/${r.id}.png"), x, y, size, size)
        } else {
            batch.color = Color.WHITE
            font.drawCentered(batch, "?", x + size / 2f, y + size / 2f - 3f, SOFT, 1, null)
        }
        batch.color = Color.WHITE
        if (beaten) {
            batch.color = GREEN
            batch.draw(white, x + size - 6f, y + 2f, 2f, 2f)
            batch.draw(white, x + size - 4f, y + 4f, 2f, 2f)
            batch.draw(white, x + size - 2f, y + 6f, 2f, 4f)
            batch.color = Color.WHITE
        }
    }

    /** A little red barn with a white X on its doors and a light in the loft window. */
    private fun drawBarn(w: Int, b: Career.Barn) {
        val x = floor(bx(w) + b.x - 10f)
        val y = floor(270f - b.y - 9f)
        val sel = barn == b
        batch.color = if (sel) YELLOW else BARN_EDGE
        batch.draw(white, x - 1f, y - 1f, 22f, 14f)
        for (i in 0..6) batch.draw(white, x + 3f + i, y + 12f + i, 14f - 2f * i, 1f)          // roof outline
        batch.color = BARN_RED
        batch.draw(white, x, y, 20f, 12f)
        batch.color = BARN_ROOF
        for (i in 0..5) batch.draw(white, x + 4f + i, y + 12f + i, 12f - 2f * i, 1f)
        batch.color = BARN_TRIM
        batch.draw(white, x + 6f, y, 8f, 8f)
        batch.color = BARN_RED
        batch.draw(white, x + 7f, y, 6f, 7f)
        batch.color = BARN_TRIM
        for (i in 0..5) {
            batch.draw(white, x + 7f + i, y + i.toFloat(), 1f, 1f)
            batch.draw(white, x + 12f - i, y + i.toFloat(), 1f, 1f)
        }
        val glint = 0.6f + 0.4f * sin(time * 4f + b.x)
        batch.setColor(1f, 0.85f * glint, 0.4f * glint, 1f)
        batch.draw(white, x + 9f, y + 13f, 2f, 2f)                 // loft window
        batch.color = Color.WHITE
    }

    override fun drawGlow(w: Int, h: Int) {
        batch.draw(mapGlow, bx(w), 0f)
        drawPaths(w, true)
        for (b in barns) {
            batch.setColor(1f, 0.7f, 0.3f, 0.5f + 0.4f * sin(time * 4f + b.x))
            batch.draw(white, floor(bx(w) + b.x - 1f), floor(270f - b.y + 4f), 2f, 2f)
        }
        barn?.let { b ->
            batch.setColor(1f, 0.9f, 0.5f, 0.6f + 0.4f * sin(time * 6f))
            batch.draw(white, bx(w) + b.x - 12f, 270f - b.y - 11f, 24f, 26f)
            batch.color = Color.WHITE
            return
        }
        val r = rival
        val pulse = 0.6f + 0.4f * sin(time * 6f)
        batch.setColor(1f, 0.9f, 0.5f, pulse)
        val size = if (r.boss) 34f else 24f
        batch.draw(white, nodeX(w, r) - size / 2f, nodeY(r) - size / 2f, size, size)
        batch.color = Color.WHITE
    }

    override fun drawUi(w: Int, h: Int) {
        barn?.let { b ->
            panel(130f, 4f, w - 236f, 44f, ORANGE, 0.9f)
            font.draw(batch, "BARN FIND", 138f, 36f, ORANGE, 1, null)
            font.draw(batch, "SOMETHING UNDER A TARP. NOBODY'S CLAIMED IT.", 138f, 25f, SOFT, 1, null)
            font.draw(batch, "FREE. ONE PER BARN.", 138f, 14f, YELLOW, 1, null)
            font.draw(batch, "CAREER", 8f, h - 20f, PINK, 2)
            cash(w - 8f, h - 20f)
            return
        }
        val r = rival
        val def = game.effective(r.asCar())
        val tier = career.tierOf(r)
        val pass = DragMath.perfectPass(def)
        panel(130f, 4f, w - 236f, 44f, classColor(tier.name), 0.9f)
        font.draw(batch, r.title, 138f, 36f, if (r.boss) RED else Color.WHITE, 1, null)
        font.draw(batch, "${def.brand} ${def.name}".uppercase(), 138f, 25f, SOFT, 1, null)
        val dist = if (tier.quarter) "1/4" else "1/8"
        val et = if (tier.quarter) pass.quarter else pass.eighth
        font.draw(batch, "$dist MILE  BEST %.2f".format(et), 138f, 14f, YELLOW, 1, null)
        val purse = if (r.id in save.beaten) "REMATCH $" + "%,d".format(r.payout / 2) else "PURSE $" + "%,d".format(r.payout)
        font.draw(batch, if (r.boss && r.id !in save.beaten) "PINK SLIPS" else purse, w - 110f - font.width(purse), 36f, if (r.boss) RED else GREEN, 1, null)
        font.draw(batch, "CAREER", 8f, h - 20f, PINK, 2)
        if (confirmSlip > 0f) {
            val text = "YOUR ${game.car(save.selectedCar()!!.carId).name.uppercase()} FOR THEIR ${game.car(rival.carId).name.uppercase()}. TAP AGAIN"
            panel(w / 2f - font.width(text) / 2f - 6f, h / 2f, font.width(text) + 12f, 16f, RED, 0.94f)
            font.drawCentered(batch, text, w / 2f, h / 2f + 5f, RED, 1, null)
        }
        cash(w - 8f, h - 20f)
    }

    companion object {
        private val BARN_RED = Color(0.62f, 0.16f, 0.14f, 1f)
        private val BARN_ROOF = Color(0.35f, 0.3f, 0.32f, 1f)
        private val BARN_TRIM = Color(0.92f, 0.88f, 0.8f, 1f)
        private val BARN_EDGE = Color(0.2f, 0.12f, 0.1f, 1f)
    }
}

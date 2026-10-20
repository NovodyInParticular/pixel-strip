package io.github.novodyinparticular.pixelstrip.ui

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import io.github.novodyinparticular.pixelstrip.PixelStrip
import io.github.novodyinparticular.pixelstrip.data.PartCatalog
import io.github.novodyinparticular.pixelstrip.race.CarLooks
import io.github.novodyinparticular.pixelstrip.race.DragMath
import kotlin.math.floor
import kotlin.math.min

/**
 * The parts shop, in the garage with the car up on the lift. Tabs per group, one row per part:
 * tier pips, the next tier and its price. Selecting a row previews its effect on the stats.
 */
class UpgradeScreen(game: PixelStrip) : MenuScreen(game) {
    private val bg = art.tex("scenes/garage.png")
    private val bgGlow = art.tex("scenes/garage_glow.png")
    private val stage = CarStage()
    private val save = game.save
    private val owned = save.selectedCar()!!
    private val catalog: PartCatalog = game.parts
    private val stock = game.car(owned.carId)
    private val stockClass = game.economy.classFor(DragMath.perfectPass(stock).quarter)
    private val groups = (catalog.slots.map { it.group } + "LOOKS").distinct()
    private var group = 0
    private var selected = 0
    private var lift = 0f
    private var installed = 0f

    private val tabs = groups.mapIndexed { i, g -> button(g, Color(BTN_PURPLE)) { group = i; selected = 0 } }
    private val rowButtons = List(6) { i -> button("", BTN_GREY) { selected = i } }
    private val buy = button("BUY", BTN_RED, big = true, keys = intArrayOf(Input.Keys.ENTER)) { purchase() }
    private val back = button("BACK", BTN_GREY, keys = intArrayOf(Input.Keys.B)) { onBack() }

    private sealed class Row {
        class Perf(val slot: PartCatalog.Slot) : Row()
        class Look(val c: PartCatalog.Cosmetic) : Row()
    }

    private fun rows(): List<Row> = if (groups[group] == "LOOKS") catalog.cosmetics.map { Row.Look(it) }
    else catalog.slots.filter { it.group == groups[group] }.map { Row.Perf(it) }

    private fun row() = rows().getOrNull(selected)

    /** Parts and cosmetics as they'd be after buying the selected row's next step. */
    private fun nextParts(): Pair<Map<String, Int>, Map<String, Int>>? {
        val parts = HashMap(owned.parts)
        val cos = HashMap(owned.cosmetics)
        when (val r = row()) {
            is Row.Perf -> {
                val tier = (parts[r.slot.id] ?: 0) + 1
                if (tier > r.slot.tiers.size) return null
                parts[r.slot.id] = tier
                r.slot.excludes.forEach { parts.remove(it) }
            }
            is Row.Look -> cos[r.c.id] = ((cos[r.c.id] ?: 0) + 1) % r.c.styles.size
            null -> return null
        }
        return parts to cos
    }

    private fun price(): Long? = when (val r = row()) {
        is Row.Perf -> {
            val tier = (owned.parts[r.slot.id] ?: 0) + 1
            if (tier > r.slot.tiers.size) null else catalog.price(r.slot, tier, stockClass)
        }
        is Row.Look -> r.c.price.toLong()
        null -> null
    }

    private fun purchase() {
        val cost = price() ?: return
        val (parts, cos) = nextParts() ?: return
        if (save.cash < cost) {
            flash = 0.3f
            flashColor.set(RED)
            return
        }
        save.cash -= cost
        owned.parts.clear()
        owned.parts.putAll(parts)
        owned.cosmetics.clear()
        owned.cosmetics.putAll(cos)
        save.write()
        installed = 1.4f
        flash = 0.5f
        flashColor.set(GREEN)
    }

    override fun onBack() = game.go(0) { GarageScreen(game) }

    override fun layout(w: Int, h: Int) {
        var tx = 8f
        tabs.forEachIndexed { i, b ->
            b.w = font.width(groups[i]) + 12f
            b.h = 16f
            b.x = tx
            b.y = h - 70f
            tx += b.w + 4f
            b.label = groups[i]
            b.face.set(if (i == group) PINK else BTN_PURPLE)
        }
        val list = rows()
        rowButtons.forEachIndexed { i, b ->
            b.visible = i < list.size
            b.x = 8f
            b.y = h - 96f - i * 24f
            b.w = 196f
            b.h = 20f
            b.label = ""
        }
        val p = price()
        buy.apply {
            x = w - 112f; y = 8f; this.w = 104f; this.h = 40f
            label = when {
                p == null -> "MAXED"
                save.cash < p -> "SHORT"
                row() is Row.Look -> "CHANGE"
                else -> "BUY"
            }
        }
        back.apply { x = 8f; y = 8f; this.w = 60f; this.h = 22f }
    }

    override fun update(dt: Float) {
        lift = min(1f, lift + dt * 0.8f)
        installed = (installed - dt).coerceAtLeast(0f)
        lights.clear()
        val bx = floor((post.width - bg.width) / 2f)
        for (lx in GarageScreen.LAMPS) lights.add(bx + lx, GarageScreen.LAMP_Y, 60f, 1f, 0.82f, 0.55f, 210f)
        lights.add(bx + 320f, 208f, 40f, 0.9f, 0.25f, 0.6f, 220f)
    }

    private fun carY() = GarageScreen.GROUND + floor(LIFT_PX * (1f - (1f - lift) * (1f - lift)))

    override fun drawScene(w: Int, h: Int) {
        batch.draw(bg, floor((w - bg.width) / 2f), 0f)
        // the lift's arms rise with the car
        batch.setColor(0.45f, 0.45f, 0.58f, 1f)
        batch.draw(white, w / 2f - 120f, carY() - 3f, 240f, 3f)
        batch.color = Color.WHITE
        stage.draw(game.effective(owned), owned.paint, w / 2f + 40f, carY(), GarageScreen.AMBIENT, looks = CarLooks.of(owned, catalog))
    }

    override fun drawGlow(w: Int, h: Int) {
        batch.draw(bgGlow, floor((w - bgGlow.width) / 2f), 0f)
        if (installed > 0f) {
            batch.setColor(0.4f, 1f, 0.5f, installed / 1.4f)
            batch.draw(white, w / 2f - 20f, carY(), 120f, 34f)
            batch.color = Color.WHITE
        }
    }

    override fun drawUiOver(w: Int, h: Int) {
        val list = rows()
        list.forEachIndexed { i, r ->
            val b = rowButtons[i]
            val sel = i == selected
            if (sel) panel(b.x - 2f, b.y - 2f, b.w + 4f, b.h + 4f, YELLOW, 0f)
            when (r) {
                is Row.Perf -> {
                    val tier = owned.parts[r.slot.id] ?: 0
                    font.draw(batch, r.slot.name, b.x + 6f, b.y + 7f, if (sel) Color.WHITE else SOFT, 1, null)
                    for (k in 0 until r.slot.tiers.size) {
                        batch.color = if (k < tier) GREEN else Color(0.2f, 0.25f, 0.2f, 1f)
                        batch.draw(white, b.x + 150f + k * 10f, b.y + 6f, 8f, 8f)
                    }
                    batch.color = Color.WHITE
                }
                is Row.Look -> {
                    val style = owned.cosmetics[r.c.id] ?: 0
                    font.draw(batch, r.c.name, b.x + 6f, b.y + 7f, if (sel) Color.WHITE else SOFT, 1, null)
                    font.draw(batch, r.c.styles[style].uppercase(), b.x + 190f - font.width(r.c.styles[style]), b.y + 7f, CYAN, 1, null)
                }
            }
        }
    }

    override fun drawUi(w: Int, h: Int) {
        val def = game.effective(owned)
        batch.setColor(0.03f, 0.02f, 0.08f, 0.85f)
        batch.draw(white, 0f, h - 76f, w.toFloat(), 26f)
        batch.color = Color.WHITE
        font.draw(batch, "PARTS", 8f, h - 24f, PINK, 2)
        font.draw(batch, game.car(owned.carId).name.uppercase(), 8f, h - 38f, SOFT)
        cash(w - 8f, h - 20f)
        // what the selected step is, its price, and what it does
        val next = nextParts()
        val r = row()
        val title = when (r) {
            is Row.Perf -> {
                val tier = (owned.parts[r.slot.id] ?: 0) + 1
                if (tier > r.slot.tiers.size) "${r.slot.name}: MAXED" else {
                    val t = r.slot.tiers[tier - 1]
                    "${r.slot.name}: ${t.label ?: t.name}"
                }
            }
            is Row.Look -> "${r.c.name}: ${r.c.styles[((owned.cosmetics[r.c.id] ?: 0) + 1) % r.c.styles.size].uppercase()}"
            null -> ""
        }
        font.draw(batch, title, w - 144f, 146f, YELLOW, 1, null)
        if (r is Row.Perf && r.slot.excludes.any { (owned.parts[it] ?: 0) > 0 } && next != null) {
            font.draw(batch, "REPLACES ${r.slot.excludes.joinToString().uppercase()}", w - 144f, 138f, RED, 1, null)
        }
        price()?.let { font.draw(batch, "$" + "%,d".format(it), w - 112f, 52f, if (save.cash >= it) YELLOW else RED, 1, null) }
        val preview = next?.let { (p, c) -> game.effectiveWith(owned.carId, p, c, owned.tune) }
        carStats(def, w - 144f, 64f, preview)
        if (installed > 0f) font.drawCentered(batch, "INSTALLED!", w / 2f + 40f, h / 2f + 30f, GREEN, if (installed > 1.25f) 3 else 2)
    }

    companion object {
        const val LIFT_PX = 22f
    }
}

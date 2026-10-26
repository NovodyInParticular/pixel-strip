package io.github.novodyinparticular.pixelstrip.ui

import io.github.novodyinparticular.pixelstrip.save.Entry
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import io.github.novodyinparticular.pixelstrip.PixelStrip
import io.github.novodyinparticular.pixelstrip.data.CarDef
import io.github.novodyinparticular.pixelstrip.data.PartCatalog
import io.github.novodyinparticular.pixelstrip.data.Upgrades
import io.github.novodyinparticular.pixelstrip.race.CarLooks
import io.github.novodyinparticular.pixelstrip.race.DragMath
import kotlin.math.floor
import kotlin.math.min

/**
 * The parts shop, in the garage with the car up on the lift. Flip through the groups; the slots
 * of a group are on the left, the selected slot's options on the right with their price (or
 * OWNED / FITTED) and why one can't go on. Picking an option previews it on the stats, with a
 * line of what it does: horsepower, torque, weight, where in the rev range, and how it sounds.
 * Parts that can't fit this car (another platform's swap, the rotary tree on a piston car) are hidden.
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
    private fun fits(o: PartCatalog.Option) = o.carOnly.isEmpty() || stock.id in o.carOnly
    private val groups = (catalog.slots.filter { s -> s.options.drop(1).any { fits(it) } }.map { it.group } + LOOKS).distinct()
    private var group = 0
    private var slotSel = 0
    private var optSel = 0
    private var lift = 0f
    private var installed = 0f

    private val prevGroup = button("<", Color(BTN_PURPLE), keys = intArrayOf(Input.Keys.Q, Input.Keys.LEFT)) { pickGroup(-1) }
    private val nextGroup = button(">", Color(BTN_PURPLE), keys = intArrayOf(Input.Keys.E, Input.Keys.RIGHT)) { pickGroup(1) }
    private val slotButtons = List(MAX_SLOTS) { i -> button("", BTN_GREY) { pickSlot(i) } }
    private val optButtons = List(MAX_OPTIONS) { i -> button("", BTN_GREY) { row()?.let { r -> visible(r).getOrNull(i)?.let { optSel = it } } } }
    private val buy = button("BUY", BTN_RED, big = true, keys = intArrayOf(Input.Keys.ENTER)) { purchase() }
    private val back = button("BACK", BTN_GREY, keys = intArrayOf(Input.Keys.B)) { onBack() }

    init {
        // desktop shots: -Dpixelstrip.pick=SLOT:OPTION opens the shop on that part
        game.shots?.let { System.getProperty("pixelstrip.pick") }?.split(":")?.let { (slot, opt) ->
            val sl = catalog.slot(slot)
            group = groups.indexOf(sl.group)
            slotSel = rows().indexOfFirst { it is Row.Part && it.slot.id == slot }
            optSel = opt.toInt()
        }
    }

    /** A slot (performance or body part) or a cosmetic, with its choices as plain names. */
    private sealed class Row(val name: String) {
        class Part(val slot: PartCatalog.Slot) : Row(slot.name)
        class Look(val c: PartCatalog.Cosmetic) : Row(c.name)
    }

    private fun rows(): List<Row> = if (groups[group] == LOOKS) catalog.cosmetics.map { Row.Look(it) }
    else catalog.slots.filter { s -> s.group == groups[group] && s.options.drop(1).any { fits(it) } }.map { Row.Part(it) }

    /** The options this car can take, as indices into the slot (the factory part always shows). */
    private fun visible(r: Row): List<Int> = when (r) {
        is Row.Part -> r.slot.options.indices.filter { it == 0 || fits(r.slot.options[it]) }
        is Row.Look -> r.c.styles.indices.toList()
    }

    private fun row() = rows().getOrNull(slotSel)

    private fun fitted(r: Row) = when (r) {
        is Row.Part -> owned.parts[r.slot.id] ?: 0
        is Row.Look -> owned.cosmetics[r.c.id] ?: 0
    }

    private fun optionCount(r: Row) = when (r) {
        is Row.Part -> r.slot.options.size
        is Row.Look -> r.c.styles.size
    }

    private fun optionName(r: Row, i: Int) = when (r) {
        is Row.Part -> r.slot.options[i].name
        is Row.Look -> r.c.styles[i].replace('_', ' ').uppercase()
    }

    private fun pickGroup(d: Int) {
        group = (group + d + groups.size) % groups.size
        pickSlot(0)
    }

    private fun pickSlot(i: Int) {
        slotSel = i
        optSel = row()?.let { fitted(it) } ?: 0
    }

    /** The build with the selected option in place. */
    private fun preview(): Pair<Map<String, Int>, Map<String, Int>>? {
        val r = row() ?: return null
        if (optSel == fitted(r)) return null
        val parts = HashMap(owned.parts)
        val cos = HashMap(owned.cosmetics)
        when (r) {
            is Row.Part -> {
                if (optSel == 0) parts.remove(r.slot.id) else parts[r.slot.id] = optSel
                if (optSel > 0) r.slot.excludes.forEach { parts.remove(it) }
            }
            is Row.Look -> cos[r.c.id] = optSel
        }
        return parts to cos
    }

    /** What fitting the selected option costs: 0 if it's owned (or the factory part). */
    private fun price(): Long = when (val r = row()) {
        is Row.Part -> if (owned.owns(r.slot.id, optSel)) 0L else catalog.price(r.slot, optSel, stockClass)
        is Row.Look -> r.c.price.toLong()
        null -> 0L
    }

    private fun blocker(): String? {
        val r = row() as? Row.Part ?: return null
        val next = preview()?.first ?: return null
        val o = r.slot.options[optSel]
        val engine = Upgrades.swapped(stock, catalog, next)
        if (!o.worksOn(engine.engineLayout)) return if (o.engineType == "rotary") "ROTARY ENGINES ONLY" else "DOES NOTHING ON A ROTARY"
        if (o.boostOnly && !Upgrades.turboed(engine, catalog, next)) return "DOES NOTHING WITHOUT A TURBO"
        return catalog.blocker(r.slot, optSel, next, stock.drivetrain, stock.id)
            // fitting this would leave another fitted part without what it needs
            ?: next.entries.firstNotNullOfOrNull { (s, o) -> catalog.slotOrNull(s)?.let { catalog.blocker(it, o, next, stock.drivetrain, stock.id) }?.let { "${catalog.slot(s).name} $it" } }
    }

    private fun purchase() {
        val r = row() ?: return
        if (optSel == fitted(r) || blocker() != null) return
        val e = when (r) {
            is Row.Part -> Entry("part", game.now(), uid = owned.uid, slot = r.slot.id, opt = optSel)
            is Row.Look -> Entry("look", game.now(), uid = owned.uid, slot = r.c.id, opt = optSel)
        }
        if (!game.commit(e)) {
            flash = 0.3f
            flashColor.set(RED)
            return
        }
        installed = 1.4f
        flash = 0.5f
        flashColor.set(GREEN)
    }

    override fun onBack() = game.go(0) { GarageScreen(game) }

    override fun layout(w: Int, h: Int) {
        prevGroup.apply { x = 8f; y = h - 70f; this.w = 18f; this.h = 16f }
        nextGroup.apply { x = 134f; y = h - 70f; this.w = 18f; this.h = 16f }
        val list = rows()
        slotButtons.forEachIndexed { i, b ->
            b.visible = i < list.size
            b.x = 8f
            b.y = h - 92f - i * 18f
            b.w = 144f
            b.h = 16f
            b.label = ""
        }
        val r = row()
        val n = r?.let { visible(it).size } ?: 0
        optButtons.forEachIndexed { i, b ->
            b.visible = i < n
            b.x = w - 178f
            val tight = n > LONG_LIST
            b.y = h - 70f - i * (if (tight) 13f else 17f)
            b.w = 170f
            b.h = if (tight) 12f else 15f
            b.label = ""
        }
        val p = price()
        buy.apply {
            x = w - 112f; y = 8f; this.w = 104f; this.h = 34f
            label = when {
                r == null || optSel == fitted(r) -> "FITTED"
                blocker() != null -> "BLOCKED"
                p == 0L -> "FIT"
                save.cash < p -> "SHORT"
                r is Row.Look -> "CHANGE"
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

    /** The car shows the selected option, so you see a kit before you buy it. */
    private fun shownCar(): io.github.novodyinparticular.pixelstrip.save.OwnedCar {
        val (p, c) = preview() ?: return owned
        return io.github.novodyinparticular.pixelstrip.save.OwnedCar(owned.uid, owned.carId, owned.paint, p.toMutableMap(), c.toMutableMap())
    }

    override fun drawScene(w: Int, h: Int) {
        batch.draw(bg, floor((w - bg.width) / 2f), 0f)
        batch.setColor(0.45f, 0.45f, 0.58f, 1f)
        batch.draw(white, w / 2f - 110f, carY() - 3f, 200f, 3f)
        batch.color = Color.WHITE
        stage.draw(game.effective(owned), owned.paint, w / 2f - 12f, carY(), GarageScreen.AMBIENT, looks = game.looks(shownCar()))
    }

    override fun drawGlow(w: Int, h: Int) {
        batch.draw(bgGlow, floor((w - bgGlow.width) / 2f), 0f)
        if (installed > 0f) {
            batch.setColor(0.4f, 1f, 0.5f, installed / 1.4f)
            batch.draw(white, w / 2f - 72f, carY(), 120f, 34f)
            batch.color = Color.WHITE
        }
    }

    override fun drawUiOver(w: Int, h: Int) {
        font.drawCentered(batch, groups[group], 80f, h - 66f, YELLOW)
        rows().forEachIndexed { i, r ->
            if (i >= MAX_SLOTS) return@forEachIndexed
            val b = slotButtons[i]
            val sel = i == slotSel
            if (sel) panel(b.x - 2f, b.y - 2f, b.w + 4f, b.h + 4f, YELLOW, 0f)
            font.draw(batch, r.name, b.x + 4f, b.y + 5f, if (sel) Color.WHITE else SOFT, 1, null)
            val f = fitted(r)
            if (f > 0) {
                batch.color = GREEN
                batch.draw(white, b.x + b.w - 8f, b.y + 5f, 5f, 5f)
                batch.color = Color.WHITE
            }
        }
        val r = row() ?: return
        val shown = visible(r)
        for (k in 0 until min(shown.size, MAX_OPTIONS)) {
            val b = optButtons[k]
            val i = shown[k]
            val sel = i == optSel
            if (sel) panel(b.x - 2f, b.y - 2f, b.w + 4f, b.h + 4f, YELLOW, 0f)
            val ty = b.y + if (b.h < 15f) 3f else 4f
            font.draw(batch, optionName(r, i), b.x + 4f, ty, if (sel) Color.WHITE else SOFT, 1, null)
            val tag = when {
                i == fitted(r) -> "FITTED" to GREEN
                r is Row.Part && owned.owns(r.slot.id, i) -> "OWNED" to CYAN
                r is Row.Part -> "$" + "%,d".format(catalog.price(r.slot, i, stockClass)) to YELLOW
                else -> "$" + "%,d".format((r as Row.Look).c.price) to YELLOW
            }
            font.draw(batch, tag.first, b.x + b.w - 4f - font.width(tag.first), ty, tag.second, 1, null)
        }
    }

    override fun drawUi(w: Int, h: Int) {
        val def = game.effective(owned)
        batch.setColor(0.03f, 0.02f, 0.08f, 0.85f)
        batch.draw(white, 0f, h - 76f, w.toFloat(), 26f)
        batch.color = Color.WHITE
        font.draw(batch, "PARTS", 8f, h - 24f, PINK, 2)
        font.draw(batch, stock.name.uppercase(), 8f, h - 38f, SOFT)
        cash(w - 8f, h - 20f)
        val r = row()
        val next = preview()
        val after = next?.let { (p, c) -> game.effectiveWith(owned.carId, p, c, owned.tune) }
        // the selected option's one-liner, or why it can't go on
        val info = when {
            r is Row.Part -> blocker()?.let { it to RED } ?: (r.slot.options[optSel].info to SOFT)
            else -> null
        }
        // below the lift, between BACK and the stats
        val mid = (72f + w - 148f) / 2f
        if (r is Row.Part && optSel > 0) {
            val o = r.slot.options[optSel]
            val what = o.engine?.let { catalog.engines[it]?.name } ?: o.name
            font.drawCentered(batch, (o.brand?.let { "$it  " } ?: "") + what, mid, 60f, YELLOW)
            effectLine(o, def, after)?.let { font.drawCentered(batch, it, mid, 50f, CYAN) }
        }
        info?.let { (text, color) -> font.drawCentered(batch, text, mid, 40f, color) }
        if (r is Row.Part && optSel > 0 && r.slot.excludes.any { (owned.parts[it] ?: 0) > 0 }) {
            font.drawCentered(batch, "REMOVES THE " + r.slot.excludes.joinToString { catalog.slot(it).name }, mid, 30f, ORANGE)
        }
        warning(after ?: def)?.let { font.drawCentered(batch, it, mid, 20f, RED) }
        if ((r?.let { visible(it).size } ?: 0) <= LONG_LIST) carStats(def, w - 144f, 42f, after)
        if (installed > 0f) font.drawCentered(batch, "INSTALLED!", w / 2f - 12f, h / 2f + 30f, GREEN, if (installed > 1.25f) 3 else 2)
    }

    /** Peak crank horsepower and torque on the dyno. */
    private fun peaks(d: CarDef): Pair<Float, Float> {
        var hp = 0f
        var nm = 0f
        var rpm = d.idleRpm
        while (rpm <= d.redline) {
            val t = DragMath.crankTorque(d, rpm)
            hp = maxOf(hp, t * rpm / 7127f * 0.986f)
            nm = maxOf(nm, t)
            rpm += 100f
        }
        return hp to nm
    }

    /** "+24 HP  +31 NM  -12 KG  TOP END  RASPY": what the option changes against what's fitted now. */
    private fun effectLine(o: PartCatalog.Option, now: CarDef, after: CarDef?): String? {
        val bits = ArrayList<String>()
        if (after != null) {
            val (hp0, nm0) = peaks(now)
            val (hp1, nm1) = peaks(after)
            val dh = (hp1 - hp0).toInt()
            val dn = (nm1 - nm0).toInt()
            val dk = (after.weightKg - now.weightKg).toInt()
            if (dh != 0) bits.add("%+d HP".format(dh))
            if (dn != 0) bits.add("%+d NM".format(dn))
            if (dk != 0) bits.add("%+d KG".format(dk))
            if (after.redline - now.redline >= 100f) bits.add("+${(after.redline - now.redline).toInt()} RPM")
        }
        val fx = o.effects
        val lo = (fx["lo"] ?: 0f) + (fx["tq"] ?: 0f)
        val hi = (fx["hi"] ?: 0f) + (fx["tq"] ?: 0f)
        when {
            o.engine != null -> catalog.engines[o.engine]?.let { bits.add(it.layout.replace("rotary", "${it.cylinders}-ROTOR").uppercase() + " SOUND") }
            hi > lo + 0.04f -> bits.add("TOP END")
            lo > hi + 0.02f -> bits.add("LOW END")
        }
        if ((fx["loud"] ?: 0f) >= 0.5f) bits.add("LOUD") else if ((fx["loud"] ?: 0f) > 0f) bits.add("LOUDER")
        if ((fx["rasp"] ?: 0f) > 0f) bits.add("RASPY")
        if ((fx["lope"] ?: 0f) >= 0.5f) bits.add("LUMPY IDLE")
        if ((fx["growl"] ?: 0f) > 0f) bits.add("INTAKE GROWL")
        if ((fx["whistle"] ?: 0f) > 0f) bits.add("WHISTLE")
        if ((fx["screamer"] ?: 0f) > 0f) bits.add("SCREAMS")
        when ((fx["bovType"] ?: 0f).toInt()) { 1 -> bits.add("PSSSH"); 2 -> bits.add("QUIET") }
        when ((fx["scType"] ?: 0f).toInt()) { 1, 2 -> bits.add("WHINE"); 3 -> bits.add("SIREN") }
        return bits.takeIf { it.isNotEmpty() }?.take(4)?.joinToString("  ")
    }

    /** What's holding this build back on the dyno, if anything. */
    private fun warning(def: CarDef): String? {
        val why = arrayOf(DragMath.Limit.NONE)
        var knock = false
        var fuel = false
        var rpm = def.idleRpm
        while (rpm <= def.redline) {
            DragMath.crankTorque(def, rpm, why)
            if (why[0] == DragMath.Limit.KNOCK) knock = true
            if (why[0] == DragMath.Limit.FUEL) fuel = true
            rpm += 250f
        }
        return when {
            fuel -> "FUEL LIMITED: UPGRADE THE FUEL SYSTEM"
            knock -> "KNOCK LIMITED: FORGED INTERNALS OR BETTER FUEL"
            else -> null
        }
    }

    companion object {
        const val LIFT_PX = 22f
        const val LOOKS = "LOOKS"
        const val MAX_SLOTS = 9
        const val MAX_OPTIONS = 11
        const val LONG_LIST = 6
    }
}

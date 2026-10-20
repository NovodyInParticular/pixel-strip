package io.github.novodyinparticular.pixelstrip.data

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.JsonReader
import com.badlogic.gdx.utils.JsonValue

/** Performance part slots with Stage 1..3 + Race tiers, and cosmetic parts, from assets/data/parts.json. */
class PartCatalog(root: JsonValue) {
    class Tier(val name: String, val label: String?, val effects: Map<String, Float>, val visual: String?)
    class Slot(val id: String, val name: String, val group: String, val tiers: List<Tier>, val excludes: List<String>, val priceMult: Float)
    class Cosmetic(val id: String, val name: String, val styles: List<String>, val price: Int, val dragArea: FloatArray?)

    class Option(val name: String, val price: Int)

    val tierPrices: IntArray = root.get("tierPrices").asIntArray()
    private val paintJson = root.get("paint")
    val colorPrice: Int = paintJson.getInt("colorPrice")
    val finishes: List<Option> = paintJson.get("finishes").map { Option(it.getString("name"), it.getInt("price")) }
    val decals: List<Option> = paintJson.get("decals").map { Option(it.getString("name"), it.getInt("price")) }
    val decalColors: List<FloatArray> = paintJson.get("decalColors").map { it.asFloatArray() }
    private val classMult: Map<String, Float> = root.get("classMultiplier").associate { it.name to it.asFloat() }
    val slots: List<Slot> = root.get("slots").map { s ->
        Slot(
            s.getString("id"), s.getString("name"), s.getString("group"),
            s.get("tiers").map { t ->
                val fx = HashMap<String, Float>()
                t.forEach { if (it.isNumber) fx[it.name] = it.asFloat() }
                Tier(t.getString("name"), t.getString("label", null), fx, t.getString("visual", null))
            },
            s.get("excludes")?.asStringArray()?.toList() ?: emptyList(),
            s.getFloat("priceMult", 1f),
        )
    }
    val cosmetics: List<Cosmetic> = root.get("cosmetic").map { c ->
        Cosmetic(c.getString("id"), c.getString("name"), c.get("styles").asStringArray().toList(), c.getInt("price"), c.get("dragArea")?.asFloatArray())
    }

    fun slot(id: String) = slots.first { it.id == id }

    /** Price of a performance tier (1-based) for a car of [stockClass]. */
    fun price(slot: Slot, tier: Int, stockClass: String): Long =
        (tierPrices[tier - 1] * slot.priceMult * (classMult[stockClass] ?: 1f)).toLong()

    companion object {
        fun load() = PartCatalog(JsonReader().parse(Gdx.files.internal("data/parts.json")))
    }
}

/** A tune preset: gear ratios, final drive, launch control, nitrous timing, tyre pressure. */
data class Tune(
    val name: String = "STOCK",
    val gears: FloatArray? = null,
    val finalDrive: Float? = null,
    val launchRpm: Float = 0f,
    val nitrousGear: Int = 0,
    val tirePressure: Float = CarDef.STOCK_PRESSURE,
)

/** Builds the effective car from the stock definition, installed parts and the active tune. */
object Upgrades {
    /** Tuning range allowed on each gear and the final drive, as a share of the base ratio. */
    class Ranges(val gear: Float, val finalDrive: Float)

    fun ranges(catalog: PartCatalog, parts: Map<String, Int>): Ranges {
        fun fx(slot: String, key: String): Float {
            val tier = parts[slot] ?: 0
            return if (tier <= 0) 0f else catalog.slot(slot).tiers[tier - 1].effects[key] ?: 0f
        }
        return Ranges(BASE_RANGE + fx("gearbox", "tuneRange"), BASE_RANGE + fx("final", "finalRange"))
    }

    /** Gear set after a gearbox swap: keeps first gear, spreads [count] gears geometrically to the top ratio. */
    fun baseGears(def: CarDef, catalog: PartCatalog, parts: Map<String, Int>): FloatArray {
        val tier = parts["gearbox"] ?: 0
        val count = if (tier > 0) (catalog.slot("gearbox").tiers[tier - 1].effects["gears"] ?: 0f).toInt() else 0
        if (count <= def.gears.size) return def.gears
        val first = def.gears.first()
        val top = minOf(def.gears.last(), 0.8f)
        return FloatArray(count) { i -> (first * Math.pow((top / first).toDouble(), i / (count - 1.0))).toFloat() }
    }

    fun apply(def: CarDef, catalog: PartCatalog, parts: Map<String, Int>, cosmetics: Map<String, Int>, tune: Tune?): CarDef {
        var hp = 0f
        var tq = 0f
        var hpRpm = 0f
        var tqRpm = 0f
        var redline = 0f
        var weight = 0f
        var grip = 0f
        var clutch = 0f
        var shift = 0f
        var drag = 0f
        var pressureSens = 1f
        var boost = 0f
        var spool = 0f
        var lag = 0.8f
        var blower = 0f
        var nitrous = def.nitrous
        for (slot in catalog.slots) {
            val tier = parts[slot.id] ?: 0
            if (tier <= 0) continue
            val fx = slot.tiers[tier - 1].effects
            hp += fx["hp"] ?: 0f
            tq += fx["tq"] ?: 0f
            hpRpm += fx["hpRpm"] ?: 0f
            tqRpm += fx["tqRpm"] ?: 0f
            redline += fx["redline"] ?: 0f
            weight += fx["weight"] ?: 0f
            grip += fx["grip"] ?: 0f
            clutch += fx["clutch"] ?: 0f
            shift += fx["shift"] ?: 0f
            drag += fx["dragArea"] ?: 0f
            fx["pressureSens"]?.let { pressureSens = it }
            fx["boost"]?.let {
                boost = it
                spool = fx["spool"] ?: 3500f
                lag = fx["lag"] ?: 0.8f
            }
            fx["blower"]?.let { blower = it }
            fx["nos"]?.let { nitrous = CarDef.Nitrous(it, (fx["charges"] ?: 1f).toInt(), fx["seconds"] ?: 2.5f) }
        }
        for (c in catalog.cosmetics) {
            val style = cosmetics[c.id] ?: 0
            c.dragArea?.let { if (style in it.indices) drag += it[style] }
        }
        val ranges = ranges(catalog, parts)
        val base = baseGears(def, catalog, parts)
        val gears = tune?.gears?.takeIf { it.size == base.size }
            ?.mapIndexed { i, g -> g.coerceIn(base[i] * (1 - ranges.gear), base[i] * (1 + ranges.gear)) }?.toFloatArray() ?: base
        val finalDrive = (tune?.finalDrive ?: def.finalDrive).coerceIn(def.finalDrive * (1 - ranges.finalDrive), def.finalDrive * (1 + ranges.finalDrive))
        val peakHp = def.peakHpRpm + hpRpm
        return def.copy(
            hp = def.hp * (1 + hp),
            torqueNm = def.torqueNm * (1 + tq),
            peakHpRpm = peakHp,
            peakTorqueRpm = minOf(def.peakTorqueRpm + tqRpm, peakHp - 300f),
            redline = maxOf(def.redline + redline, peakHp + 300f),
            weightKg = def.weightKg + weight,
            grip = def.grip * (1 + grip),
            clutchTorque = def.clutchTorque + clutch,
            shiftTime = (def.shiftTime * (1 + shift)).coerceAtLeast(0.08f),
            dragArea = (def.dragArea + drag).coerceAtLeast(0.3f),
            gears = gears,
            finalDrive = finalDrive,
            nitrous = nitrous,
            boostGain = boost,
            boostSpoolRpm = spool,
            boostLag = lag,
            blowerGain = blower,
            tirePressure = tune?.tirePressure ?: CarDef.STOCK_PRESSURE,
            pressureSens = pressureSens,
            launchControl = tune?.launchRpm ?: 0f,
            nitrousAutoGear = tune?.nitrousGear ?: 0,
        )
    }

    const val BASE_RANGE = 0.05f
}

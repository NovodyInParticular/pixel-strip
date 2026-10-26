package io.github.novodyinparticular.pixelstrip.data

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.JsonReader
import com.badlogic.gdx.utils.JsonValue

/**
 * Part slots and their options, and cosmetics, from assets/data/parts.json (written by
 * tools/make_parts.py, which documents every effect key). Option 0 of a slot is the factory part.
 */
class PartCatalog(root: JsonValue) {
    /** [requires]: any one of these (slot, minimum option) must be fitted. */
    class Option(
        val name: String, val price: Int, val info: String, val effects: Map<String, Float>,
        val visual: String?, val requires: List<Pair<String, Int>>, val rwdOnly: Boolean,
        /** Only these cars can take it (empty = any). */
        val carOnly: List<String> = emptyList(),
    )
    class Slot(val id: String, val name: String, val group: String, val options: List<Option>, val excludes: List<String>)
    class Cosmetic(val id: String, val name: String, val styles: List<String>, val price: Int, val dragArea: FloatArray?)

    class Choice(val name: String, val price: Int)

    private val paintJson = root.get("paint")
    val colorPrice: Int = paintJson.getInt("colorPrice")
    val finishes: List<Choice> = paintJson.get("finishes").map { Choice(it.getString("name"), it.getInt("price")) }
    val decals: List<Choice> = paintJson.get("decals").map { Choice(it.getString("name"), it.getInt("price")) }
    val decalColors: List<FloatArray> = paintJson.get("decalColors").map { it.asFloatArray() }
    private val classMult: Map<String, Float> = root.get("classMultiplier").associate { it.name to it.asFloat() }
    val slots: List<Slot> = root.get("slots").map { s ->
        Slot(
            s.getString("id"), s.getString("name"), s.getString("group"),
            s.get("options").map { o ->
                val fx = HashMap<String, Float>()
                o.forEach { if (it.isNumber) fx[it.name] = it.asFloat() }
                Option(
                    o.getString("name"), o.getInt("price"), o.getString("info", ""), fx, o.getString("visual", null),
                    o.get("requires")?.map { it.getString(0) to it.getInt(1) } ?: emptyList(), o.getBoolean("rwdOnly", false),
                    o.get("carOnly")?.asStringArray()?.toList() ?: emptyList(),
                )
            },
            s.get("excludes")?.asStringArray()?.toList() ?: emptyList(),
        )
    }
    val cosmetics: List<Cosmetic> = root.get("cosmetic").map { c ->
        Cosmetic(c.getString("id"), c.getString("name"), c.get("styles").asStringArray().toList(), c.getInt("price"), c.get("dragArea")?.asFloatArray())
    }
    private val byId = slots.associateBy { it.id }

    fun slot(id: String) = byId.getValue(id)
    fun slotOrNull(id: String) = byId[id]

    /** The fitted option of [slot] in [parts] (the factory part if none). */
    fun fitted(parts: Map<String, Int>, slot: String): Option {
        val s = slot(slot)
        return s.options[(parts[slot] ?: 0).coerceIn(0, s.options.size - 1)]
    }

    /** One effect of the fitted option (0 if it has none). */
    fun fx(parts: Map<String, Int>, slot: String, key: String): Float = fitted(parts, slot).effects[key] ?: 0f

    /** Price of option [option] (1-based; 0 is the free factory part) for a car of [stockClass]. */
    fun price(slot: Slot, option: Int, stockClass: String): Long =
        if (option <= 0) 0L else (slot.options[option].price * (classMult[stockClass] ?: 1f)).toLong()

    /** Why [option] can't go on this car with [parts] fitted, or null if it can. */
    fun blocker(slot: Slot, option: Int, parts: Map<String, Int>, drivetrain: String, carId: String): String? {
        val o = slot.options[option]
        if (o.carOnly.isNotEmpty() && carId !in o.carOnly) return "DOESN'T FIT THIS CAR"
        if (o.rwdOnly && drivetrain != "RWD") return "RWD ONLY"
        if (o.requires.isNotEmpty() && o.requires.none { (s, min) -> (parts[s] ?: 0) >= min }) {
            return "NEEDS " + o.requires.joinToString(" OR ") { (s, min) -> slot(s).options[min].name }
        }
        return null
    }

    companion object {
        fun load() = PartCatalog(JsonReader().parse(Gdx.files.internal("data/parts.json")))
    }
}

/**
 * A tune preset. Nullable fields mean "the part's default". Which of these the car can actually
 * use depends on its parts (see [Upgrades.limits]); the rest are ignored.
 */
data class Tune(
    val name: String = "STOCK",
    val gears: FloatArray? = null,
    val finalDrive: Float? = null,
    val launchRpm: Float = 0f,
    val nitrousGear: Int = 0,
    val tirePressure: Float = CarDef.STOCK_PRESSURE,
    /** Turbo boost target (psi). */
    val boostPsi: Float? = null,
    /** Share of the boost target allowed in each gear (electronic boost control). */
    val boostByGear: FloatArray? = null,
    /** Supercharger boost (a pulley swap), psi. */
    val scPsi: Float? = null,
    /** Ignition timing relative to the base map, degrees. */
    val timing: Float = 0f,
    /** Wide-open-throttle air/fuel ratio (gasoline-equivalent). */
    val afr: Float? = null,
    /** Rev limiter, rpm. */
    val limiter: Float? = null,
    /** Nitrous shot size, hp. */
    val nitrousShot: Float? = null,
    /** Progressive controller: seconds to bring the full shot in. */
    val nitrousRamp: Float = 0.6f,
    /** Coilovers: -1 slammed .. +1 raised. */
    val rideHeight: Float = 0f,
    /** Double-adjustable shocks: 0 slow weight transfer .. 1 fastest. */
    val shockRate: Float = 0.5f,
)

/** Builds the effective car from the stock definition, installed parts and the active tune. */
object Upgrades {
    /** What the tune screen may change on this build, and in what range. */
    class Limits(
        val gearRange: Float,
        val finalRange: Float,
        val timingRange: Float,
        val afr: Boolean,
        val limiter: Boolean,
        val limiterMax: Float,
        val launchControl: Boolean,
        /** Turbo: most boost the turbo makes (0 = no turbo); [boostAdjustable] false = spring only. */
        val boostMax: Float,
        val boostAdjustable: Boolean,
        val boostByGear: Boolean,
        val scMax: Float,
        val nitrousMax: Float,
        val progressive: Boolean,
        val rideHeight: Boolean,
        val shocks: Boolean,
        val antiLag: Boolean,
    ) {
        val springBoost get() = minOf(SPRING_PSI, boostMax)
    }

    fun limits(def: CarDef, c: PartCatalog, parts: Map<String, Int>): Limits {
        val ecu = c.fx(parts, "ecu", "ecu").toInt()
        val turbo = c.fx(parts, "turbo", "psi")
        val ctl = c.fx(parts, "boostctl", "boostCtl").toInt()
        val sum = { key: String -> c.slots.sumOf { (c.fx(parts, it.id, key)).toDouble() }.toFloat() }
        return Limits(
            gearRange = BASE_RANGE + c.fx(parts, "gearbox", "tuneRange"),
            finalRange = BASE_RANGE + c.fx(parts, "final", "finalRange"),
            timingRange = TIMING_RANGE[ecu],
            afr = ecu >= 2,
            limiter = ecu >= 2,
            limiterMax = def.redline + sum("rev"),
            launchControl = ecu >= 2,
            boostMax = turbo,
            boostAdjustable = turbo > 0f && ctl >= 1,
            boostByGear = turbo > 0f && ctl >= 2 && ecu >= 3,
            scMax = c.fx(parts, "supercharger", "scPsi"),
            nitrousMax = c.fx(parts, "nitrous", "nosHp"),
            progressive = c.fx(parts, "nosctl", "progressive") > 0f,
            rideHeight = c.fx(parts, "springs", "adjustable") > 0f,
            shocks = c.fx(parts, "shocks", "adjustable") > 0f,
            antiLag = turbo > 0f && ecu >= 3,
        )
    }

    /** Gear set after a gearbox swap: keeps first gear, spreads [count] gears geometrically to the top ratio. */
    fun baseGears(def: CarDef, c: PartCatalog, parts: Map<String, Int>): FloatArray {
        val count = c.fx(parts, "gearbox", "gears").toInt()
        if (count <= def.gears.size) return def.gears
        val first = def.gears.first()
        val top = minOf(def.gears.last(), 0.8f)
        return FloatArray(count) { i -> (first * Math.pow((top / first).toDouble(), i / (count - 1.0))).toFloat() }
    }

    /** Default air/fuel: richer with boost or nitrous (safe), leaner naturally aspirated. */
    fun defaultAfr(forced: Boolean) = if (forced) 11.5f else 12.8f

    fun apply(def: CarDef, c: PartCatalog, parts: Map<String, Int>, cosmetics: Map<String, Int>, tune: Tune?): CarDef {
        val lim = limits(def, c, parts)
        var lo = 0f
        var mid = 0f
        var hi = 0f
        var peakRpm = 0f
        var idle = 0f
        var weight = 0f
        var rear = 0f
        var grip = 0f
        var clutch = 0f
        var drag = 0f
        var clutchTime = 1f
        var inertia = 1f
        var wheelInertia = 1f
        var shift = 1f
        var sens = 1f
        var transfer = 1f
        var transferRate = 1f
        for (slot in c.slots) {
            val fx = c.fitted(parts, slot.id).effects
            val all = fx["tq"] ?: 0f
            lo += all + (fx["lo"] ?: 0f)
            mid += all + (fx["mid"] ?: 0f)
            hi += all + (fx["hi"] ?: 0f)
            peakRpm += fx["peakRpm"] ?: 0f
            idle += fx["idle"] ?: 0f
            weight += fx["weight"] ?: 0f
            rear += fx["rear"] ?: 0f
            grip += fx["grip"] ?: 0f
            clutch += fx["clutch"] ?: 0f
            drag += fx["drag"] ?: 0f
            fx["clutchTime"]?.let { clutchTime *= it }
            fx["inertia"]?.let { inertia *= it }
            fx["wheelInertia"]?.let { wheelInertia *= it }
            fx["shift"]?.let { shift *= it }
            fx["sens"]?.let { sens *= it }
            fx["transfer"]?.let { transfer *= it }
            fx["transferRate"]?.let { transferRate *= it }
        }
        for (cos in c.cosmetics) {
            val style = cosmetics[cos.id] ?: 0
            cos.dragArea?.let { if (style in it.indices) drag += it[style] }
        }

        // Ignition and fuelling: timing adds power and eats knock margin; leaner adds a little power and eats more
        val timing = (tune?.timing ?: 0f).coerceIn(-lim.timingRange, lim.timingRange)
        val forced = lim.boostMax > 0f || lim.scMax > 0f || lim.nitrousMax > 0f || def.stockTurbo
        val afr = if (lim.afr) (tune?.afr ?: defaultAfr(forced)).coerceIn(AFR_MIN, AFR_MAX) else defaultAfr(forced)
        val afrDelta = afr - defaultAfr(forced)
        val powerMult = (1f + timing * TIMING_POWER) * (1f + afrDelta * AFR_POWER)
        val knockLimit = c.fx(parts, "internals", "strength").let { s ->
            maxOf(s, c.fx(parts, "swap", "strength"))
        } * c.fx(parts, "fueltype", "octane") * (1f - afrDelta * AFR_KNOCK) / (1f + timing * TIMING_KNOCK)
        val fuelFlow = c.fitted(parts, "fueltype").effects["fuelFlow"] ?: 1f
        val stockFuel = def.hp * def.ratingFactor * STOCK_FUEL_MARGIN
        val fuelHp = maxOf(stockFuel, c.fx(parts, "fuel", "fuelHp")) / fuelFlow

        // Turbo: psi becomes extra torque share through the intercooler; factory turbo cars already have ~10 psi in their numbers
        val icEff = c.fitted(parts, "intercooler").effects["icEff"]?.let { if ((parts["intercooler"] ?: 0) == 0 && def.stockTurbo) STOCK_IC else it } ?: 0.78f
        val psi = when {
            lim.boostMax <= 0f -> 0f
            !lim.boostAdjustable -> lim.springBoost
            else -> (tune?.boostPsi ?: minOf(lim.boostMax, DEFAULT_PSI)).coerceIn(SPRING_PSI.coerceAtMost(lim.boostMax), lim.boostMax)
        }
        val extraPsi = if (def.stockTurbo) maxOf(0f, psi - STOCK_TURBO_PSI) else psi
        val turbo = c.fitted(parts, "turbo").effects
        val sc = c.fitted(parts, "supercharger").effects
        val scPsi = if (lim.scMax > 0f) (tune?.scPsi ?: lim.scMax * 0.8f).coerceIn(lim.scMax * 0.5f, lim.scMax) else 0f
        val byGear = tune?.boostByGear?.takeIf { lim.boostByGear }

        val nosMax = lim.nitrousMax
        val nitrous = if (nosMax > 0f) {
            val shot = (tune?.nitrousShot ?: nosMax).coerceIn(nosMax * 0.25f, nosMax)
            CarDef.Nitrous(shot, c.fx(parts, "bottle", "charges").toInt().coerceAtLeast(1), def.nitrous.seconds.takeIf { it > 0f } ?: NITROUS_SECONDS)
        } else def.nitrous

        val ranges = lim
        val base = baseGears(def, c, parts)
        val gears = tune?.gears?.takeIf { it.size == base.size }
            ?.mapIndexed { i, g -> g.coerceIn(base[i] * (1 - ranges.gearRange), base[i] * (1 + ranges.gearRange)) }?.toFloatArray() ?: base
        val stockFinal = def.finalDrive * (c.fitted(parts, "final").effects["finalMult"] ?: 1f)
        val finalDrive = (tune?.finalDrive ?: stockFinal).coerceIn(stockFinal * (1 - ranges.finalRange), stockFinal * (1 + ranges.finalRange))

        val peakHp = def.peakHpRpm + peakRpm
        val ride = if (lim.rideHeight) (tune?.rideHeight ?: 0f).coerceIn(-1f, 1f) else 0f
        val shockRate = if (lim.shocks) 0.6f + 0.8f * (tune?.shockRate ?: 0.5f).coerceIn(0f, 1f) else 1f
        val maxRedline = maxOf(lim.limiterMax, peakHp + 300f)
        val redline = if (lim.limiter) (tune?.limiter ?: maxRedline).coerceIn(peakHp + 200f, maxRedline) else maxRedline
        return def.copy(
            peakHpRpm = peakHp,
            peakTorqueRpm = minOf(def.peakTorqueRpm + peakRpm * 0.6f, peakHp - 300f),
            idleRpm = def.idleRpm + idle,
            redline = redline,
            weightKg = def.weightKg + weight,
            rearWeight = (def.rearWeight + rear).coerceIn(0.3f, 0.7f),
            cgHeightM = def.cgHeightM * (1f + ride * RIDE_CG),
            grip = def.grip * (1 + grip),
            clutchTorque = def.clutchTorque + clutch,
            clutchTime = def.clutchTime * clutchTime,
            engineInertia = def.engineInertia * inertia,
            wheelInertia = def.wheelInertia * wheelInertia,
            shiftTime = (def.shiftTime * shift).coerceAtLeast(0.06f),
            dragArea = ((def.dragArea + drag) * (1f + ride * RIDE_DRAG)).coerceAtLeast(0.3f),
            gears = gears,
            finalDrive = finalDrive,
            nitrous = nitrous,
            boostGain = extraPsi * PSI_GAIN * icEff,
            // an aftermarket turbo replaces a factory one, so the stock boost spools with it
            boostSpoolRpm = turbo["spool"]?.let { it * spoolScale(def) } ?: def.boostSpoolRpm,
            boostLag = turbo["lag"] ?: def.boostLag,
            boostPsi = psi,
            boostByGear = byGear,
            blowOff = c.fx(parts, "bov", "bov") > 0f,
            antiLag = lim.antiLag && lim.launchControl && (tune?.launchRpm ?: 0f) > 0f,
            blowerGain = scPsi * PSI_GAIN * SC_EFF[(sc["scType"] ?: 0f).toInt()],
            blowerType = (sc["scType"] ?: 0f).toInt(),
            tirePressure = tune?.tirePressure ?: CarDef.STOCK_PRESSURE,
            pressureSens = c.fx(parts, "tires", "sens").let { if (it > 0f) it else 1f } * c.fitted(parts, "wheels").let { it.effects["sens"] ?: 1f },
            tireWarm = c.fx(parts, "tires", "warm").takeIf { it > 0f } ?: 0.78f,
            launchControl = if (lim.launchControl) tune?.launchRpm ?: 0f else 0f,
            nitrousAutoGear = tune?.nitrousGear ?: 0,
            nitrousRamp = if (lim.progressive) (tune?.nitrousRamp ?: 0.6f).coerceIn(0.1f, 2f) else 0f,
            bandLo = lo,
            bandMid = mid,
            bandHi = hi,
            powerMult = powerMult,
            knockLimit = knockLimit,
            fuelHp = fuelHp,
            transferMult = transfer,
            transferRate = transferRate * shockRate,
            wheelieBars = c.fx(parts, "wheeliebars", "wheelieBars") > 0f,
            timingDeg = timing,
            afr = afr,
        )
    }

    /**
     * A big engine moves more air and spools a given turbo sooner: the turbo's rated spool rpm is
     * for a ~300 Nm engine and scales with the square root of the stock torque, within 0.6..1.4.
     */
    fun spoolScale(def: CarDef) = Math.sqrt((SPOOL_REF_NM / (def.torqueNm * def.ratingFactor)).toDouble()).toFloat().coerceIn(0.6f, 1.4f)

    const val SPOOL_REF_NM = 300f
    const val BASE_RANGE = 0.05f
    /** Timing adjustment allowed by each ECU level. */
    val TIMING_RANGE = floatArrayOf(0f, 2f, 4f, 6f)
    const val TIMING_POWER = 0.012f
    const val TIMING_KNOCK = 0.03f
    const val AFR_MIN = 10.5f
    const val AFR_MAX = 13.5f
    const val AFR_POWER = 0.015f
    const val AFR_KNOCK = 0.05f
    /** Extra torque share per psi of boost (before the intercooler). */
    const val PSI_GAIN = 0.05f
    const val SPRING_PSI = 8f
    const val DEFAULT_PSI = 14f
    const val STOCK_TURBO_PSI = 10f
    const val STOCK_IC = 0.86f
    /** How well each supercharger type turns boost into power: none, roots, twin-screw, centrifugal. */
    val SC_EFF = floatArrayOf(0f, 0.72f, 0.85f, 0.9f)
    const val STOCK_FUEL_MARGIN = 1.3f
    const val NITROUS_SECONDS = 2.5f
    const val RIDE_CG = 0.06f
    const val RIDE_DRAG = 0.03f
}

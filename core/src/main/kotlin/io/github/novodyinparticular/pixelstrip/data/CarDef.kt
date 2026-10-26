package io.github.novodyinparticular.pixelstrip.data

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.JsonReader
import com.badlogic.gdx.utils.JsonValue

/**
 * One car, loaded from assets/data/cars/<id>.json. A data class so upgrades and tunes can make an
 * "effective" copy (see Upgrades.apply); the fields after [defaultPaint] only come from parts/tunes.
 */
data class CarDef(
    val id: String,
    val name: String,
    val brand: String,
    val year: Int,
    val era: String,
    val price: Int,
    /** False for cars you can only win (boss cars). */
    val dealer: Boolean,
    val hp: Float,
    val peakHpRpm: Float,
    val torqueNm: Float,
    val peakTorqueRpm: Float,
    val idleRpm: Float,
    val redline: Float,
    val weightKg: Float,
    val drivetrain: String,
    val rearWeight: Float,
    val wheelbaseM: Float,
    val cgHeightM: Float,
    val gears: FloatArray,
    val finalDrive: Float,
    val shiftTime: Float,
    val clutchTime: Float,
    /** Clutch capacity as a multiple of peak engine torque. */
    val clutchTorque: Float,
    val tireRadiusM: Float,
    val grip: Float,
    val engineInertia: Float,
    /** Driven wheels, tyres and axle together, kg m^2. */
    val wheelInertia: Float,
    val dragArea: Float,
    /** "gross" for period SAE gross ratings (derated in the physics), "net" otherwise. */
    val powerRating: String,
    val nitrous: Nitrous,
    /**
     * For the sound: "v8", "v6", "v10", "v12", "i4", "i6", "flat4", "flat6", "flat12" or "rotary";
     * cylinders (rotors for a rotary).
     */
    val engineLayout: String,
    val cylinders: Int,
    val stockTurbo: Boolean,
    val sprite: SpriteDef,
    val defaultPaint: Int,
    /** Turbo: extra torque share at full boost, the RPM it starts spooling, seconds to spool. */
    val boostGain: Float = 0f,
    val boostSpoolRpm: Float = 0f,
    val boostLag: Float = 0.8f,
    /**
     * Factory turbo: the share of the published torque its stock boost makes. It spools like any
     * turbo (from [boostSpoolRpm], over [boostLag]); off boost the engine makes 1 / (1 + this).
     */
    val stockBoostGain: Float = 0f,
    /** An automatic with a torque converter: staged on the brake it loads the engine and builds boost. */
    val converter: Boolean = false,
    /** First-person cockpit art (tools/art/cockpit.py STYLES); null = the era's. */
    val cockpit: String? = null,
    /** Variable cam timing that switches to the high-lift lobe at this rpm (0 = none): the sound gets harder. */
    val vtecRpm: Float = 0f,
    /** Supercharger: instant extra torque share (grows a little with RPM). */
    val blowerGain: Float = 0f,
    val tirePressure: Float = STOCK_PRESSURE,
    val pressureSens: Float = 1f,
    /** Launch control: staged RPM held automatically while the gas is down (0 = off). */
    val launchControl: Float = 0f,
    /** Spray automatically on reaching this gear (1-based, 0 = manual). */
    val nitrousAutoGear: Int = 0,
    /** Progressive nitrous: seconds to bring the full shot in (0 = all at once). */
    val nitrousRamp: Float = 0f,
    /** Turbo boost target (psi, for the gauges), its share allowed per gear, blow-off valve, anti-lag on the two-step. */
    val boostPsi: Float = 0f,
    val boostByGear: FloatArray? = null,
    val blowOff: Boolean = false,
    val antiLag: Boolean = false,
    /** Supercharger type: 0 none, 1 roots, 2 twin-screw, 3 centrifugal (boost builds with rpm). */
    val blowerType: Int = 0,
    /** Torque added near idle / at the torque peak / at peak power and above (shares), and a global multiplier. */
    val bandLo: Float = 0f,
    val bandMid: Float = 0f,
    val bandHi: Float = 0f,
    val powerMult: Float = 1f,
    /**
     * Highest cylinder-pressure multiplier (boost, blower and nitrous over the engine on its own)
     * before it knocks; past it the ECU pulls timing and power is capped there.
     */
    val knockLimit: Float = Float.MAX_VALUE,
    /** Crank horsepower the fuel system can feed; past it the engine runs lean and is held there. */
    val fuelHp: Float = Float.MAX_VALUE,
    /** Weight transfer: amount and speed multipliers; wheelie bars stop the driver lifting. */
    val transferMult: Float = 1f,
    val transferRate: Float = 1f,
    val wheelieBars: Boolean = false,
    /** Tyre temperature this compound likes best (slicks want more heat). */
    val tireWarm: Float = 0.78f,
    val timingDeg: Float = 0f,
    val afr: Float = 12.8f,
) {
    data class Nitrous(val hp: Float, val charges: Int, val seconds: Float)

    val ratingFactor get() = if (powerRating == "gross") io.github.novodyinparticular.pixelstrip.race.DragMath.GROSS_TO_NET else 1f

    class SpriteDef(
        val dir: String,
        val width: Int,
        val height: Int,
        val rearWheel: Pair<Float, Float>,
        val frontWheel: Pair<Float, Float>,
        val wheelSize: Int,
        val wheelFrames: Int,
        val blurFrames: Int,
        val spokes: Int,
        val headlight: Pair<Float, Float>,
        val taillight: Pair<Float, Float>,
        val exhaust: Pair<Float, Float>,
        val groundY: Float,
        val frontX: Float,
        val popup: Boolean,
        val wheelRadius10: Int,
        val sideExhaust: Pair<Float, Float>?,
        val driver: Pair<Float, Float>?,
        /** Centre of the door roundel, where a crew emblem goes (the CREW decal). */
        val door: Pair<Float, Float>?,
        /** Pixels per profile unit the sprite was drawn at (profiles are ~18.2 units per metre). */
        val scale: Float = 1f,
    )

    companion object {
        /** Stats from data/cars/<id>.json; sprite anchors from the generated cars/<id>/sprite.json. */
        const val STOCK_PRESSURE = 32f
        /** Stock boost (Upgrades.STOCK_TURBO_PSI through the factory intercooler) as a torque share. */
        const val STOCK_BOOST_GAIN = Upgrades.STOCK_TURBO_PSI * Upgrades.PSI_GAIN * Upgrades.STOCK_IC
        const val STOCK_SPOOL_MIN = 1200f
        const val STOCK_LAG = 0.6f

        fun load(id: String): CarDef {
            val stats = JsonReader().parse(Gdx.files.internal("data/cars/$id.json"))
            val sprite = stats.get("sprite") ?: JsonReader().parse(Gdx.files.internal("cars/$id/sprite.json"))
            return parse(stats, sprite)
        }

        /** Every car id in the data folder, sorted by the order of the roster file. */
        fun ids(): List<String> = JsonReader().parse(Gdx.files.internal("data/roster.json")).asStringArray().toList()

        private fun JsonValue.pair(name: String): Pair<Float, Float> = get(name).let { it.getFloat(0) to it.getFloat(1) }

        fun parse(j: JsonValue, s: JsonValue = j.get("sprite")): CarDef {
            return CarDef(
                id = j.getString("id"),
                name = j.getString("name"),
                brand = j.getString("brand"),
                year = j.getInt("year"),
                era = j.getString("era"),
                price = j.getInt("price", 0),
                dealer = j.getBoolean("dealer", true),
                hp = j.getFloat("hp"),
                peakHpRpm = j.getFloat("peakHpRpm"),
                torqueNm = j.getFloat("torqueNm"),
                peakTorqueRpm = j.getFloat("peakTorqueRpm"),
                idleRpm = j.getFloat("idleRpm", 800f),
                redline = j.getFloat("redline"),
                weightKg = j.getFloat("weightKg"),
                drivetrain = j.getString("drivetrain"),
                rearWeight = j.getFloat("rearWeight", 0.5f),
                wheelbaseM = j.getFloat("wheelbaseM", 2.6f),
                cgHeightM = j.getFloat("cgHeightM", 0.5f),
                gears = j.get("gears").asFloatArray(),
                finalDrive = j.getFloat("finalDrive"),
                shiftTime = j.getFloat("shiftTime", 0.25f),
                clutchTime = j.getFloat("clutchTime", 0.3f),
                clutchTorque = j.getFloat("clutchTorque", 1.5f),
                tireRadiusM = j.getFloat("tireRadiusM"),
                grip = j.getFloat("grip"),
                engineInertia = j.getFloat("engineInertia", 0.25f),
                wheelInertia = j.getFloat("wheelInertia", 2.5f),
                dragArea = j.getFloat("dragArea"),
                powerRating = j.getString("powerRating", "net"),
                nitrous = j.get("nitrous")?.let { Nitrous(it.getFloat("hp"), it.getInt("charges"), it.getFloat("seconds")) }
                    ?: Nitrous(0f, 0, 0f),
                engineLayout = j.get("engine")?.getString("layout", "v8") ?: "v8",
                cylinders = j.get("engine")?.getInt("cylinders", 8) ?: 8,
                stockTurbo = j.get("engine")?.getBoolean("stockTurbo", false) ?: false,
                converter = j.getBoolean("converter", false),
                vtecRpm = j.get("engine")?.getFloat("vtecRpm", 0f) ?: 0f,
                cockpit = j.getString("cockpit", null),
                stockBoostGain = if (j.get("engine")?.getBoolean("stockTurbo", false) == true) STOCK_BOOST_GAIN else 0f,
                // a factory turbo is sized to be on full boost by the torque peak
                boostSpoolRpm = if (j.get("engine")?.getBoolean("stockTurbo", false) == true)
                    j.get("engine").getFloat("spoolRpm", maxOf(STOCK_SPOOL_MIN, j.getFloat("peakTorqueRpm") - io.github.novodyinparticular.pixelstrip.race.Vehicle.BOOST_RANGE_RPM)) else 0f,
                boostLag = if (j.get("engine")?.getBoolean("stockTurbo", false) == true) j.get("engine").getFloat("lag", STOCK_LAG) else 0.8f,
                defaultPaint = j.getInt("defaultPaint", 0),
                sprite = SpriteDef(
                    dir = s.getString("dir"),
                    width = s.getInt("width"),
                    height = s.getInt("height"),
                    rearWheel = s.pair("rearWheel"),
                    frontWheel = s.pair("frontWheel"),
                    wheelSize = s.getInt("wheelSize"),
                    wheelFrames = s.getInt("wheelFrames"),
                    blurFrames = s.getInt("blurFrames"),
                    spokes = s.getInt("spokes"),
                    headlight = s.pair("headlight"),
                    taillight = s.pair("taillight"),
                    exhaust = s.pair("exhaust"),
                    groundY = s.getFloat("groundY"),
                    frontX = s.getFloat("frontX"),
                    popup = s.getBoolean("popup", false),
                    wheelRadius10 = s.getInt("wheelRadius10", 70),
                    sideExhaust = s.get("sideExhaust")?.takeIf { it.isArray }?.let { it.getFloat(0) to it.getFloat(1) },
                    driver = s.get("driver")?.takeIf { it.isArray }?.let { it.getFloat(0) to it.getFloat(1) },
                    door = s.get("door")?.takeIf { it.isArray }?.let { it.getFloat(0) to it.getFloat(1) },
                    scale = s.getFloat("scale", 1f),
                ),
            )
        }
    }
}

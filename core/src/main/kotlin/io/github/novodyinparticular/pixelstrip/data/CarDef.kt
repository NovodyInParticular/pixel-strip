package io.github.novodyinparticular.pixelstrip.data

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.JsonReader
import com.badlogic.gdx.utils.JsonValue

/** One car, loaded from assets/data/cars/<id>.json. Stats are stock; upgrades layer on top later. */
class CarDef(
    val id: String,
    val name: String,
    val brand: String,
    val year: Int,
    val era: String,
    val price: Int,
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
    val sprite: SpriteDef,
    val defaultPaint: Int,
) {
    class Nitrous(val hp: Float, val charges: Int, val seconds: Float)

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
    )

    companion object {
        /** Stats from data/cars/<id>.json; sprite anchors from the generated cars/<id>/sprite.json. */
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
                ),
            )
        }
    }
}

package io.github.novodyinparticular.pixelstrip.race

import io.github.novodyinparticular.pixelstrip.data.CarDef
import kotlin.math.PI

/** Car-level numbers derived from the stats: torque curve, best shift points, best launch RPM. */
object DragMath {
    private val launchCache = HashMap<Pair<CarDef, Float>, Float>()

    /** Naturally aspirated torque (Nm) at an RPM: the stock curve, reshaped by the parts' torque bands. */
    fun torqueAt(def: CarDef, r: Float): Float = rawTorque(def, r) * def.ratingFactor * band(def, r) * def.powerMult

    /** Torque share the parts add at [r]: idle -> torque peak -> power peak, flat past it. */
    fun band(def: CarDef, r: Float): Float {
        val add = when {
            r <= def.idleRpm -> def.bandLo
            r <= def.peakTorqueRpm -> def.bandLo + (def.bandMid - def.bandLo) * (r - def.idleRpm) / (def.peakTorqueRpm - def.idleRpm)
            r <= def.peakHpRpm -> def.bandMid + (def.bandHi - def.bandMid) * (r - def.peakTorqueRpm) / (def.peakHpRpm - def.peakTorqueRpm)
            else -> def.bandHi
        }
        return (1f + add).coerceAtLeast(0.3f)
    }

    /** Supercharger torque share at [r]: roots fades up top, twin-screw holds, centrifugal builds with rpm. */
    fun blower(def: CarDef, r: Float): Float {
        val t = (r / def.redline).coerceIn(0f, 1f)
        return def.blowerGain * when (def.blowerType) {
            1 -> 1.05f - 0.25f * t
            2 -> 0.95f + 0.05f * t
            3 -> t * t
            else -> 0.75f + 0.25f * t
        }
    }

    /** Why the last [limit] call held the engine back. */
    enum class Limit { NONE, KNOCK, FUEL }

    /**
     * Caps [total] crank torque at [r] when the boost/nitrous on top of [na] (the engine on its
     * own) is past the knock limit (the ECU pulls timing, costing a little extra), or when the fuel
     * system can't feed the power.
     */
    fun limit(def: CarDef, r: Float, na: Float, total: Float, why: Array<Limit>? = null): Float {
        var t = total
        var reason = Limit.NONE
        if (na > 0f && t > na * def.knockLimit) {
            t = na * def.knockLimit * KNOCK_RETARD
            reason = Limit.KNOCK
        }
        val fuelT = def.fuelHp * Vehicle.HP_TO_W / (maxOf(r, 1000f) * Vehicle.RPM_TO_RAD)
        if (t > fuelT) {
            t = fuelT * LEAN_CUT
            reason = Limit.FUEL
        }
        why?.set(0, reason)
        return t
    }

    const val KNOCK_RETARD = 0.97f
    const val LEAN_CUT = 0.97f

    /** Full-throttle crank torque as the dyno sees it: blower, turbo at steady-state boost, knock and fuel limits. */
    fun crankTorque(def: CarDef, r: Float, why: Array<Limit>? = null): Float {
        val na = torqueAt(def, r)
        var t = na
        if (def.blowerGain > 0f) t *= 1f + blower(def, r)
        if (def.boostGain > 0f) t *= 1f + def.boostGain * ((r - def.boostSpoolRpm) / Vehicle.BOOST_RANGE_RPM).coerceIn(0f, 1f)
        return limit(def, r, na, t, why)
    }

    /** Wheel horsepower at an RPM. */
    fun wheelHp(def: CarDef, r: Float) = crankTorque(def, r) * r / 7127f * Vehicle.DRIVELINE_EFF

    /** Pre-1972 cars were rated in SAE gross horsepower (no accessories, open headers): ~18% optimistic. */
    const val GROSS_TO_NET = 0.82f

    private fun rawTorque(def: CarDef, r: Float): Float {
        val tPeak = def.torqueNm
        val tAtPeakHp = def.hp * 745.7f / (def.peakHpRpm * 2f * PI.toFloat() / 60f)
        return when {
            r <= def.peakTorqueRpm -> {
                val t = ((r - def.idleRpm) / (def.peakTorqueRpm - def.idleRpm)).coerceIn(0f, 1f)
                tPeak * (0.62f + 0.38f * (1f - (1f - t) * (1f - t)))
            }
            r <= def.peakHpRpm -> tPeak + (tAtPeakHp - tPeak) * (r - def.peakTorqueRpm) / (def.peakHpRpm - def.peakTorqueRpm)
            else -> tAtPeakHp * (1f - 0.55f * ((r - def.peakHpRpm) / (def.redline - def.peakHpRpm + 1f)).coerceIn(0f, 1f))
        }
    }

    /**
     * Best upshift RPM per gear: the point where the next gear puts more torque on the wheels
     * than this one. If that never happens before the limiter, shift just under redline.
     */
    fun shiftPoints(def: CarDef): FloatArray = FloatArray(def.gears.size) { g ->
        if (g == def.gears.size - 1) return@FloatArray def.redline
        val r1 = def.gears[g]
        val r2 = def.gears[g + 1]
        var rpm = def.peakTorqueRpm
        while (rpm < def.redline * 0.985f) {
            if (torqueAt(def, rpm * r2 / r1) * r2 >= torqueAt(def, rpm) * r1) return@FloatArray rpm
            rpm += 25f
        }
        def.redline * 0.985f
    }

    /**
     * The launch sweet spot. A car that can overpower its tyres in first gear wants just enough
     * RPM to stay out of the bog; a car that can't wants to launch nearer its torque peak.
     */
    fun launchRpm(def: CarDef, trackGrip: Float): Float = launchCache.getOrPut(def to trackGrip) {
        val g = 9.81f * def.weightKg
        val load = when (def.drivetrain) {
            "AWD" -> g
            "FWD" -> g * (1f - def.rearWeight) * 0.9f
            else -> g * (def.rearWeight + 0.08f)
        }
        val grip = def.grip * trackGrip * load
        val ratio = def.gears[0] * def.finalDrive
        val bogEdge = def.idleRpm * 2.0f / (1f - Vehicle.LAUNCH_SAG)
        var rpm = def.idleRpm * 1.5f
        while (rpm < def.peakTorqueRpm && torqueAt(def, rpm) * ratio * Vehicle.DRIVELINE_EFF / def.tireRadiusM < grip * 1.6f) rpm += 50f
        rpm.coerceIn(bogEdge, def.peakHpRpm * 0.85f)
    }

    class Pass(val sixty: Float, val eighth: Float, val quarter: Float, val trapMph: Float)

    private val passCache = HashMap<CarDef, Pass>()

    /** A perfect driver's quarter mile on warm tyres: the basis for classes and the stat panels. */
    fun perfectPass(def: CarDef): Pass = passCache.getOrPut(def) {
        val car = Vehicle(def)
        car.prime(launchRpm(def, Vehicle.DEFAULT_PREP), 0.78f)
        car.launch(0f, rate = false)
        car.throttle = 1f
        var t = 0f
        while (car.x < Vehicle.SPLITS_M[4] && t < 40f) {
            if (car.mode == Vehicle.Mode.DRIVE && car.gear < def.gears.size - 1 && car.rpm >= car.shiftPoints[car.gear]) car.shift()
            car.step(1f / 240f)
            t += 1f / 240f
        }
        car.events.clear()
        Pass(car.splits[0], car.splits[2], car.splits[4], car.splitSpeed[4] * 2.23694f)
    }
}

package io.github.novodyinparticular.pixelstrip.race

import io.github.novodyinparticular.pixelstrip.data.CarDef
import kotlin.math.PI

/** Car-level numbers derived from the stats: torque curve, best shift points, best launch RPM. */
object DragMath {
    private val launchCache = HashMap<Pair<CarDef, Float>, Float>()

    /** Torque (Nm) at an RPM: rises to the peak, holds toward peak power, falls past it. */
    fun torqueAt(def: CarDef, r: Float): Float = rawTorque(def, r) * def.ratingFactor

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
}

package io.github.novodyinparticular.pixelstrip.race

import com.badlogic.gdx.math.MathUtils

/**
 * Drives a [Vehicle] through the whole sequence: burnout, stage, rev, react to the green,
 * shift and spray. [skill] 0..1 tightens the reaction, launch RPM and shift timing.
 */
class AutoDriver(private val car: Vehicle, private val skill: Float, private val trackGrip: Float) {
    private val reaction = MathUtils.lerp(0.42f, 0.17f, skill) + MathUtils.random(-0.04f, 0.04f)
    private val launchError = MathUtils.random(-1f, 1f) * MathUtils.lerp(0.2f, 0.03f, skill)
    private val shiftError = FloatArray(car.def.gears.size) { MathUtils.random(-1f, 1f) * MathUtils.lerp(0.09f, 0.012f, skill) }
    var burnoutTime = MathUtils.random(1.8f, 2.6f)
    var wantsStage = false
        private set
    var wantsLaunch = false
        private set
    var wantsShift = false
        private set
    var wantsNitrous = false
        private set

    /** Throttle for this frame; [raceClock] is seconds since green (negative before). */
    fun update(dt: Float, staged: Boolean, raceClock: Float, treeRunning: Boolean): Float {
        wantsStage = false
        wantsLaunch = false
        wantsShift = false
        wantsNitrous = false
        return when (car.mode) {
            Vehicle.Mode.BURNOUT -> {
                burnoutTime -= dt
                if (burnoutTime <= 0f) {
                    wantsStage = true
                    0f
                } else 1f
            }
            Vehicle.Mode.CREEP -> 0f
            Vehicle.Mode.HELD -> {
                if (!staged || !treeRunning) return 0f
                val target = DragMath.launchRpm(car.def, trackGrip) * (1f + launchError)
                if (raceClock >= reaction) wantsLaunch = true
                if (car.rpm < target - 120f) 1f else if (car.rpm > target + 120f) 0f else 0.5f
            }
            Vehicle.Mode.LAUNCH, Vehicle.Mode.DRIVE, Vehicle.Mode.SHIFT -> {
                if (car.mode == Vehicle.Mode.DRIVE && car.gear < car.def.gears.size - 1 &&
                    car.rpm >= car.shiftPoints[car.gear] * (1f + shiftError[car.gear])) wantsShift = true
                if (car.gear >= 1 && car.mode == Vehicle.Mode.DRIVE) wantsNitrous = true
                1f
            }
        }
    }
}

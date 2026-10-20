package io.github.novodyinparticular.pixelstrip.race

import com.badlogic.gdx.math.MathUtils

/** Body pitch on its springs: squat and nose-lift under power, wheelstands, a dip and rebound on each shift. */
class CarMotion {
    var pitch = 0f          // degrees, nose up positive
        private set
    var lift = 0f           // pixels the body rides above its rest height
        private set
    private var vel = 0f

    fun update(dt: Float, car: Vehicle, idle: Boolean) {
        val target = (car.accel / 9.81f).coerceIn(-0.6f, 1.2f) * 2.6f + car.wheelie * 9f
        // spring-damper toward the load-dependent angle
        vel += ((target - pitch) * STIFFNESS - vel * DAMPING) * dt
        pitch += vel * dt
        lift = when {
            car.mode == Vehicle.Mode.BURNOUT && car.spinning -> MathUtils.random(0, 1).toFloat()
            idle -> if (MathUtils.random() < 0.15f) 1f else 0f      // big-cam idle lope
            else -> 0f
        }
    }

    /** Called on each upshift: the nose dips as the drive cuts, then the next gear lifts it. */
    fun kick(amount: Float = -14f) {
        vel += amount
    }

    companion object {
        const val STIFFNESS = 120f
        const val DAMPING = 11f
    }
}

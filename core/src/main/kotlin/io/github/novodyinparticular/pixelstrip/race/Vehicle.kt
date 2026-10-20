package io.github.novodyinparticular.pixelstrip.race

import io.github.novodyinparticular.pixelstrip.data.CarDef
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Straight-line drag car. Engine with a torque curve, rev limiter and nitrous; a clutch that
 * slips while it engages; a driven axle that either rolls with the road (grip) or spins on its
 * own inertia once the tyres break loose; weight transfer onto the driven wheels; gears with
 * timed shifts. Stats from [CarDef] (and later upgrades) meaningfully change every part of it.
 */
class Vehicle(val def: CarDef, private val powerScale: Float = 1f) {
    enum class Mode { HELD, BURNOUT, CREEP, LAUNCH, DRIVE, SHIFT }
    enum class Rating { PERFECT, GOOD, EARLY, LATE, BOG, SPIN }

    sealed class Event {
        class Launched(val rating: Rating, val rpm: Float) : Event()
        class Shifted(val toGear: Int, val rating: Rating, val rpm: Float) : Event()
        object NitrousOn : Event()
        object NitrousOff : Event()
    }

    var mode = Mode.HELD
        private set
    var x = 0f              // front bumper, metres past the start line
    var v = 0f              // m/s
        private set
    var accel = 0f
        private set
    var rpm = def.idleRpm
        private set
    var gear = 0
        private set
    /** Driven wheels' angular speed (rad/s). Faster than the road while spinning. */
    var driveW = 0f
        private set
    val rollW get() = v / tireR
    var spinning = false
        private set
    /** Surface speed of the spinning tyres over the road, m/s (smoke comes from this). */
    var slipSpeed = 0f
        private set
    var tireTemp = 0.15f
        private set
    /** 0..1 how far the front end is off the ground. */
    var wheelie = 0f
        private set
    var limiter = false
        private set

    var throttle = 0f
    var brake = 0f
    /** Track surface grip: prepped drag strips hook far better than a street (set from the track). */
    var trackGrip = DEFAULT_PREP

    var nitrousCharges = def.nitrous.charges
        private set
    var nitrousTime = 0f
        private set
    val nitrousActive get() = nitrousTime > 0f

    val shiftPoints: FloatArray = DragMath.shiftPoints(def)
    val ratings = ArrayList<Rating>()       // launch first, then each shift
    val events = ArrayList<Event>()

    // Timing (race clock: 0 at green, so a negative leave time is a red light). Splits: -1 = not reached.
    var hasLeft = false
        private set
    var leaveTime = 0f
        private set
    val splits = FloatArray(SPLITS_M.size) { -1f }
    val splitSpeed = FloatArray(SPLITS_M.size)

    private var clutch = 0f
    private var launchRpm = 0f
    private var feather = 1f
    private var shiftTimer = 0f
    private var bonusTimer = 0f
    private var limiterTimer = 0f
    private var creepTarget = 0f
    private var surface = DEFAULT_PREP
    private var clock = 0f

    /** Turbo boost 0..1: spools up with RPM and time. */
    var boost = 0f
        private set
    /** Why the engine is being held back right now (for the HUD and the dyno). */
    val held = arrayOf(DragMath.Limit.NONE)
    private var nitrousOn = 0f
    /** Weight transfer arrives through the springs and shocks, not instantly. */
    private var transferAccel = 0f

    private val mass get() = def.weightKg
    private val ratio get() = def.gears[gear] * def.finalDrive
    private val pressureDelta = (CarDef.STOCK_PRESSURE - def.tirePressure)
    /** Lower pressure: a bigger contact patch, more grip, more rolling drag, a slightly smaller tyre. */
    private val pressureGrip = 1f + pressureDelta * PRESSURE_GRIP * def.pressureSens
    private val tireR = def.tireRadiusM * (1f - pressureDelta * PRESSURE_RADIUS)
    private val rolling = ROLLING * (1f + pressureDelta * PRESSURE_ROLL)
    private val peakGrip get() = def.grip * tempGrip() * surface * pressureGrip

    // --- Commands ------------------------------------------------------------------------

    /** Wet burnout box: front brakes on, driven wheels free to spin. */
    fun startBurnout() {
        mode = Mode.BURNOUT
        surface = BURNOUT_SURFACE
        gear = 0
    }

    fun creepTo(targetX: Float) {
        mode = Mode.CREEP
        surface = trackGrip
        creepTarget = targetX
    }

    fun hold() {
        mode = Mode.HELD
        surface = trackGrip
    }

    /** Dump the clutch from the current (staged) RPM. [rate] = false skips the rating (simulations). */
    fun launch(raceClock: Float, rate: Boolean = true) {
        if (mode != Mode.HELD) return
        clock = raceClock
        mode = Mode.LAUNCH
        clutch = 0f
        gear = 0
        launchRpm = rpm
        feather = 1f
        val sweet = DragMath.launchRpm(def, trackGrip)
        if (rpm > sweet) {
            // The extra engine speed above the sweet spot dumps into the tyres as a spin burst
            val extraW = (rpm - sweet) * RPM_TO_RAD
            val inertia = def.wheelInertia + def.engineInertia * ratio * ratio
            driveW = def.engineInertia * extraW * ratio / inertia * SPIN_SHOCK
            spinning = driveW * tireR > 0.4f
        }
        if (!rate) return
        val d = (rpm - sweet) / sweet
        val r = when {
            abs(d) <= 0.08f -> Rating.PERFECT
            abs(d) <= 0.18f -> Rating.GOOD
            d < 0f -> Rating.BOG
            else -> Rating.SPIN
        }
        ratings.add(r)
        events.add(Event.Launched(r, rpm))
    }

    fun shift() {
        if (gear >= def.gears.size - 1 || (mode != Mode.DRIVE && mode != Mode.LAUNCH)) return
        val opt = shiftPoints[gear]
        val d = (rpm - opt) / opt
        val r = when {
            abs(d) <= 0.035f -> Rating.PERFECT
            abs(d) <= 0.09f -> Rating.GOOD
            d < 0f -> Rating.EARLY
            else -> Rating.LATE
        }
        shiftTimer = def.shiftTime * when (r) {
            Rating.PERFECT -> 0.55f
            Rating.GOOD -> 0.85f
            else -> 1f
        }
        if (r == Rating.PERFECT) bonusTimer = PERFECT_BONUS_S
        mode = Mode.SHIFT
        ratings.add(r)
        events.add(Event.Shifted(gear + 2, r, rpm))
    }

    fun nitrous(): Boolean {
        if (nitrousCharges <= 0 || nitrousActive || def.nitrous.hp <= 0f) return false
        if (mode != Mode.DRIVE && mode != Mode.SHIFT && mode != Mode.LAUNCH) return false
        nitrousCharges--
        nitrousTime = def.nitrous.seconds
        events.add(Event.NitrousOn)
        return true
    }

    /** Test and AI hooks: set the staged RPM and tyre temperature directly. */
    fun prime(stagedRpm: Float, temp: Float) {
        rpm = stagedRpm.coerceIn(def.idleRpm, def.redline)
        tireTemp = temp
    }

    // --- Model ---------------------------------------------------------------------------

    fun torqueAt(r: Float) = DragMath.torqueAt(def, r) * powerScale

    private fun tempGrip(): Float {
        val t = tireTemp
        val bell = 1f - ((t - def.tireWarm) / 0.4f).let { it * it }
        val hot = if (t > 1.1f) (t - 1.1f) * 0.6f else 0f
        return 1f + 0.12f * max(0f, bell) - hot
    }

    private fun friction(r: Float) = 15f + r * 0.008f

    /** Dragged down near idle the engine falls on its face: launching too low bogs. */
    private fun bogFactor(): Float {
        val lo = def.idleRpm * 0.8f
        val hi = def.idleRpm * 2.0f
        val t = ((rpm - lo) / (hi - lo)).coerceIn(0f, 1f)
        return 0.22f + 0.78f * t * t * (3f - 2f * t)
    }

    /** Net engine torque at the crank. */
    private fun engineTorque(dt: Float, bog: Boolean = true): Float {
        if (rpm >= def.redline) limiterTimer = LIMITER_CUT_S
        limiter = limiterTimer > 0f
        if (limiter) {
            limiterTimer -= dt
            return -friction(rpm)
        }
        val na = torqueAt(rpm) * (if (bog) bogFactor() else 1f)
        var t = na
        if (def.blowerGain > 0f) t *= 1f + DragMath.blower(def, rpm)
        if (def.boostGain > 0f) t *= 1f + def.boostGain * boost
        if (nitrousActive) {
            // a progressive controller brings the shot in over nitrousRamp seconds
            nitrousOn = if (def.nitrousRamp > 0f) min(1f, nitrousOn + dt / def.nitrousRamp) else 1f
            t += def.nitrous.hp * nitrousOn * HP_TO_W / (max(rpm, NITROUS_MIN_RPM) * RPM_TO_RAD)
        } else nitrousOn = 0f
        t = DragMath.limit(def, rpm, na, t, held) * throttle
        if (bonusTimer > 0f) t *= 1f + PERFECT_BONUS
        // Published torque is already net of friction; it only shows up as engine braking off-throttle
        return t - friction(rpm) * (1f - throttle)
    }

    private fun driveLoad(): Float {
        val g = mass * GRAVITY
        val transfer = mass * transferAccel * def.cgHeightM / def.wheelbaseM * def.transferMult
        val front = g * (1f - def.rearWeight) - transfer
        wheelie = if (front < 0f) min(if (def.wheelieBars) WHEELIE_BAR_LIFT else 1f, -front / g * 3f) else 0f
        return when (def.drivetrain) {
            "AWD" -> g
            "FWD" -> max(g * 0.15f, front)
            else -> min(g, g * def.rearWeight + transfer)
        }
    }

    private fun slipRatio(slip: Float) = slip / max(v, 3f)

    /** Grip share (0..1 of peak) at a slip ratio: rises to the peak, plateaus, then falls off as the tyres spin. */
    private fun gripShare(s: Float) = when {
        s < PEAK_SLIP -> (s / PEAK_SLIP) * (2f - s / PEAK_SLIP)
        s < PLATEAU_SLIP -> 1f
        else -> 1f - SPIN_FALLOFF * min(1f, (s - PLATEAU_SLIP) / 0.8f)
    }

    private fun spool(dt: Float) {
        if (def.boostGain <= 0f) return
        var target = ((rpm - def.boostSpoolRpm) / BOOST_RANGE_RPM).coerceIn(0f, 1f) * throttle
        def.boostByGear?.getOrNull(gear)?.let { target *= it.coerceIn(0.2f, 1f) }
        // anti-lag on the two-step builds boost on the line; without a blow-off valve a shift dumps it
        if (mode == Mode.HELD && def.antiLag && throttle > 0f) target = max(target, ANTI_LAG_BOOST)
        if (mode == Mode.SHIFT) {
            boost *= if (def.blowOff) 1f - dt * 0.4f else 1f - min(1f, dt * 6f)
            return
        }
        val rate = if (target > boost) dt / def.boostLag else dt * 3f      // blow-off: boost drops fast
        boost += (target - boost) * minOf(1f, rate * 3f)
    }

    fun step(dt: Float) {
        clock += dt
        transferAccel += (accel - transferAccel) * min(1f, dt * def.transferRate / TRANSFER_TAU)
        spool(dt)
        if (def.nitrousAutoGear > 0 && hasLeft && mode == Mode.DRIVE && gear + 1 >= def.nitrousAutoGear) nitrous()
        if (nitrousActive) {
            nitrousTime -= dt
            if (nitrousTime <= 0f) events.add(Event.NitrousOff)
        }
        if (bonusTimer > 0f) bonusTimer -= dt
        when (mode) {
            Mode.HELD -> {
                // Staged: brakes on, clutch in. Gas revs the engine toward the limiter.
                rpm = if (throttle > 0f) min(def.redline, rpm + REV_UP * throttle * dt) else max(def.idleRpm, rpm - REV_DOWN * dt)
                if (def.launchControl > 0f && throttle > 0f) rpm = min(rpm, def.launchControl)   // two-step holds the revs
                limiter = rpm >= def.redline - 1f
                v = 0f
                driveW = 0f
                spinning = false
                slipSpeed = 0f
                cool(dt)
            }
            Mode.CREEP -> {
                rpm += (def.idleRpm * 1.15f - rpm) * min(1f, dt * 4f)
                v = if (creepTarget - x > 0.7f) CREEP_SPEED else STAGE_SPEED    // roll up, then inch into the beams
                x = min(creepTarget, x + v * dt)
                if (x >= creepTarget) {
                    v = 0f
                    mode = Mode.HELD
                }
                driveW = rollW
                spinning = false
                slipSpeed = 0f
            }
            Mode.BURNOUT -> burnout(dt)
            Mode.LAUNCH, Mode.DRIVE -> drive(dt)
            Mode.SHIFT -> shifting(dt)
        }
        if (mode == Mode.LAUNCH || mode == Mode.DRIVE || mode == Mode.SHIFT) timing()
    }

    private fun cool(dt: Float) {
        tireTemp = max(0.1f, tireTemp - dt * 0.015f)
    }

    private fun burnout(dt: Float) {
        v = 0f
        accel = 0f
        val inertia = def.wheelInertia + def.engineInertia * ratio * ratio
        val load = driveLoad()
        val wheelT = engineTorque(dt, bog = false) * ratio * DRIVELINE_EFF
        val staticLimit = peakGrip * load * tireR
        if (driveW * tireR < 0.3f && wheelT <= staticLimit) {
            driveW = 0f                               // not enough to break them loose
        } else {
            val f = peakGrip * (1f - SPIN_FALLOFF) * load
            driveW = max(0f, driveW + (wheelT - f * tireR) / inertia * dt)
        }
        rpm = max(def.idleRpm * if (throttle > 0f) 1.8f else 1f, driveW * ratio / RPM_TO_RAD)
        if (rpm >= def.redline) rpm = def.redline
        slipSpeed = driveW * tireR
        spinning = slipSpeed > 0.5f
        tireTemp = min(1.4f, tireTemp + slipSpeed * BURNOUT_HEAT * dt)
    }

    private fun drive(dt: Float) {
        val load = driveLoad()
        val grip = peakGrip * load
        val coupledRpm = driveW * ratio / RPM_TO_RAD
        if (mode == Mode.LAUNCH) {
            // Clutch slipping: the engine sags from the staged RPM until the axle catches up.
            // Staged too low, it sags into the bog zone and falls on its face.
            clutch = min(1f, clutch + dt / def.clutchTime)
            val sagged = launchRpm * (1f - LAUNCH_SAG * clutch)
            rpm = max(coupledRpm, sagged)
            if (coupledRpm >= sagged) mode = Mode.DRIVE
        } else {
            rpm = coupledRpm
        }
        rpm = rpm.coerceIn(def.idleRpm * BOG_FLOOR, def.redline + 50f)

        val fullT = engineTorque(dt) * ratio * DRIVELINE_EFF
        // The driver pedals to the traction limit: off while the tyres spin, back to just past
        // the limit once they hook. More power than grip costs spin, not a permanent loss.
        val ideal = if (fullT > 0f) (grip * tireR / fullT).coerceIn(FEATHER_MIN, 1f) else 1f
        val share = if (spinning) gripShare(slipRatio(max(0f, driveW * tireR - v))) else 1f
        feather = if (spinning) max(ideal * share * 0.85f, feather - dt * FEATHER_DROP)
        else min(min(1f, ideal * 1.04f), feather + dt * FEATHER_RISE)
        // Without wheelie bars a big wheelie makes the driver lift
        if (!def.wheelieBars && wheelie > WHEELIE_LIFT) feather = min(feather, 1f - (wheelie - WHEELIE_LIFT) * 1.2f)
        val axleT = fullT * feather
        val driveF = axleT / tireR
        val inertia = def.wheelInertia + def.engineInertia * ratio * ratio
        val force: Float
        if (!spinning && driveF <= grip) {
            force = driveF                      // tyres hook: the axle rolls with the road
        } else {
            spinning = true
            val slip = max(0f, driveW * tireR - v)
            val s = slipRatio(slip)
            // Just broken loose, a tyre sits at its peak; it only loses grip past the plateau
            force = peakGrip * gripShare(max(s, PEAK_SLIP)) * load
            driveW += (axleT - force * tireR) / inertia * dt
            if (s < PEAK_SLIP && driveF <= grip) spinning = false    // hooked again
        }
        integrate(force, dt)
        if (!spinning) driveW = rollW
        driveW = max(driveW, rollW)
        slipSpeed = max(0f, driveW * tireR - v)
        tireTemp = min(1.4f, tireTemp + slipSpeed * 0.004f * dt)
    }

    private fun shifting(dt: Float) {
        // Clutch in: no drive, the engine falls toward the next gear's speed
        val nextRatio = def.gears[gear + 1] * def.finalDrive
        val target = max(def.idleRpm, rollW * nextRatio / RPM_TO_RAD)
        rpm += (target - rpm) * min(1f, dt * 14f)
        driveLoad()
        integrate(0f, dt)
        driveW += (rollW - driveW) * min(1f, dt * 10f)
        slipSpeed = max(0f, driveW * tireR - v)
        shiftTimer -= dt
        if (shiftTimer <= 0f) {
            gear++
            mode = Mode.DRIVE
            spinning = false
            rpm = max(def.idleRpm, driveW * ratio / RPM_TO_RAD)
        }
    }

    private fun integrate(force: Float, dt: Float) {
        val resist = 0.5f * AIR_DENSITY * def.dragArea * v * v + rolling * mass * GRAVITY
        accel = (force - resist) / mass - brake * BRAKE_DECEL
        v = max(0f, v + accel * dt)
        x += v * dt
    }

    private fun timing() {
        if (!hasLeft && x > LEAVE_M) {
            hasLeft = true
            leaveTime = clock
        }
        if (!hasLeft) return
        for (i in SPLITS_M.indices) {
            if (splits[i] < 0f && x >= SPLITS_M[i]) {
                splits[i] = clock - leaveTime
                splitSpeed[i] = v
            }
        }
    }

    companion object {
        /** 60 ft, 330 ft, 1/8 mile, 1000 ft, 1/4 mile. */
        val SPLITS_M = floatArrayOf(18.288f, 100.584f, 201.168f, 304.8f, 402.336f)
        const val LEAVE_M = 0.06f   // rollout before the stage beam clears
        const val AIR_DENSITY = 1.2f
        const val ROLLING = 0.015f
        const val GRAVITY = 9.81f
        const val DRIVELINE_EFF = 0.80f   // also absorbs gross-vs-net power ratings
        const val BRAKE_DECEL = 7f
        const val RPM_TO_RAD = 0.10471976f   // 2 pi / 60
        const val HP_TO_W = 745.7f
        const val PEAK_SLIP = 0.12f
        const val PLATEAU_SLIP = 0.3f
        const val SPIN_FALLOFF = 0.4f
        const val SPIN_SHOCK = 2.5f
        const val BURNOUT_SURFACE = 0.4f
        const val DEFAULT_PREP = 1.25f
        const val BOG_FLOOR = 0.9f
        const val BOOST_RANGE_RPM = 1400f
        const val PRESSURE_GRIP = 0.008f
        const val PRESSURE_ROLL = 0.025f
        const val PRESSURE_RADIUS = 0.0015f
        const val LAUNCH_SAG = 0.25f
        const val FEATHER_MIN = 0.45f
        const val FEATHER_DROP = 4f
        const val FEATHER_RISE = 1.4f
        const val BURNOUT_HEAT = 0.012f
        const val REV_UP = 2600f
        const val REV_DOWN = 3200f
        const val CREEP_SPEED = 3.4f
        const val STAGE_SPEED = 0.45f
        const val LIMITER_CUT_S = 0.035f
        const val NITROUS_MIN_RPM = 2800f
        const val PERFECT_BONUS = 0.06f
        const val PERFECT_BONUS_S = 0.6f
        const val ANTI_LAG_BOOST = 0.6f
        const val TRANSFER_TAU = 0.06f
        const val WHEELIE_LIFT = 0.35f
        const val WHEELIE_BAR_LIFT = 0.18f
    }
}

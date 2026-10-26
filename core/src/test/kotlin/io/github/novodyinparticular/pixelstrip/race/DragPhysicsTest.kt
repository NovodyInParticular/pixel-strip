package io.github.novodyinparticular.pixelstrip.race

import com.badlogic.gdx.utils.JsonReader
import io.github.novodyinparticular.pixelstrip.data.CarDef
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class DragPhysicsTest {
    private fun car(id: String) = CarDef.parse(
        JsonReader().parse(File("data/cars/$id.json").readText()),
        JsonReader().parse(File("cars/$id/sprite.json").readText()),
    )

    private class Run(val et: FloatArray, val mph: FloatArray, val sixty: Float, val ratings: List<Vehicle.Rating>)

    /** Quarter-mile pass from a staged RPM, shifting [shiftOffset] away from the best points. */
    private fun pass(def: CarDef, rpm: Float, temp: Float = 0.78f, shiftOffset: Float = 0f, nitrous: Boolean = false): Run {
        val car = Vehicle(def)
        car.prime(rpm, temp)
        car.launch(0f)
        car.throttle = 1f
        val dt = 1f / 240f
        var t = 0f
        while (car.x < Vehicle.SPLITS_M[4] && t < 30f) {
            if (car.mode == Vehicle.Mode.DRIVE && car.rpm >= car.shiftPoints[car.gear] * (1f + shiftOffset)) car.shift()
            if (nitrous && car.gear >= 1 && car.mode == Vehicle.Mode.DRIVE) car.nitrous()
            car.step(dt)
            t += dt
        }
        return Run(car.splits.copyOf(), FloatArray(5) { car.splitSpeed[it] * 2.23694f }, car.splits[0], car.ratings.toList())
    }

    private fun describe(label: String, r: Run) = "%-16s 60ft %.3f  330 %.3f  1/8 %.3f @ %.1f  1/4 %.3f @ %.1f  %s".format(
        label, r.sixty, r.et[1], r.et[2], r.mph[2], r.et[4], r.mph[4], r.ratings)

    @Test
    fun vortexBehavesLikeAStockV8SportsCar() {
        val def = car("vortex")
        val sweet = DragMath.launchRpm(def, Vehicle.DEFAULT_PREP)
        println("launch sweet spot ${sweet.toInt()} rpm, shift points ${DragMath.shiftPoints(def).map { it.toInt() }}")
        val best = pass(def, sweet)
        val spin = pass(def, def.redline * 0.95f)
        val cold = pass(def, sweet, temp = 0.15f)
        val early = pass(def, sweet, shiftOffset = -0.15f)
        val nos = pass(def.copy(nitrous = CarDef.Nitrous(75f, 2, 2.5f)), sweet, nitrous = true)        // a two-shot 75 hp kit
        listOf("sweet" to best, "spin" to spin, "cold tyres" to cold, "early shifts" to early, "nitrous" to nos)
            .forEach { (l, r) -> println(describe(l, r)) }

        // Real 2002 Corvette Z06 (C5) 6-speed: mid-12s at ~114-116 mph on street tyres; a perfect sim pass a bit quicker
        assertTrue(best.et[4] in 11.6f..13.2f, "quarter ET ${best.et[4]}")
        assertTrue(best.mph[4] in 110f..122f, "trap ${best.mph[4]}")
        assertTrue(spin.sixty > best.sixty + 0.05f, "too much RPM should spin the tyres and cost time")
        assertTrue(spin.et[4] > best.et[4] + 0.05f, "a spun launch should lose the whole pass, not just the 60 ft")
        assertTrue(cold.sixty > best.sixty, "a burnout (warm tyres) should help")
        assertTrue(early.et[4] > best.et[4], "short-shifting should be slower")
        assertTrue(nos.et[4] < best.et[4] - 0.1f, "nitrous should help")
    }

    @Test
    fun aPeakyEngineBogsOffTheLine() {
        // the S2000's F20C makes its torque up top: launched low it falls out of its band (the V8 above has torque to spare)
        val def = car("tenshi")
        val sweet = DragMath.launchRpm(def, Vehicle.DEFAULT_PREP)
        val best = pass(def, sweet)
        val bog = pass(def, sweet * 0.6f)
        listOf("sweet" to best, "bog" to bog).forEach { (l, r) -> println(describe(l, r)) }
        assertTrue(bog.sixty > best.sixty + 0.05f, "bogging should cost 60 ft time")
        assertTrue(bog.et[4] > best.et[4] + 0.05f, "a bogged launch should lose the whole pass")
    }

    @Test
    fun burnoutHeatsTheTyres() {
        val car = Vehicle(car("vortex"))
        car.startBurnout()
        car.throttle = 1f
        var t = 0f
        while (t < 3f) {
            car.step(1f / 240f)
            t += 1f / 240f
        }
        println("after 3 s burnout: temp %.2f slip %.1f m/s rpm %.0f x %.2f".format(car.tireTemp, car.slipSpeed, car.rpm, car.x))
        assertTrue(car.tireTemp > 0.6f, "burnout should warm the tyres")
        assertTrue(car.x == 0f, "the car must stay in the box")
    }

    @Test
    fun leavingBeforeGreenIsARedLight() {
        val def = car("vortex")
        val car = Vehicle(def)
        car.prime(DragMath.launchRpm(def, Vehicle.DEFAULT_PREP), 0.78f)
        car.launch(-0.25f)            // launched 0.25 s before green
        car.throttle = 1f
        var t = 0f
        while (!car.hasLeft && t < 2f) {
            car.step(1f / 240f)
            t += 1f / 240f
        }
        println("left the beam at race clock %.3f".format(car.leaveTime))
        assertTrue(car.hasLeft && car.leaveTime < 0f, "should leave before green")

        val late = Vehicle(def)
        late.prime(DragMath.launchRpm(def, Vehicle.DEFAULT_PREP), 0.78f)
        late.launch(0.2f)
        late.throttle = 1f
        repeat(480) { late.step(1f / 240f) }
        assertTrue(late.leaveTime in 0.2f..0.5f, "reaction time includes only a short rollout: ${late.leaveTime}")
    }
}

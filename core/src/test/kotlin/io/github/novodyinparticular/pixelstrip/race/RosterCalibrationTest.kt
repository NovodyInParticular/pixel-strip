package io.github.novodyinparticular.pixelstrip.race

import com.badlogic.gdx.utils.JsonReader
import io.github.novodyinparticular.pixelstrip.data.CarDef
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every car against its real inspiration, from the table in car_references.md: the same
 * drivetrain, and a perfect driver (sweet-spot launch, best shift points, warm tyres) landing near
 * the stock quarter mile period road tests got.
 */
class RosterCalibrationTest {
    class Ref(val id: String, val drivetrain: String, val et: Float)

    /** The rows of the reference table: id, real drivetrain, stock quarter mile. */
    private val reference: List<Ref> = File("../car_references.md").readLines()
        .filter { it.startsWith("| ") && "`" in it }
        .map { line ->
            val cols = line.trim('|').split("|").map { it.trim() }
            Ref(cols[1].trim('`'), cols[3], cols[5].toFloat())
        }

    private fun load(id: String): CarDef {
        val stats = JsonReader().parse(File("data/cars/$id.json").readText())
        val sprite = JsonReader().parse(File("cars/$id/sprite.json").readText())
        return CarDef.parse(stats, sprite)
    }

    @Test
    fun theTableCoversTheWholeRoster() {
        val roster = JsonReader().parse(File("data/roster.json").readText()).asStringArray().toList()
        assertEquals(roster.sorted(), reference.map { it.id }.sorted(), "car_references.md and roster.json list different cars")
    }

    @Test
    fun everyCarHasItsRealDrivetrain() {
        val wrong = reference.filter { load(it.id).drivetrain != it.drivetrain }.map { "${it.id}: ${load(it.id).drivetrain}, real car ${it.drivetrain}" }
        assertTrue(wrong.isEmpty(), wrong.joinToString("\n"))
    }

    @Test
    fun everyCarIsCloseToItsRealQuarterMile() {
        val failures = ArrayList<String>()
        for (ref in reference) {
            val def = load(ref.id)
            val car = Vehicle(def)
            car.prime(DragMath.launchRpm(def, Vehicle.DEFAULT_PREP), 0.78f)
            car.launch(0f)
            car.throttle = 1f
            var t = 0f
            while (car.x < Vehicle.SPLITS_M[4] && t < 40f) {
                if (car.mode == Vehicle.Mode.DRIVE && car.gear < def.gears.size - 1 && car.rpm >= car.shiftPoints[car.gear]) car.shift()
                car.step(1f / 240f)
                t += 1f / 240f
            }
            val et = car.splits[4]
            val mph = car.splitSpeed[4] * 2.23694f
            val line = "%-13s %s 60ft %.2f  1/8 %.2f  1/4 %.2f @ %5.1f  (real ~%.1f, %+.2f) launch %4.0f".format(
                ref.id, ref.drivetrain, car.splits[0], car.splits[2], et, mph, ref.et, et - ref.et, DragMath.launchRpm(def, Vehicle.DEFAULT_PREP))
            println(line)
            if (et < ref.et - TOLERANCE || et > ref.et + TOLERANCE) failures.add(line)
        }
        assertTrue(failures.isEmpty(), "out of range:\n" + failures.joinToString("\n"))
    }

    companion object {
        /** Road tests of one car spread 0.3-0.5 s between magazines: past this a car is way off, not just unlucky. */
        const val TOLERANCE = 0.8f
    }
}

package io.github.novodyinparticular.pixelstrip.race

import com.badlogic.gdx.utils.JsonReader
import io.github.novodyinparticular.pixelstrip.data.CarDef
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A perfect driver (sweet-spot launch, best shift points, warm tyres) should land each car near
 * its real stock quarter mile. Reference ETs are typical period magazine results.
 */
class RosterCalibrationTest {
    private val reference = mapOf(
        "vandal" to 13.9f, "ranger" to 15.2f, "corsair" to 14.5f, "mammoth" to 13.8f, "piranha" to 13.9f, "warrior" to 14.8f,
        "phantom" to 13.4f, "ranger50" to 14.9f, "corsair_iroc" to 15.0f, "kestrel" to 16.5f, "spiral_turbo" to 15.0f,
        "kessler" to 14.5f, "zephyr" to 15.2f, "tsunami" to 13.3f, "kaminari_mk1" to 13.3f, "kaminari_mk3" to 13.0f,
        "spiral_r" to 13.8f, "kirin" to 13.6f, "typhoon" to 13.8f, "squall" to 14.5f, "kite" to 16.0f, "arrow" to 15.1f,
        "vortex" to 13.0f, "asp" to 13.0f,
    )

    private fun load(id: String): CarDef {
        val stats = JsonReader().parse(File("data/cars/$id.json").readText())
        val sprite = JsonReader().parse(File("cars/$id/sprite.json").readText())
        return CarDef.parse(stats, sprite)
    }

    @Test
    fun everyCarIsCloseToItsRealQuarterMile() {
        val failures = ArrayList<String>()
        for ((id, ref) in reference) {
            val def = load(id)
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
            val line = "%-13s 60ft %.2f  1/8 %.2f  1/4 %.2f @ %5.1f  (real ~%.1f) launch %4.0f".format(
                id, car.splits[0], car.splits[2], et, mph, ref, DragMath.launchRpm(def, Vehicle.DEFAULT_PREP))
            println(line)
            if (et < ref - 1.0f || et > ref + 1.0f) failures.add(line)
        }
        assertTrue(failures.isEmpty(), "out of range:\n" + failures.joinToString("\n"))
    }
}

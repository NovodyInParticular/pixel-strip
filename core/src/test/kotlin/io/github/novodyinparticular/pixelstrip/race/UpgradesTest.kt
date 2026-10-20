package io.github.novodyinparticular.pixelstrip.race

import com.badlogic.gdx.utils.JsonReader
import io.github.novodyinparticular.pixelstrip.data.CarDef
import io.github.novodyinparticular.pixelstrip.data.PartCatalog
import io.github.novodyinparticular.pixelstrip.data.Tune
import io.github.novodyinparticular.pixelstrip.data.Upgrades
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UpgradesTest {
    private val catalog = PartCatalog(JsonReader().parse(File("data/parts.json").readText()))

    private fun car(id: String) = CarDef.parse(
        JsonReader().parse(File("data/cars/$id.json").readText()),
        JsonReader().parse(File("cars/$id/sprite.json").readText()),
    )

    private fun build(id: String, parts: Map<String, Int>, tune: Tune? = null) = Upgrades.apply(car(id), catalog, parts, emptyMap(), tune)

    private fun pass(def: CarDef) = DragMath.perfectPass(def)

    @Test
    fun everyPartHelps() {
        val stock = pass(car("vandal")).quarter
        for (slot in catalog.slots) {
            if (slot.id == "nitrous" || slot.id == "final") continue      // nitrous needs a spray; final drive only widens the tune range
            val et = pass(build("vandal", mapOf(slot.id to 2))).quarter
            println("%-13s stage 2: %.3f (stock %.3f)".format(slot.id, et, stock))
            assertTrue(et < stock + 0.001f, "${slot.id} should not make the car slower")
        }
    }

    @Test
    fun fullRaceBuildIsAMonster() {
        val all = catalog.slots.filter { it.id != "supercharger" }.associate { it.id to 4 }
        val stock = pass(car("vandal"))
        val built = pass(build("vandal", all))
        println("vandal stock %.2f @ %.1f -> race build %.2f @ %.1f".format(stock.quarter, stock.trapMph, built.quarter, built.trapMph))
        assertTrue(built.quarter < stock.quarter - 2.5f, "a full race build should be seconds quicker")
        assertTrue(built.trapMph < 160f, "but still a believable trap speed for its ET")
    }

    @Test
    fun turboLagsWhereTheBlowerDoesNot() {
        // On race slicks, so the launch isn't traction-limited whatever the power adder
        val turbo = pass(build("vandal", mapOf("turbo" to 4, "tires" to 4)))
        val blower = pass(build("vandal", mapOf("supercharger" to 4, "tires" to 4)))
        println("turbo 60ft %.3f, blower 60ft %.3f".format(turbo.sixty, blower.sixty))
        assertTrue(turbo.sixty > blower.sixty, "the turbo should launch softer (lag)")
    }

    @Test
    fun gearboxAndTuneLimits() {
        val sixSpeed = build("vandal", mapOf("gearbox" to 3))
        assertEquals(6, sixSpeed.gears.size)
        val wild = Tune(gears = floatArrayOf(9f, 9f, 9f, 9f), finalDrive = 9f)
        val clamped = build("vandal", emptyMap(), wild)
        assertTrue(clamped.gears[0] <= 2.66f * 1.051f && clamped.finalDrive <= 3.55f * 1.051f, "stock parts only allow +-5%")
    }

    @Test
    fun lowTirePressureHelpsTheLaunch() {
        val parts = mapOf("tires" to 2)
        val high = pass(build("vandal", parts, Tune(tirePressure = 36f))).sixty
        val low = pass(build("vandal", parts, Tune(tirePressure = 18f))).sixty
        println("drag radials 60ft: 36 psi %.3f, 18 psi %.3f".format(high, low))
        assertTrue(low < high)
    }
}

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

    private fun peakHp(def: CarDef): Float {
        var best = 0f
        var r = def.idleRpm
        while (r <= def.redline) {
            best = maxOf(best, DragMath.crankTorque(def, r) * r / 7127f)
            r += 50f
        }
        return best
    }

    private fun limited(def: CarDef): Set<DragMath.Limit> {
        val why = arrayOf(DragMath.Limit.NONE)
        val out = HashSet<DragMath.Limit>()
        var r = def.idleRpm
        while (r <= def.redline) {
            DragMath.crankTorque(def, r, why)
            out.add(why[0])
            r += 100f
        }
        return out - DragMath.Limit.NONE
    }

    /** Big boost wants all of these. */
    private val supported = mapOf("internals" to 2, "fueltype" to 2, "fuel" to 3, "intercooler" to 2, "boostctl" to 1)

    @Test
    fun boltOnsAddPeakPower() {
        val stock = peakHp(build("vandal", emptyMap()))
        for (slot in listOf("intake", "throttle", "manifold", "heads", "cams", "headers", "exhaust", "swap", "ecu")) {
            val hp = peakHp(build("vandal", mapOf(slot to 1)))
            println("%-9s option 1: %.0f hp (stock %.0f)".format(slot, hp, stock))
            assertTrue(hp > stock, "$slot should add peak power")
        }
    }

    @Test
    fun raceCamTradesLowEndForTopEnd() {
        val stock = build("vandal", emptyMap())
        val cam = build("vandal", mapOf("cams" to 3))
        assertTrue(DragMath.crankTorque(cam, 1500f) < DragMath.crankTorque(stock, 1500f), "less low end")
        assertTrue(peakHp(cam) > peakHp(stock) * 1.08f, "more top end")
        assertTrue(cam.redline > stock.redline && cam.idleRpm > stock.idleRpm, "revs higher, idles lumpy")
    }

    @Test
    fun boostNeedsTheSupportingParts() {
        val tune = Tune(boostPsi = 22f)
        val bare = build("vandal", mapOf("turbo" to 2, "boostctl" to 1), tune)
        val built = build("vandal", supported + mapOf("turbo" to 2), tune)
        println("vandal medium turbo 22 psi: stock bottom end %.0f hp %s, built %.0f hp %s".format(peakHp(bare), limited(bare), peakHp(built), limited(built)))
        assertTrue(DragMath.Limit.KNOCK in limited(bare), "stock internals on pump gas knock at 22 psi")
        assertTrue(limited(built).isEmpty(), "forged, E85, big fuel system: clean")
        assertTrue(peakHp(built) > peakHp(bare) * 1.3f)
        val noController = build("vandal", supported + mapOf("turbo" to 2, "boostctl" to 0), tune)
        assertEquals(Upgrades.SPRING_PSI, noController.boostPsi, "the wastegate spring holds it at 8 psi")
    }

    @Test
    fun theFuelSystemCapsPower() {
        val tune = Tune(boostPsi = 30f)
        val starved = build("vandal", supported + mapOf("turbo" to 4, "fuel" to 0), tune)
        val fed = build("vandal", supported + mapOf("turbo" to 4), tune)
        println("large turbo 30 psi: stock fuel %.0f hp %s, race fuel %.0f hp".format(peakHp(starved), limited(starved), peakHp(fed)))
        assertTrue(DragMath.Limit.FUEL in limited(starved))
        assertTrue(peakHp(fed) > peakHp(starved) * 1.4f)
    }

    @Test
    fun timingNeedsAnEcuAndCostsKnockMargin() {
        val noEcu = build("vandal", emptyMap(), Tune(timing = 4f))
        assertEquals(0f, noEcu.timingDeg, "the stock ECU is locked")
        val flashed = build("vandal", mapOf("ecu" to 2), Tune(timing = 4f))
        val flat = build("vandal", mapOf("ecu" to 2), Tune(timing = 0f))
        assertTrue(peakHp(flashed) > peakHp(flat))
        assertTrue(flashed.knockLimit < flat.knockLimit)
    }

    @Test
    fun fullRaceBuildIsAMonster() {
        val all = catalog.slots.filter { it.id != "turbo" && it.id != "final" }.associate { it.id to it.options.size - 1 }
        val stock = pass(car("vandal"))
        val built = pass(build("vandal", all, Tune(nitrousGear = 2)))
        println("vandal stock %.2f @ %.1f -> everything %.2f @ %.1f (%.0f hp)".format(stock.quarter, stock.trapMph, built.quarter, built.trapMph, peakHp(build("vandal", all))))
        assertTrue(built.quarter < stock.quarter - 3f, "a full build should be seconds quicker")
        assertTrue(built.trapMph < 185f, "but still a believable trap speed")
    }

    @Test
    fun turboLagsWhereTheBlowerDoesNot() {
        // on slicks, so the launch isn't traction-limited whatever the power adder
        val grip = mapOf("tires" to 4, "tubs" to 1)
        val turbo = pass(build("vandal", supported + grip + mapOf("turbo" to 4), Tune(boostPsi = 18f)))
        val blower = pass(build("vandal", supported + grip + mapOf("supercharger" to 2)))
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
        assertTrue(build("vandal", mapOf("final" to 1)).finalDrive > 3.55f * 1.07f, "a shorter ring and pinion")
    }

    @Test
    fun lowTirePressureHelpsTheLaunch() {
        val parts = mapOf("tires" to 2)
        val high = pass(build("vandal", parts, Tune(tirePressure = 36f))).sixty
        val low = pass(build("vandal", parts, Tune(tirePressure = 18f))).sixty
        println("drag radials 60ft: 36 psi %.3f, 18 psi %.3f".format(high, low))
        assertTrue(low < high)
    }

    @Test
    fun weightComesOff() {
        val light = build("vandal", mapOf("interior" to 2, "panels" to 3, "glass" to 2, "battery" to 2))
        assertTrue(light.weightKg < car("vandal").weightKg - 150f)
        assertTrue(light.rearWeight > car("vandal").rearWeight)
    }
}

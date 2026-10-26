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
        val stock = peakHp(build("mammoth", emptyMap()))
        for (slot in listOf("intake", "throttle", "manifold", "heads", "cams", "headers", "exhaust", "swap", "ecu")) {
            val hp = peakHp(build("mammoth", mapOf(slot to 1)))
            println("%-9s option 1: %.0f hp (stock %.0f)".format(slot, hp, stock))
            assertTrue(hp > stock, "$slot should add peak power")
        }
    }

    @Test
    fun raceCamTradesLowEndForTopEnd() {
        val stock = build("mammoth", emptyMap())
        val cam = build("mammoth", mapOf("cams" to 3))
        assertTrue(DragMath.crankTorque(cam, 1500f) < DragMath.crankTorque(stock, 1500f), "less low end")
        assertTrue(peakHp(cam) > peakHp(stock) * 1.08f, "more top end")
        assertTrue(cam.redline > stock.redline && cam.idleRpm > stock.idleRpm, "revs higher, idles lumpy")
    }

    @Test
    fun boostNeedsTheSupportingParts() {
        val tune = Tune(boostPsi = 22f)
        val bare = build("mammoth", mapOf("turbo" to 2, "boostctl" to 1), tune)
        val built = build("mammoth", supported + mapOf("turbo" to 2), tune)
        println("mammoth medium turbo 22 psi: stock bottom end %.0f hp %s, built %.0f hp %s".format(peakHp(bare), limited(bare), peakHp(built), limited(built)))
        assertTrue(DragMath.Limit.KNOCK in limited(bare), "stock internals on pump gas knock at 22 psi")
        assertTrue(limited(built).isEmpty(), "forged, E85, big fuel system: clean")
        assertTrue(peakHp(built) > peakHp(bare) * 1.3f)
        val noController = build("mammoth", supported + mapOf("turbo" to 2, "boostctl" to 0), tune)
        assertEquals(Upgrades.SPRING_PSI, noController.boostPsi, "the wastegate spring holds it at 8 psi")
    }

    @Test
    fun theFuelSystemCapsPower() {
        val tune = Tune(boostPsi = 30f)
        val starved = build("mammoth", supported + mapOf("turbo" to 4, "fuel" to 0), tune)
        val fed = build("mammoth", supported + mapOf("turbo" to 4), tune)
        println("large turbo 30 psi: stock fuel %.0f hp %s, race fuel %.0f hp".format(peakHp(starved), limited(starved), peakHp(fed)))
        assertTrue(DragMath.Limit.FUEL in limited(starved))
        assertTrue(peakHp(fed) > peakHp(starved) * 1.4f)
    }

    @Test
    fun timingNeedsAnEcuAndCostsKnockMargin() {
        val noEcu = build("mammoth", emptyMap(), Tune(timing = 4f))
        assertEquals(0f, noEcu.timingDeg, "the stock ECU is locked")
        val flashed = build("mammoth", mapOf("ecu" to 2), Tune(timing = 4f))
        val flat = build("mammoth", mapOf("ecu" to 2), Tune(timing = 0f))
        assertTrue(peakHp(flashed) > peakHp(flat))
        assertTrue(flashed.knockLimit < flat.knockLimit)
    }

    @Test
    fun fullRaceBuildIsAMonster() {
        val all = catalog.slots.filter { it.id != "turbo" && it.id != "final" }.associate { it.id to it.options.size - 1 }
        val stock = pass(car("mammoth"))
        val built = pass(build("mammoth", all, Tune(nitrousGear = 2)))
        println("mammoth stock %.2f @ %.1f -> everything %.2f @ %.1f (%.0f hp)".format(stock.quarter, stock.trapMph, built.quarter, built.trapMph, peakHp(build("mammoth", all))))
        assertTrue(built.quarter < stock.quarter - 3f, "a full build should be seconds quicker")
        assertTrue(built.trapMph < 185f, "but still a believable trap speed")
    }

    @Test
    fun turboLagsWhereTheBlowerDoesNot() {
        // on slicks, so the launch isn't traction-limited whatever the power adder
        val grip = mapOf("tires" to 4, "tubs" to 1)
        val turbo = pass(build("mammoth", supported + grip + mapOf("turbo" to 4), Tune(boostPsi = 18f)))
        val blower = pass(build("mammoth", supported + grip + mapOf("supercharger" to 2)))
        println("turbo 60ft %.3f, blower 60ft %.3f".format(turbo.sixty, blower.sixty))
        assertTrue(turbo.sixty > blower.sixty, "the turbo should launch softer (lag)")
    }

    /** Seconds from the launch until the turbo makes 90% of its boost, flat out with perfect shifts. */
    private fun spoolTime(def: CarDef): Float {
        val car = Vehicle(def)
        car.prime(DragMath.launchRpm(def, Vehicle.DEFAULT_PREP), 0.78f)
        car.launch(0f, rate = false)
        car.throttle = 1f
        var t = 0f
        while (car.boost < 0.9f && t < 20f) {
            if (car.mode == Vehicle.Mode.DRIVE && car.gear < def.gears.size - 1 && car.rpm >= car.shiftPoints[car.gear]) car.shift()
            car.step(1f / 240f)
            t += 1f / 240f
        }
        return t
    }

    @Test
    fun biggerTurbosLagMore() {
        val grip = supported + mapOf("tires" to 4, "tubs" to 1, "bov" to 1)
        val times = (1..5).associateWith { spoolTime(build("mammoth", grip + mapOf("turbo" to it), Tune(boostPsi = 15f))) }
        val names = catalog.slot("turbo").options
        for ((o, t) in times) println("%-14s full boost after %.2f s".format(names[o].name, t))
        assertTrue(times[1]!! < times[2]!! && times[2]!! < times[4]!! && times[4]!! < times[5]!!, "small < medium < large < 88mm")
        assertTrue(times[5]!! > times[1]!! + 1f, "the big one makes you wait")
    }

    @Test
    fun gearboxAndTuneLimits() {
        val sixSpeed = build("mammoth", mapOf("gearbox" to 3))
        assertEquals(6, sixSpeed.gears.size)
        val wild = Tune(gears = floatArrayOf(9f, 9f, 9f, 9f), finalDrive = 9f)
        val clamped = build("mammoth", emptyMap(), wild)
        assertTrue(clamped.gears[0] <= 2.2f * 1.051f && clamped.finalDrive <= 3.73f * 1.051f, "stock parts only allow +-5%")
        assertTrue(build("mammoth", mapOf("final" to 1)).finalDrive > 3.73f * 1.07f, "a shorter ring and pinion")
    }

    @Test
    fun lowTirePressureHelpsTheLaunch() {
        val parts = mapOf("tires" to 2)
        val high = pass(build("mammoth", parts, Tune(tirePressure = 36f))).sixty
        val low = pass(build("mammoth", parts, Tune(tirePressure = 18f))).sixty
        println("drag radials 60ft: 36 psi %.3f, 18 psi %.3f".format(high, low))
        assertTrue(low < high)
    }

    @Test
    fun weightComesOff() {
        val light = build("mammoth", mapOf("interior" to 2, "panels" to 3, "glass" to 2, "battery" to 2))
        assertTrue(light.weightKg < car("mammoth").weightKg - 150f)
        assertTrue(light.rearWeight > car("mammoth").rearWeight)
    }

    private fun opt(slot: String, name: String) = catalog.slot(slot).options.indexOfFirst { it.name == name }.also { assertTrue(it > 0, "$slot $name") }

    @Test
    fun aSwapPutsInTheWholeEngine() {
        val ae86 = car("kestrel")
        val swapped = build("kestrel", mapOf("swap" to opt("swap", "2JZ SWAP")))
        assertEquals("i6", swapped.engineLayout, "it sounds like a straight six now")
        assertTrue(swapped.stockTurbo && swapped.stockBoostGain > 0f, "the 2JZ brings its turbos")
        assertTrue(swapped.weightKg > ae86.weightKg + 50f, "an iron six weighs more than the 4A-GE: ${ae86.weightKg} -> ${swapped.weightKg}")
        assertTrue(peakHp(swapped) > peakHp(ae86) * 2f, "${peakHp(ae86)} -> ${peakHp(swapped)} hp")
        val rx7 = build("spiral_sa", mapOf("swap" to opt("swap", "26B 4-ROTOR SWAP")))
        assertEquals("rotary" to 4, rx7.engineLayout to rx7.cylinders)
        assertTrue(rx7.redline >= 9500f)
    }

    @Test
    fun pistonPartsDoNothingOnARotaryAndViceVersa() {
        val stock = peakHp(car("spiral_sa"))
        assertEquals(stock, peakHp(build("spiral_sa", mapOf("cams" to 3, "valvesprings" to 1, "heads" to 2))), "cams on a Wankel")
        assertTrue(peakHp(build("spiral_sa", mapOf("porting" to 2))) > stock * 1.05f, "a bridge port does work")
        // once an LS goes in, the cams count and the porting doesn't
        val ls = mapOf("swap" to opt("swap", "LS V8 SWAP"))
        assertTrue(peakHp(build("spiral_sa", ls + mapOf("cams" to 2))) > peakHp(build("spiral_sa", ls)))
        assertEquals(peakHp(build("spiral_sa", ls)), peakHp(build("spiral_sa", ls + mapOf("porting" to 2))))
    }

    @Test
    fun portingTradesLowEndForTopEnd() {
        val stock = car("spiral_turbo")
        val peri = build("spiral_turbo", mapOf("porting" to 3, "apexseals" to 1, "oilinject" to 1))
        val low = { d: CarDef -> DragMath.crankTorque(d, 2500f) }
        assertTrue(low(peri) < low(stock), "less low end")
        assertTrue(peri.redline > stock.redline + 1500f && peri.peakHpRpm > stock.peakHpRpm + 1000f, "revs higher, peaks later")
        assertTrue(peakHp(peri) > peakHp(stock) * 1.15f)
    }

    @Test
    fun aDownpipeNeedsATurbo() {
        val pipe = mapOf("downpipe" to 2)
        assertEquals(peakHp(car("kokoro")), peakHp(build("kokoro", pipe)), "nothing to do on an NA Miata")
        assertTrue(build("halberd_6", pipe).boostLag < car("halberd_6").boostLag, "spools the Evo sooner")
    }

    @Test
    fun launchControlAndAntiLagArePartsNow() {
        val ecu = mapOf("ecu" to 3)
        assertTrue(!Upgrades.limits(car("halberd_6"), catalog, ecu).launchControl, "a standalone alone has no 2-step")
        assertTrue(Upgrades.limits(car("halberd_6"), catalog, ecu + mapOf("launch" to 1)).launchControl)
        assertTrue(Upgrades.limits(car("halberd_6"), catalog, ecu + mapOf("launch" to 2)).antiLag, "factory turbo cars get anti-lag")
        assertTrue(!Upgrades.limits(car("kokoro"), catalog, ecu + mapOf("launch" to 2)).antiLag, "no turbo, no anti-lag")
    }
}

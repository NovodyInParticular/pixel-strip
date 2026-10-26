package io.github.novodyinparticular.pixelstrip.race

import com.badlogic.gdx.utils.JsonReader
import io.github.novodyinparticular.pixelstrip.data.CarDef
import io.github.novodyinparticular.pixelstrip.data.Economy
import io.github.novodyinparticular.pixelstrip.data.PartCatalog
import io.github.novodyinparticular.pixelstrip.data.Tune
import io.github.novodyinparticular.pixelstrip.data.Upgrades
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tuned potential: every car's best quarter mile with everything it can take, trying an NA build,
 * turbo builds across boost settings and blower builds, each with nitrous, on whichever suspension
 * and ballast suit its drivetrain. Joe's EF is an honest front-driver (no AWD special): it must not
 * be the quickest street car once built.
 */
class PotentialTest {
    private val catalog = PartCatalog(JsonReader().parse(File("data/parts.json").readText()))
    private val economy = Economy.parse(JsonReader().parse(File("data/economy.json").readText()))

    private fun car(id: String) = CarDef.parse(JsonReader().parse(File("data/cars/$id.json").readText()), JsonReader().parse(File("cars/$id/sprite.json").readText()))

    fun best(def: CarDef): Pair<Float, String> {
        val base = HashMap<String, Int>()
        for (s in catalog.slots) {
            if (s.id in setOf("turbo", "supercharger", "final", "nitrous")) continue
            val top = (s.options.size - 1 downTo 0).first { catalog.blocker(s, it, base + mapOf("tubs" to 1, "fenders" to 2), def.drivetrain, def.id) == null }
            base[s.id] = top
        }
        if (def.drivetrain != "RWD") base.remove("tubs").also { base["fenders"] = 2 }
        // weight transfer helps a rear-driver and hurts a front-driver: pick the setup on a strong reference build
        val chassis = SETUP_SLOTS.map { id -> catalog.slots.first { it.id == id } }
        var setup = emptyMap<String, Int>()
        var setupEt = Float.MAX_VALUE
        fun combos(i: Int, acc: Map<String, Int>) {
            if (i == chassis.size) {
                val et = DragMath.perfectPass(Upgrades.apply(def, catalog, base + acc + mapOf("turbo" to 3, "nitrous" to 2), emptyMap(),
                    Tune(boostPsi = 25f, nitrousGear = 2))).quarter
                if (et < setupEt) { setupEt = et; setup = acc }
                return
            }
            for (o in chassis[i].options.indices) combos(i + 1, acc + (chassis[i].id to o))
        }
        combos(0, emptyMap())
        base.putAll(setup)
        var best = Float.MAX_VALUE
        var what = ""
        fun tryBuild(parts: Map<String, Int>, tune: Tune, label: String) {
            for (shot in listOf(0, 1, 2, 3, 4)) {
                val p = parts + (if (shot > 0) mapOf("nitrous" to shot) else emptyMap())
                for (fd in listOf(0, 1, 2, 3)) {
                    val et = DragMath.perfectPass(Upgrades.apply(def, catalog, p + mapOf("final" to fd), emptyMap(), tune.copy(nitrousGear = if (shot > 0) 2 else 0))).quarter
                    if (et < best) { best = et; what = "$label nos$shot final$fd" }
                }
            }
        }
        tryBuild(base, Tune(), "NA")
        for (t in 1..5) for (psi in listOf(10f, 15f, 20f, 25f, 30f, 40f)) tryBuild(base + mapOf("turbo" to t), Tune(boostPsi = psi), "turbo$t@$psi")
        for (sc in 1..4) tryBuild(base + mapOf("supercharger" to sc), Tune(), "sc$sc")
        return best to "$what ${setup.entries.joinToString(" ") { "${it.key}${it.value}" }}"
    }

    @Test
    fun joesCivicIsNotTheQuickestStreetBuild() {
        val ids = JsonReader().parse(File("data/roster.json").readText()).asStringArray().toList()
        val byClass = ids.map { car(it) }.groupBy { economy.classFor(DragMath.perfectPass(it).quarter) }
        val street = byClass.getValue("STREET")
        val results = street.associate { it.id to best(it) }
        for ((id, r) in results.entries.sortedBy { it.value.first }) println("STREET %-13s stock %.2f  built %.2f  (%s)".format(id, DragMath.perfectPass(car(id)).quarter, r.first, r.second))
        val order = results.entries.sortedBy { it.value.first }.map { it.key }
        val rank = order.indexOf("kite_ef")
        val gap = results.getValue("kite_ef").first - results.getValue(order.first()).first
        println("Joe's EF built: #${rank + 1} of ${order.size} in STREET, %.2f s off the quickest".format(gap))
        assertTrue(rank > 0, "Joe's EF shouldn't be the quickest street car once built: an equal build has to be able to beat it")
    }

    companion object {
        /** Slots whose best option depends on which wheels drive. */
        val SETUP_SLOTS = listOf("springs", "shocks", "battery")
    }
}

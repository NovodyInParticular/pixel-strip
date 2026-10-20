package io.github.novodyinparticular.pixelstrip.race

import com.badlogic.gdx.utils.JsonReader
import io.github.novodyinparticular.pixelstrip.data.CarDef
import io.github.novodyinparticular.pixelstrip.data.Economy
import io.github.novodyinparticular.pixelstrip.data.PartCatalog
import io.github.novodyinparticular.pixelstrip.data.Tune
import io.github.novodyinparticular.pixelstrip.data.Upgrades
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tuned potential: every car's best quarter mile with everything it can take, trying an NA build,
 * turbo builds across boost settings and blower builds, each with nitrous. Joe's EF Civic must be
 * the quickest of its stock class when built right.
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
        return best to what
    }

    @Test
    fun joesCivicIsTheQuickestStreetCarBuilt() {
        val ids = JsonReader().parse(File("data/roster.json").readText()).asStringArray().toList()
        val byClass = ids.map { car(it) }.groupBy { economy.classFor(DragMath.perfectPass(it).quarter) }
        val street = byClass.getValue("STREET")
        val results = street.associate { it.id to best(it) }
        for ((id, r) in results.entries.sortedBy { it.value.first }) println("STREET %-13s stock %.2f  built %.2f  (%s)".format(id, DragMath.perfectPass(car(id)).quarter, r.first, r.second))
        assertEquals("kite_ef", results.minByOrNull { it.value.first }!!.key, "Joe's Civic should have the most potential in its class")
    }
}

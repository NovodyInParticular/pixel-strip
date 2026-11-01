package io.github.novodyinparticular.pixelstrip.race

import com.badlogic.gdx.utils.JsonReader
import io.github.novodyinparticular.pixelstrip.data.CarDef
import io.github.novodyinparticular.pixelstrip.data.PartCatalog
import io.github.novodyinparticular.pixelstrip.data.Upgrades
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Each tier's rivals should get quicker toward the boss, and each boss quicker than the last.
 * Races run over different distances, so difficulty is compared on the quarter mile.
 */
class CareerBalanceTest {
    private val catalog = PartCatalog(JsonReader().parse(File("data/parts.json").readText()))

    private fun car(id: String) = CarDef.parse(JsonReader().parse(File("data/cars/$id.json").readText()), JsonReader().parse(File("cars/$id/sprite.json").readText()))

    @Test
    fun ladderGetsHarder() {
        val career = JsonReader().parse(File("data/career.json").readText())
        var lastBoss = Float.MAX_VALUE
        val problems = ArrayList<String>()
        for (tier in career.get("tiers")) {
            var prev = Float.MAX_VALUE
            for (r in tier.get("rivals")) {
                val parts = HashMap<String, Int>()
                r.get("parts").forEach { parts[it.name] = it.asInt() }
                val def = Upgrades.apply(car(r.getString("car")), catalog, parts, emptyMap(), null)
                val dist = Distance.of(r.getString("distance", tier.getString("distance")))
                val pass = DragMath.perfectPass(def)
                println("%-7s %-10s %-13s %s %.3f (1/4 %.2f @ %.0f)".format(tier.getString("id"), r.getString("id"), r.getString("car"),
                    dist.short, DragMath.perfectEt(def, dist), pass.quarter, pass.trapMph))
                if (pass.quarter >= prev + 0.35f) problems.add("${r.getString("id")} should not be much slower than the rival before")
                prev = pass.quarter
                if (r.getBoolean("boss", false)) {
                    if (pass.quarter >= lastBoss) problems.add("${r.getString("id")}: each boss should be quicker than the last")
                    lastBoss = pass.quarter
                }
            }
        }
        for (s in listOf("kite", "kite_sol", "spiral_turbo")) println("starter %-9s 1/8 %.3f".format(s, DragMath.perfectPass(car(s)).eighth))
        assertTrue(problems.isEmpty(), problems.joinToString("; "))
    }
}

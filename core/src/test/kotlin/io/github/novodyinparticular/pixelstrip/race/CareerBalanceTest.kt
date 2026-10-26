package io.github.novodyinparticular.pixelstrip.race

import com.badlogic.gdx.utils.JsonReader
import io.github.novodyinparticular.pixelstrip.data.CarDef
import io.github.novodyinparticular.pixelstrip.data.PartCatalog
import io.github.novodyinparticular.pixelstrip.data.Upgrades
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Each tier's rivals should get quicker toward the boss, and each tier should be quicker than the last. */
class CareerBalanceTest {
    private val catalog = PartCatalog(JsonReader().parse(File("data/parts.json").readText()))

    private fun car(id: String) = CarDef.parse(JsonReader().parse(File("data/cars/$id.json").readText()), JsonReader().parse(File("cars/$id/sprite.json").readText()))

    @Test
    fun ladderGetsHarder() {
        val career = JsonReader().parse(File("data/career.json").readText())
        var lastBoss = Float.MAX_VALUE
        val problems = ArrayList<String>()
        for (tier in career.get("tiers")) {
            val eighth = tier.getString("distance") == "eighth"
            var prev = Float.MAX_VALUE
            for (r in tier.get("rivals")) {
                val parts = HashMap<String, Int>()
                r.get("parts").forEach { parts[it.name] = it.asInt() }
                val def = Upgrades.apply(car(r.getString("car")), catalog, parts, emptyMap(), null)
                val pass = DragMath.perfectPass(def)
                val et = if (eighth) pass.eighth else pass.quarter
                println("%-7s %-10s %-13s %s %.3f (1/4 %.2f @ %.0f)".format(tier.getString("id"), r.getString("id"), r.getString("car"),
                    if (eighth) "1/8" else "1/4", et, pass.quarter, pass.trapMph))
                if (et >= prev + 0.25f) problems.add("${r.getString("id")} should not be much slower than the rival before")
                prev = et
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

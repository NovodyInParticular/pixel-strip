package io.github.novodyinparticular.pixelstrip.race

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.utils.JsonReader
import io.github.novodyinparticular.pixelstrip.data.CarDef
import io.github.novodyinparticular.pixelstrip.data.PartCatalog
import io.github.novodyinparticular.pixelstrip.data.Upgrades
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Simulated career races against two player models, per difficulty: each step up must be
 * harder than the last, and HARD must beat a good driver more often than not.
 */
class DifficultyTest {
    private val catalog = PartCatalog(JsonReader().parse(File("data/parts.json").readText()))

    private fun car(id: String) = CarDef.parse(JsonReader().parse(File("data/cars/$id.json").readText()), JsonReader().parse(File("cars/$id/sprite.json").readText()))

    /** A driver model: reaction to the green, and how big their launch/shift mistakes are (AutoDriver scale at skill 1). */
    class Driver(val name: String, val reaction: Float, val errors: Float)

    private val good = Driver("good", 0.22f, 2.5f)
    private val sharp = Driver("sharp", 0.15f, 1.3f)

    /** One pass: total time from green to the line (reaction + ET), or 99 on a red light. */
    private fun pass(def: CarDef, scale: Float, dist: Distance, skill: Float, slow: Float, fast: Float, errors: Float): Float {
        val car = Vehicle(def, scale)
        car.trackGrip = Vehicle.DEFAULT_PREP
        car.hold()
        car.prime(def.idleRpm, 0.78f)
        val d = AutoDriver(car, skill, car.trackGrip, slow, fast, errors)
        var clock = -2f
        val dt = 1f / 240f
        while (car.splits[dist.split] < 0f && clock < 60f) {
            car.throttle = d.update(dt, true, clock, true)
            if (d.wantsLaunch && clock >= 0f && car.mode == Vehicle.Mode.HELD) car.launch(clock, rate = false)
            if (d.wantsShift) car.shift()
            if (d.wantsNitrous) car.nitrous()
            car.step(dt)
            clock += dt
            car.events.clear()
        }
        return car.leaveTime + car.splits[dist.split]
    }

    private class Rival(val id: String, val def: CarDef, val dist: Distance, val skill: Float, val boss: Boolean)

    private fun rivals(): List<Rival> {
        val career = JsonReader().parse(File("data/career.json").readText())
        val out = ArrayList<Rival>()
        for (tier in career.get("tiers")) for (r in tier.get("rivals")) {
            val parts = HashMap<String, Int>()
            r.get("parts").forEach { parts[it.name] = it.asInt() }
            out.add(Rival(r.getString("id"), Upgrades.apply(car(r.getString("car")), catalog, parts, emptyMap(), null),
                Distance.of(r.getString("distance", tier.getString("distance"))), r.getFloat("skill"), r.getBoolean("boss", false)))
        }
        return out
    }

    private fun winRate(diff: Difficulty, driver: Driver, rivals: List<Rival>, runs: Int): Float {
        MathUtils.random.setSeed(7)
        var wins = 0
        var races = 0
        for (r in rivals) {
            // the player races the same build as the rival: close racing paces the rival to it either way
            val scale = RivalPace.scale(r.def, r.def, r.dist, r.skill, r.boss, diff)
            val skill = diff.skill(r.skill)
            var sm = 0f; var st = 0f
            repeat(runs) {
                val mine = pass(r.def, 1f, r.dist, 1f, driver.reaction, driver.reaction, driver.errors)
                val theirs = pass(r.def, scale, r.dist, skill, diff.reactSlow, diff.reactFast, diff.errorScale)
                if (mine <= theirs) wins++
                races++
                sm += mine; st += theirs
            }
            if (System.getenv("DIFF_DEBUG") != null) println("  %s %s %-8s %s scale %.3f  me %.3f  them %.3f".format(diff.label, driver.name, r.id, r.dist.short, scale, sm / runs, st / runs))
        }
        return wins.toFloat() / races
    }

    @Test
    fun eachLevelIsHarder() {
        val all = rivals()
        val sample = all.filterIndexed { i, r -> i % 3 == 0 || r.boss }
        val rates = HashMap<Pair<Difficulty, String>, Float>()
        for (diff in Difficulty.entries) for (drv in listOf(good, sharp)) {
            val rate = winRate(diff, drv, sample, 6)
            val bosses = winRate(diff, drv, sample.filter { it.boss }, 10)
            rates[diff to drv.name] = rate
            println("%-7s %-6s win %.0f%%  bosses %.0f%%".format(diff.label, drv.name, rate * 100, bosses * 100))
        }
        val problems = ArrayList<String>()
        for (drv in listOf("good", "sharp")) Difficulty.entries.zipWithNext().forEach { (a, b) ->
            if (rates[b to drv]!! > rates[a to drv]!! + 0.02f) problems.add("${b.label} should be harder than ${a.label} for a $drv driver")
        }
        if (rates[Difficulty.HARD to "good"]!! > 0.5f) problems.add("HARD should beat a good driver more often than not")
        if (rates[Difficulty.EASY to "good"]!! < 0.5f) problems.add("EASY should stay easy")
        assertTrue(problems.isEmpty(), problems.joinToString("; "))
    }
}

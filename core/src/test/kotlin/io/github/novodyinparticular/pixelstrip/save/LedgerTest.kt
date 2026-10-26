package io.github.novodyinparticular.pixelstrip.save

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.utils.JsonReader
import com.badlogic.gdx.utils.JsonWriter
import io.github.novodyinparticular.pixelstrip.PixelStrip
import io.github.novodyinparticular.pixelstrip.data.CarDef
import io.github.novodyinparticular.pixelstrip.data.Career
import io.github.novodyinparticular.pixelstrip.data.Economy
import io.github.novodyinparticular.pixelstrip.data.PartCatalog
import io.github.novodyinparticular.pixelstrip.data.Tune
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Keeps assets/data/rules.json in step with the game data, and runs the shared ledger cases
 * (shared/ledger_cases.json) that the server's tests run too. Set REGEN=1 to rewrite rules.json.
 */
class LedgerTest {
    private val rulesFile = File(Rules.PATH)

    private fun built(): Rules {
        val ids = File("data/cars").listFiles()!!.filter { it.extension == "json" }.map { it.nameWithoutExtension }.sorted()
        val cars = ids.map { CarDef.parse(JsonReader().parse(File("data/cars/$it.json").readText()), JsonReader().parse(File("cars/$it/sprite.json").readText())) }
        val colors = ImageIO.read(File("paint/ramps.png")).height
        return Rules.build(
            cars, PartCatalog(JsonReader().parse(File("data/parts.json").readText())),
            Economy.parse(JsonReader().parse(File("data/economy.json").readText())),
            Career.parse(JsonReader().parse(File("data/career.json").readText())),
            colors, PixelStrip.PARTS_RESALE_PERCENT, JsonReader().parse(File("data/retired.json").readText()),
        )
    }

    @Test
    fun rulesFileIsCurrent() {
        val fresh = built().toJson()
        if (System.getenv("REGEN") == "1" || !rulesFile.exists()) rulesFile.writeText(fresh)
        assertEquals(fresh, rulesFile.readText(), "assets/data/rules.json is stale: run REGEN=1 ./gradlew core:test --tests '*LedgerTest*'")
    }

    @Test
    fun sharedCases() {
        val rules = Rules.parse(rulesFile.readText())
        val cases = JsonReader().parse(File("../shared/ledger_cases.json").readText())
        val tmp = FileHandle(File.createTempFile("ledger", ".json"))
        for (c in cases) {
            val save = SaveGame(tmp)
            c.get("state")?.let { save.adopt(it, null) }      // a garage from before a rule changed (retired cars)
            val refused = ArrayList<Int>()
            c.get("entries").forEachIndexed { i, e -> if (!Ledger.apply(rules, save, Entry.from(e))) refused.add(i) }
            val want = c.get("expect")
            val name = c.getString("name")
            assertEquals(want.get("refused").asIntArray().toList(), refused, "$name: refused entries")
            want.get("cash")?.let { assertEquals(it.asLong(), save.cash, "$name: cash") }
            want.get("xp")?.let { assertEquals(it.asLong(), save.xp, "$name: xp") }
            want.get("beaten")?.let { assertEquals(it.asStringArray().toList(), save.beaten.toList(), "$name: beaten") }
            want.get("found")?.let { assertEquals(it.asStringArray().toList(), save.found.toList(), "$name: found") }
            want.get("cars")?.let { cars ->
                assertEquals(cars.size, save.cars.size, "$name: car count")
                cars.forEachIndexed { i, wc ->
                    val sc = save.cars[i]
                    assertEquals(wc.getString("uid"), sc.uid, "$name: car $i uid")
                    assertEquals(wc.getString("car"), sc.carId, "$name: car $i id")
                    wc.get("parts")?.let { p -> assertEquals(p.associate { it.name to it.asInt() }, sc.parts.toMap(), "$name: car $i parts") }
                    wc.get("paint")?.let { assertEquals(it.asInt(), sc.paint, "$name: car $i paint") }
                }
            }
        }
        assertTrue(cases.size >= 10)
    }

    @Test
    fun adoptKeepsLocalTunesAndRebuildsTheGarage() {
        val rules = Rules.parse(rulesFile.readText())
        val tmp = FileHandle(File.createTempFile("adopt", ".json"))
        val save = SaveGame(tmp)
        Ledger.apply(rules, save, Entry("start", 1, car = "kite"))
        save.cars[0].tunes.add(Tune("MINE"))
        val server = SaveGame(FileHandle(File.createTempFile("srv", ".json")))
        Ledger.apply(rules, server, Entry("start", 1, car = "kite"))
        Ledger.apply(rules, server, Entry("part", 2, uid = "c1", slot = "intake", opt = 1))
        save.adopt(JsonReader().parse(server.economicState().toJson(JsonWriter.OutputType.json)), null)
        assertEquals(server.cash, save.cash)
        assertEquals(1, save.cars[0].parts["intake"])
        assertTrue(save.cars[0].owns("intake", 1), "owned parts come across too")
        assertEquals("MINE", save.cars[0].tunes[0].name)
    }
}

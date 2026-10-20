package io.github.novodyinparticular.pixelstrip.save

import com.badlogic.gdx.utils.JsonReader
import com.badlogic.gdx.utils.JsonValue
import com.badlogic.gdx.utils.JsonWriter
import io.github.novodyinparticular.pixelstrip.data.CarDef
import io.github.novodyinparticular.pixelstrip.data.Career
import io.github.novodyinparticular.pixelstrip.data.Economy
import io.github.novodyinparticular.pixelstrip.data.PartCatalog
import io.github.novodyinparticular.pixelstrip.race.DragMath

/**
 * Every number the [Ledger] needs, flattened to integers: car and part prices per car, resale,
 * paint, payouts and prize cars. Generated from the game data into assets/data/rules.json
 * (RulesTest keeps it current) and read by both the game and the server, so a price can never
 * round differently on the two sides.
 */
class Rules(
    val startingCash: Long,
    val starterCars: List<String>,
    val resalePercent: Long,
    val cars: Map<String, CarRule>,
    val slots: Map<String, SlotRule>,
    val cosmetics: Map<String, CosmeticRule>,
    val colors: Int,
    val colorPrice: Long,
    val finishes: List<Long>,
    val decals: List<Long>,
    val decalColors: Int,
    val decalColorPrice: Long,
    val rivals: List<RivalRule>,
    /** Two career races can't finish closer together than this (staging + the run itself). */
    val minRaceGapMs: Long,
    val xp: XpRule,
) {
    /**
     * XP for races, and the levels it buys: reaching level L takes levelStep * (L-1) * L / 2 XP in
     * all and pays levelCash * (L-1). Online and tournament XP is granted by the server alone.
     */
    class XpRule(
        val careerWin: Long, val careerLoss: Long, val bossBonus: Long, val onlineWin: Long, val onlineLoss: Long,
        val tournamentRun: Long, val levelStep: Long, val levelCash: Long, val maxLevel: Int,
    ) {
        /** Total XP to reach [level]. */
        fun threshold(level: Int): Long = levelStep * (level - 1) * level / 2

        fun levelOf(xp: Long): Int {
            var l = 1
            while (l < maxLevel && xp >= threshold(l + 1)) l++
            return l
        }
    }

    /** [parts]: slot -> price of options 1..n on this car. */
    class CarRule(val price: Long, val sell: Long, val dealer: Boolean, val paint: Int, val drivetrain: String, val parts: Map<String, List<Long>>)
    /** [options] counts the factory part; [requires]: option -> any one of (slot, min option). */
    class SlotRule(
        val options: Int, val excludes: List<String>, val requires: Map<Int, List<Pair<String, Int>>>, val rwdOnly: List<Int>,
        /** option -> the only cars it fits. */
        val carOnly: Map<Int, List<String>>,
    )
    class CosmeticRule(val price: Long, val styles: Int)
    class RivalRule(
        val id: String, val payout: Long, val boss: Boolean,
        val car: String, val paint: Int, val parts: Map<String, Int>, val cosmetics: Map<String, Int>,
    )

    fun rivalIndex(id: String) = rivals.indexOfFirst { it.id == id }

    fun toJson(): String {
        val root = obj()
        root.addChild("startingCash", JsonValue(startingCash))
        root.addChild("starterCars", arr(starterCars.map { JsonValue(it) }))
        root.addChild("resalePercent", JsonValue(resalePercent))
        root.addChild("cars", obj().also { o ->
            for ((id, c) in cars.toSortedMap()) o.addChild(id, obj().also { co ->
                co.addChild("price", JsonValue(c.price))
                co.addChild("sell", JsonValue(c.sell))
                co.addChild("dealer", JsonValue(c.dealer))
                co.addChild("paint", JsonValue(c.paint.toLong()))
                co.addChild("drivetrain", JsonValue(c.drivetrain))
                co.addChild("parts", obj().also { po -> for ((s, p) in c.parts) po.addChild(s, arr(p.map { JsonValue(it) })) })
            })
        })
        root.addChild("slots", obj().also { o ->
            for ((id, s) in slots) o.addChild(id, obj().also { so ->
                so.addChild("options", JsonValue(s.options.toLong()))
                so.addChild("excludes", arr(s.excludes.map { JsonValue(it) }))
                so.addChild("requires", obj().also { ro ->
                    for ((opt, any) in s.requires.toSortedMap()) ro.addChild(opt.toString(), arr(any.map { (sl, min) -> arr(listOf(JsonValue(sl), JsonValue(min.toLong()))) }))
                })
                so.addChild("rwdOnly", arr(s.rwdOnly.map { JsonValue(it.toLong()) }))
                so.addChild("carOnly", obj().also { co -> for ((opt, ids) in s.carOnly.toSortedMap()) co.addChild(opt.toString(), arr(ids.map { JsonValue(it) })) })
            })
        })
        root.addChild("cosmetics", obj().also { o ->
            for ((id, c) in cosmetics) o.addChild(id, obj().also { co ->
                co.addChild("price", JsonValue(c.price))
                co.addChild("styles", JsonValue(c.styles.toLong()))
            })
        })
        root.addChild("colors", JsonValue(colors.toLong()))
        root.addChild("colorPrice", JsonValue(colorPrice))
        root.addChild("finishes", arr(finishes.map { JsonValue(it) }))
        root.addChild("decals", arr(decals.map { JsonValue(it) }))
        root.addChild("decalColors", JsonValue(decalColors.toLong()))
        root.addChild("decalColorPrice", JsonValue(decalColorPrice))
        root.addChild("rivals", arr(rivals.map { r ->
            obj().also { o ->
                o.addChild("id", JsonValue(r.id))
                o.addChild("payout", JsonValue(r.payout))
                o.addChild("boss", JsonValue(r.boss))
                o.addChild("car", JsonValue(r.car))
                o.addChild("paint", JsonValue(r.paint.toLong()))
                o.addChild("parts", intMap(r.parts))
                o.addChild("cosmetics", intMap(r.cosmetics))
            }
        }))
        root.addChild("minRaceGapMs", JsonValue(minRaceGapMs))
        root.addChild("xp", obj().also { o ->
            o.addChild("careerWin", JsonValue(xp.careerWin))
            o.addChild("careerLoss", JsonValue(xp.careerLoss))
            o.addChild("bossBonus", JsonValue(xp.bossBonus))
            o.addChild("onlineWin", JsonValue(xp.onlineWin))
            o.addChild("onlineLoss", JsonValue(xp.onlineLoss))
            o.addChild("tournamentRun", JsonValue(xp.tournamentRun))
            o.addChild("levelStep", JsonValue(xp.levelStep))
            o.addChild("levelCash", JsonValue(xp.levelCash))
            o.addChild("maxLevel", JsonValue(xp.maxLevel.toLong()))
        })
        return root.prettyPrint(JsonWriter.OutputType.json, 120) + "\n"
    }

    companion object {
        const val PATH = "data/rules.json"

        private fun obj() = JsonValue(JsonValue.ValueType.`object`)
        private fun arr(items: List<JsonValue>) = JsonValue(JsonValue.ValueType.array).also { a -> items.forEach { a.addChild(it) } }
        private fun intMap(m: Map<String, Int>) = obj().also { o -> for ((k, v) in m.toSortedMap()) o.addChild(k, JsonValue(v.toLong())) }
        private fun ints(j: JsonValue?) = j?.associate { it.name to it.asInt() } ?: emptyMap()

        /** From the game data; the stock class (which scales part prices) comes from a perfect-pass simulation. */
        fun build(cars: List<CarDef>, catalog: PartCatalog, economy: Economy, career: Career, colors: Int, partsResalePercent: Long) = Rules(
            startingCash = economy.startingCash,
            starterCars = economy.starterCars,
            resalePercent = partsResalePercent,
            cars = cars.associate { def ->
                val cls = economy.classFor(DragMath.perfectPass(def).quarter)
                def.id to CarRule(def.price.toLong(), economy.sellPrice(def.price), def.dealer, def.defaultPaint, def.drivetrain,
                    catalog.slots.associate { s -> s.id to (1 until s.options.size).map { o -> catalog.price(s, o, cls) } })
            },
            slots = catalog.slots.associate { s ->
                s.id to SlotRule(
                    s.options.size, s.excludes,
                    s.options.withIndex().filter { it.value.requires.isNotEmpty() }.associate { it.index to it.value.requires },
                    s.options.withIndex().filter { it.value.rwdOnly }.map { it.index },
                    s.options.withIndex().filter { it.value.carOnly.isNotEmpty() }.associate { it.index to it.value.carOnly },
                )
            },
            cosmetics = catalog.cosmetics.associate { it.id to CosmeticRule(it.price.toLong(), it.styles.size) },
            colors = colors,
            colorPrice = catalog.colorPrice.toLong(),
            finishes = catalog.finishes.map { it.price.toLong() },
            decals = catalog.decals.map { it.price.toLong() },
            decalColors = catalog.decalColors.size,
            decalColorPrice = DECAL_COLOR_PRICE,
            rivals = career.rivals.map { r -> RivalRule(r.id, r.payout, r.boss, r.carId, r.paint, r.parts, r.cosmetics) },
            minRaceGapMs = 6000L,
            xp = XpRule(careerWin = 100, careerLoss = 25, bossBonus = 250, onlineWin = 150, onlineLoss = 40, tournamentRun = 30,
                levelStep = 250, levelCash = 1000, maxLevel = 50),
        )

        const val DECAL_COLOR_PRICE = 500L

        fun parse(text: String): Rules {
            val j = JsonReader().parse(text)
            return Rules(
                startingCash = j.getLong("startingCash"),
                starterCars = j.get("starterCars").asStringArray().toList(),
                resalePercent = j.getLong("resalePercent"),
                cars = j.get("cars").associate { c ->
                    c.name to CarRule(c.getLong("price"), c.getLong("sell"), c.getBoolean("dealer"), c.getInt("paint"), c.getString("drivetrain"),
                        c.get("parts").associate { p -> p.name to p.asLongArray().toList() })
                },
                slots = j.get("slots").associate { s ->
                    s.name to SlotRule(
                        s.getInt("options"), s.get("excludes").asStringArray().toList(),
                        s.get("requires").associate { r -> r.name.toInt() to r.map { it.getString(0) to it.getInt(1) } },
                        s.get("rwdOnly").asIntArray().toList(),
                        s.get("carOnly").associate { c -> c.name.toInt() to c.asStringArray().toList() },
                    )
                },
                cosmetics = j.get("cosmetics").associate { it.name to CosmeticRule(it.getLong("price"), it.getInt("styles")) },
                colors = j.getInt("colors"),
                colorPrice = j.getLong("colorPrice"),
                finishes = j.get("finishes").asLongArray().toList(),
                decals = j.get("decals").asLongArray().toList(),
                decalColors = j.getInt("decalColors"),
                decalColorPrice = j.getLong("decalColorPrice"),
                rivals = j.get("rivals").map { r ->
                    RivalRule(r.getString("id"), r.getLong("payout"), r.getBoolean("boss"), r.getString("car"), r.getInt("paint"),
                        ints(r.get("parts")), ints(r.get("cosmetics")))
                },
                minRaceGapMs = j.getLong("minRaceGapMs"),
                xp = j.get("xp").let { x ->
                    XpRule(x.getLong("careerWin"), x.getLong("careerLoss"), x.getLong("bossBonus"), x.getLong("onlineWin"), x.getLong("onlineLoss"),
                        x.getLong("tournamentRun"), x.getLong("levelStep"), x.getLong("levelCash"), x.getInt("maxLevel"))
                },
            )
        }
    }
}

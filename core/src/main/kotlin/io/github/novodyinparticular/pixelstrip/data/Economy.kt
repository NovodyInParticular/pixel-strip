package io.github.novodyinparticular.pixelstrip.data

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.JsonReader

/** Economy constants and performance classes, from assets/data/economy.json. */
class Economy(val startingCash: Long, val sellRate: Float, val starterCars: List<String>, private val classes: List<Pair<String, Float>>) {
    /** Class name for a quarter-mile ET (perfect driver, stock parts until A3 adds upgrades). */
    fun classFor(et: Float): String = classes.first { et <= it.second }.first

    /** Matchmaking rating: higher is quicker. 13.0 s -> 500, every tenth of a second is 10 points. */
    fun rating(et: Float): Int = ((18f - et) * 100f).toInt().coerceAtLeast(0)

    /** Integer maths, so the server (JavaScript doubles) gets the same number. */
    fun sellPrice(price: Int) = price.toLong() * sellPercent / 100
    val sellPercent get() = Math.round(sellRate * 100f)

    companion object {
        fun load(): Economy = parse(JsonReader().parse(Gdx.files.internal("data/economy.json")))

        fun parse(j: com.badlogic.gdx.utils.JsonValue): Economy {
            return Economy(
                j.getLong("startingCash"),
                j.getFloat("sellRate"),
                j.get("starterCars").asStringArray().toList(),
                j.get("classes").map { it.getString("name") to it.getFloat("maxEt") },
            )
        }
    }
}

package io.github.novodyinparticular.pixelstrip.data

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.JsonReader
import io.github.novodyinparticular.pixelstrip.save.OwnedCar
import io.github.novodyinparticular.pixelstrip.save.SaveGame

/**
 * The career ladder from assets/data/career.json: four tiers of rivals, each ending in a boss
 * whose car is the prize. Rivals unlock in order; a tier's boss after its rivals; the next tier
 * after the boss.
 */
class Career(val tiers: List<Tier>) {
    class Tier(val id: String, val name: String, val quarter: Boolean, val rivals: List<Rival>)

    class Rival(
        val id: String,
        val name: String,
        val handle: String?,
        val carId: String,
        val paint: Int,
        val parts: Map<String, Int>,
        val cosmetics: Map<String, Int>,
        val skill: Float,
        val payout: Long,
        val x: Float,
        val y: Float,
        val boss: Boolean,
        val intro: List<String>,
        val tier: String,
        /** Who's visible through the glass: "joe" for the boss drawn from the user's friend. */
        val driver: String?,
        /** Where the race happens. Joe's San Antonio is his alone. */
        val track: String,
    ) {
        /** The rival's car as an OwnedCar, so the usual effective-stats and looks code applies. */
        fun asCar(): OwnedCar = OwnedCar("rival_$id", carId, paint, parts.toMutableMap(), cosmetics.toMutableMap())
        val title get() = if (handle != null) "$name \"$handle\"" else name
    }

    val rivals get() = tiers.flatMap { it.rivals }
    fun tierOf(r: Rival) = tiers.first { it.id == r.tier }

    /** Unlocked: the first rival, then each one after a beaten rival; a tier opens after the previous boss. */
    fun unlocked(save: SaveGame, r: Rival): Boolean {
        val all = rivals
        val i = all.indexOf(r)
        return i == 0 || all[i - 1].id in save.beaten
    }

    companion object {
        fun load(): Career {
            val root = JsonReader().parse(Gdx.files.internal("data/career.json"))
            var n = 0
            return Career(root.get("tiers").map { t ->
                val tid = t.getString("id")
                Tier(tid, t.getString("name"), t.getString("distance") == "quarter", t.get("rivals").map { r ->
                    val parts = HashMap<String, Int>()
                    r.get("parts").forEach { parts[it.name] = it.asInt() }
                    val cos = HashMap<String, Int>()
                    r.get("cosmetics")?.forEach { cos[it.name] = it.asInt() }
                    cos["finish"] = r.getInt("finish", 0)
                    Rival(
                        r.getString("id"), r.getString("name"), r.getString("handle", null), r.getString("car"), r.getInt("paint"),
                        parts, cos, r.getFloat("skill"), r.getLong("payout"), r.getFloat("x"), r.getFloat("y"),
                        r.getBoolean("boss", false), r.get("intro")?.asStringArray()?.toList() ?: emptyList(), tid,
                        r.getString("driver", null),
                        r.getString("track", TrackDef.GENERAL[n++ % TrackDef.GENERAL.size]),
                    )
                })
            })
        }
    }
}

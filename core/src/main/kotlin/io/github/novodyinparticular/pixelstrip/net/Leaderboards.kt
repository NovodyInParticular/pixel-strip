package io.github.novodyinparticular.pixelstrip.net

import com.badlogic.gdx.utils.JsonValue
import io.github.novodyinparticular.pixelstrip.gfx.Emblem

/** The server's leaderboards (server/functions/src/leaderboards.ts). */
class Leaderboards(private val cloud: Cloud) {
    /** One row: a racer (et boards: [et], [mph], [car]; wins boards: [wins]) or a crew ([emblem], [members], [wins]). */
    class Row(
        val id: String, val name: String, val tag: String?, val et: Float = 0f, val mph: Float = 0f, val car: String? = null,
        val wins: Int = 0, val emblem: Emblem? = null, val members: Int = 0,
    ) {
        val tagged get() = tag?.let { "[$it] $name" } ?: name
    }
    class Board(val rows: List<Row>, val mine: Row?, val place: Int)

    /** [board] "et" (with [cls] and [quarter]), "wins" or "crews" (with [week]). */
    fun load(board: String, cls: String = "STREET", quarter: Boolean = true, week: Boolean = true, then: (Board?, String?) -> Unit) {
        val data = JsonValue(JsonValue.ValueType.`object`)
        data.addChild("board", JsonValue(board))
        data.addChild("cls", JsonValue(cls))
        data.addChild("dist", JsonValue(if (quarter) "quarter" else "eighth"))
        data.addChild("period", JsonValue(if (week) "week" else "all"))
        cloud.call("leaderboard", data) { r, err ->
            if (r == null) return@call then(null, err ?: "FAILED")
            val rows = r.get("rows")?.map(::row) ?: emptyList()
            val mine = r.get("mine")?.takeIf { it.isObject }
            then(Board(rows, mine?.let(::row), mine?.getInt("place", 0) ?: 0), null)
        }
    }

    private fun row(j: JsonValue) = Row(
        j.getString("uid", null) ?: j.getString("id", ""), j.getString("name", "?"), j.get("tag")?.takeIf { it.isString }?.asString(),
        j.getFloat("et", 0f), j.getFloat("mph", 0f), j.get("car")?.takeIf { it.isString }?.asString(), j.getInt("wins", 0),
        Emblem.parse(j.get("emblem")), j.getInt("members", 0),
    )
}

package io.github.novodyinparticular.pixelstrip.net

import com.badlogic.gdx.utils.JsonValue

/** Daily rewards (server/functions/src/daily.ts): one claim a UTC day, a seven-day streak cycle. */
class Daily(private val cloud: Cloud) {
    class Reward(val cash: Long, val xp: Long)
    class Status(val ready: Boolean, val streak: Int, val day: Int, val week: List<Reward>, val claimed: Reward?)

    /** Looks (or, with [claim], takes today's reward); [then] gets the status or the error. */
    fun fetch(claim: Boolean, then: (Status?, String?) -> Unit) {
        val data = JsonValue(JsonValue.ValueType.`object`)
        data.addChild("claim", JsonValue(claim))
        cloud.call("daily", data) { r, err ->
            if (r == null) return@call then(null, err ?: "FAILED")
            if (claim) cloud.requestSync()        // the cash (and any XP) went into the garage on the server
            fun reward(j: JsonValue) = Reward(j.getLong("cash", 0), j.getLong("xp", 0))
            then(Status(r.getBoolean("ready", false), r.getInt("streak", 1), r.getInt("day", 1),
                r.get("week")?.map(::reward) ?: emptyList(), r.get("claimed")?.takeIf { it.isObject }?.let(::reward)), null)
        }
    }
}

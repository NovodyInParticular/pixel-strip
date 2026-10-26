package io.github.novodyinparticular.pixelstrip.net

import com.badlogic.gdx.utils.JsonValue
import io.github.novodyinparticular.pixelstrip.race.RunRecorder
import io.github.novodyinparticular.pixelstrip.save.SaveGame
import io.github.novodyinparticular.pixelstrip.save.OwnedCar

/**
 * Daily tournaments (server/functions/src/tournaments.ts): one per class, five timed quarter-mile
 * passes each, replayed by the server; the top ten split the pool when the day ends.
 */
class Tournaments(private val cloud: Cloud) {
    class Best(val et: Float, val mph: Float, val rt: Float)
    class Entry(val uid: String, val name: String, val tag: String?, val car: String, val paint: Int, val runs: Int, val best: Best?) {
        val tagged get() = tag?.let { "[$it] $name" } ?: name
    }
    class Mine(val entry: Entry, val rank: Int)
    class Result(val uid: String, val name: String, val tag: String?, val car: String?, val et: Float, val prize: Long)
    class Event(
        val id: String, val cls: String, val track: String, val fee: Long, val pool: Long, val entrants: Int, val ends: Long,
        val board: List<Entry>, val prizes: List<Long>, val mine: Mine?, val settled: Boolean, val results: List<Result>?,
        /** A rare car the day's winner also gets (if they don't have one); null: cash only. */
        val prizeCar: String? = null,
    ) {
        val runsLeft get() = mine?.let { MAX_RUNS - it.entry.runs } ?: 0
    }

    /** A pass the server has replayed. [et] is null for a red light or a pass that didn't finish. */
    class RunResult(val et: Float?, val mph: Float, val rt: Float?, val red: Boolean, val best: Best?, val runsLeft: Int, val newBest: Boolean)

    var today = emptyList<Event>()
        private set
    var yesterday = emptyList<Event>()
        private set
    /** Server time minus ours, so countdowns use the server's midnight. */
    var clockSkew = 0L
        private set

    fun refresh(then: (String?) -> Unit) {
        cloud.call("tournaments", JsonValue(JsonValue.ValueType.`object`)) { r, err ->
            if (r == null) return@call then(err ?: "FAILED")
            clockSkew = r.getLong("now", System.currentTimeMillis()) - System.currentTimeMillis()
            today = r.get("today")?.map(::event) ?: emptyList()
            yesterday = r.get("yesterday")?.map(::event) ?: emptyList()
            then(null)
        }
    }

    fun enter(event: Event, car: OwnedCar, then: (String?) -> Unit) {
        val data = JsonValue(JsonValue.ValueType.`object`)
        data.addChild("id", JsonValue(event.id))
        data.addChild("car", JsonValue(car.uid))
        car.tune?.let { data.addChild("tune", SaveGame.tuneJson(it)) }
        cloud.call("enterTournament", data) { r, err ->
            cloud.requestSync()        // the fee came out of the garage on the server
            then(if (r == null) err ?: "FAILED" else null)
        }
    }

    /** Sends a finished pass; [then] gets the server's replay of it, or the error. */
    fun submit(id: String, recorder: RunRecorder, then: (RunResult?, String?) -> Unit) {
        val data = JsonValue(JsonValue.ValueType.`object`)
        data.addChild("id", JsonValue(id))
        data.addChild("events", recorder.toJson())
        cloud.call("tournamentRun", data) { r, err ->
            if (r == null) return@call then(null, err ?: "FAILED")
            cloud.requestSync()        // a clean pass earns XP on the server
            val run = r.get("run")
            then(RunResult(num(run?.get("et")), run?.getFloat("mph", 0f) ?: 0f, num(run?.get("rt")), run?.getBoolean("red", false) ?: false,
                best(r.get("best")), r.getInt("runsLeft", 0), r.getBoolean("newBest", false)), null)
        }
    }

    private fun num(v: JsonValue?) = v?.takeIf { it.isNumber }?.asFloat()
    private fun best(v: JsonValue?) = v?.takeIf { it.isObject }?.let { Best(it.getFloat("et"), it.getFloat("mph", 0f), it.getFloat("rt", 0f)) }

    private fun entry(e: JsonValue) = Entry(e.getString("uid", ""), e.getString("name", "?"), e.get("tag")?.takeIf { it.isString }?.asString(),
        e.getString("car", "kite"), e.getInt("paint", 0), e.getInt("runs", 0), best(e.get("best")))

    private fun event(t: JsonValue) = Event(
        t.getString("id"), t.getString("cls"), t.getString("track"), t.getLong("fee", 0), t.getLong("pool", 0), t.getInt("entrants", 0),
        t.getLong("ends", 0), t.get("board")?.map(::entry) ?: emptyList(), t.get("prizes")?.map { it.asLong() } ?: emptyList(),
        t.get("mine")?.takeIf { it.isObject }?.let { Mine(entry(it), it.getInt("rank", 0)) },
        t.get("settled")?.isNumber == true,
        t.get("results")?.takeIf { it.isArray }?.map {
            Result(it.getString("uid", ""), it.getString("name", "?"), it.get("tag")?.takeIf { v -> v.isString }?.asString(),
                it.get("car")?.takeIf { v -> v.isString }?.asString(), it.getFloat("et", 0f), it.getLong("prize", 0))
        },
        t.get("prizeCar")?.takeIf { it.isString }?.asString(),
    )

    companion object {
        const val MAX_RUNS = 5
    }
}

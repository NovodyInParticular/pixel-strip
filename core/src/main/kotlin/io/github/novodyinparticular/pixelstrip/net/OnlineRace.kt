package io.github.novodyinparticular.pixelstrip.net

import com.badlogic.gdx.utils.JsonValue
import io.github.novodyinparticular.pixelstrip.race.RunRecorder
import io.github.novodyinparticular.pixelstrip.race.Vehicle

/**
 * One online race: the room the server opened (players, track, distance, the starter's delay),
 * the live lanes both phones stream through the Realtime Database, and the server's verdict.
 */
class OnlineRace(private val cloud: Cloud, val raceId: String, room: JsonValue) {
    class Player(val uid: String, val name: String, val car: String, val paint: Int, val parts: Map<String, Int>, val cosmetics: Map<String, Int>, val rating: Int)

    /** The other phone's latest report. */
    class Live(val x: Float, val v: Float, val rpm: Float, val gear: Int, val mode: Vehicle.Mode, val leave: Float?, val splits: FloatArray?, val nitrous: Boolean, val t: Float)

    class Run(val rt: Float?, val et: Float?, val mph: Float, val red: Boolean)
    /** [off]: the race was called off before it ran ([reason]); stakes, if taken, went back. */
    class Result(
        val won: Boolean, val forfeit: Boolean, val mine: Run?, val theirs: Run?, val off: Boolean = false, val reason: String? = null,
        /** What the winner got: both cash stakes, or the loser's car (its model id). */
        val prizeCash: Long = 0, val prizeCar: String? = null,
    )

    /** "none", "cash" or "pink"; [amount] for cash. */
    class Wager(val kind: String, val amount: Long) {
        val none get() = kind == "none"
        val label get() = when (kind) {
            "cash" -> "$" + "%,d".format(amount)
            "pink" -> "PINK SLIPS"
            else -> "FOR FUN"
        }
    }

    val me: Player
    val them: Player
    val track: String = room.getString("track")
    val quarter: Boolean = room.getBoolean("quarter", true)
    val treeDelay: Float = room.getFloat("treeDelay", 1f)
    val wager = room.get("wager")?.let { Wager(it.getString("kind", "none"), it.getLong("amount", 0)) } ?: Wager("none", 0)
    /** confirm (both must accept the stakes), racing, off or done. */
    var state: String = room.getString("state", "racing")
        private set
    var offReason: String? = null
        private set
    /** Seconds left to accept the stakes. */
    val confirmBy: Long = room.getLong("confirmBy", 0)
    var accepted = false
        private set

    /** Accept or decline the stakes; [then] gets the room's state afterwards (racing, confirm or off). */
    fun confirm(accept: Boolean, then: (String) -> Unit) {
        val data = JsonValue(JsonValue.ValueType.`object`)
        data.addChild("raceId", JsonValue(raceId))
        data.addChild("accept", JsonValue(accept))
        cloud.call("confirmWager", data) { r, err ->
            if (r == null) {
                error = err
                return@call then("off")
            }
            accepted = accept
            state = r.getString("state", state)
            r.get("result")?.takeIf { it.isObject }?.let { offReason = it.getString("reason", null) }
            then(state)
        }
    }

    /** While confirming: picks up the other side's answer from the room. */
    fun pollState(then: (String) -> Unit) {
        cloud.dbGet("races/$raceId") { room ->
            room ?: return@dbGet
            state = room.getString("state", state)
            room.get("result")?.takeIf { it.isObject }?.let { offReason = it.getString("reason", null) }
            then(state)
        }
    }

    init {
        val uid = cloud.accountId ?: error("not signed in")
        val players = room.get("players").map { p ->
            Player(p.name, p.getString("name"), p.getString("car"), p.getInt("paint", 0), ints(p.get("parts")), ints(p.get("cosmetics")), p.getInt("rating", 0))
        }
        me = players.first { it.uid == uid }
        them = players.first { it.uid != uid }
    }

    @Volatile var live: Live? = null
        private set
    /** Seconds since the opponent's phone last reported. */
    var silence = 0f
        private set
    var result: Result? = null
        private set
    var error: String? = null
        private set
    var submitted = false
        private set

    private var pushTimer = 0f
    private var pollTimer = 0f
    private var polling = false
    private var resultTimer = 0f

    /** Streams our car and pulls theirs, ten times a second. */
    fun update(dt: Float, car: Vehicle, clock: Float) {
        silence += dt
        pushTimer -= dt
        if (pushTimer <= 0f) {
            pushTimer = LIVE_INTERVAL
            cloud.dbPut("races/$raceId/live/${me.uid}", liveJson(car, clock))
        }
        pollTimer -= dt
        if (pollTimer <= 0f && !polling) {
            pollTimer = LIVE_INTERVAL
            polling = true
            cloud.dbGet("races/$raceId/live/${them.uid}") { j ->
                polling = false
                if (j != null && !j.isNull) {
                    val t = j.getFloat("t", 0f)
                    if (live == null || t > live!!.t) silence = 0f
                    live = Live(
                        j.getFloat("x"), j.getFloat("v"), j.getFloat("rpm", 900f), j.getInt("g", 0),
                        runCatching { Vehicle.Mode.valueOf(j.getString("m", "BURNOUT")) }.getOrDefault(Vehicle.Mode.BURNOUT),
                        j.get("lt")?.takeIf { it.isNumber }?.asFloat(), j.get("sp")?.asFloatArray(), j.getBoolean("n", false), t,
                    )
                }
            }
        }
        if (submitted && result == null) {
            resultTimer -= dt
            if (resultTimer <= 0f) {
                resultTimer = RESULT_POLL
                cloud.call("raceResult", JsonValue(JsonValue.ValueType.`object`).also { it.addChild("raceId", JsonValue(raceId)) }) { r, _ ->
                    r?.get("result")?.takeIf { !it.isNull }?.let { readResult(it) }
                }
            }
        }
    }

    private fun liveJson(car: Vehicle, clock: Float) = JsonValue(JsonValue.ValueType.`object`).also { o ->
        o.addChild("x", JsonValue(car.x.toDouble()))
        o.addChild("v", JsonValue(car.v.toDouble()))
        o.addChild("t", JsonValue(clock.toDouble()))
        o.addChild("rpm", JsonValue(car.rpm.toDouble()))
        o.addChild("g", JsonValue(car.gear.toLong()))
        o.addChild("m", JsonValue(car.mode.name))
        o.addChild("n", JsonValue(car.nitrousActive))
        if (car.hasLeft) o.addChild("lt", JsonValue(car.leaveTime.toDouble()))
        o.addChild("sp", JsonValue(JsonValue.ValueType.array).also { a -> car.splits.forEach { a.addChild(JsonValue(it.toDouble())) } })
    }

    /** Sends our run for the server to replay. */
    fun submit(recorder: RunRecorder) {
        if (submitted) return
        submitted = true
        resultTimer = RESULT_POLL
        val data = JsonValue(JsonValue.ValueType.`object`)
        data.addChild("raceId", JsonValue(raceId))
        data.addChild("events", recorder.toJson())
        cloud.call("submitRun", data) { r, err ->
            if (r == null) error = err
            r?.get("result")?.takeIf { !it.isNull }?.let { readResult(it) }
        }
    }

    private fun readResult(j: JsonValue) {
        fun run(o: JsonValue?) = o?.let {
            Run(it.get("rt")?.takeIf { v -> v.isNumber }?.asFloat(), it.get("et")?.takeIf { v -> v.isNumber }?.asFloat(), it.getFloat("mph", 0f), it.getBoolean("red", false))
        }
        if (j.getBoolean("off", false)) {
            result = Result(false, false, null, null, off = true, reason = j.getString("reason", null))
            cloud.requestSync()
            return
        }
        val runs = j.get("runs")
        val prize = j.get("prize")
        result = Result(j.getString("winner") == me.uid, j.getBoolean("forfeit", false), run(runs?.get(me.uid)), run(runs?.get(them.uid)),
            prizeCash = prize?.getLong("cash", 0) ?: 0, prizeCar = prize?.getString("car", null))
        if (!wager.none) cloud.requestSync()          // the server moved cash or a car: take its garage
    }

    companion object {
        const val LIVE_INTERVAL = 0.1f
        const val RESULT_POLL = 1.5f
        /** No word from the other phone for this long: they're shown as gone. */
        const val GONE_S = 8f

        private fun ints(j: JsonValue?): Map<String, Int> = j?.associate { it.name to it.asInt() } ?: emptyMap()
    }
}

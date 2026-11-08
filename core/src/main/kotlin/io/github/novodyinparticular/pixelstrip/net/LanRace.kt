package io.github.novodyinparticular.pixelstrip.net

import com.badlogic.gdx.utils.JsonValue
import io.github.novodyinparticular.pixelstrip.race.Distance
import io.github.novodyinparticular.pixelstrip.race.Vehicle

/**
 * Two players connected over the local Wi-Fi, from the lobby through any number of races.
 * Messages: hello (who you are and what you drive), lobby (back in the lobby, ready), pick (the
 * host's track and distance), race (the host starts one), live, run (a finished pass), bye.
 */
class LanSession(val link: Lan.Link, val me: OnlineRace.Player) {
    val host get() = link.host
    var them: OnlineRace.Player? = null
        private set
    /** Their protocol, when it isn't ours (different game versions). */
    var mismatch: String? = null
        private set
    /** The other player is in the lobby, waiting for a race. */
    var theyReady = false
    /** The host's pick, as last sent. */
    var pickTrack: String? = null
        private set
    var pickDistance: Distance = Distance.QUARTER
        private set
    /** A race the host started that nobody has picked up yet. */
    var started: LanRace? = null
    var race: LanRace? = null
    var left = false
        private set
    private var raceNo = 0

    val closed get() = link.dead || left

    fun sayHello() = link.send(Lan.obj(
        "k" to "hello", "v" to Lan.PROTOCOL, "name" to me.name, "car" to me.car, "paint" to me.paint,
        "parts" to ints(me.parts), "cosmetics" to ints(me.cosmetics),
    ))

    fun sayReady() = link.send(Lan.obj("k" to "lobby"))

    fun sayPick(track: String, dist: Distance) = link.send(Lan.obj("k" to "pick", "track" to track, "dist" to dist.name))

    /** Host only: start a race for both phones. */
    fun start(track: String, dist: Distance, treeDelay: Float): LanRace {
        raceNo++
        link.send(Lan.obj("k" to "race", "rn" to raceNo, "track" to track, "dist" to dist.name, "delay" to treeDelay))
        theyReady = false
        return LanRace(this, raceNo, track, dist, treeDelay).also { race = it }
    }

    /** Hang up (the other phone sees the connection close). */
    fun leave() {
        if (left) return
        left = true
        link.send(Lan.obj("k" to "bye"))
        Thread({
            Thread.sleep(400)
            link.close()
        }, "lan-bye").apply { isDaemon = true; start() }
    }

    /** Handles everything that came in since last time; [knownCar] checks their car exists in this version. */
    fun pump(knownCar: (String) -> Boolean = { true }) {
        while (true) {
            val j = link.poll() ?: break
            when (j.getString("k", "")) {
                "hello" -> {
                    val v = j.getInt("v", 0)
                    val car = j.getString("car", "")
                    if (v != Lan.PROTOCOL) mismatch = "THEY HAVE A DIFFERENT GAME VERSION"
                    else if (!knownCar(car)) mismatch = "THEIR CAR ISN'T IN THIS VERSION: UPDATE BOTH GAMES"
                    them = OnlineRace.Player(if (host) "join" else "host", j.getString("name", "RACER"), car, j.getInt("paint", 0),
                        intsOf(j.get("parts")), intsOf(j.get("cosmetics")), 0)
                }
                "lobby" -> theyReady = true
                "pick" -> {
                    pickTrack = j.getString("track", null)
                    pickDistance = runCatching { Distance.valueOf(j.getString("dist")) }.getOrDefault(Distance.QUARTER)
                }
                "race" -> if (!host) {
                    val n = j.getInt("rn", 0)
                    raceNo = n
                    val dist = runCatching { Distance.valueOf(j.getString("dist")) }.getOrDefault(Distance.QUARTER)
                    started = LanRace(this, n, j.getString("track", "neon_city"), dist, j.getFloat("delay", 1f)).also { race = it }
                }
                "live", "run" -> race?.takeIf { it.n == j.getInt("rn", -1) }?.receive(j)
                "bye" -> link.close()
            }
        }
    }

    private fun ints(m: Map<String, Int>) = JsonValue(JsonValue.ValueType.`object`).also { o -> m.forEach { (k, v) -> o.addChild(k, JsonValue(v.toLong())) } }

    private fun intsOf(j: JsonValue?): Map<String, Int> = j?.associate { it.name to it.asInt() } ?: emptyMap()
}

/**
 * One race over the Wi-Fi. Each phone runs its own car and streams it to the other; when both
 * runs are in, both phones work out the same winner: a red light loses, otherwise the lower
 * reaction + ET (each measured against that phone's own tree, so lag can't help either side).
 */
class LanRace(val session: LanSession, val n: Int, override val track: String, override val distance: Distance, override val treeDelay: Float) : HeadsUp {
    override val me get() = session.me
    override val them get() = session.them!!
    override val wager = OnlineRace.Wager("none", 0)
    override val local get() = true

    @Volatile override var live: OnlineRace.Live? = null
        private set
    override var silence = 0f
        private set
    override var result: OnlineRace.Result? = null
        private set
    override val error: String? get() = if (session.closed && result == null) "CONNECTION LOST" else null

    private var mine: OnlineRace.Run? = null
    private var theirs: OnlineRace.Run? = null
    private var pushTimer = 0f
    private var finishedFor = 0f

    fun receive(j: JsonValue) {
        when (j.getString("k")) {
            "live" -> {
                val l = OnlineRace.parseLive(j)
                if (live == null || l.t > live!!.t) silence = 0f
                live = l
            }
            "run" -> theirs = OnlineRace.Run(
                j.get("rt")?.takeIf { it.isNumber }?.asFloat(), j.get("et")?.takeIf { it.isNumber }?.asFloat(),
                j.getFloat("mph", 0f), j.getBoolean("red", false),
            )
        }
    }

    override fun update(dt: Float, car: Vehicle, clock: Float) {
        session.pump()
        silence += dt
        pushTimer -= dt
        if (pushTimer <= 0f) {
            pushTimer = LIVE_INTERVAL
            session.link.send(OnlineRace.liveJson(car, clock).also { it.addChild("k", JsonValue("live")); it.addChild("rn", JsonValue(n.toLong())) })
        }
        if (mine != null) finishedFor += dt
        settle()
    }

    override fun finish(car: Vehicle, red: Boolean, split: Int) {
        if (mine != null) return
        val et = car.splits[split].takeIf { it >= 0f }
        val run = OnlineRace.Run(if (car.hasLeft) car.leaveTime else null, et, car.splitSpeed[split] * 2.23694f, red)
        mine = run
        session.link.send(Lan.obj("k" to "run", "rn" to n, "rt" to run.rt, "et" to run.et, "mph" to run.mph, "red" to run.red))
        settle()
    }

    override fun quit() = session.leave()

    private fun settle() {
        if (result != null) return
        val m = mine ?: return
        val t = theirs
        if (t == null) {
            // they went quiet (or hung up) while we were done: the race is ours
            if (session.closed || (silence > OnlineRace.GONE_S && finishedFor > OnlineRace.GONE_S)) {
                result = OnlineRace.Result(won = !m.red && m.et != null, forfeit = true, mine = m, theirs = null)
            }
            return
        }
        result = OnlineRace.Result(won = beats(m, t, session.host), forfeit = false, mine = m, theirs = t)
    }

    companion object {
        const val LIVE_INTERVAL = 0.05f

        /** Whether run [a] beats run [b]; a dead heat goes to the host, so both phones agree. */
        fun beats(a: OnlineRace.Run, b: OnlineRace.Run, aIsHost: Boolean): Boolean {
            if (a.red || b.red) {
                if (a.red && b.red) return (a.rt ?: 0f) > (b.rt ?: 0f) || ((a.rt ?: 0f) == (b.rt ?: 0f) && aIsHost)
                return b.red
            }
            val ta = if (a.et != null) (a.rt ?: 0f) + a.et else Float.MAX_VALUE
            val tb = if (b.et != null) (b.rt ?: 0f) + b.et else Float.MAX_VALUE
            return ta < tb || (ta == tb && aIsHost)
        }
    }
}

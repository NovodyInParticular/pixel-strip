package io.github.novodyinparticular.pixelstrip.save

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.utils.JsonReader
import com.badlogic.gdx.utils.JsonValue
import com.badlogic.gdx.utils.JsonWriter
import io.github.novodyinparticular.pixelstrip.data.Tune

/**
 * A car in the player's garage. [parts]: slot -> fitted option (0 = factory). [owned]: slot ->
 * bitmask of options bought for this car (swapping between them is free). [cosmetics]:
 * cosmetic id -> style index. [tunes]: saved presets, [activeTune] the one in use (-1 = stock).
 */
class OwnedCar(
    val uid: String,
    val carId: String,
    var paint: Int,
    val parts: MutableMap<String, Int> = mutableMapOf(),
    val cosmetics: MutableMap<String, Int> = mutableMapOf(),
    val tunes: MutableList<Tune> = mutableListOf(),
    var activeTune: Int = -1,
    val runs: MutableList<RunRecord> = mutableListOf(),
    /** Last dyno pull: wheel horsepower sampled every [DYNO_STEP_RPM] from idle, for before/after. */
    var dyno: FloatArray? = null,
    val owned: MutableMap<String, Int> = mutableMapOf(),
) {
    fun owns(slot: String, option: Int) = option == 0 || (owned[slot] ?: 0) and (1 shl option) != 0

    val tune: Tune? get() = tunes.getOrNull(activeTune)
    val tuneName: String get() = tune?.name ?: "STOCK"

    fun bestRun(tune: String, quarter: Boolean) = runs.filter { it.tune == tune && it.quarter == quarter }.minByOrNull { it.et }

    fun logRun(r: RunRecord) {
        runs.add(r)
        while (runs.size > MAX_RUNS) runs.removeAt(0)
    }

    companion object {
        const val MAX_RUNS = 40
        const val DYNO_STEP_RPM = 100f
    }
}

/** One test-n-tune pass. */
data class RunRecord(val tune: String, val quarter: Boolean, val et: Float, val sixty: Float, val trapMph: Float, val rt: Float)

/**
 * The local save: cash and the garage. Written atomically (temp file, then move) so a crash
 * mid-write never corrupts it. The cloud copy (later) syncs from this; this stays the fallback.
 */
class SaveGame(private val file: FileHandle) {
    var cash = 0L
    val cars = ArrayList<OwnedCar>()
    var selected: String? = null
    /** Career rivals beaten at least once (ids from career.json). */
    val beaten = LinkedHashSet<String>()
    var nextUid = 1
    /** Client time of the last career race (the ledger spaces them out). */
    var lastRaceT = 0L
    /** Ledger entries applied here but not yet confirmed by the server. */
    val pending = ArrayList<Entry>()
    /** How many entries the server has accepted for this account (0 = never synced). */
    var serverSeq = 0L
    /** A save from before the ledger existed: its first sync imports it as a snapshot. */
    var legacy = false

    val isNew get() = cars.isEmpty()
    fun selectedCar(): OwnedCar? = cars.firstOrNull { it.uid == selected } ?: cars.firstOrNull()
    fun owns(carId: String) = cars.any { it.carId == carId }

    fun addCar(carId: String, paint: Int): OwnedCar {
        val car = OwnedCar("c${nextUid++}", carId, paint)
        cars.add(car)
        if (selected == null) selected = car.uid
        return car
    }

    fun removeCar(uid: String) {
        cars.removeAll { it.uid == uid }
        if (selected == uid) selected = cars.firstOrNull()?.uid
    }

    fun write() {
        val root = JsonValue(JsonValue.ValueType.`object`)
        root.addChild("version", JsonValue(VERSION))
        root.addChild("lastRaceT", JsonValue(lastRaceT))
        root.addChild("serverSeq", JsonValue(serverSeq))
        root.addChild("legacy", JsonValue(legacy))
        root.addChild("pending", JsonValue(JsonValue.ValueType.array).also { a -> pending.forEach { a.addChild(it.toJson()) } })
        root.addChild("cash", JsonValue(cash))
        root.addChild("nextUid", JsonValue(nextUid.toLong()))
        selected?.let { root.addChild("selected", JsonValue(it)) }
        val list = JsonValue(JsonValue.ValueType.array)
        for (c in cars) {
            val o = JsonValue(JsonValue.ValueType.`object`)
            o.addChild("uid", JsonValue(c.uid))
            o.addChild("car", JsonValue(c.carId))
            o.addChild("paint", JsonValue(c.paint.toLong()))
            val parts = JsonValue(JsonValue.ValueType.`object`)
            for ((k, v) in c.parts) parts.addChild(k, JsonValue(v.toLong()))
            o.addChild("parts", parts)
            o.addChild("owned", intMap(c.owned))
            val cos = JsonValue(JsonValue.ValueType.`object`)
            for ((k, v) in c.cosmetics) cos.addChild(k, JsonValue(v.toLong()))
            o.addChild("cosmetics", cos)
            val tunes = JsonValue(JsonValue.ValueType.array)
            for (t in c.tunes) tunes.addChild(tuneJson(t))
            o.addChild("tunes", tunes)
            o.addChild("activeTune", JsonValue(c.activeTune.toLong()))
            val runs = JsonValue(JsonValue.ValueType.array)
            for (r in c.runs) {
                val ro = JsonValue(JsonValue.ValueType.`object`)
                ro.addChild("tune", JsonValue(r.tune))
                ro.addChild("q", JsonValue(r.quarter))
                ro.addChild("et", JsonValue(r.et.toDouble()))
                ro.addChild("s60", JsonValue(r.sixty.toDouble()))
                ro.addChild("mph", JsonValue(r.trapMph.toDouble()))
                ro.addChild("rt", JsonValue(r.rt.toDouble()))
                runs.addChild(ro)
            }
            o.addChild("runs", runs)
            c.dyno?.let { d ->
                val a = JsonValue(JsonValue.ValueType.array)
                d.forEach { a.addChild(JsonValue(it.toDouble())) }
                o.addChild("dyno", a)
            }
            list.addChild(o)
        }
        root.addChild("cars", list)
        val b = JsonValue(JsonValue.ValueType.array)
        beaten.forEach { b.addChild(JsonValue(it)) }
        root.addChild("beaten", b)
        val tmp = file.sibling(file.name() + ".tmp")
        tmp.writeString(root.toJson(JsonWriter.OutputType.json), false)
        tmp.moveTo(file)
        lastJson = root
    }

    /** The JSON of the last write (the cloud keeps it as the profile: tunes, runs, dyno). */
    var lastJson: JsonValue? = null
        private set

    /** Only cash, cars, beaten and counters: what the ledger decides. */
    fun economicState(): JsonValue {
        val o = JsonValue(JsonValue.ValueType.`object`)
        o.addChild("cash", JsonValue(cash))
        o.addChild("nextUid", JsonValue(nextUid.toLong()))
        o.addChild("lastRaceT", JsonValue(lastRaceT))
        o.addChild("beaten", JsonValue(JsonValue.ValueType.array).also { a -> beaten.forEach { a.addChild(JsonValue(it)) } })
        o.addChild("cars", JsonValue(JsonValue.ValueType.array).also { a ->
            for (c in cars) a.addChild(JsonValue(JsonValue.ValueType.`object`).also { co ->
                co.addChild("uid", JsonValue(c.uid))
                co.addChild("car", JsonValue(c.carId))
                co.addChild("paint", JsonValue(c.paint.toLong()))
                co.addChild("parts", JsonValue(JsonValue.ValueType.`object`).also { po -> for ((k, v) in c.parts.toSortedMap()) po.addChild(k, JsonValue(v.toLong())) })
                co.addChild("owned", intMap(c.owned))
                co.addChild("cosmetics", JsonValue(JsonValue.ValueType.`object`).also { po -> for ((k, v) in c.cosmetics.toSortedMap()) po.addChild(k, JsonValue(v.toLong())) })
            })
        })
        return o
    }

    /**
     * Takes the server's word for cash, cars and progress. Cars keep their local tunes, runs and
     * dyno sheet when the uid still exists here, or take them from [profile] (another device's
     * save) when it doesn't.
     */
    fun adopt(state: JsonValue, profile: JsonValue?) {
        val old = cars.associateBy { it.uid }
        val fromProfile = profile?.let { load(file, it).cars.associateBy { c -> c.uid } } ?: emptyMap()
        cash = state.getLong("cash")
        nextUid = state.getInt("nextUid")
        lastRaceT = state.getLong("lastRaceT", 0)
        beaten.clear()
        state.get("beaten")?.asStringArray()?.let { beaten.addAll(it) }
        cars.clear()
        state.get("cars")?.forEach { o ->
            val uid = o.getString("uid")
            val keep = old[uid] ?: fromProfile[uid]
            val parts = mutableMapOf<String, Int>()
            o.get("parts")?.forEach { parts[it.name] = it.asInt() }
            val cos = mutableMapOf<String, Int>()
            o.get("cosmetics")?.forEach { cos[it.name] = it.asInt() }
            cars.add(OwnedCar(uid, o.getString("car"), o.getInt("paint"), parts, cos,
                keep?.tunes ?: mutableListOf(), keep?.activeTune ?: -1, keep?.runs ?: mutableListOf(), keep?.dyno, ints(o.get("owned"))))
        }
        if (cars.none { it.uid == selected }) selected = fromProfile.keys.firstOrNull { k -> cars.any { it.uid == k } } ?: cars.firstOrNull()?.uid
    }

    /** Set when the save predates the current parts catalog (PixelStrip converts it after loading). */
    var oldParts = false

    companion object {
        private fun intMap(m: Map<String, Int>) =
            JsonValue(JsonValue.ValueType.`object`).also { po -> for ((k, v) in m.toSortedMap()) po.addChild(k, JsonValue(v.toLong())) }

        private fun ints(j: JsonValue?): MutableMap<String, Int> = j?.associate { it.name to it.asInt() }?.toMutableMap() ?: mutableMapOf()

        fun tuneJson(t: Tune): JsonValue {
            val o = JsonValue(JsonValue.ValueType.`object`)
            o.addChild("name", JsonValue(t.name))
            t.gears?.let { g ->
                val a = JsonValue(JsonValue.ValueType.array)
                g.forEach { a.addChild(JsonValue(it.toDouble())) }
                o.addChild("gears", a)
            }
            t.finalDrive?.let { o.addChild("final", JsonValue(it.toDouble())) }
            o.addChild("launch", JsonValue(t.launchRpm.toDouble()))
            o.addChild("nosGear", JsonValue(t.nitrousGear.toLong()))
            o.addChild("psi", JsonValue(t.tirePressure.toDouble()))
            t.boostPsi?.let { o.addChild("boost", JsonValue(it.toDouble())) }
            t.boostByGear?.let { g -> o.addChild("boostByGear", JsonValue(JsonValue.ValueType.array).also { a -> g.forEach { a.addChild(JsonValue(it.toDouble())) } }) }
            t.scPsi?.let { o.addChild("sc", JsonValue(it.toDouble())) }
            o.addChild("timing", JsonValue(t.timing.toDouble()))
            t.afr?.let { o.addChild("afr", JsonValue(it.toDouble())) }
            t.limiter?.let { o.addChild("limiter", JsonValue(it.toDouble())) }
            t.nitrousShot?.let { o.addChild("shot", JsonValue(it.toDouble())) }
            o.addChild("ramp", JsonValue(t.nitrousRamp.toDouble()))
            o.addChild("ride", JsonValue(t.rideHeight.toDouble()))
            o.addChild("shock", JsonValue(t.shockRate.toDouble()))
            return o
        }

        fun tuneFrom(o: JsonValue) = Tune(
            name = o.getString("name", "TUNE"),
            gears = o.get("gears")?.asFloatArray(),
            finalDrive = o.get("final")?.asFloat(),
            launchRpm = o.getFloat("launch", 0f),
            nitrousGear = o.getInt("nosGear", 0),
            tirePressure = o.getFloat("psi", 32f),
            boostPsi = o.get("boost")?.asFloat(),
            boostByGear = o.get("boostByGear")?.asFloatArray(),
            scPsi = o.get("sc")?.asFloat(),
            timing = o.getFloat("timing", 0f),
            afr = o.get("afr")?.asFloat(),
            limiter = o.get("limiter")?.asFloat(),
            nitrousShot = o.get("shot")?.asFloat(),
            nitrousRamp = o.getFloat("ramp", 0.6f),
            rideHeight = o.getFloat("ride", 0f),
            shockRate = o.getFloat("shock", 0.5f),
        )

        /** 2: the ledger. 3: the option-based parts catalog. */
        const val VERSION = 3L

        fun load(file: FileHandle): SaveGame {
            if (!file.exists()) return SaveGame(file)
            val root = runCatching { JsonReader().parse(file) }.getOrNull() ?: return SaveGame(file)
            return load(file, root)
        }

        fun load(file: FileHandle, root: JsonValue): SaveGame {
            val save = SaveGame(file)
            save.cash = root.getLong("cash", 0)
            save.lastRaceT = root.getLong("lastRaceT", 0)
            save.serverSeq = root.getLong("serverSeq", 0)
            root.get("pending")?.forEach { save.pending.add(Entry.from(it)) }
            save.nextUid = root.getInt("nextUid", 1)
            save.selected = root.getString("selected", null)
            root.get("beaten")?.asStringArray()?.let { save.beaten.addAll(it) }
            root.get("cars")?.forEach { o ->
                val parts = mutableMapOf<String, Int>()
                o.get("parts")?.forEach { parts[it.name] = it.asInt() }
                val cos = mutableMapOf<String, Int>()
                o.get("cosmetics")?.forEach { cos[it.name] = it.asInt() }
                val tunes = o.get("tunes")?.map { tuneFrom(it) }?.toMutableList() ?: mutableListOf()
                val runs = o.get("runs")?.map {
                    RunRecord(it.getString("tune"), it.getBoolean("q"), it.getFloat("et"), it.getFloat("s60"), it.getFloat("mph"), it.getFloat("rt", 0f))
                }?.toMutableList() ?: mutableListOf()
                save.cars.add(OwnedCar(o.getString("uid"), o.getString("car"), o.getInt("paint", 0), parts, cos, tunes,
                    o.getInt("activeTune", -1), runs, o.get("dyno")?.asFloatArray(), ints(o.get("owned"))))
            }
            // version 1 saves predate the ledger; nothing in them was ever checked by a server
            val version = root.getLong("version", 1)
            save.legacy = root.getBoolean("legacy", version < 2 && save.cars.isNotEmpty())
            save.oldParts = version < 3 && save.cars.isNotEmpty()
            return save
        }
    }
}

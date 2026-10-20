package io.github.novodyinparticular.pixelstrip.save

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.utils.JsonReader
import com.badlogic.gdx.utils.JsonValue
import com.badlogic.gdx.utils.JsonWriter
import io.github.novodyinparticular.pixelstrip.data.Tune

/**
 * A car in the player's garage. [parts]: performance slot -> tier (0 = stock). [cosmetics]:
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
) {
    val tune: Tune? get() = tunes.getOrNull(activeTune)
}

/**
 * The local save: cash and the garage. Written atomically (temp file, then move) so a crash
 * mid-write never corrupts it. The cloud copy (later) syncs from this; this stays the fallback.
 */
class SaveGame(private val file: FileHandle) {
    var cash = 0L
    val cars = ArrayList<OwnedCar>()
    var selected: String? = null
    private var nextUid = 1

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
        root.addChild("version", JsonValue(1L))
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
            val cos = JsonValue(JsonValue.ValueType.`object`)
            for ((k, v) in c.cosmetics) cos.addChild(k, JsonValue(v.toLong()))
            o.addChild("cosmetics", cos)
            val tunes = JsonValue(JsonValue.ValueType.array)
            for (t in c.tunes) tunes.addChild(tuneJson(t))
            o.addChild("tunes", tunes)
            o.addChild("activeTune", JsonValue(c.activeTune.toLong()))
            list.addChild(o)
        }
        root.addChild("cars", list)
        val tmp = file.sibling(file.name() + ".tmp")
        tmp.writeString(root.toJson(JsonWriter.OutputType.json), false)
        tmp.moveTo(file)
    }

    companion object {
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
            return o
        }

        fun tuneFrom(o: JsonValue) = Tune(
            name = o.getString("name", "TUNE"),
            gears = o.get("gears")?.asFloatArray(),
            finalDrive = o.get("final")?.asFloat(),
            launchRpm = o.getFloat("launch", 0f),
            nitrousGear = o.getInt("nosGear", 0),
            tirePressure = o.getFloat("psi", 32f),
        )

        fun load(file: FileHandle): SaveGame {
            val save = SaveGame(file)
            if (!file.exists()) return save
            val root = runCatching { JsonReader().parse(file) }.getOrNull() ?: return save
            save.cash = root.getLong("cash", 0)
            save.nextUid = root.getInt("nextUid", 1)
            save.selected = root.getString("selected", null)
            root.get("cars")?.forEach { o ->
                val parts = mutableMapOf<String, Int>()
                o.get("parts")?.forEach { parts[it.name] = it.asInt() }
                val cos = mutableMapOf<String, Int>()
                o.get("cosmetics")?.forEach { cos[it.name] = it.asInt() }
                val tunes = o.get("tunes")?.map { tuneFrom(it) }?.toMutableList() ?: mutableListOf()
                save.cars.add(OwnedCar(o.getString("uid"), o.getString("car"), o.getInt("paint", 0), parts, cos, tunes, o.getInt("activeTune", -1)))
            }
            return save
        }
    }
}

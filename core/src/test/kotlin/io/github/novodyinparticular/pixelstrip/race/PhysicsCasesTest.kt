package io.github.novodyinparticular.pixelstrip.race

import com.badlogic.gdx.utils.JsonReader
import com.badlogic.gdx.utils.JsonValue
import com.badlogic.gdx.utils.JsonWriter
import io.github.novodyinparticular.pixelstrip.data.CarDef
import io.github.novodyinparticular.pixelstrip.data.PartCatalog
import io.github.novodyinparticular.pixelstrip.data.Tune
import io.github.novodyinparticular.pixelstrip.data.Upgrades
import io.github.novodyinparticular.pixelstrip.save.SaveGame
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Writes shared/physics_cases.json: whole races (burnout, staging, launch, shifts, nitrous)
 * driven by a script and captured by RunRecorder, with the splits this physics produced. The
 * server's TypeScript port replays the same events and must land within 0.01 s
 * (server/functions/src/test/physics.test.ts). Fails if the file is stale; REGEN=1 rewrites it.
 */
class PhysicsCasesTest {
    private val catalog = PartCatalog(JsonReader().parse(File("data/parts.json").readText()))
    private fun car(id: String) = CarDef.parse(JsonReader().parse(File("data/cars/$id.json").readText()), JsonReader().parse(File("cars/$id/sprite.json").readText()))
    private val file = File("../shared/physics_cases.json")

    private class Case(val name: String, val car: String, val parts: Map<String, Int>, val tune: Tune?, val launchClock: Float = 0.35f,
                       val spray: Boolean = false, val quarter: Boolean = true, val trackGrip: Float = Vehicle.DEFAULT_PREP)

    private val supported = mapOf("internals" to 2, "fueltype" to 2, "fuel" to 3, "intercooler" to 2, "boostctl" to 1, "ecu" to 2)

    private val cases = listOf(
        Case("mammoth stock", "mammoth", emptyMap(), null),
        Case("kite stock, eighth mile", "kite", emptyMap(), null, quarter = false),
        Case("mammoth bolt-ons on slicks", "mammoth", mapOf("intake" to 2, "headers" to 2, "exhaust" to 2, "cams" to 2, "heads" to 1, "tires" to 4, "tubs" to 1), null),
        Case("mammoth turbo, launch control, timing", "mammoth", supported + mapOf("turbo" to 3, "tires" to 3, "tubs" to 1, "launch" to 1),
            Tune(boostPsi = 20f, launchRpm = 3600f, timing = 2f)),
        Case("joe's K-swap on nitrous", "kite_ef", supported + mapOf("swap" to 4, "turbo" to 3, "nitrous" to 2, "bottle" to 1, "gearbox" to 3),
            Tune(boostPsi = 25f, nitrousGear = 2)),
        Case("mammoth blower, manual spray", "mammoth", mapOf("supercharger" to 2, "internals" to 1, "fuel" to 2, "nitrous" to 2, "tires" to 2), null, spray = true),
        Case("squall stock, wet track", "squall", emptyMap(), null, trackGrip = 0.95f),
        Case("tsunami tuned gears and pressure", "tsunami", mapOf("gearbox" to 1, "final" to 4, "tires" to 2),
            Tune(gears = floatArrayOf(3.4f, 2.1f, 1.45f, 1.1f, 0.88f, 0.7f), finalDrive = 3.9f, tirePressure = 20f)),
        Case("spiral rotary with wheelie bars", "spiral_r", mapOf("wheeliebars" to 1, "springs" to 1, "shocks" to 2, "tires" to 2), Tune(shockRate = 0.9f)),
        Case("red light", "mammoth", emptyMap(), null, launchClock = -0.5f),
        Case("phantom stock: the converter builds boost on the brake", "phantom", emptyMap(), null),
        Case("kestrel 2JZ swap, ball-bearing turbo, test pipe, external gate", "kestrel",
            supported + mapOf("swap" to 6, "turbo" to 6, "downpipe" to 2, "wastegate" to 1, "tires" to 2), Tune(boostPsi = 24f)),
        Case("spiral R bridge port, ceramic seals, anti-lag", "spiral_r",
            supported + mapOf("ecu" to 3, "porting" to 2, "apexseals" to 2, "oilinject" to 1, "launch" to 2, "turbo" to 6, "tires" to 3, "tubs" to 1),
            Tune(boostPsi = 22f, launchRpm = 5200f)),
        Case("spiral SA 26B four-rotor", "spiral_sa", supported + mapOf("ecu" to 3, "oilinject" to 1, "swap" to 14, "tires" to 2), null),
        Case("halberd stock: a factory turbo spools off the line", "halberd_6", emptyMap(), null),
    )

    /** Drives one race the way a decent player would, with every input going through the recorder. */
    private fun run(c: Case): Pair<Vehicle, RunRecorder> {
        val def = Upgrades.apply(car(c.car), catalog, c.parts, emptyMap(), c.tune)
        val v = Vehicle(def).apply { trackGrip = c.trackGrip }
        val rec = RunRecorder()
        v.recorder = rec
        v.x = RaceScreen.BURNOUT_X
        val dist = Vehicle.SPLITS_M[if (c.quarter) 4 else 2]
        v.startBurnout()
        val target = DragMath.launchRpm(def, c.trackGrip)
        var heldSteps = 0
        var sprayed = false
        while (v.steps < 240 * 60) {
            when {
                v.steps < 520 -> v.throttle = 1f                                    // burnout
                v.steps < 640 -> v.throttle = 0f
                v.steps == 640 -> v.creepTo(Tree.STAGE_M)
                v.mode == Vehicle.Mode.CREEP -> v.throttle = 0f
                v.mode == Vehicle.Mode.HELD -> {
                    heldSteps++
                    v.throttle = if (v.rpm < target) 1f else 0f                    // hold the revs on the line
                    if (heldSteps == 300) {
                        v.throttle = 1f
                        v.launch(c.launchClock)
                    }
                }
                else -> {
                    v.throttle = if (v.hasLeft && v.x > dist + 0.5f) 0f else 1f
                    v.brake = if (v.hasLeft && v.x > dist + 6f) 1f else 0f
                    if (v.mode == Vehicle.Mode.DRIVE && v.gear < def.gears.size - 1 && v.rpm >= v.shiftPoints[v.gear]) v.shift()
                    if (c.spray && !sprayed && v.gear == 1 && v.hasLeft) sprayed = v.nitrous()
                }
            }
            v.step(1f / 240f)
            if (v.hasLeft && v.x > dist + 10f) break
        }
        return v to rec
    }

    private fun build(): JsonValue {
        val arr = JsonValue(JsonValue.ValueType.array)
        for (c in cases) {
            val (v, rec) = run(c)
            arr.addChild(JsonValue(JsonValue.ValueType.`object`).also { o ->
                o.addChild("name", JsonValue(c.name))
                o.addChild("car", JsonValue(c.car))
                o.addChild("parts", JsonValue(JsonValue.ValueType.`object`).also { p -> for ((k, n) in c.parts.toSortedMap()) p.addChild(k, JsonValue(n.toLong())) })
                c.tune?.let { o.addChild("tune", SaveGame.tuneJson(it)) }
                o.addChild("trackGrip", JsonValue(c.trackGrip.toDouble()))
                o.addChild("startX", JsonValue(RaceScreen.BURNOUT_X.toDouble()))
                o.addChild("finishM", JsonValue(Vehicle.SPLITS_M[if (c.quarter) 4 else 2].toDouble()))
                o.addChild("events", rec.toJson())
                o.addChild("expect", JsonValue(JsonValue.ValueType.`object`).also { e ->
                    e.addChild("leaveTime", JsonValue(v.leaveTime.toDouble()))
                    e.addChild("splits", JsonValue(JsonValue.ValueType.array).also { a -> v.splits.forEach { a.addChild(JsonValue(it.toDouble())) } })
                    e.addChild("splitSpeed", JsonValue(JsonValue.ValueType.array).also { a -> v.splitSpeed.forEach { a.addChild(JsonValue(it.toDouble())) } })
                    e.addChild("ratings", JsonValue(JsonValue.ValueType.array).also { a -> v.ratings.forEach { a.addChild(JsonValue(it.name)) } })
                })
            })
            println("%-40s RT %.3f  60' %.3f  1/8 %.3f  1/4 %.3f @ %.1f mph  %s".format(c.name, v.leaveTime, v.splits[0], v.splits[2], v.splits[4],
                v.splitSpeed[4] * 2.23694f, v.ratings))
        }
        return arr
    }

    @Test
    fun casesFileIsCurrent() {
        val fresh = build()
        if (System.getenv("REGEN") == "1" || !file.exists()) file.writeText(fresh.toJson(JsonWriter.OutputType.json) + "\n")
        val old = JsonReader().parse(file.readText())
        assertTrue(old.size == fresh.size, "shared/physics_cases.json is stale: REGEN=1 ./gradlew core:test --tests '*PhysicsCasesTest*'")
        for (i in 0 until fresh.size) {
            val a = fresh[i].get("expect").get("splits").asFloatArray()
            val b = old[i].get("expect").get("splits").asFloatArray()
            for (k in a.indices) assertTrue(abs(a[k] - b[k]) < 1e-4f,
                "${fresh[i].getString("name")}: physics changed since shared/physics_cases.json was written (REGEN=1, then check the server port)")
        }
    }
}

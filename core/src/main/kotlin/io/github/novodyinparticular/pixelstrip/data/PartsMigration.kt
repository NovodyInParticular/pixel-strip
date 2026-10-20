package io.github.novodyinparticular.pixelstrip.data

import com.badlogic.gdx.utils.JsonValue

/**
 * Converts builds from the old four-tier parts (parts.json v1) to the current options, using
 * assets/data/parts_migration.json (shared with tools/migrate_career.py).
 */
class PartsMigration(root: JsonValue) {
    private val map: Map<String, List<Map<String, Int>>> = root.filter { !it.name.startsWith("_") }.associate { slot ->
        slot.name to slot.map { tier -> tier.associate { it.name to it.asInt() } }
    }

    /**
     * [parts] old slot -> tier, plus the old cosmetic kit style; returns new slot -> option.
     * Parts the car can't take (RWD-only bits on other drivetrains, both blowers and turbos) drop out.
     */
    fun convert(parts: Map<String, Int>, kit: Int, catalog: PartCatalog, drivetrain: String, carId: String): MutableMap<String, Int> {
        val out = HashMap<String, Int>()
        fun merge(m: Map<String, Int>) { for ((k, v) in m) out[k] = maxOf(out[k] ?: 0, v) }
        for ((slot, tier) in parts) map[slot]?.getOrNull(tier - 1)?.let(::merge)
        if (kit > 0) map["kit"]?.getOrNull(kit - 1)?.let(::merge)
        if ((out["turbo"] ?: 0) > 0 && (out["supercharger"] ?: 0) > 0) out.remove("supercharger")
        out.entries.removeIf { (slot, opt) ->
            val s = catalog.slotOrNull(slot) ?: return@removeIf true
            opt !in s.options.indices || catalog.blocker(s, opt, out, drivetrain, carId) != null
        }
        return out
    }
}

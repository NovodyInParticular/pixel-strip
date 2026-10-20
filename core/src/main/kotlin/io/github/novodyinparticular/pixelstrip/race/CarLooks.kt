package io.github.novodyinparticular.pixelstrip.race

import com.badlogic.gdx.graphics.Color
import io.github.novodyinparticular.pixelstrip.data.PartCatalog
import io.github.novodyinparticular.pixelstrip.save.OwnedCar

/** What a car visibly has fitted: from performance parts (scoops, blowers, pipes) and cosmetics. */
data class CarLooks(
    val wing: String? = null,          // ducktail, gt, drag
    val kit: String? = null,           // street, race
    val scoop: Boolean = false,
    val blower: Boolean = false,
    val intercooler: Boolean = false,
    val sidepipe: Boolean = false,
    val rim: String? = null,           // null = the car's stock wheels
    val underglow: Color? = null,
) {
    companion object {
        val STOCK = CarLooks()

        private val GLOW = mapOf(
            "cyan" to Color(0.3f, 1f, 0.95f, 1f), "magenta" to Color(1f, 0.3f, 0.8f, 1f), "green" to Color(0.4f, 1f, 0.4f, 1f),
            "orange" to Color(1f, 0.6f, 0.2f, 1f), "purple" to Color(0.6f, 0.35f, 1f, 1f),
        )

        /** Spoke counts of the shared rim sheets (tools/art/car.py RIM_DEFAULTS). */
        val RIM_SPOKES = mapOf("magnum" to 5, "rally" to 6, "fivestar" to 5, "mesh" to 10, "turbine" to 10, "phone" to 6, "three" to 3, "sixspoke" to 6)

        fun of(car: OwnedCar, catalog: PartCatalog): CarLooks {
            val visuals = HashSet<String>()
            for (slot in catalog.slots) {
                val tier = car.parts[slot.id] ?: 0
                if (tier > 0) slot.tiers[tier - 1].visual?.let { visuals.add(it) }
            }
            fun style(id: String): String? {
                val c = catalog.cosmetics.firstOrNull { it.id == id } ?: return null
                val i = car.cosmetics[id] ?: 0
                return if (i <= 0) null else c.styles[i]
            }
            return CarLooks(
                wing = style("wing"),
                kit = style("kit"),
                scoop = "scoop" in visuals && "blower" !in visuals,
                blower = "blower" in visuals,
                intercooler = "intercooler" in visuals,
                sidepipe = "sidepipe" in visuals,
                rim = style("rims"),
                underglow = style("underglow")?.let { GLOW[it] },
            )
        }
    }
}

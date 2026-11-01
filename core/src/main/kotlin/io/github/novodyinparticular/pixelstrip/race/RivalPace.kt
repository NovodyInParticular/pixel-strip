package io.github.novodyinparticular.pixelstrip.race

import io.github.novodyinparticular.pixelstrip.PixelStrip
import io.github.novodyinparticular.pixelstrip.data.CarDef
import io.github.novodyinparticular.pixelstrip.data.Career

/**
 * Close racing: a career rival's engine is scaled (within [MIN]..[MAX]) so that its perfect pass
 * lands just behind the player's perfect pass, or just ahead for a boss. Races then come down to
 * the launch and the shifts, but a car far out of its depth still can't be saved: a stock car
 * against a built one stays slower, and a built car still walks away from a weak rival.
 */
object RivalPace {
    const val MIN = 0.85f
    const val MAX = 1.2f

    private val cache = HashMap<List<Any>, Float>()

    /** The torque multiplier for [rival]'s car against the player's selected car (1 with close racing off). */
    fun scale(game: PixelStrip, rival: Career.Rival): Float {
        if (!game.settings.closeRacing) return 1f
        val mine = game.save.selectedCar() ?: return 1f
        return scale(game.effective(mine), game.effective(rival.asCar()), game.career.distanceOf(rival), rival.skill, rival.boss)
    }

    /**
     * How far behind the player's perfect ET the rival aims: a weak rival leaves ~2.5% on the table,
     * a sharp one under 1%; a boss sits a touch ahead, so beating one takes a clean run.
     */
    fun margin(skill: Float, boss: Boolean) = if (boss) -0.006f else 0.025f - 0.018f * skill.coerceIn(0f, 1f)

    fun scale(player: CarDef, rival: CarDef, dist: Distance, skill: Float, boss: Boolean): Float =
        cache.getOrPut(listOf(player, rival, dist, skill, boss)) {
            val target = DragMath.perfectEt(player, dist) * (1f + margin(skill, boss))
            // ET falls as the scale rises: bisect, in steps of 0.005 so the ET cache stays small
            if (DragMath.perfectEt(rival, dist, MAX) >= target) return@getOrPut MAX
            if (DragMath.perfectEt(rival, dist, MIN) <= target) return@getOrPut MIN
            var lo = MIN
            var hi = MAX
            repeat(7) {
                val mid = Math.round((lo + hi) / 2f * 200f) / 200f
                if (DragMath.perfectEt(rival, dist, mid) > target) lo = mid else hi = mid
            }
            hi
        }
}

package io.github.novodyinparticular.pixelstrip.race

import io.github.novodyinparticular.pixelstrip.PixelStrip
import io.github.novodyinparticular.pixelstrip.data.CarDef
import io.github.novodyinparticular.pixelstrip.data.Career

/**
 * Close racing: a career rival's engine is scaled (within [MIN]..[Difficulty.maxScale]) so that its perfect pass
 * lands just behind the player's perfect pass, or just ahead for a boss. Races then come down to
 * the launch and the shifts, but a car far out of its depth still can't be saved: a stock car
 * against a built one stays slower, and a built car still walks away from a weak rival.
 */
object RivalPace {
    const val MIN = 0.85f

    private val cache = HashMap<List<Any>, Float>()

    /** The torque multiplier for [rival]'s car against the player's selected car (1 with close racing off). */
    fun scale(game: PixelStrip, rival: Career.Rival): Float {
        if (!game.settings.closeRacing) return 1f
        val mine = game.save.selectedCar() ?: return 1f
        return scale(game.effective(mine), game.effective(rival.asCar()), game.career.distanceOf(rival), rival.skill, rival.boss, game.settings.difficulty)
    }

    /**
     * How far behind the player's perfect ET the rival aims (negative: ahead), set by the
     * [Difficulty]: on EASY a weak rival leaves ~2.5% on the table; from HARD on most rivals
     * are a touch quicker than the player's own perfect pass, so it takes a sharp launch to win.
     */
    fun margin(skill: Float, boss: Boolean, difficulty: Difficulty = Difficulty.DEFAULT) = difficulty.marginFor(skill, boss)

    fun scale(player: CarDef, rival: CarDef, dist: Distance, skill: Float, boss: Boolean, difficulty: Difficulty = Difficulty.DEFAULT): Float =
        cache.getOrPut(listOf(player, rival, dist, skill, boss, difficulty)) {
            val max = difficulty.maxScale
            val target = DragMath.perfectEt(player, dist) * (1f + margin(skill, boss, difficulty))
            // ET falls as the scale rises: bisect, in steps of 0.005 so the ET cache stays small
            // a traction-limited car gains nothing from more torque past a point (it just spins):
            // when even the most it may get misses the target, take the quickest scale on offer
            if (DragMath.perfectEt(rival, dist, max) >= target) return@getOrPut bestScale(rival, dist, max)
            if (DragMath.perfectEt(rival, dist, MIN) <= target) return@getOrPut MIN
            var lo = MIN
            var hi = max
            repeat(7) {
                val mid = Math.round((lo + hi) / 2f * 200f) / 200f
                if (DragMath.perfectEt(rival, dist, mid) > target) lo = mid else hi = mid
            }
            hi
        }

    private fun bestScale(rival: CarDef, dist: Distance, max: Float): Float {
        var best = max
        var bestEt = DragMath.perfectEt(rival, dist, max)
        var s = 1f
        while (s < max) {
            val et = DragMath.perfectEt(rival, dist, s)
            if (et < bestEt - 0.002f) {
                best = s
                bestEt = et
            }
            s = Math.round((s + 0.05f) * 200f) / 200f
        }
        return best
    }
}

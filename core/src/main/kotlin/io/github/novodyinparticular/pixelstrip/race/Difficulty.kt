package io.github.novodyinparticular.pixelstrip.race

import com.badlogic.gdx.math.MathUtils

/**
 * How hard career rivals push. Close racing paces a rival's engine to the player's build; the
 * difficulty decides where that pace lands (behind or ahead of the player's own perfect pass),
 * how sharp the rival drives, and how far the pacing may lift a rival that is out of its depth.
 *
 * [margin] is how far behind the player's perfect ET a weak rival aims (a sharp one aims
 * [sharpGain] closer); negative means ahead. [bossMargin] is the same for bosses.
 * [skillFloor] lifts every rival's driving skill: skill = floor + (1 - floor) * rival skill.
 * [reactSlow]/[reactFast] are a rival's reaction to the green at skill 0 and 1.
 * [errorScale] scales launch and shift mistakes. [maxScale] is the most the pacing may add.
 */
enum class Difficulty(
    val label: String,
    val margin: Float,
    val sharpGain: Float,
    val bossMargin: Float,
    val skillFloor: Float,
    val reactSlow: Float,
    val reactFast: Float,
    val errorScale: Float,
    val maxScale: Float,
) {
    EASY("EASY", 0.025f, 0.018f, -0.006f, 0.5f, 0.42f, 0.17f, 1f, 1.2f),
    NORMAL("NORMAL", 0.015f, 0.015f, -0.008f, 0.6f, 0.34f, 0.15f, 0.9f, 1.3f),
    HARD("HARD", 0.009f, 0.012f, -0.012f, 0.7f, 0.28f, 0.13f, 0.75f, 1.45f),
    INSANE("INSANE", 0.005f, 0.010f, -0.016f, 0.8f, 0.25f, 0.12f, 0.6f, 1.6f);

    /** The rival's ET target relative to the player's perfect pass. */
    fun marginFor(skill: Float, boss: Boolean) = if (boss) bossMargin else margin - sharpGain * skill.coerceIn(0f, 1f)

    fun skill(rivalSkill: Float) = skillFloor + (1f - skillFloor) * rivalSkill.coerceIn(0f, 1f)

    fun reaction(skill: Float) = MathUtils.lerp(reactSlow, reactFast, skill.coerceIn(0f, 1f))

    fun next() = entries[(ordinal + 1) % entries.size]

    companion object {
        val DEFAULT = HARD
        fun of(name: String?) = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

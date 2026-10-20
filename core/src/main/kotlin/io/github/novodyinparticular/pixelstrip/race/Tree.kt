package io.github.novodyinparticular.pixelstrip.race

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.utils.JsonReader
import io.github.novodyinparticular.pixelstrip.gfx.Art
import kotlin.math.floor

/**
 * The Christmas tree. Pre-stage and stage bulbs follow each car's front bumper through the
 * beams; once both lanes are staged the ambers count down (sportsman: one at a time, 0.5 s
 * apart; pro: all three, 0.4 s) to green. Leaving before green lights that lane's red.
 */
class Tree(art: Art, private val pro: Boolean = false) {
    private val tex: Texture = art.tex("fx/tree.png")
    private val bulbs: Texture = art.tex("fx/bulbs.png")
    private val layout = JsonReader().parse(Gdx.files.internal("data/tree.json"))
    private val cell = layout.getInt("cell")
    private val cols = layout.get("columns").asIntArray()
    private val rows = layout.get("rows").map { Triple(it.getString("kind"), it.getInt("y"), it.getInt("size")) }
    private val sprite = layout.get("sprites")

    val prestaged = BooleanArray(2)
    val staged = BooleanArray(2)
    val red = BooleanArray(2)
    var running = false
        private set
    /** Seconds since the sequence started; green comes at [greenAt]. */
    var clock = 0f
        private set
    var greenAt = 0f
        private set
    private var startDelay = 0f
    val green get() = running && clock >= greenAt

    fun reset() {
        running = false
        clock = 0f
        for (i in 0..1) {
            prestaged[i] = false
            staged[i] = false
            red[i] = false
        }
    }

    /** Lane 0 = far, 1 = near. Bumper position in metres from the start line. */
    fun beams(lane: Int, frontX: Float) {
        // Both bulbs go dark once the car has rolled through and out of the beams
        val gone = frontX > STAGE_M + 0.3f
        prestaged[lane] = frontX >= PRESTAGE_M && !gone
        staged[lane] = frontX >= STAGE_M && !gone
    }

    fun start() {
        if (running) return
        running = true
        clock = 0f
        startDelay = MathUtils.random(0.7f, 1.6f)        // the starter makes you wait
        greenAt = startDelay + if (pro) 0.4f else 1.5f
    }

    fun update(dt: Float) {
        if (running) clock += dt
    }

    /** Race clock: seconds since green (negative before it). */
    val raceClock get() = clock - greenAt

    fun foul(lane: Int) {
        red[lane] = true
    }

    private fun ambersLit(): Int {
        if (!running || clock < startDelay) return 0
        if (pro) return 3
        return (((clock - startDelay) / 0.5f).toInt() + 1).coerceAtMost(3)
    }

    private fun lit(kind: String, lane: Int, amberIndex: Int): Boolean = when (kind) {
        "prestage" -> prestaged[lane]
        "stage" -> staged[lane]
        "amber" -> !green && ambersLit() > amberIndex
        "green" -> green && !red[lane]
        "red" -> red[lane]
        else -> false
    }

    /** (x, bottomY) of the housing's bottom-left in framebuffer pixels. */
    fun draw(batch: SpriteBatch, x: Float, y: Float, bulbsOnly: Boolean = false) {
        val bx = floor(x)
        val by = floor(y)
        if (!bulbsOnly) batch.draw(tex, bx, by)
        var amber = 0
        for ((kind, ry, _) in rows) {
            for (lane in 0..1) {
                if (!lit(kind, lane, amber)) continue
                val idx = sprite.getInt(kind)
                val cx = bx + cols[lane] - cell / 2
                val cy = by + tex.height - ry - cell / 2 - 1
                batch.draw(bulbs, cx, cy, idx * cell, 0, cell, cell)
            }
            if (kind == "amber") amber++
        }
    }

    val width get() = tex.width

    companion object {
        /** Pre-stage beam 7 inches before the stage beam. */
        const val PRESTAGE_M = -0.18f
        const val STAGE_M = -0.02f
    }
}

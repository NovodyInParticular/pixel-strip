package io.github.novodyinparticular.pixelstrip.race

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import io.github.novodyinparticular.pixelstrip.gfx.PixelFont
import kotlin.math.floor
import kotlin.math.sin

/**
 * The nitrous button's face: a glowing bottle, a meter that drains while the shot runs, a pip per
 * charge, a pulse when one is ready, and a dimmed, empty look once the last one is used.
 * One tap fires a whole shot ([Vehicle.nitrous]); the button only draws, the race decides.
 */
class NosButton(private val font: PixelFont, private val white: Texture) {
    enum class State { STANDBY, READY, SPRAYING, EMPTY }

    fun state(car: Vehicle, armed: Boolean) = when {
        car.nitrousActive -> State.SPRAYING
        car.nitrousCharges <= 0 -> State.EMPTY
        armed -> State.READY
        else -> State.STANDBY
    }

    /** 1 when a charge is waiting, draining to 0 over the shot, 0 when empty. */
    fun fill(car: Vehicle) = when {
        car.nitrousActive -> (car.nitrousTime / car.def.nitrous.seconds).coerceIn(0f, 1f)
        car.nitrousCharges > 0 -> 1f
        else -> 0f
    }

    private fun pulse(t: Float) = 0.5f + 0.5f * sin(t * 6.5f)

    fun draw(batch: SpriteBatch, b: Controls.Button, car: Vehicle, armed: Boolean, t: Float) {
        val s = state(car, armed)
        val dim = s == State.EMPTY || s == State.STANDBY
        val sink = floor(b.pressAnim * 2f)
        val x = floor(b.x)
        val y = floor(b.y)
        val face = if (dim) DIM_FACE else FACE
        val dark = Color(face).mul(0.45f, 0.45f, 0.55f, 1f)
        val lit = Color(face).lerp(Color.WHITE, 0.45f)
        val a = if (s == State.EMPTY) 0.55f else 0.85f
        batch.setColor(dark.r, dark.g, dark.b, a)                                   // bottom lip
        batch.draw(white, x + 1f, y, b.w - 2f, b.h)
        batch.setColor(face.r, face.g, face.b, a)
        batch.draw(white, x, y + 2f - sink, b.w, b.h - 2f)
        batch.setColor(lit.r, lit.g, lit.b, a)
        batch.draw(white, x + 1f, y + b.h - 1f - sink, b.w - 2f, 1f)
        batch.setColor(dark.r, dark.g, dark.b, a)
        batch.draw(white, x, y + 2f - sink, 1f, b.h - 3f)
        batch.draw(white, x + b.w - 1f, y + 2f - sink, 1f, b.h - 3f)

        // the bottle, centred in the left half
        val by = y + 14f - sink
        val bx = x + 14f
        drawBottle(batch, bx, by, b.h - 26f, s, t)

        // meter on the right: drains during the shot
        val mx = x + b.w - 22f
        val mh = b.h - 26f
        batch.setColor(0.05f, 0.04f, 0.12f, 0.9f)
        batch.draw(white, mx, by, 10f, mh)
        val f = fill(car)
        val level = floor((mh - 2f) * f)
        if (level > 0f) {
            val c = when (s) {
                State.SPRAYING -> SPRAY
                State.READY -> Color(NOS).lerp(Color.WHITE, 0.35f * pulse(t))
                else -> DIM_GAS
            }
            batch.setColor(c)
            batch.draw(white, mx + 1f, by + 1f, 8f, level)
            batch.setColor(1f, 1f, 1f, 0.5f)
            batch.draw(white, mx + 2f, by + 1f, 1f, level)                         // glassy edge
        }
        batch.color = Color.WHITE

        // charges left, one pip each
        val total = car.def.nitrous.charges
        val pipW = 8f
        val pipsX = x + (b.w - total * (pipW + 2f) + 2f) / 2f
        for (i in 0 until total) {
            val on = i < car.nitrousCharges
            batch.color = if (on) (when (s) { State.READY -> SPRAY; State.SPRAYING -> NOS; else -> DIM_GAS }) else SPENT
            batch.draw(white, pipsX + i * (pipW + 2f), y + b.h - 9f - sink, pipW, 4f)
        }
        batch.color = Color.WHITE

        val label = when (s) {
            State.SPRAYING -> "SPRAY"
            State.EMPTY -> "EMPTY"
            else -> "NOS"
        }
        val lc = when (s) {
            State.EMPTY, State.STANDBY -> DIM_TEXT
            State.SPRAYING -> SPRAY
            State.READY -> Color.WHITE
        }
        font.drawCentered(batch, label, x + b.w / 2f, y + 4f - sink, lc)
    }

    private fun drawBottle(batch: SpriteBatch, x: Float, y: Float, h: Float, s: State, t: Float) {
        val body = when (s) {
            State.EMPTY -> SPENT
            State.STANDBY -> DIM_GAS
            State.READY -> Color(NOS).lerp(Color.WHITE, 0.25f * pulse(t))
            State.SPRAYING -> SPRAY
        }
        val bodyH = h - 10f
        val w = 18f
        batch.color = Color(body).mul(0.55f, 0.55f, 0.7f, 1f)                     // shaded side
        batch.draw(white, x, y + 2f, w, bodyH - 4f)
        batch.draw(white, x + 2f, y, w - 4f, bodyH)
        batch.color = body
        batch.draw(white, x + 2f, y + 2f, w - 7f, bodyH - 4f)
        batch.setColor(1f, 1f, 1f, if (s == State.EMPTY) 0.2f else 0.6f)            // highlight streak
        batch.draw(white, x + 4f, y + 4f, 2f, bodyH - 10f)
        batch.color = LABEL                                                          // white label band
        batch.draw(white, x + 1f, y + bodyH * 0.35f, w - 2f, 4f)
        batch.color = Color(body).mul(0.55f, 0.55f, 0.7f, 1f)                     // shoulder and neck
        batch.draw(white, x + 4f, y + bodyH, w - 8f, 2f)
        batch.draw(white, x + 7f, y + bodyH + 2f, 4f, 4f)
        batch.color = VALVE                                                          // valve and knob
        batch.draw(white, x + 5f, y + bodyH + 6f, 8f, 2f)
        batch.draw(white, x + 13f, y + bodyH + 5f, 3f, 2f)
        batch.color = Color.WHITE
    }

    /** Bloom pass: the bottle and meter glow while ready (pulsing) and burn while spraying. */
    fun drawGlow(batch: SpriteBatch, b: Controls.Button, car: Vehicle, armed: Boolean, t: Float) {
        val s = state(car, armed)
        val k = when (s) {
            State.SPRAYING -> 0.45f
            State.READY -> 0.25f + 0.45f * pulse(t)
            else -> 0f
        }
        if (k <= 0f) return
        val c = if (s == State.SPRAYING) SPRAY else NOS
        batch.setColor(c.r, c.g, c.b, k)
        batch.draw(white, floor(b.x) + 10f, floor(b.y) + 10f, 26f, b.h - 18f)
        val mh = (b.h - 28f) * fill(car)
        batch.draw(white, floor(b.x + b.w) - 23f, floor(b.y) + 13f, 12f, mh)
        if (s == State.SPRAYING) {
            batch.setColor(c.r, c.g, c.b, 0.12f)
            batch.draw(white, b.x - 2f, b.y, b.w + 4f, b.h + 2f)
        }
        batch.color = Color.WHITE
    }

    companion object {
        private val FACE = Color(0.12f, 0.22f, 0.62f, 1f)
        private val DIM_FACE = Color(0.16f, 0.17f, 0.26f, 1f)
        private val NOS = Color(0.3f, 0.6f, 1f, 1f)
        private val SPRAY = Color(0.42f, 0.78f, 1f, 1f)
        private val DIM_GAS = Color(0.25f, 0.33f, 0.52f, 1f)
        private val SPENT = Color(0.2f, 0.2f, 0.26f, 1f)
        private val DIM_TEXT = Color(0.55f, 0.58f, 0.7f, 1f)
        private val LABEL = Color(0.88f, 0.9f, 0.96f, 1f)
        private val VALVE = Color(0.72f, 0.74f, 0.8f, 1f)
    }
}

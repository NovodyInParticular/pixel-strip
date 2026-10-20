package io.github.novodyinparticular.pixelstrip.race

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import io.github.novodyinparticular.pixelstrip.gfx.PixelFont
import io.github.novodyinparticular.pixelstrip.gfx.PostFx
import kotlin.math.floor

/** Thumb-sized on-screen buttons, multi-touch, with keyboard fallbacks for the desktop build. */
class Controls(private val post: PostFx, private val font: PixelFont, private val white: Texture) {
    class Button(val keys: IntArray, var label: String, val face: Color, val big: Boolean = false) {
        var x = 0f
        var y = 0f
        var w = 0f
        var h = 0f
        var visible = true
        var down = false
            internal set
        var justPressed = false
            internal set
        /** Forced by the autoplay driver (desktop screenshots). */
        var forced = false
        internal var pressAnim = 0f

        fun contains(px: Float, py: Float) = px >= x - 4f && px <= x + w + 4f && py >= y - 4f && py <= y + h + 4f
    }

    val buttons = ArrayList<Button>()

    fun add(b: Button): Button = b.also { buttons.add(it) }

    fun update(dt: Float) {
        for (b in buttons) {
            val was = b.down
            var now = b.forced
            if (b.visible) {
                for (i in 0 until 5) {
                    if (Gdx.input.isTouched(i) && b.contains(post.toBufferX(Gdx.input.getX(i)), post.toBufferY(Gdx.input.getY(i)))) now = true
                }
                for (k in b.keys) if (Gdx.input.isKeyPressed(k)) now = true
            }
            b.down = now && b.visible
            b.justPressed = b.down && !was
            b.pressAnim += ((if (b.down) 1f else 0f) - b.pressAnim) * minOf(1f, dt * 30f)
        }
    }

    fun draw(batch: SpriteBatch) {
        for (b in buttons) {
            if (!b.visible) continue
            val sink = floor(b.pressAnim * 2f)
            val x = floor(b.x)
            val y = floor(b.y)
            val dark = Color(b.face).mul(0.45f, 0.45f, 0.55f, 1f)
            val lit = Color(b.face).lerp(Color.WHITE, 0.45f)
            batch.setColor(dark.r, dark.g, dark.b, 0.9f)                       // bottom lip
            batch.draw(white, x + 1f, y, b.w - 2f, b.h)
            batch.setColor(b.face.r, b.face.g, b.face.b, if (b.down) 0.95f else 0.8f)
            batch.draw(white, x, y + 2f - sink, b.w, b.h - 2f)
            batch.setColor(lit.r, lit.g, lit.b, 0.95f)                           // lit top edge
            batch.draw(white, x + 1f, y + b.h - 1f - sink, b.w - 2f, 1f)
            batch.setColor(dark.r, dark.g, dark.b, 0.9f)
            batch.draw(white, x, y + 2f - sink, 1f, b.h - 3f)
            batch.draw(white, x + b.w - 1f, y + 2f - sink, 1f, b.h - 3f)
            batch.color = Color.WHITE
            val scale = if (b.big) 2 else 1
            font.drawCentered(batch, b.label, x + b.w / 2f, y + (b.h - 7f * scale) / 2f + 1f - sink, Color.WHITE, scale)
        }
    }

    /** Pressed buttons glow. */
    fun drawGlow(batch: SpriteBatch) {
        for (b in buttons) {
            if (!b.visible || b.pressAnim < 0.05f) continue
            batch.setColor(b.face.r, b.face.g, b.face.b, 0.7f * b.pressAnim)
            batch.draw(white, b.x - 2f, b.y, b.w + 4f, b.h + 2f)
        }
        batch.color = Color.WHITE
    }
}

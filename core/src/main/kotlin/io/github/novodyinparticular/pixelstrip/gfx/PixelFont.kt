package io.github.novodyinparticular.pixelstrip.gfx

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion

/** The chunky 5x7 font from tools/art/ui.py, drawn at integer scales with a hard drop shadow. */
class PixelFont(texture: Texture) {
    private val glyphs: Map<Char, TextureRegion> =
        ORDER.mapIndexed { i, c -> c to TextureRegion(texture, i * CELL_W, 0, 5, 7) }.toMap()

    fun width(text: String, scale: Int = 1) = if (text.isEmpty()) 0 else (text.length * CELL_W - 1) * scale

    /** Draws with the bottom-left of the first glyph at (x, y). */
    fun draw(batch: SpriteBatch, text: String, x: Float, y: Float, color: Color, scale: Int = 1, shadow: Color? = SHADOW) {
        if (shadow != null) {
            batch.color = shadow
            drawRaw(batch, text, x + scale, y - scale, scale)
        }
        batch.color = color
        drawRaw(batch, text, x, y, scale)
        batch.color = Color.WHITE
    }

    fun drawCentered(batch: SpriteBatch, text: String, cx: Float, y: Float, color: Color, scale: Int = 1, shadow: Color? = SHADOW) =
        draw(batch, text, (cx - width(text, scale) / 2f).toInt().toFloat(), y, color, scale, shadow)

    private fun drawRaw(batch: SpriteBatch, text: String, x: Float, y: Float, scale: Int) {
        // whole pixels only: a half-pixel offset smears glyphs through the sharp upscale
        var cx = kotlin.math.floor(x)
        val y = kotlin.math.floor(y)
        for (ch in text.uppercase()) {
            val g = glyphs[ch] ?: glyphs['?']!!
            if (ch != ' ') batch.draw(g, cx, y, 5f * scale, 7f * scale)
            cx += CELL_W * scale
        }
    }

    companion object {
        const val ORDER = " ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.,:;!?'\"-+/%$#()<>=_*&@"
        const val CELL_W = 6
        val SHADOW = Color(0.04f, 0.03f, 0.09f, 1f)
    }
}

package io.github.novodyinparticular.pixelstrip.gfx

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.JsonValue
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot

/**
 * A crew emblem: a shape filled with one colour, edged with a second, and an icon in the third
 * (server/functions/src/crews.ts validates the same ranges). Drawn at any size, pixel by pixel.
 */
class Emblem(val shape: Int, val colors: IntArray, val icon: Int) {
    val key get() = "$shape-${colors.joinToString(",")}-$icon"
    fun color(i: Int): Color = PALETTE[colors.getOrElse(i) { 0 }.coerceIn(0, PALETTE.size - 1)]

    fun toJson() = JsonValue(JsonValue.ValueType.`object`).also { o ->
        o.addChild("shape", JsonValue(shape.toLong()))
        o.addChild("colors", JsonValue(JsonValue.ValueType.array).also { a -> colors.forEach { a.addChild(JsonValue(it.toLong())) } })
        o.addChild("icon", JsonValue(icon.toLong()))
    }

    /** Is (u, v), from -1..1 with v up, inside the shape? */
    private fun inside(u: Float, v: Float): Boolean = when (shape) {
        0 -> abs(u) <= 0.9f && v <= 0.9f && (v >= -0.2f || abs(u) <= 0.9f * (v + 1.05f) / 0.85f)       // shield
        1 -> hypot(u, v) <= 0.95f                                                                      // circle
        2 -> abs(u) + abs(v) <= 1f                                                                     // diamond
        3 -> abs(v) <= 0.85f && abs(u) * 0.5f + abs(v) * 0.866f <= 0.9f                                // hexagon
        4 -> {                                                                                         // star
            val r = hypot(u, v)
            val a = atan2(v, u) + PI.toFloat() / 2f
            val k = cos(((a % (2 * PI.toFloat() / 5)) + 2 * PI.toFloat() / 5) % (2 * PI.toFloat() / 5) - PI.toFloat() / 5)
            r <= 0.98f * (0.5f + 0.5f * ((k - 0.809f) / 0.191f).coerceIn(0f, 1f))
        }
        else -> abs(u) <= 0.88f && abs(v) <= 0.88f && !(abs(u) > 0.7f && abs(v) > 0.7f)               // square, clipped corners
    }

    fun render(size: Int): Pixmap {
        val pm = Pixmap(size, size, Pixmap.Format.RGBA8888)
        pm.setColor(0f, 0f, 0f, 0f)
        pm.fill()
        val n = size
        fun at(x: Int, y: Int) = x in 0 until n && y in 0 until n && inside((x + 0.5f) / n * 2f - 1f, 1f - (y + 0.5f) / n * 2f)
        for (y in 0 until n) for (x in 0 until n) {
            if (!at(x, y)) continue
            val edge = !at(x - 1, y) || !at(x + 1, y) || !at(x, y - 1) || !at(x, y + 1)
            pm.setColor(if (edge) color(1) else color(0))
            pm.drawPixel(x, y)
        }
        // the icon: 7x7, scaled to about half the emblem and centred
        val glyph = ICONS[icon.coerceIn(0, ICONS.size - 1)]
        val scale = maxOf(1, (size * 0.55f / 7f).toInt())
        val ox = (n - 7 * scale) / 2
        val oy = (n - 7 * scale) / 2 + (if (shape == 0) -scale / 2 else 0)
        pm.setColor(color(2))
        for (gy in 0 until 7) for (gx in 0 until 7) {
            if (glyph[gy][gx] != '#') continue
            for (sy in 0 until scale) for (sx in 0 until scale) {
                val x = ox + gx * scale + sx
                val y = oy + gy * scale + sy
                if (at(x, y)) pm.drawPixel(x, y)
            }
        }
        return pm
    }

    companion object {
        const val SHAPES = 6
        const val ICON_COUNT = 12
        val SHAPE_NAMES = listOf("SHIELD", "CIRCLE", "DIAMOND", "HEX", "STAR", "BADGE")
        val ICON_NAMES = listOf("SKULL", "BOLT", "FLAME", "CROWN", "WINGS", "PISTON", "STAR", "WRENCH", "EYE", "HEART", "DICE", "SPADE")
        val PALETTE = listOf(
            Color(0.96f, 0.95f, 1f, 1f), Color(0.08f, 0.07f, 0.12f, 1f), Color(0.94f, 0.33f, 0.25f, 1f), Color(1f, 0.6f, 0.2f, 1f),
            Color(1f, 0.79f, 0.31f, 1f), Color(0.58f, 0.84f, 0.42f, 1f), Color(0.21f, 0.78f, 0.75f, 1f), Color(0.3f, 0.5f, 0.95f, 1f),
            Color(0.6f, 0.35f, 1f, 1f), Color(0.94f, 0.37f, 0.68f, 1f), Color(0.55f, 0.55f, 0.65f, 1f), Color(0.65f, 0.4f, 0.25f, 1f),
        )

        private val ICONS = listOf(
            listOf(" ##### ", "#######", "## # ##", "#######", " ## ## ", "  # #  ", "       "),   // skull
            listOf("   ### ", "  ###  ", " ###   ", "#######", "   ### ", "  ###  ", " ##    "),   // bolt
            listOf("   #   ", "  ##   ", "  ## # ", " ### # ", " ##### ", "#######", " ##### "),   // flame
            listOf("#  #  #", "## # ##", "#######", "#######", "#######", "       ", "#######"),   // crown
            listOf("#     #", "##   ##", "### ###", "#######", " ##### ", "  ###  ", "   #   "),   // wings
            listOf(" ##### ", " ##### ", " ##### ", "  ###  ", "   #   ", "  ###  ", "  ###  "),   // piston
            listOf("   #   ", "   #   ", "#######", " ##### ", "  ###  ", " ## ## ", "#     #"),   // star
            listOf(" ##    ", "### ## ", " ##### ", "  ###  ", "   ### ", "    ###", "     # "),   // wrench
            listOf("       ", " ##### ", "## # ##", "# ### #", "## # ##", " ##### ", "       "),   // eye
            listOf(" ## ## ", "#######", "#######", "#######", " ##### ", "  ###  ", "   #   "),   // heart
            listOf("#######", "# # # #", "#######", "#  #  #", "#######", "# # # #", "#######"),   // dice
            listOf("   #   ", "  ###  ", " ##### ", "#######", "#######", "   #   ", "  ###  "),   // spade
        )

        fun parse(j: JsonValue?): Emblem? {
            j ?: return null
            if (!j.isObject) return null
            return Emblem(j.getInt("shape", 0), j.get("colors")?.asIntArray() ?: intArrayOf(0, 1, 2), j.getInt("icon", 0))
        }
    }
}

/** Emblem textures, made once per emblem and size. */
class EmblemCache : Disposable {
    private val textures = HashMap<String, Texture>()

    fun texture(e: Emblem, size: Int): Texture = textures.getOrPut("${e.key}@$size") {
        val pm = e.render(size)
        Texture(pm).also { pm.dispose() }
    }

    override fun dispose() {
        textures.values.forEach { it.dispose() }
        textures.clear()
    }
}

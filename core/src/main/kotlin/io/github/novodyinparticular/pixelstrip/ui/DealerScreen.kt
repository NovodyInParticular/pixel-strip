package io.github.novodyinparticular.pixelstrip.ui

import io.github.novodyinparticular.pixelstrip.save.Entry
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.TextureRegion
import io.github.novodyinparticular.pixelstrip.PixelStrip
import io.github.novodyinparticular.pixelstrip.data.CarDef
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Chrome & Co. Motors: every car on a turntable under sweeping spotlights, sorted by price.
 * [starterMode] is the first-launch pick: three starter cars, free.
 */
open class DealerScreen(game: PixelStrip, private val starterMode: Boolean = false) : MenuScreen(game) {
    private val bg = art.tex("scenes/showroom.png")
    private val bgGlow = art.tex("scenes/showroom_glow.png")
    private val cone = TextureRegion(art.tex("fx/light_cone.png"))
    private val stage = CarStage()
    private val stock: List<CarDef> =
        if (starterMode) game.economy.starterCars.map { game.car(it) } else game.carIds.map { game.car(it) }.filter { it.dealer }.sortedBy { it.price }
    private var index = 0
    private var soldTimer = 0f

    private val prev = button("<", BTN_GREY, keys = intArrayOf(Input.Keys.LEFT)) { browse(-1) }
    private val next = button(">", BTN_GREY, keys = intArrayOf(Input.Keys.RIGHT)) { browse(1) }
    private val buy = button("BUY", BTN_RED, big = true, keys = intArrayOf(Input.Keys.ENTER)) { purchase() }
    private val back = button("BACK", BTN_GREY, keys = intArrayOf(Input.Keys.B)) { onBack() }

    private val def get() = stock[index]

    private fun browse(d: Int) {
        index = (index + d + stock.size) % stock.size
        flash = 0.2f
        flashColor.set(YELLOW)
    }

    private fun purchase() {
        val save = game.save
        if (starterMode) {
            if (game.commit(Entry("start", game.now(), car = def.id))) game.go(2) { GarageScreen(game) }
            return
        }
        if (!game.commit(Entry("buy", game.now(), car = def.id))) return
        save.selected = save.cars.last().uid
        save.write()
        soldTimer = 1.6f
        flash = 0.6f
        flashColor.set(YELLOW)
    }

    override fun onBack() {
        if (!starterMode) game.go(0) { GarageScreen(game) }
    }

    override fun layout(w: Int, h: Int) {
        prev.apply { x = w / 2f - 134f; y = 10f; this.w = 26f; this.h = 22f }
        next.apply { x = w / 2f + 108f; y = 10f; this.w = 26f; this.h = 22f }
        buy.apply { x = w - 112f; y = 8f; this.w = 104f; this.h = 40f }
        back.apply { x = 8f; y = 8f; this.w = 60f; this.h = 22f; visible = !starterMode }
        val owned = game.save.owns(def.id)
        buy.label = when {
            starterMode -> "TAKE IT"
            owned -> "OWNED"
            game.save.cash < def.price -> "SHORT"
            else -> "BUY"
        }
    }

    private fun bgX(w: Int) = floor((w - bg.width) / 2f)

    override fun update(dt: Float) {
        soldTimer = (soldTimer - dt).coerceAtLeast(0f)
        lights.clear()
        val w = post.width
        for (i in 0..1) {
            val (x, _) = spotEnd(w, i)
            lights.add(x, 60f, 50f, 0.45f, 0.5f, 0.55f, 150f)
        }
        lights.add(w / 2f, 20f, 20f, 0.2f, 0.7f, 0.7f, 140f)        // turntable rim light
        lights.add(bgX(w) + 320f, 262f, 60f, 1f, 0.8f, 0.3f, 260f)   // the sign
    }

    /** Where spotlight i hits the floor right now: they sweep across the car. */
    private fun spotEnd(w: Int, i: Int): Pair<Float, Float> {
        val a = sin(time * 0.6f + i * 2.4f) * 0.35f
        return w / 2f + (if (i == 0) -60f else 60f) + a * 140f to 40f
    }

    override fun drawScene(w: Int, h: Int) {
        batch.draw(bg, bgX(w), 0f)
        stage.draw(def, def.defaultPaint, w / 2f, GROUND, AMBIENT, lit = false)
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE)
        for (i in 0..1) {
            val sx = if (i == 0) w / 2f - 190f else w / 2f + 190f
            val (ex, ey) = spotEnd(w, i)
            val ang = Math.toDegrees(kotlin.math.atan2((ey - SPOT_Y).toDouble(), (ex - sx).toDouble())).toFloat()
            val len = kotlin.math.hypot(ex - sx, ey - SPOT_Y)
            batch.setColor(0.85f, 0.95f, 1f, 0.13f)
            batch.draw(cone, sx, SPOT_Y - 24f, 0f, 24f, 128f, 48f, len / 128f, 1.6f, ang)
        }
        batch.color = Color.WHITE
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
    }

    override fun drawGlow(w: Int, h: Int) {
        batch.draw(bgGlow, bgX(w), 0f)
        stage.drawGlow(def, w / 2f, GROUND)
        // chase lights running around the turntable rim: it reads as the platform turning
        for (k in 0 until 14) {
            val a = time * 1.2f + k * (6.283f / 14f)
            val x = w / 2f + cos(a) * 104f
            val y = GROUND - 2f + sin(a) * 8f
            val front = sin(a) < 0f
            batch.setColor(0.4f, 1f, 0.9f, if (front) 1f else 0.4f)
            batch.draw(white, floor(x) - 1f, floor(y) - 1f, 3f, 2f)
        }
        batch.color = Color.WHITE
    }

    override fun drawUi(w: Int, h: Int) {
        if (starterMode) font.drawCentered(batch, "PICK YOUR FIRST RIDE", w / 2f, h - 44f, YELLOW, 2)
        font.draw(batch, def.brand.uppercase(), 8f, h - 64f, CYAN)
        font.draw(batch, def.name.uppercase(), 8f, h - 80f, Color.WHITE, 2)
        font.draw(batch, def.year.toString(), 8f, h - 92f, SOFT)
        classBadge(def, 40f, h - 92f)
        val price = if (starterMode) "FREE" else "$" + "%,d".format(def.price)
        font.draw(batch, "PRICE", w - 8f - font.width("PRICE"), h - 64f, SOFT)
        font.draw(batch, price, w - 8f - font.width(price, 3), h - 88f, if (starterMode) GREEN else YELLOW, 3)
        if (!starterMode) {
            font.draw(batch, "CASH", w - 8f - font.width("CASH"), h - 104f, SOFT)
            val cashText = "$" + "%,d".format(game.save.cash)
            font.draw(batch, cashText, w - 8f - font.width(cashText), h - 114f, if (game.save.cash >= def.price) Color.WHITE else RED)
        }
        if (!starterMode && game.save.owns(def.id)) font.draw(batch, "IN YOUR GARAGE", w - 8f - font.width("IN YOUR GARAGE"), h - 128f, CYAN)
        font.drawCentered(batch, "${index + 1}/${stock.size}", w / 2f, 17f, SOFT)
        carStats(def, 8f, 56f)
        if (soldTimer > 0f) font.drawCentered(batch, "SOLD!", w / 2f, h / 2f + 20f, YELLOW, if (soldTimer > 1.45f) 4 else 3)
    }

    companion object {
        const val GROUND = 270f - 234f
        const val SPOT_Y = 262f
        val AMBIENT = Color(0.55f, 0.55f, 0.7f, 1f)
    }
}

/** First launch: the dealer in starter mode. */
class StarterScreen(game: PixelStrip) : DealerScreen(game, starterMode = true)

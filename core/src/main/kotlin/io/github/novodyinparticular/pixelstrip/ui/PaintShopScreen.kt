package io.github.novodyinparticular.pixelstrip.ui

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import io.github.novodyinparticular.pixelstrip.PixelStrip
import io.github.novodyinparticular.pixelstrip.race.CarLooks
import io.github.novodyinparticular.pixelstrip.race.Controls
import kotlin.math.floor

/**
 * Paint booth: colour, finish, decal and decal colour. Changes preview live on the car;
 * APPLY charges for what changed, BACK throws the changes away.
 */
class PaintShopScreen(game: PixelStrip) : MenuScreen(game) {
    private val bg = art.tex("scenes/garage.png")
    private val bgGlow = art.tex("scenes/garage_glow.png")
    private val stage = CarStage()
    private val save = game.save
    private val owned = save.selectedCar()!!
    private val cat = game.parts
    private val ramps = Pixmap(com.badlogic.gdx.Gdx.files.internal("paint/ramps.png"))

    private var color = owned.paint
    private var finish = owned.cosmetics["finish"] ?: 0
    private var decal = owned.cosmetics["decal"] ?: 0
    private var decalColor = owned.cosmetics["decalColor"] ?: 0
    private var applied = 0f

    private fun rampColor(row: Int) = Color(ramps.getPixel(5, row))

    private val colorButtons = List(ramps.height) { i -> button("", rampColor(i)) { color = i } }
    private val finishButtons = cat.finishes.mapIndexed { i, f -> button(f.name, Color(BTN_PURPLE)) { finish = i } }
    private val decalButtons = cat.decals.mapIndexed { i, d -> button(d.name, Color(BTN_PURPLE)) { decal = i } }
    private val decalColorButtons = cat.decalColors.mapIndexed { i, c -> button("", Color(c[0], c[1], c[2], 1f)) { decalColor = i } }
    private val apply = button("APPLY", BTN_RED, big = true, keys = intArrayOf(Input.Keys.ENTER)) { commit() }
    private val back = button("BACK", BTN_GREY, keys = intArrayOf(Input.Keys.B)) { onBack() }

    private fun cost(): Long {
        var c = 0L
        if (color != owned.paint) c += cat.colorPrice
        if (finish != (owned.cosmetics["finish"] ?: 0)) c += cat.finishes[finish].price
        if (decal != (owned.cosmetics["decal"] ?: 0)) c += cat.decals[decal].price
        if (decalColor != (owned.cosmetics["decalColor"] ?: 0) && decal > 0) c += 500
        return c
    }

    private fun commit() {
        val c = cost()
        if (c == 0L || save.cash < c) return
        save.cash -= c
        owned.paint = color
        owned.cosmetics["finish"] = finish
        owned.cosmetics["decal"] = decal
        owned.cosmetics["decalColor"] = decalColor
        save.write()
        applied = 1.4f
        flash = 0.5f
        flashColor.set(rampColor(color))
    }

    override fun onBack() = game.go(0) { GarageScreen(game) }

    private fun previewLooks(): CarLooks {
        val base = CarLooks.of(owned, cat)
        val dc = cat.decalColors[decalColor]
        return base.copy(finish = finish, decal = if (decal > 0) cat.decals[decal].name.lowercase() else null, decalColor = Color(dc[0], dc[1], dc[2], 1f))
    }

    override fun layout(w: Int, h: Int) {
        fun row(list: List<Controls.Button>, y: Float, bw: Float, gap: Float, selected: Int, small: Boolean) {
            var x = 70f
            list.forEachIndexed { i, b ->
                b.x = x
                b.y = y
                b.w = bw
                b.h = if (small) 14f else 16f
                x += bw + gap
                if (!small) b.face.set(if (i == selected) PINK else BTN_PURPLE)
            }
        }
        row(colorButtons, h - 58f, 16f, 3f, color, true)
        row(finishButtons, h - 80f, 56f, 3f, finish, false)
        row(decalButtons, h - 102f, 56f, 3f, decal, false)
        row(decalColorButtons, h - 122f, 16f, 3f, decalColor, true)
        decalColorButtons.forEach { it.visible = decal > 0 }
        val c = cost()
        apply.apply {
            x = w - 112f; y = 8f; this.w = 104f; this.h = 40f
            label = if (c == 0L) "SAVED" else if (save.cash < c) "SHORT" else "APPLY"
        }
        back.apply { x = 8f; y = 8f; this.w = 60f; this.h = 22f }
    }

    override fun update(dt: Float) {
        applied = (applied - dt).coerceAtLeast(0f)
        // ease the camera in on the car: paint is worth a close look
        zoom += (ZOOM - zoom) * minOf(1f, dt * 2.5f)
        focusX = post.width / 2f - 10f
        focusY = GarageScreen.GROUND + 16f
        lights.clear()
        val bx = floor((post.width - bg.width) / 2f)
        for (lx in GarageScreen.LAMPS) lights.add(bx + lx, GarageScreen.LAMP_Y, 60f, 1f, 0.88f, 0.7f, 220f)
        lights.add(post.width / 2f, 120f, 80f, 0.5f, 0.5f, 0.55f, 260f)          // booth light over the car
    }

    override fun drawScene(w: Int, h: Int) {
        batch.draw(bg, floor((w - bg.width) / 2f), 0f)
        batch.setColor(0.02f, 0.01f, 0.05f, 0.35f)
        batch.draw(white, 0f, 0f, w.toFloat(), h.toFloat())
        batch.color = Color.WHITE
        stage.draw(game.effective(owned), color, w / 2f - 10f, GarageScreen.GROUND, Color(0.7f, 0.68f, 0.8f, 1f), looks = previewLooks())
    }

    override fun drawGlow(w: Int, h: Int) {
        batch.setColor(0.6f, 0.6f, 0.6f, 1f)
        batch.draw(bgGlow, floor((w - bgGlow.width) / 2f), 0f)
        batch.color = Color.WHITE
        if (applied > 0f) {
            val c = rampColor(color)
            batch.setColor(c.r, c.g, c.b, applied / 1.4f)
            batch.draw(white, w / 2f - 70f, GarageScreen.GROUND, 120f, 34f)
            batch.color = Color.WHITE
        }
    }

    override fun drawUi(w: Int, h: Int) {
        batch.setColor(0.03f, 0.02f, 0.08f, 0.8f)
        batch.draw(white, 0f, h - 128f, w.toFloat(), 92f)
        batch.color = Color.WHITE
        font.draw(batch, "PAINT", 8f, h - 24f, PINK, 2)
        font.draw(batch, game.car(owned.carId).name.uppercase(), 8f, h - 38f, SOFT)
        cash(w - 8f, h - 20f)
        font.draw(batch, "COLOR", 8f, h - 54f, SOFT, 1, null)
        font.draw(batch, "FINISH", 8f, h - 76f, SOFT, 1, null)
        font.draw(batch, "DECAL", 8f, h - 98f, SOFT, 1, null)
        if (decal > 0) font.draw(batch, "DECAL INK", 8f, h - 118f, SOFT, 1, null)
        val c = cost()
        if (c > 0) font.draw(batch, "$" + "%,d".format(c), w - 112f, 52f, if (save.cash >= c) YELLOW else RED, 1, null)
        if (applied > 0f) font.drawCentered(batch, "FRESH PAINT!", w / 2f - 10f, h / 2f + 6f, YELLOW, if (applied > 1.25f) 3 else 2)
    }

    override fun drawUiOver(w: Int, h: Int) {
        // selection rings on the swatches
        val sel = listOf(colorButtons[color]) + (if (decal > 0) listOf(decalColorButtons[decalColor]) else emptyList())
        for (b in sel) {
            batch.color = Color.WHITE
            batch.draw(white, b.x - 2f, b.y - 2f, b.w + 4f, 1f)
            batch.draw(white, b.x - 2f, b.y + b.h + 1f, b.w + 4f, 1f)
            batch.draw(white, b.x - 2f, b.y - 2f, 1f, b.h + 4f)
            batch.draw(white, b.x + b.w + 1f, b.y - 2f, 1f, b.h + 4f)
        }
        batch.color = Color.WHITE
    }

    companion object {
        const val ZOOM = 1.9f
    }

    override fun dispose() {
        ramps.dispose()
        super.dispose()
    }
}

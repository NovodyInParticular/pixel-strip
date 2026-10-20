package io.github.novodyinparticular.pixelstrip.ui

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.MathUtils
import io.github.novodyinparticular.pixelstrip.PixelStrip
import io.github.novodyinparticular.pixelstrip.race.RaceScreen
import kotlin.math.floor
import kotlin.math.sin

/** The player's garage: the selected car on the lift under swaying shop lamps, dust in the beams. */
class GarageScreen(game: PixelStrip) : MenuScreen(game) {
    private val bg = art.tex("scenes/garage.png")
    private val bgGlow = art.tex("scenes/garage_glow.png")
    private val cone = TextureRegion(art.tex("fx/light_cone.png"))
    private val stage = CarStage()
    private val save = game.save
    private var index = save.cars.indexOfFirst { it.uid == save.selected }.coerceAtLeast(0)
    private val dust = Array(46) { floatArrayOf(MathUtils.random(-30f, 30f), MathUtils.random(40f, 200f), MathUtils.random(0f, 6.28f), MathUtils.random()) }

    private val prev = button("<", BTN_GREY, keys = intArrayOf(Input.Keys.LEFT)) { cycle(-1) }
    private val next = button(">", BTN_GREY, keys = intArrayOf(Input.Keys.RIGHT)) { cycle(1) }
    private val race = button("RACE", BTN_RED, big = true, keys = intArrayOf(Input.Keys.ENTER)) {
        val car = save.cars[index]
        game.go(1) { RaceScreen(game, "neon_city", car) }
    }
    private val dealer = button("DEALER", BTN_TEAL, keys = intArrayOf(Input.Keys.D)) { game.go(0) { DealerScreen(game) } }
    private val upgrades = button("UPGRADES", BTN_TEAL, keys = intArrayOf(Input.Keys.U)) { game.go(0) { UpgradeScreen(game) } }
    private val settings = button("SETTINGS", BTN_PURPLE, keys = intArrayOf(Input.Keys.S)) { game.go(0) { SettingsScreen(game) } }

    private val owned get() = save.cars[index]
    private val def get() = game.car(owned.carId)

    private fun cycle(d: Int) {
        if (save.cars.size < 2) return
        index = (index + d + save.cars.size) % save.cars.size
        save.selected = owned.uid
        save.write()
        flash = 0.25f
        flashColor.set(CYAN)
    }

    override fun onBack() = game.go(0) { TitleScreen(game) }

    override fun layout(w: Int, h: Int) {
        race.apply { x = w - 104f; y = 8f; this.w = 96f; this.h = 40f }
        dealer.apply { x = 8f; y = 8f; this.w = 70f; this.h = 22f }
        upgrades.apply { x = 84f; y = 8f; this.w = 78f; this.h = 22f; label = "UPGRADES"; visible = true }
        settings.apply { x = 168f; y = 8f; this.w = 76f; this.h = 22f }
        prev.apply { x = w / 2f - 96f; y = 52f; this.w = 22f; this.h = 22f; visible = save.cars.size > 1 }
        next.apply { x = w / 2f + 74f; y = 52f; this.w = 22f; this.h = 22f; visible = save.cars.size > 1 }
    }

    private fun bgX(w: Int) = floor((w - bg.width) / 2f)

    private fun lampX(w: Int, i: Int) = bgX(w) + LAMPS[i]

    override fun update(dt: Float) {
        lights.clear()
        val w = post.width
        for (i in LAMPS.indices) lights.add(lampX(w, i), LAMP_Y, 60f, 1f, 0.82f, 0.55f, 210f)
        lights.add(bgX(w) + 320f, 270f - 62f, 40f, 0.9f, 0.25f, 0.6f, 220f)        // neon sign
        lights.add(bgX(w) + 578f, 270f - 100f, 30f, 0.25f, 0.4f, 0.8f, 160f)       // city window
        for (d in dust) {
            d[1] += dt * (3f + d[3] * 4f)
            d[0] += sin(time * 0.7f + d[2]) * dt * 3f
            if (d[1] > 210f) d[1] = 40f
        }
    }

    override fun drawScene(w: Int, h: Int) {
        val bx = bgX(w)
        batch.draw(bg, bx, 0f)
        stage.draw(game.effective(owned), owned.paint, w / 2f, GROUND, AMBIENT)
        // lamp beams and the dust floating in them
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE)
        for (i in LAMPS.indices) {
            val sway = sin(time * 0.9f + i * 1.7f) * 1.5f
            batch.setColor(1f, 0.85f, 0.6f, 0.16f)
            // the cone sprite points along +x from its left-middle; rotate it to hang down from the lamp
            batch.draw(cone, lampX(w, i), LAMP_Y - 24f, 0f, 24f, 128f, 48f, 1.45f, 2.4f, -90f + sway)
            for (d in dust) {
                val dx = lampX(w, i) + d[0] * (LAMP_Y - d[1]) / 120f
                val tw = 0.5f + 0.5f * sin(time * 2f + d[2])
                batch.setColor(1f, 0.9f, 0.7f, 0.25f * tw)
                batch.draw(white, floor(dx), floor(d[1]), 1f, 1f)
            }
        }
        batch.color = Color.WHITE
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
    }

    override fun drawGlow(w: Int, h: Int) {
        batch.draw(bgGlow, bgX(w), 0f)
        stage.drawGlow(def, w / 2f, GROUND)
    }

    override fun drawUi(w: Int, h: Int) {
        font.draw(batch, def.brand.uppercase(), 8f, h - 14f, CYAN)
        font.draw(batch, def.name.uppercase(), 8f, h - 30f, Color.WHITE, 2)
        font.draw(batch, def.year.toString(), 8f, h - 42f, SOFT)
        classBadge(game.effective(owned), 40f, h - 42f)
        cash(w - 8f, h - 20f)
        font.draw(batch, "${index + 1}/${save.cars.size}", w - 8f - font.width("${index + 1}/${save.cars.size}"), h - 32f, SOFT)
        carStats(game.effective(owned), w - 144f, 56f)
    }

    companion object {
        val LAMPS = floatArrayOf(186f, 456f)
        const val LAMP_Y = 270f - 58f
        const val GROUND = 270f - 232f
        val AMBIENT = Color(0.5f, 0.48f, 0.66f, 1f)
    }
}

package io.github.novodyinparticular.pixelstrip.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.TextureRegion
import io.github.novodyinparticular.pixelstrip.PixelStrip
import io.github.novodyinparticular.pixelstrip.data.Career
import io.github.novodyinparticular.pixelstrip.race.CarLooks
import io.github.novodyinparticular.pixelstrip.race.DragMath
import io.github.novodyinparticular.pixelstrip.race.RaceScreen
import kotlin.math.floor
import kotlin.math.min

/**
 * Before a career race: the rival slides in under a spotlight with both cars. Bosses talk first,
 * their lines typed out one at a time; tap to move on.
 */
class VsScreen(game: PixelStrip, private val rival: Career.Rival, private val pinkSlip: Boolean = false) : MenuScreen(game) {
    private val bg = art.tex("scenes/showroom.png")
    private val cone = TextureRegion(art.tex("fx/light_cone.png"))
    private val stage = CarStage()
    private val portrait = art.tex("portraits/${rival.id}.png")
    private val save = game.save
    private val mine = save.selectedCar()!!
    private val theirs = rival.asCar()
    private var line = 0
    private var typed = 0f
    override val song = if (rival.boss) "boss" else "career"
    private val talking get() = rival.boss && !pinkSlip && rival.id !in game.save.beaten && line < rival.intro.size

    private val race = button("RACE", BTN_RED, big = true, keys = intArrayOf(Input.Keys.ENTER)) { go() }
    private val back = button("BACK", BTN_GREY, keys = intArrayOf(Input.Keys.B)) { onBack() }

    private fun go() {
        if (talking) return advance()
        game.go(1) { RaceScreen(game, rival.track, mine, rival = rival, pinkSlip = pinkSlip) }
    }

    private fun advance() {
        val text = rival.intro[line]
        if (typed < text.length) typed = text.length.toFloat() else {
            line++
            typed = 0f
        }
    }

    override fun onBack() = game.go(0) { CareerScreen(game) }

    override fun layout(w: Int, h: Int) {
        race.apply { x = w - 104f; y = 8f; this.w = 96f; this.h = 36f; label = if (talking) "NEXT" else "RACE" }
        back.apply { x = 8f; y = 8f; this.w = 60f; this.h = 20f }
    }

    override fun update(dt: Float) {
        if (talking) {
            typed = min(rival.intro[line].length.toFloat(), typed + dt * 28f)
            if (Gdx.input.justTouched() && !race.down && !back.down) advance()
            if (game.shots?.screen == "vs" && time > 1.2f + line * 0.9f) advance()
        }
        lights.clear()
        lights.add(post.width * 0.72f, 200f, 60f, 1f, 0.85f, 0.6f, 230f)       // the rival's spotlight
        lights.add(post.width * 0.28f, 200f, 60f, 0.6f, 0.7f, 0.9f, 200f)
    }

    override fun drawScene(w: Int, h: Int) {
        batch.draw(bg, floor((w - bg.width) / 2f), 0f)
        batch.setColor(0.02f, 0.01f, 0.05f, if (rival.boss) 0.75f else 0.5f)    // bosses get the lights down
        batch.draw(white, 0f, 0f, w.toFloat(), h.toFloat())
        batch.color = Color.WHITE
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE)
        batch.setColor(1f, 0.85f, 0.6f, 0.14f)
        batch.draw(cone, w * 0.72f, h - 10f, 0f, 24f, 128f, 48f, 1.9f, 2.2f, -90f)
        batch.setColor(0.6f, 0.7f, 1f, 0.1f)
        batch.draw(cone, w * 0.28f, h - 10f, 0f, 24f, 128f, 48f, 1.9f, 2.2f, -90f)
        batch.color = Color.WHITE
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        // cars roll in from each side
        val roll = min(1f, time / 0.9f)
        val ease = 1f - (1f - roll) * (1f - roll) * (1f - roll)
        stage.draw(game.effective(mine), mine.paint, -60f + (w * 0.28f + 60f) * ease, GROUND, AMBIENT, looks = game.looks(mine))
        if (rival.driver == "joe") stage.view(game.effective(theirs)).driver = 1
        stage.draw(game.effective(theirs), theirs.paint, w + 60f - (w * 0.28f + 60f) * ease, GROUND, AMBIENT, looks = CarLooks.of(theirs, game.parts))
    }

    override fun drawGlow(w: Int, h: Int) {
        val roll = min(1f, time / 0.9f)
        val ease = 1f - (1f - roll) * (1f - roll) * (1f - roll)
        stage.drawGlow(game.effective(theirs), w + 60f - (w * 0.28f + 60f) * ease, GROUND)
    }

    override fun drawUi(w: Int, h: Int) {
        val tier = game.career.tierOf(rival)
        // portrait slams in from the right
        val slide = min(1f, time / 0.5f)
        val px = floor(w - 110f + (1f - slide) * 120f)
        panel(px - 4f, h - 120f, 104f, 104f, if (rival.boss) RED else classColor(tier.name), 0.95f)
        batch.draw(portrait, px, h - 116f, 96f, 96f)
        font.draw(batch, rival.name, px - 4f, h - 132f, Color.WHITE, 2)
        rival.handle?.let { font.draw(batch, "\"$it\"", px - 4f - font.width(it) / 2f, h - 144f, if (rival.boss) RED else SOFT) }
        font.drawCentered(batch, "VS", w / 2f, h / 2f + 10f, YELLOW, 4)
        if (pinkSlip) font.drawCentered(batch, "PINK SLIPS - LOSER WALKS", w / 2f, h / 2f - 10f, RED)
        else if (rival.boss && rival.id !in game.save.beaten) font.drawCentered(batch, "BOSS RACE - PINK SLIPS", w / 2f, h / 2f - 10f, RED)
        // the two cars' numbers
        val mineDef = game.effective(mine)
        val theirDef = game.effective(theirs)
        val q = tier.quarter
        fun et(d: io.github.novodyinparticular.pixelstrip.data.CarDef) = DragMath.perfectPass(d).let { if (q) it.quarter else it.eighth }
        font.draw(batch, "YOU", 8f, h - 24f, CYAN, 2)
        font.draw(batch, mineDef.name.uppercase(), 8f, h - 38f, SOFT)
        font.draw(batch, "%s BEST %.2f".format(if (q) "1/4" else "1/8", et(mineDef)), 8f, h - 50f, YELLOW)
        font.draw(batch, theirDef.name.uppercase(), w - 112f - 8f - font.width(theirDef.name), h - 24f, SOFT)
        font.draw(batch, "%.2f".format(et(theirDef)), w - 112f - 8f - font.width("0.00"), h - 36f, YELLOW)
        if (talking) {
            val text = rival.intro[line].take(typed.toInt())
            panel(16f, 84f, w - 140f, 26f, RED, 0.94f)          // above the cars, so the drivers show
            batch.draw(portrait, 20f, 86f, 22f, 22f)
            font.draw(batch, text, 48f, 94f, Color.WHITE, 1, null)
        }
    }

    companion object {
        const val GROUND = 270f - 234f
        val AMBIENT = Color(0.55f, 0.52f, 0.7f, 1f)
    }
}

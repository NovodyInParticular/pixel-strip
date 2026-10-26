package io.github.novodyinparticular.pixelstrip.ui

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.MathUtils
import io.github.novodyinparticular.pixelstrip.PixelStrip
import io.github.novodyinparticular.pixelstrip.data.Career
import io.github.novodyinparticular.pixelstrip.race.CarLooks
import kotlin.math.floor
import kotlin.math.min

/** A car you've won or found rolls onto your lift under confetti: a boss's pink slip, or a barn find. */
class PrizeScreen(game: PixelStrip, private val carId: String, private val headline: String, private val lines: List<String>) : MenuScreen(game) {
    /** A boss's pink slip, with every part they built into it. */
    constructor(game: PixelStrip, rival: Career.Rival) : this(game, rival.carId, "PINK SLIP WON!",
        listOf("${rival.name}'S ${game.car(rival.carId).name.uppercase()} IS YOURS", "WITH EVERY PART HE BUILT INTO IT"))

    /** A barn find, with its story. */
    constructor(game: PixelStrip, barn: Career.Barn) : this(game, barn.carId, "BARN FIND!", barn.story)

    private val bg = art.tex("scenes/garage.png")
    private val bgGlow = art.tex("scenes/garage_glow.png")
    private val stage = CarStage()
    private val car = game.save.cars.last { it.carId == carId }
    private val confetti = ArrayList<FloatArray>()     // x, y, vx, vy, colour index, spin
    private val colors = arrayOf(PINK, CYAN, YELLOW, GREEN, ORANGE, Color.WHITE)
    private val garage = button("GARAGE", BTN_RED, big = true, keys = intArrayOf(Input.Keys.ENTER)) {
        game.save.selected = car.uid
        game.save.write()
        game.go(2) { GarageScreen(game) }
    }

    init {
        flash = 0.8f
        flashColor.set(YELLOW)
    }

    override fun onBack() = game.go(2) { GarageScreen(game) }

    override fun layout(w: Int, h: Int) {
        garage.apply { x = w - 104f; y = 8f; this.w = 96f; this.h = 36f }
    }

    override fun update(dt: Float) {
        if (time < 3f || MathUtils.random() < 0.3f) repeat(3) {
            confetti.add(floatArrayOf(MathUtils.random(0f, post.width.toFloat()), post.height + 4f, MathUtils.random(-20f, 20f),
                MathUtils.random(-60f, -25f), MathUtils.random(0, colors.size - 1).toFloat(), MathUtils.random(0f, 6f)))
        }
        for (c in confetti) {
            c[0] += (c[2] + MathUtils.sin(time * 3f + c[5]) * 14f) * dt
            c[1] += c[3] * dt
        }
        confetti.removeAll { it[1] < -4f }
        lights.clear()
        val bx = floor((post.width - bg.width) / 2f)
        for (lx in GarageScreen.LAMPS) lights.add(bx + lx, GarageScreen.LAMP_Y, 60f, 1f, 0.85f, 0.6f, 230f)
    }

    override fun drawScene(w: Int, h: Int) {
        batch.draw(bg, floor((w - bg.width) / 2f), 0f)
        // the car drives in from the left and stops on the lift
        val roll = min(1f, time / 1.6f)
        val ease = 1f - (1f - roll) * (1f - roll)
        stage.draw(game.effective(car), car.paint, -80f + (w / 2f + 80f) * ease, GarageScreen.GROUND, GarageScreen.AMBIENT, looks = game.looks(car))
        for (c in confetti) {
            batch.color = colors[c[4].toInt()]
            val flip = MathUtils.sin(time * 8f + c[5]) > 0f
            batch.draw(white, floor(c[0]), floor(c[1]), if (flip) 2f else 1f, if (flip) 1f else 2f)
        }
        batch.color = Color.WHITE
    }

    override fun drawGlow(w: Int, h: Int) = batch.draw(bgGlow, floor((w - bgGlow.width) / 2f), 0f)

    override fun drawUi(w: Int, h: Int) {
        val pop = if (time < 0.3f) 4 else 3
        font.drawCentered(batch, headline, w / 2f, h - 50f, YELLOW, pop)
        lines.getOrNull(0)?.let { font.drawCentered(batch, it, w / 2f, h - 70f, Color.WHITE) }
        lines.getOrNull(1)?.let { font.drawCentered(batch, it, w / 2f, h - 82f, SOFT) }
    }
}

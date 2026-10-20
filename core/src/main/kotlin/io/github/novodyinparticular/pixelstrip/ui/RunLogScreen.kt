package io.github.novodyinparticular.pixelstrip.ui

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import io.github.novodyinparticular.pixelstrip.PixelStrip
import kotlin.math.floor

/** Test-n-tune log: recent runs for the selected car, the best per tune flashing. */
class RunLogScreen(game: PixelStrip) : MenuScreen(game) {
    private val owned = game.save.selectedCar()!!
    private val bg = art.tex("scenes/garage.png")
    private var quarter = false
    private val dist = button("1/8 MILE", BTN_PURPLE) { quarter = !quarter }
    private val back = button("BACK", BTN_GREY, keys = intArrayOf(Input.Keys.B)) { onBack() }

    override fun onBack() = game.go(0) { TuneScreen(game) }

    override fun layout(w: Int, h: Int) {
        dist.apply { x = w - 92f; y = h - 46f; this.w = 84f; this.h = 18f; label = if (quarter) "1/4 MILE" else "1/8 MILE" }
        back.apply { x = 8f; y = 8f; this.w = 60f; this.h = 22f }
    }

    override fun drawScene(w: Int, h: Int) {
        batch.draw(bg, floor((w - bg.width) / 2f), 0f)
        batch.setColor(0.02f, 0.01f, 0.05f, 0.8f)
        batch.draw(white, 0f, 0f, w.toFloat(), h.toFloat())
        batch.color = Color.WHITE
    }

    override fun drawUi(w: Int, h: Int) {
        font.draw(batch, "RUN LOG", 8f, h - 24f, PINK, 2)
        font.draw(batch, game.car(owned.carId).name.uppercase(), 8f, h - 38f, SOFT)
        val runs = owned.runs.filter { it.quarter == quarter }.takeLast(12).reversed()
        val best = runs.groupBy { it.tune }.mapValues { (_, list) -> list.minOf { it.et } }
        val x = w / 2f - 150f
        var y = h - 70f
        panel(x - 8f, 36f, 316f, h - 96f, CYAN, 0.75f)
        listOf("TUNE" to 0f, "R/T" to 90f, "60'" to 140f, "ET" to 190f, if (game.settings.metric) "KMH" to 250f else "MPH" to 250f).forEach { (t, dx) ->
            font.draw(batch, t, x + dx, y, YELLOW, 1, null)
        }
        y -= 14f
        if (runs.isEmpty()) font.drawCentered(batch, "NO RUNS YET: TRY TEST N TUNE", w / 2f, h / 2f, SOFT)
        val blink = (time * 3f).toInt() % 2 == 0
        for (r in runs) {
            val isBest = best[r.tune] == r.et
            val c = if (isBest) (if (blink) GREEN else Color.WHITE) else SOFT
            font.draw(batch, r.tune, x, y, c, 1, null)
            font.draw(batch, "%.3f".format(r.rt), x + 90f, y, c, 1, null)
            font.draw(batch, "%.3f".format(r.sixty), x + 140f, y, c, 1, null)
            font.draw(batch, "%.3f".format(r.et), x + 190f, y, c, 1, null)
            val spd = if (game.settings.metric) r.trapMph * 1.60934f else r.trapMph
            font.draw(batch, "%.1f".format(spd), x + 250f, y, c, 1, null)
            y -= 12f
        }
    }
}

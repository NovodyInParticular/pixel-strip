package io.github.novodyinparticular.pixelstrip.ui

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.math.MathUtils
import io.github.novodyinparticular.pixelstrip.PixelStrip
import io.github.novodyinparticular.pixelstrip.race.CarLooks
import io.github.novodyinparticular.pixelstrip.race.DragMath
import io.github.novodyinparticular.pixelstrip.save.OwnedCar
import kotlin.math.floor
import kotlin.math.max

/**
 * The dyno: the car strapped onto glowing rollers, a full-throttle pull from low revs to the
 * limiter, wheel horsepower and torque drawing in live over the previous pull.
 */
class DynoScreen(game: PixelStrip) : MenuScreen(game) {
    private val bg = art.tex("scenes/garage.png")
    private val bgGlow = art.tex("scenes/garage_glow.png")
    private val flame = art.tex("fx/flame.png")
    private val stage = CarStage()
    private val owned: OwnedCar = game.save.selectedCar()!!
    private val def = game.effective(owned)
    private val previous = owned.dyno
    private val startRpm = def.idleRpm * 2f
    private var rpm = def.idleRpm
    private var pulling = false
    private var done = false
    private var t = 0f
    private var shake = 0f
    private var pop = 0f
    private val samples = ArrayList<Float>()      // whp every DYNO_STEP_RPM from idle

    private val run = button("RUN", BTN_RED, big = true, keys = intArrayOf(Input.Keys.ENTER)) { start() }
    private val back = button("BACK", BTN_GREY, keys = intArrayOf(Input.Keys.B)) { onBack() }

    private fun start() {
        if (pulling) return
        pulling = true
        done = false
        t = 0f
        samples.clear()
    }

    override fun onBack() = game.go(0) { TuneScreen(game) }

    override fun layout(w: Int, h: Int) {
        run.apply { x = w - 104f; y = 8f; this.w = 96f; this.h = 40f; label = if (pulling) "PULLING" else if (done) "AGAIN" else "RUN" }
        back.apply { x = 8f; y = 8f; this.w = 60f; this.h = 22f }
    }

    override fun update(dt: Float) {
        if (game.shots?.screen == "dyno" && !pulling && !done && time > 0.2f) start()
        shake = max(0f, shake - dt * 6f)
        pop = max(0f, pop - dt)
        game.audio.engine(0, def, rpm, if (pulling) 1f else 0.04f, game.sound(owned), 0.7f)
        if (pop > 0.09f) game.audio.play(io.github.novodyinparticular.pixelstrip.audio.Sfx.Kind.POP, 0.8f)
        if (pulling) {
            t += dt
            rpm = startRpm + (def.redline - startRpm) * (t / PULL_S).coerceIn(0f, 1f)
            val step = io.github.novodyinparticular.pixelstrip.save.OwnedCar.DYNO_STEP_RPM
            while (def.idleRpm + samples.size * step <= rpm) samples.add(DragMath.wheelHp(def, def.idleRpm + samples.size * step))
            shake = max(shake, 0.4f + rpm / def.redline)
            if (rpm > def.redline * 0.9f && MathUtils.random() < 0.08f) pop = 0.1f
            if (t >= PULL_S) {
                pulling = false
                done = true
                pop = 0.2f
                owned.dyno = samples.toFloatArray()
                game.save.write()
            }
        } else {
            rpm += (def.idleRpm - rpm) * minOf(1f, dt * 3f)
        }
        lights.clear()
        val bx = floor((post.width - bg.width) / 2f)
        for (lx in GarageScreen.LAMPS) lights.add(bx + lx, GarageScreen.LAMP_Y, 60f, 1f, 0.82f, 0.55f, 210f)
        lights.add(post.width / 2f, 30f, 20f, 0.2f, 0.8f, 0.9f, 140f)            // roller glow from below
        if (pop > 0f) {
            val ex = carX() + def.sprite.exhaust.first
            lights.add(ex, GarageScreen.GROUND + 8f, 14f, 1f, 0.5f, 0.2f, 90f)
        }
    }

    private fun carX() = floor(post.width / 2f - def.sprite.width / 2f)

    override fun drawScene(w: Int, h: Int) {
        val sx = if (shake > 0f) floor(MathUtils.random(-shake, shake)) else 0f
        batch.draw(bg, floor((w - bg.width) / 2f), 0f)
        batch.setColor(0.02f, 0.01f, 0.05f, 0.62f)
        batch.draw(white, 0f, 0f, w.toFloat(), h.toFloat())
        batch.color = Color.WHITE
        // rollers under both wheels, stripes rolling with the revs
        val s = def.sprite
        for ((wx, _) in listOf(s.rearWheel, s.frontWheel)) {
            val rx = carX() + wx
            batch.setColor(0.25f, 0.27f, 0.38f, 1f)
            batch.draw(white, rx - 9f, GarageScreen.GROUND - 5f, 18f, 5f)
            val phase = (time * rpm / 300f) % 4f
            for (k in 0 until 5) {
                batch.setColor(0.4f, 0.95f, 0.9f, 0.9f)
                batch.draw(white, floor(rx - 9f + ((k * 4f + phase) % 18f)), GarageScreen.GROUND - 4f, 1f, 3f)
            }
        }
        batch.color = Color.WHITE
        val v = stage.view(def)
        v.update(1f / 60f, 0f, if (pulling) rpm / 60f else 0f)
        stage.draw(def, owned.paint, w / 2f + sx, GarageScreen.GROUND, GarageScreen.AMBIENT, looks = game.looks(owned))
        if (pop > 0f) {
            val ex = carX() + s.exhaust.first
            val ey = GarageScreen.GROUND + (s.height - s.groundY) * 0f + (s.groundY - s.exhaust.second)
            batch.draw(flame, ex - 18f, ey - 4f, ((time * 25f).toInt() % 3) * 18, 0, 18, 9)
        }
    }

    override fun drawGlow(w: Int, h: Int) {
        batch.setColor(0.5f, 0.5f, 0.5f, 1f)
        batch.draw(bgGlow, floor((w - bgGlow.width) / 2f), 0f)
        batch.color = Color.WHITE
        val s = def.sprite
        for ((wx, _) in listOf(s.rearWheel, s.frontWheel)) {
            batch.setColor(0.2f, 0.8f, 0.8f, 0.8f)
            batch.draw(white, carX() + wx - 10f, GarageScreen.GROUND - 6f, 20f, 7f)
        }
        batch.color = Color.WHITE
        drawGraph(w, h, true)
    }

    private fun drawGraph(w: Int, h: Int, glow: Boolean) {
        val gx = 24f
        val gy = 96f
        val gw = w - 48f
        val gh = h - 172f
        val maxHp = max(DragMath.wheelHp(def, def.peakHpRpm + 300f), previous?.maxOrNull() ?: 0f) * 1.2f
        val step = io.github.novodyinparticular.pixelstrip.save.OwnedCar.DYNO_STEP_RPM
        fun px(r: Float) = gx + (r - def.idleRpm) / (def.redline - def.idleRpm) * gw
        fun py(hp: Float) = gy + hp / maxHp * gh
        if (!glow) {
            panel(gx - 6f, gy - 16f, gw + 12f, gh + 26f, CYAN, 0.8f)
            var r = 1000f
            while (r < def.redline) {
                if (r > def.idleRpm) font.draw(batch, "${(r / 1000).toInt()}K", floor(px(r)) - 5f, gy - 12f, SOFT, 1, null)
                r += 1000f
            }
        }
        /** Joins samples into a solid line, one pixel column at a time. */
        fun curve(values: List<Float>, color: Color, size: Float, map: (Float, Float) -> Float) {
            batch.color = color
            for (i in 0 until values.size - 1) {
                val r0 = def.idleRpm + i * step
                val x0 = px(r0)
                val x1 = px(r0 + step)
                var x = floor(x0)
                while (x < x1) {
                    val f = (x - x0) / (x1 - x0)
                    val r = r0 + step * f
                    val v = values[i] + (values[i + 1] - values[i]) * f
                    batch.draw(white, x, floor(py(map(v, r))), size, size)
                    x += 1f
                }
            }
        }
        val torque = { hp: Float, r: Float -> hp / (r / 7127f * io.github.novodyinparticular.pixelstrip.race.Vehicle.DRIVELINE_EFF) * 0.7376f * 0.9f }
        if (!glow) previous?.let { curve(it.toList(), Color(0.45f, 0.45f, 0.62f, 0.8f), 1f) { v, _ -> v } }
        curve(samples, PINK, 2f) { v, _ -> v }
        curve(samples, ORANGE, 2f, torque)
        batch.color = Color.WHITE
    }

    override fun drawUi(w: Int, h: Int) {
        font.draw(batch, "DYNO", 8f, h - 24f, PINK, 2)
        font.draw(batch, game.car(owned.carId).name.uppercase(), 8f, h - 38f, SOFT)
        drawGraph(w, h, false)
        font.draw(batch, "WHP", w - 120f, h - 24f, PINK, 1, null)
        font.draw(batch, "TORQUE", w - 90f, h - 24f, ORANGE, 1, null)
        font.draw(batch, "LAST PULL", w - 120f, h - 34f, SOFT, 1, null)
        val rpmText = "${rpm.toInt()} RPM"
        font.draw(batch, rpmText, w - 8f - font.width(rpmText, 2), 56f, Color.WHITE, 2)
        if (done && samples.isNotEmpty()) {
            val peak = samples.max()
            val before = previous?.maxOrNull()
            val delta = before?.let { peak - it }
            val text = "PEAK %.0f WHP".format(peak) + (delta?.let { if (it >= 0.5f) "  +%.0f".format(it) else if (it <= -0.5f) "  %.0f".format(it) else "" } ?: "")
            font.drawCentered(batch, text, w / 2f, h - 46f, if ((delta ?: 1f) >= 0f) GREEN else RED, 2)
        }
    }

    companion object {
        const val PULL_S = 4f
    }
}

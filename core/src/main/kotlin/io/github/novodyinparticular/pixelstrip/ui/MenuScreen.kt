package io.github.novodyinparticular.pixelstrip.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.graphics.Texture
import io.github.novodyinparticular.pixelstrip.PixelStrip
import io.github.novodyinparticular.pixelstrip.data.CarDef
import io.github.novodyinparticular.pixelstrip.gfx.Lights
import io.github.novodyinparticular.pixelstrip.race.CarLooks
import io.github.novodyinparticular.pixelstrip.race.CarRenderer
import io.github.novodyinparticular.pixelstrip.race.Controls
import io.github.novodyinparticular.pixelstrip.race.DragMath
import java.util.zip.Deflater
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Base for the menu screens. Subclasses fill the same passes as the race (scene, glow, UI) and
 * get buttons, the back key, the post pipeline and shared drawing helpers.
 */
abstract class MenuScreen(protected val game: PixelStrip) : ScreenAdapter() {
    protected val art = game.art
    protected val post = game.post
    protected val batch = game.batch
    protected val font = game.font
    protected val white: Texture = art.tex("ui/white.png")
    protected val controls = Controls(post, font, white)
    protected val lights = Lights()
    private val cam = OrthographicCamera()
    private val handlers = ArrayList<Pair<Controls.Button, () -> Unit>>()
    protected var time = 0f
        private set
    protected open val lut: Texture get() = art.tex("tracks/neon_city/lut.png")
    protected open val bloom = 1.4f
    /** Music for this screen (see audio/Music.kt SONGS). */
    protected open val song: String? = "garage"
    /** World zoom around (focusX, focusY) in buffer pixels; the UI layer is never zoomed. */
    protected var zoom = 1f
    protected var focusX = 0f
    protected var focusY = 0f
    private var shotIndex = 0

    protected fun button(label: String, color: Color, big: Boolean = false, keys: IntArray = intArrayOf(), onClick: () -> Unit): Controls.Button {
        val b = controls.add(Controls.Button(keys, label, color, big))
        handlers.add(b to onClick)
        return b
    }

    /** Position buttons for the current buffer size. */
    protected abstract fun layout(w: Int, h: Int)
    protected open fun update(dt: Float) {}
    protected abstract fun drawScene(w: Int, h: Int)
    protected open fun drawGlow(w: Int, h: Int) {}
    protected abstract fun drawUi(w: Int, h: Int)
    /** Drawn after the buttons: labels and markers that sit on top of them. */
    protected open fun drawUiOver(w: Int, h: Int) {}
    protected open fun onBack() {}

    override fun render(delta: Float) {
        val dt = min(delta, 1f / 20f)
        time += dt
        val w = post.width
        val h = post.height
        layout(w, h)
        controls.update(dt)
        if (!game.transitioning) {
            for ((b, fn) in handlers) if (b.justPressed && b.visible) {
                game.audio.play(io.github.novodyinparticular.pixelstrip.audio.Sfx.Kind.BLIP)
                fn()
                break
            }
            if (Gdx.input.isKeyJustPressed(Input.Keys.BACK) || Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) onBack()
        }
        game.audio.music(song, MENU_MUSIC)
        update(dt)

        cam.setToOrtho(false, w.toFloat(), h.toFloat())
        cam.update()
        batch.projectionMatrix = cam.combined
        post.clearUiAndDistort()

        post.scene.begin()
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        batch.begin()
        drawScene(w, h)
        batch.end()
        post.scene.end()

        post.glow.begin()
        Gdx.gl.glClearColor(0f, 0f, 0f, 0f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        batch.begin()
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE)
        drawGlow(w, h)
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        batch.end()
        post.glow.end()

        post.ui.begin()
        batch.begin()
        batch.setBlendFunctionSeparate(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA)
        drawUi(w, h)
        controls.draw(batch)
        drawUiOver(w, h)
        batch.setBlendFunctionSeparate(GL20.GL_SRC_ALPHA, GL20.GL_ONE, GL20.GL_ZERO, GL20.GL_ONE)
        controls.drawGlow(batch)
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        batch.end()
        post.ui.end()

        post.bloomGain = bloom
        post.lutMix = 1f
        post.crt = game.settings.crt
        post.flash = flash * 0.5f
        post.flashColor.set(flashColor)
        post.chromatic = 0f
        post.radialBlur = 0f
        post.zoom = zoom
        post.focusX = if (zoom > 1f) focusX / w else 0.5f
        post.focusY = if (zoom > 1f) focusY / h else 0.5f
        post.hazeAmp = 0f
        post.print = 0f
        post.time = time
        post.present(lut)
        flash = max(0f, flash - dt * 2f)
        chimeOnCashChange()
        captureShots()
    }

    // --- Shared drawing ------------------------------------------------------------------------

    protected var flash = 0f
    protected val flashColor = Color(1f, 1f, 1f, 1f)

    protected fun panel(x: Float, y: Float, w: Float, h: Float, edge: Color = PINK, alpha: Float = 0.82f) {
        batch.setColor(0.03f, 0.02f, 0.08f, alpha)
        batch.draw(white, x, y, w, h)
        batch.color = edge
        batch.draw(white, x, y + h - 1f, w, 1f)
        batch.setColor(edge.r * 0.5f, edge.g * 0.5f, edge.b * 0.5f, 1f)
        batch.draw(white, x, y, w, 1f)
        batch.draw(white, x, y, 1f, h)
        batch.draw(white, x + w - 1f, y, 1f, h)
        batch.color = Color.WHITE
    }

    /** Labelled bar, value 0..1, in segmented LED blocks; [preview] adds blinking segments up to that value. */
    protected fun statBar(label: String, x: Float, y: Float, value: Float, color: Color, width: Int = 70, preview: Float = -1f) {
        font.draw(batch, label, x, y, SOFT, 1, null)
        val segs = width / 4
        val lit = (value.coerceIn(0f, 1f) * segs).toInt()
        val next = if (preview < 0f) lit else (preview.coerceIn(0f, 1f) * segs).toInt()
        val blink = (time * 4f).toInt() % 2 == 0
        for (i in 0 until segs) {
            when {
                i < minOf(lit, next) -> batch.color = color
                i < lit -> batch.color = if (blink) RED else color                       // this part would lose it
                i < next -> batch.color = if (blink) GREEN else Color(0.3f, 0.5f, 0.25f, 1f)   // this part would add it
                else -> batch.setColor(color.r * 0.2f, color.g * 0.2f, color.b * 0.3f, 1f)
            }
            batch.draw(white, x + 46f + i * 4f, y, 3f, 6f)
        }
        batch.color = Color.WHITE
    }

    protected fun classColor(cls: String): Color = when (cls) {
        "OUTLAW" -> RED
        "PRO" -> PINK
        "SPORT" -> CYAN
        else -> YELLOW
    }

    protected fun classBadge(def: CarDef, x: Float, y: Float) {
        val et = DragMath.perfectPass(def).quarter
        val cls = game.economy.classFor(et)
        val text = "$cls ${game.economy.rating(et)}"
        val c = classColor(cls)
        val wpx = font.width(text) + 8f
        batch.setColor(c.r * 0.35f, c.g * 0.35f, c.b * 0.4f, 0.95f)
        batch.draw(white, x, y - 2f, wpx, 11f)
        batch.color = c
        batch.draw(white, x, y + 8f, wpx, 1f)
        batch.color = Color.WHITE
        font.draw(batch, text, x + 4f, y, c, 1, null)
    }

    /** Peak power as raced: rated power (net) with the supercharger and full turbo boost. */
    protected fun peakHp(def: CarDef) = def.hp * def.ratingFactor * (1f + def.blowerGain) * (1f + def.boostGain)

    private fun statValues(def: CarDef): FloatArray = floatArrayOf(
        peakHp(def) / 900f,
        def.torqueNm * def.ratingFactor * (1f + def.blowerGain) * (1f + def.boostGain) / 1300f,
        1f - (def.weightKg - 950f) / 900f,
        (def.grip - 0.75f) / 0.6f + (if (def.drivetrain == "AWD") 0.15f else 0f),
    )

    /** Car stat block shared by the garage, dealer and shop. [preview] shows what a change would do. */
    protected fun carStats(def: CarDef, x: Float, y: Float, preview: CarDef? = null) {
        val pass = DragMath.perfectPass(def)
        panel(x, y, 136f, 70f, CYAN, 0.7f)
        val now = statValues(def)
        val next = preview?.let { statValues(it) }
        val labels = arrayOf("POWER", "TORQUE", "WEIGHT", "GRIP")
        val colors = arrayOf(PINK, ORANGE, CYAN, GREEN)
        for (i in 0..3) statBar(labels[i], x + 6f, y + 56f - i * 10f, now[i], colors[i], preview = next?.get(i) ?: -1f)
        if (preview != null) {
            val after = DragMath.perfectPass(preview)
            val better = after.quarter < pass.quarter
            font.draw(batch, "1/4  %.2f > %.2f".format(pass.quarter, after.quarter), x + 6f, y + 14f, if (better) GREEN else RED, 1, null)
        } else {
            val trap = if (game.settings.metric) "%.0f KMH".format(pass.trapMph * 1.60934f) else "%.0f MPH".format(pass.trapMph)
            font.draw(batch, "1/4  %.2f @ %s".format(pass.quarter, trap), x + 6f, y + 14f, YELLOW, 1, null)
        }
        font.draw(batch, "${def.drivetrain}  ${def.gears.size}-SPD  ${peakHp(def).toInt()} HP", x + 6f, y + 4f, SOFT, 1, null)
    }

    private var shownCash = -1L
    private var heardCash = -1L

    private fun chimeOnCashChange() {
        val c = game.save.cash
        if (heardCash >= 0 && c != heardCash) game.audio.play(io.github.novodyinparticular.pixelstrip.audio.Sfx.Kind.CHIME)
        heardCash = c
    }

    /** Cash counter that rolls toward the real balance, arcade style. */
    protected fun cash(x: Float, y: Float, dt: Float = 1f / 60f) {
        val target = game.save.cash
        if (shownCash < 0) shownCash = target
        val diff = target - shownCash
        if (diff != 0L) shownCash += (diff * min(1f, dt * 8f)).toLong().let { if (it == 0L) (if (diff > 0) 1L else -1L) else it }
        val text = "$" + "%,d".format(shownCash)
        val c = if (abs(diff) > 0) Color.WHITE else YELLOW
        font.draw(batch, text, x - font.width(text, 2), y, c, 2)
    }

    /** A car standing still in a scene (garage lift, showroom turntable), lit by the scene's lights. */
    protected inner class CarStage {
        private val views = HashMap<String, CarRenderer>()
        private val paint = art.shader("batch.vert", "paint.frag")
        private val tint = art.shader("batch.vert", "tint.frag")
        private val ramps = art.tex("paint/ramps.png")

        fun view(def: CarDef): CarRenderer = views.getOrPut(def.id) { CarRenderer(def, art, paint, tint, ramps) }

        fun draw(def: CarDef, paintRow: Int, centerX: Float, groundY: Float, ambient: Color, lit: Boolean = true, looks: CarLooks = CarLooks.STOCK) {
            val v = view(def)
            v.paintRow = paintRow
            if (v.looks != looks) v.looks = looks
            v.update(1f / 60f, 0f, 0f)
            // a slow shine sweep across the paint, so finishes show off in the menus
            val sweep = (time * 0.22f) % 1f * 1.8f - 0.4f
            v.draw(batch, floor(centerX - def.sprite.width / 2f), groundY, time, lit = lit, ambient = ambient, lights = lights, shine = sweep)
        }

        fun drawGlow(def: CarDef, centerX: Float, groundY: Float) =
            view(def).drawGlow(batch, floor(centerX - def.sprite.width / 2f), groundY)
    }

    // --- Desktop screenshots ---------------------------------------------------------------

    private fun captureShots() {
        val plan = game.shots ?: return
        if (plan.screen == null || shotIndex >= plan.times.size || time < plan.times[shotIndex]) return
        val pixmap = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.backBufferWidth, Gdx.graphics.backBufferHeight)
        val file = Gdx.files.absolute("${plan.dir}/${plan.screen}_%02d.png".format(shotIndex))
        PixmapIO.writePNG(file, pixmap, Deflater.DEFAULT_COMPRESSION, true)
        pixmap.dispose()
        Gdx.app.log("shots", "saved ${file.name()}")
        shotIndex++
        if (shotIndex >= plan.times.size) Gdx.app.exit()
    }

    companion object {
        val PINK = Color(0.94f, 0.37f, 0.68f, 1f)
        val CYAN = Color(0.49f, 0.95f, 0.86f, 1f)
        val YELLOW = Color(1f, 0.91f, 0.56f, 1f)
        val ORANGE = Color(1f, 0.6f, 0.24f, 1f)
        val RED = Color(0.95f, 0.33f, 0.25f, 1f)
        val GREEN = Color(0.58f, 0.84f, 0.42f, 1f)
        val SOFT = Color(0.8f, 0.82f, 0.95f, 1f)
        val BTN_RED = Color(0.82f, 0.18f, 0.32f, 1f)
        val BTN_TEAL = Color(0.13f, 0.6f, 0.62f, 1f)
        val BTN_PURPLE = Color(0.45f, 0.2f, 0.55f, 1f)
        val BTN_GREY = Color(0.3f, 0.3f, 0.42f, 1f)
        const val MENU_MUSIC = 0.5f
    }
}

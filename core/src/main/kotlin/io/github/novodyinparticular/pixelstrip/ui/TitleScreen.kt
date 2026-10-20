package io.github.novodyinparticular.pixelstrip.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import io.github.novodyinparticular.pixelstrip.PixelStrip
import io.github.novodyinparticular.pixelstrip.data.TrackDef
import io.github.novodyinparticular.pixelstrip.world.TrackRenderer
import kotlin.math.sin

/** Attract screen: Neon City scrolling by behind the neon logo. */
class TitleScreen(game: PixelStrip) : MenuScreen(game) {
    private val track = TrackRenderer(TrackDef.load("neon_city"), art)
    private var camX = 0f
    private var started = false

    override val bloom = 1.8f
    override val song = "title"

    override fun layout(w: Int, h: Int) {}

    override fun update(dt: Float) {
        camX += dt * 140f
        track.update(dt, 0.25f)
        val autoStart = game.shots?.screen == "title" && time > 1f
        if (!started && !game.transitioning && (autoStart || Gdx.input.justTouched() || Gdx.input.isKeyJustPressed(Input.Keys.SPACE) || Gdx.input.isKeyJustPressed(Input.Keys.ENTER))) {
            started = true
            flash = 0.5f
            flashColor.set(PINK)
            game.go(2) { game.home() }
        }
    }

    override fun onBack() = Gdx.app.exit()

    override fun drawScene(w: Int, h: Int) {
        track.drawSky(batch, w)
        track.drawWorld(batch, camX, w)
        track.drawLights(batch, camX, w)
        track.drawForeground(batch, camX, w)
        batch.setColor(0.02f, 0.01f, 0.06f, 0.35f)
        batch.draw(white, 0f, 0f, w.toFloat(), h.toFloat())
        batch.color = Color.WHITE
    }

    override fun drawGlow(w: Int, h: Int) {
        track.drawGlow(batch, camX, w)
        // the logo's neon, into the bloom buffer (the sharp letters are on the UI layer)
        val flicker = if ((time * 7f).toInt() % 23 == 0) 0.4f else 1f
        font.drawCentered(batch, "PIXEL", w / 2f, h - 90f, Color(0.95f * flicker, 0.3f * flicker, 0.65f * flicker, 1f), 6, null)
        font.drawCentered(batch, "STRIP", w / 2f, h - 140f, Color(0.3f, 0.9f, 0.85f, 1f), 6, null)
    }

    override fun drawUi(w: Int, h: Int) {
        val bob = sin(time * 2f) * 1.5f
        font.drawCentered(batch, "PIXEL", w / 2f, h - 90f + bob, Color(1f, 0.62f, 0.82f, 1f), 6, Color(0.25f, 0.05f, 0.2f, 1f))
        font.drawCentered(batch, "STRIP", w / 2f, h - 140f + bob, Color(0.75f, 1f, 0.95f, 1f), 6, Color(0.03f, 0.2f, 0.22f, 1f))
        font.drawCentered(batch, "8-BIT DRAG RACING", w / 2f, h - 156f, YELLOW)
        if ((time * 2f).toInt() % 2 == 0) font.drawCentered(batch, "TAP TO START", w / 2f, 40f, Color.WHITE, 2)
    }
}

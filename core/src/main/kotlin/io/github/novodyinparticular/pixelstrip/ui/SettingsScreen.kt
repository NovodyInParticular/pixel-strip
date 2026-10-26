package io.github.novodyinparticular.pixelstrip.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import io.github.novodyinparticular.pixelstrip.PixelStrip
import io.github.novodyinparticular.pixelstrip.Quality
import kotlin.math.floor

/** Options over the dimmed garage. Every change applies at once and is saved. */
class SettingsScreen(game: PixelStrip) : MenuScreen(game) {
    private val bg = art.tex("scenes/garage.png")
    private val bgGlow = art.tex("scenes/garage_glow.png")
    private val s = game.settings

    private val rows = listOf(
        Row("GRAPHICS") { s.quality.name },
        Row("CRT FILTER") { if (s.crt) "ON" else "OFF" },
        Row("SOUND") { if (s.sound) "ON" else "OFF" },
        Row("VIBRATION") { if (s.vibration) "ON" else "OFF" },
        Row("UNITS") { if (s.metric) "KM/H" else "MPH" },
        Row("RACE VIEW") { if (s.cockpit) "COCKPIT" else "SIDE" },
    )
    private val toggles = rows.mapIndexed { i, r ->
        button(r.value(), BTN_TEAL) { change(i) }
    }
    private val back = button("BACK", BTN_GREY, keys = intArrayOf(Input.Keys.B)) { onBack() }

    private class Row(val label: String, val value: () -> String)

    private fun change(i: Int) {
        when (i) {
            0 -> {
                s.quality = Quality.entries[(s.quality.ordinal + 1) % Quality.entries.size]
                post.resize(Gdx.graphics.backBufferWidth, Gdx.graphics.backBufferHeight, s.quality)
            }
            1 -> s.crt = !s.crt
            2 -> s.sound = !s.sound
            3 -> {
                s.vibration = !s.vibration
                if (s.vibration) game.platform.vibrate(40)
            }
            4 -> s.metric = !s.metric
            5 -> s.cockpit = !s.cockpit
        }
        s.save()
        flash = 0.2f
        flashColor.set(CYAN)
    }

    override fun onBack() = game.go(0) { GarageScreen(game) }

    override fun layout(w: Int, h: Int) {
        toggles.forEachIndexed { i, b ->
            b.x = w / 2f + 10f
            b.y = h - 92f - i * 30f
            b.w = 90f
            b.h = 22f
            b.label = rows[i].value()
        }
        back.apply { x = 8f; y = 8f; this.w = 60f; this.h = 22f }
    }

    override fun drawScene(w: Int, h: Int) {
        batch.draw(bg, floor((w - bg.width) / 2f), 0f)
        batch.setColor(0.02f, 0.01f, 0.05f, 0.72f)
        batch.draw(white, 0f, 0f, w.toFloat(), h.toFloat())
        batch.color = Color.WHITE
    }

    override fun drawGlow(w: Int, h: Int) {
        batch.setColor(0.5f, 0.5f, 0.5f, 1f)
        batch.draw(bgGlow, floor((w - bgGlow.width) / 2f), 0f)
        batch.color = Color.WHITE
    }

    override fun drawUi(w: Int, h: Int) {
        font.drawCentered(batch, "SETTINGS", w / 2f, h - 44f, PINK, 3)
        panel(w / 2f - 120f, h - 70f - rows.size * 30f, 240f, rows.size * 30f + 4f, CYAN, 0.7f)
        rows.forEachIndexed { i, r -> font.draw(batch, r.label, w / 2f - 108f, h - 85f - i * 30f, SOFT, 1, null) }
        val hint = when (s.quality) {
            Quality.LOW -> "LOW: LIGHT BLOOM, NO HAZE OR ABERRATION"
            Quality.MEDIUM -> "MEDIUM: FULL EFFECTS, LIGHTER BLUR"
            Quality.HIGH -> "HIGH: EVERY EFFECT"
        }
        font.drawCentered(batch, hint, w / 2f, 40f, SOFT)
        val cloud = game.cloud
        val pending = game.save.pending.size
        val line = when (cloud.status) {
            io.github.novodyinparticular.pixelstrip.net.Cloud.Status.OFFLINE -> "CLOUD: OFFLINE" + if (pending > 0) " ($pending QUEUED)" else ""
            io.github.novodyinparticular.pixelstrip.net.Cloud.Status.SIGNING_IN -> "CLOUD: SIGNING IN..."
            io.github.novodyinparticular.pixelstrip.net.Cloud.Status.SYNCING -> "CLOUD: SYNCING..."
            io.github.novodyinparticular.pixelstrip.net.Cloud.Status.SYNCED -> "CLOUD: SAVED  ID ${cloud.playerId?.uppercase() ?: "?"}"
            io.github.novodyinparticular.pixelstrip.net.Cloud.Status.ERROR -> "CLOUD: RETRYING" + if (pending > 0) " ($pending QUEUED)" else ""
        }
        font.drawCentered(batch, line, w / 2f, 24f, if (cloud.status == io.github.novodyinparticular.pixelstrip.net.Cloud.Status.ERROR) PINK else CYAN)
        if (cloud.rejected > 0) font.drawCentered(batch, "${cloud.rejected} RESULT(S) REFUSED BY THE SERVER", w / 2f, 12f, PINK)
    }
}

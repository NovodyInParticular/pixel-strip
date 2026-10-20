package io.github.novodyinparticular.pixelstrip.ui

import io.github.novodyinparticular.pixelstrip.save.Entry
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.MathUtils
import io.github.novodyinparticular.pixelstrip.PixelStrip
import io.github.novodyinparticular.pixelstrip.race.CarLooks
import io.github.novodyinparticular.pixelstrip.data.TrackDef
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
    private val race = button("CAREER", BTN_RED, big = true, keys = intArrayOf(Input.Keys.ENTER)) { game.go(0) { CareerScreen(game) } }
    private val onlineRace = button("ONLINE", Color(0.15f, 0.35f, 0.75f, 1f), big = true, keys = intArrayOf(Input.Keys.O)) { game.go(0) { OnlineScreen(game) } }
    private val quick = button("QUICK RACE", Color(0.5f, 0.15f, 0.3f, 1f), keys = intArrayOf(Input.Keys.Q)) {
        val car = save.cars[index]
        game.go(1) { RaceScreen(game, TrackDef.GENERAL.random(), car) }
    }
    private val testRun = button("TEST", Color(0.6f, 0.3f, 0.1f, 1f), keys = intArrayOf(Input.Keys.X)) {
        val car = save.cars[index]
        game.go(1) { RaceScreen(game, "neon_city", car, test = true) }
    }
    private val ranks = button("RANKS", Color(0.15f, 0.45f, 0.55f, 1f), keys = intArrayOf(Input.Keys.L)) { game.go(0) { LeaderboardScreen(game) } }
    private val tourney = button("TOURNEY", Color(0.55f, 0.45f, 0.1f, 1f), keys = intArrayOf(Input.Keys.Y)) { game.go(0) { TournamentScreen(game) } }
    private val crew = button("CREW", Color(0.6f, 0.3f, 0.1f, 1f), keys = intArrayOf(Input.Keys.C)) { game.go(0) { CrewScreen(game) } }
    private val dealer = button("DEALER", BTN_TEAL, keys = intArrayOf(Input.Keys.D)) { game.go(0) { DealerScreen(game) } }
    private val upgrades = button("PARTS", BTN_TEAL, keys = intArrayOf(Input.Keys.U)) { game.go(0) { UpgradeScreen(game) } }
    private val tune = button("TUNE", BTN_TEAL, keys = intArrayOf(Input.Keys.T)) { game.go(0) { TuneScreen(game) } }
    private val paintShop = button("PAINT", BTN_TEAL, keys = intArrayOf(Input.Keys.P)) { game.go(0) { PaintShopScreen(game) } }
    private val settings = button("SETTINGS", BTN_PURPLE, keys = intArrayOf(Input.Keys.S)) { game.go(0) { SettingsScreen(game) } }

    private val sell = button("SELL", Color(0.35f, 0.3f, 0.2f, 1f)) { sellTapped() }

    // --- daily reward pop-up -------------------------------------------------------------------
    private var daily: io.github.novodyinparticular.pixelstrip.net.Daily.Status? = null
    private var dailyBusy = false
    private var claimed: io.github.novodyinparticular.pixelstrip.net.Daily.Reward? = null
    private var claimedTime = 0f
    private val dailyOpen get() = daily?.ready == true && !game.dailyDismissed || claimedTime > 0f
    private val claim = button("CLAIM", Color(0.2f, 0.55f, 0.3f, 1f), big = true, keys = intArrayOf(Input.Keys.ENTER)) { claimDaily() }
    private val later = button("LATER", BTN_GREY, keys = intArrayOf(Input.Keys.ESCAPE)) { game.dailyDismissed = true }

    init {
        if (game.cloud.configured && !game.dailyDismissed) game.daily.fetch(false) { s, _ ->
            daily = s
            // desktop shots: -Dpixelstrip.daily=claim takes it
            if (game.shots != null && System.getProperty("pixelstrip.daily") == "claim") claimDaily()
        }
    }

    private fun claimDaily() {
        if (dailyBusy || daily?.ready != true) return
        dailyBusy = true
        game.daily.fetch(true) { s, err ->
            dailyBusy = false
            if (s == null) {
                daily = null
                return@fetch
            }
            daily = s
            claimed = s.claimed
            claimedTime = 2.2f
            flash = 0.7f
            flashColor.set(YELLOW)
        }
    }
    private var confirmSell = 0f

    /** First tap asks, a second tap within 3 s sells. Your last car stays. */
    private fun sellTapped() {
        if (save.cars.size < 2) return
        if (confirmSell <= 0f) {
            confirmSell = 3f
            return
        }
        if (!game.commit(Entry("sell", game.now(), uid = owned.uid))) return
        index = index.coerceAtMost(save.cars.size - 1)
        save.selected = save.cars[index].uid
        save.write()
        confirmSell = 0f
        flash = 0.5f
        flashColor.set(YELLOW)
    }

    /** Desktop shots only: -Dpixelstrip.showcase=RIVAL puts that rival's car on the lift, zoomed in. */
    private val showcase = game.shots?.let { System.getProperty("pixelstrip.showcase") }?.let { id -> game.career.rivals.first { it.id == id }.asCar() }
    private val owned get() = showcase ?: save.cars[index]
    private val def get() = game.car(owned.carId)

    private fun cycle(d: Int) {
        if (save.cars.size < 2) return
        index = (index + d + save.cars.size) % save.cars.size
        save.selected = owned.uid
        save.write()
        flash = 0.25f
        flashColor.set(CYAN)
    }

    override fun onBack() {
        if (dailyOpen) game.dailyDismissed = true else game.go(0) { TitleScreen(game) }
    }

    override fun layout(w: Int, h: Int) {
        for (b in controls.buttons) b.visible = true        // the daily pop-up hides them; each frame starts from shown
        race.apply { x = w - 104f; y = 8f; this.w = 96f; this.h = 40f }
        quick.apply { x = w - 104f; y = 52f; this.w = 96f; this.h = 18f }
        testRun.apply { x = w - 104f; y = 74f; this.w = 96f; this.h = 18f; label = "TEST N TUNE" }
        onlineRace.apply { x = w - 190f; y = 8f; this.w = 82f; this.h = 40f }
        tourney.apply { x = w - 190f; y = 74f; this.w = 82f; this.h = 18f }
        ranks.apply { x = w - 112f; y = h - 58f; this.w = 56f; this.h = 14f }
        crew.apply {
            x = w - 190f; y = 52f; this.w = 82f; this.h = 18f
            label = game.cloud.badge?.let { "CREW [${it.tag}]" } ?: "CREW"
        }
        val row = listOf(dealer to 58f, upgrades to 52f, paintShop to 52f, tune to 46f, settings to 52f)
        var bx = 8f
        for ((b, bw) in row) {
            b.x = bx; b.y = 8f; b.w = bw; b.h = 22f
            bx += bw + 5f
        }
        settings.label = "SETUP"
        prev.apply { x = w / 2f - 96f; y = 52f; this.w = 22f; this.h = 22f; visible = save.cars.size > 1 }
        sell.apply {
            x = w - 52f; y = h - 58f; this.w = 44f; this.h = 14f; visible = save.cars.size > 1
            label = if (confirmSell > 0f) "SURE?" else "SELL"
        }
        next.apply { x = w / 2f + 74f; y = 52f; this.w = 22f; this.h = 22f; visible = save.cars.size > 1 }
        claim.apply { x = w / 2f - 60f; y = h / 2f - 58f; this.w = 120f; this.h = 30f; visible = dailyOpen && claimedTime <= 0f }
        later.apply { x = w / 2f + 70f; y = h / 2f - 52f; this.w = 54f; this.h = 18f; visible = dailyOpen && claimedTime <= 0f }
        // while the reward is up, it's the only thing to press
        if (dailyOpen) for (b in controls.buttons) if (b !== claim && b !== later) b.visible = false
    }

    private fun bgX(w: Int) = floor((w - bg.width) / 2f)

    private fun lampX(w: Int, i: Int) = bgX(w) + LAMPS[i]

    override fun update(dt: Float) {
        claimedTime = (claimedTime - dt).coerceAtLeast(0f)
        if (showcase != null) {
            zoom = 3.2f
            focusX = post.width / 2f
            focusY = post.height / 2f
            for (b in controls.buttons) b.visible = false
        }
        confirmSell = (confirmSell - dt).coerceAtLeast(0f)
        // the car on the lift idles, with a lumpy little blip every few seconds
        val blip = if ((time % 5f) in 2f..2.35f) 0.6f else 0f
        val d = game.effective(owned)
        game.audio.engine(0, d, d.idleRpm + blip * 2600f, 0.04f + blip, owned.parts["exhaust"] ?: 0, 0.3f)
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
        stage.draw(game.effective(owned), owned.paint, w / 2f, if (showcase != null) h / 2f - 12f else GROUND, AMBIENT, looks = game.looks(owned))
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

    /** The daily reward pop-up: drawn last under the buttons, so CLAIM and LATER sit on top. */
    private fun drawDaily(w: Int, h: Int) {
        if (!dailyOpen) return
        val s = daily ?: return
        // the rest of the screen dims behind the pop-up
        batch.setColor(0f, 0f, 0.02f, 0.6f)
        batch.draw(white, 0f, 0f, w.toFloat(), h.toFloat())
        batch.color = Color.WHITE
        val pw = 7 * 52f + 16f
        val px = w / 2f - pw / 2f
        val py = h / 2f - 68f
        panel(px, py, pw, 140f, YELLOW, 0.96f)
        font.drawCentered(batch, "DAILY REWARD", w / 2f, py + 120f, YELLOW, 2)
        font.drawCentered(batch, if (s.streak > 1) "${s.streak} DAYS IN A ROW" else "COME BACK TOMORROW TO BUILD A STREAK", w / 2f, py + 106f, SOFT)
        s.week.forEachIndexed { i, r ->
            val day = i + 1
            val x = px + 8f + i * 52f
            val today = day == s.day
            val past = day < s.day || (day == s.day && !s.ready)
            batch.setColor(if (today) 0.35f else 0.1f, if (today) 0.3f else 0.09f, if (today) 0.08f else 0.18f, 0.95f)
            batch.draw(white, x, py + 46f, 48f, 50f)
            batch.color = if (today) YELLOW else if (past) GREEN else BTN_GREY
            batch.draw(white, x, py + 95f, 48f, 1f)
            batch.color = Color.WHITE
            font.drawCentered(batch, "DAY $day", x + 24f, py + 84f, if (today) YELLOW else SOFT)
            font.drawCentered(batch, "$" + if (r.cash >= 10_000) "${r.cash / 1000}K" else "%,d".format(r.cash), x + 24f, py + 66f, if (past) GREEN else Color.WHITE)
            if (r.xp > 0) font.drawCentered(batch, "+${r.xp} XP", x + 24f, py + 54f, CYAN)
            if (past) font.drawCentered(batch, "OK", x + 24f, py + 54f, GREEN)
        }
        claimed?.takeIf { claimedTime > 0f }?.let { c ->
            font.drawCentered(batch, "+$" + "%,d".format(c.cash) + if (c.xp > 0) "  +${c.xp} XP" else "", w / 2f, py + 20f, GREEN, 2)
        }
    }

    override fun drawUi(w: Int, h: Int) {
        if (showcase != null) return
        font.draw(batch, def.brand.uppercase(), 8f, h - 14f, CYAN)
        font.draw(batch, def.name.uppercase(), 8f, h - 30f, Color.WHITE, 2)
        font.draw(batch, def.year.toString(), 8f, h - 42f, SOFT)
        classBadge(game.effective(owned), 40f, h - 42f)
        levelBadge(8f, h - 56f)
        cash(w - 8f, h - 20f)
        font.draw(batch, "${index + 1}/${save.cars.size}", w - 8f - font.width("${index + 1}/${save.cars.size}"), h - 32f, SOFT)
        carStats(game.effective(owned), w - 144f, 98f)
        if (confirmSell > 0f) {
            val text = "SELL FOR $" + "%,d".format(game.saleValue(owned)) + "? TAP AGAIN"
            panel(w / 2f - font.width(text) / 2f - 6f, h / 2f + 40f, font.width(text) + 12f, 16f, YELLOW, 0.92f)
            font.drawCentered(batch, text, w / 2f, h / 2f + 45f, YELLOW, 1, null)
        }
        drawDaily(w, h)
    }

    companion object {
        val LAMPS = floatArrayOf(186f, 456f)
        const val LAMP_Y = 270f - 58f
        const val GROUND = 270f - 232f
        val AMBIENT = Color(0.5f, 0.48f, 0.66f, 1f)
    }
}

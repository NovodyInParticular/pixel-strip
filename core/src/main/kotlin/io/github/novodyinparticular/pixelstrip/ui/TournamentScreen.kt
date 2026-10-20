package io.github.novodyinparticular.pixelstrip.ui

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import io.github.novodyinparticular.pixelstrip.PixelStrip
import io.github.novodyinparticular.pixelstrip.data.TrackDef
import io.github.novodyinparticular.pixelstrip.net.Tournaments
import io.github.novodyinparticular.pixelstrip.race.DragMath
import io.github.novodyinparticular.pixelstrip.race.RaceScreen
import kotlin.math.floor
import kotlin.math.max

/**
 * Daily tournaments: one per class. Pick a class, enter with a car of that class (the fee goes
 * into the pool), make up to five timed passes, and watch the leaderboard; yesterday's tab shows
 * who got paid. The server runs it all (server/functions/src/tournaments.ts).
 */
class TournamentScreen(game: PixelStrip, private val focus: String? = null) : MenuScreen(game) {
    private enum class View { LOADING, ERROR, TODAY, YESTERDAY }

    private val bg = art.tex("scenes/garage.png")
    private val cloud = game.cloud
    private val events = game.tournaments
    private val save = game.save
    override val song = "career"

    private var view = if (cloud.configured) View.LOADING else View.ERROR
    private var message = if (cloud.configured) "" else "TOURNAMENTS NEED THE CLOUD: SET UP FIREBASE (SEE README)"
    private var busy = false
    private var notice = ""
    private var noticeTime = 0f
    private val noticeColor = Color(GREEN)
    private var carIndex = save.cars.indexOfFirst { it.uid == save.selected }.coerceAtLeast(0)
    private var cls = focus?.substringAfterLast('-') ?: classOf(carIndex)

    private val classButtons = CLASSES.map { c -> button(c, Color(BTN_PURPLE)) { cls = c } }
    private val todayTab = button("TODAY", BTN_TEAL, keys = intArrayOf(Input.Keys.T)) { if (view == View.YESTERDAY) view = View.TODAY }
    private val yesterdayTab = button("YESTERDAY", BTN_TEAL, keys = intArrayOf(Input.Keys.Y)) { if (view == View.TODAY) view = View.YESTERDAY }
    private val backButton = button("BACK", BTN_GREY, keys = intArrayOf(Input.Keys.B)) { onBack() }
    private val retry = button("RETRY", BTN_RED, keys = intArrayOf(Input.Keys.R)) { refresh() }
    private val prevCar = button("<", BTN_GREY, keys = intArrayOf(Input.Keys.LEFT)) { cycle(-1) }
    private val nextCar = button(">", BTN_GREY, keys = intArrayOf(Input.Keys.RIGHT)) { cycle(1) }
    private val enter = button("ENTER", Color(0.2f, 0.55f, 0.3f, 1f), big = true, keys = intArrayOf(Input.Keys.E)) { enterSelected() }
    private val race = button("RACE", BTN_RED, big = true, keys = intArrayOf(Input.Keys.ENTER)) { raceSelected() }

    init {
        if (cloud.configured) refresh()
    }

    private fun car() = save.cars.getOrNull(carIndex)

    private fun classOf(i: Int): String = save.cars.getOrNull(i)?.let { game.economy.classFor(DragMath.perfectPass(game.effective(it)).quarter) } ?: "STREET"

    private fun cycle(d: Int) {
        if (save.cars.size < 2) return
        carIndex = (carIndex + d + save.cars.size) % save.cars.size
        if (event()?.mine == null) cls = classOf(carIndex)
    }

    private fun event(): Tournaments.Event? = (if (view == View.YESTERDAY) events.yesterday else events.today).firstOrNull { it.cls == cls }

    private fun refresh() {
        if (busy) return
        busy = true
        if (view == View.ERROR) view = View.LOADING
        events.refresh { err ->
            busy = false
            if (err != null) {
                view = View.ERROR
                message = err.uppercase().take(48)
                return@refresh
            }
            if (view == View.LOADING) view = View.TODAY
            scripted()
        }
    }

    private fun enterSelected() {
        val e = event() ?: return
        val c = car() ?: return
        if (busy || e.mine != null) return
        if (classOf(carIndex) != e.cls) return say("THAT CAR IS ${classOf(carIndex)} CLASS", RED)
        if (save.cash < e.fee) return say("THE FEE IS $" + "%,d".format(e.fee), RED)
        busy = true
        events.enter(e, c) { err ->
            busy = false
            if (err != null) return@enter say(err.uppercase(), RED)
            say("YOU'RE IN: ${e.cls} TOURNAMENT", GREEN)
            flash = 0.5f
            flashColor.set(YELLOW)
            refresh()
        }
    }

    private fun raceSelected() {
        val e = event() ?: return
        val mine = e.mine ?: return
        if (e.runsLeft <= 0 || game.transitioning) return
        // the pass runs with the car as entered; drive the same car you entered with
        val c = save.cars.firstOrNull { it.carId == mine.entry.car && classOf(save.cars.indexOf(it)) == e.cls } ?: car() ?: return
        game.go(1) { RaceScreen(game, e.track, c, tournament = e) }
    }

    /**
     * Screenshot runs: -Dpixelstrip.tournament=enter enters the selected car's class, race enters
     * if needed and then drives a pass (the autoplay driver), yesterday shows the results tab.
     */
    private var scriptDone = false
    private fun scripted() {
        if (game.shots == null || scriptDone) return
        val mode = System.getProperty("pixelstrip.tournament")
        when {
            mode == "yesterday" -> view = View.YESTERDAY
            (mode == "enter" || mode == "race") && event()?.mine == null -> return enterSelected()   // back here after the refresh
            mode == "race" -> raceSelected()
        }
        scriptDone = true
    }

    private fun say(text: String, color: Color) {
        notice = text.take(50)
        noticeColor.set(color)
        noticeTime = 3f
    }

    override fun onBack() = game.go(0) { GarageScreen(game) }

    override fun update(dt: Float) {
        noticeTime = (noticeTime - dt).coerceAtLeast(0f)
    }

    override fun layout(w: Int, h: Int) {
        val ready = view == View.TODAY || view == View.YESTERDAY
        classButtons.forEachIndexed { i, b ->
            b.apply { x = 8f + i * 62f; y = h - 62f; this.w = 58f; this.h = 16f; visible = ready }
            b.face.set(if (CLASSES[i] == cls) classColor(CLASSES[i]).cpy().mul(0.75f, 0.75f, 0.75f, 1f) else BTN_GREY)
        }
        todayTab.apply { x = w - 156f; y = h - 62f; this.w = 70f; this.h = 16f; visible = ready }
        yesterdayTab.apply { x = w - 82f; y = h - 62f; this.w = 74f; this.h = 16f; visible = ready }
        todayTab.face.set(if (view == View.TODAY) BTN_TEAL else BTN_GREY)
        yesterdayTab.face.set(if (view == View.YESTERDAY) BTN_TEAL else BTN_GREY)
        backButton.apply { x = 8f; y = 8f; this.w = 56f; this.h = 22f }
        retry.apply { x = w - 88f; y = 8f; this.w = 80f; this.h = 22f; visible = view == View.ERROR && cloud.configured }
        val e = event()
        val today = view == View.TODAY
        prevCar.apply { x = 70f; y = 8f; this.w = 18f; this.h = 22f; visible = today && e?.mine == null && save.cars.size > 1 }
        nextCar.apply { x = 236f; y = 8f; this.w = 18f; this.h = 22f; visible = today && e?.mine == null && save.cars.size > 1 }
        enter.apply {
            x = w - 128f; y = 8f; this.w = 120f; this.h = 34f
            visible = today && e != null && e.mine == null
            label = "ENTER"
        }
        race.apply {
            x = w - 128f; y = 8f; this.w = 120f; this.h = 34f
            visible = today && e?.mine != null && e.runsLeft > 0
            label = "RACE"
        }
    }

    override fun drawScene(w: Int, h: Int) {
        batch.draw(bg, floor((w - bg.width) / 2f), 0f)
        batch.setColor(0f, 0f, 0.02f, 0.72f)
        batch.draw(white, 0f, 0f, w.toFloat(), h.toFloat())
        batch.color = Color.WHITE
    }

    private fun money(v: Long) = "$" + "%,d".format(v)

    override fun drawUi(w: Int, h: Int) {
        font.draw(batch, "TOURNAMENTS", 8f, h - 24f, PINK, 2)
        cash(w - 8f, h - 20f)
        val dots = ".".repeat(1 + (time * 2f).toInt() % 3)
        when (view) {
            View.LOADING -> font.drawCentered(batch, "GETTING TODAY'S EVENTS$dots", w / 2f, h / 2f, CYAN, 2)
            View.ERROR -> {
                panel(w / 2f - 160f, h / 2f - 20f, 320f, 40f, RED)
                font.drawCentered(batch, message, w / 2f, h / 2f - 3f, RED)
            }
            View.TODAY, View.YESTERDAY -> drawEvent(w, h)
        }
        if (busy && view != View.LOADING) font.draw(batch, "...", w - 26f, h - 34f, CYAN, 1, null)
        if (noticeTime > 0f) font.drawCentered(batch, notice, w / 2f, 48f, noticeColor)
    }

    private fun drawEvent(w: Int, h: Int) {
        val e = event() ?: return
        val col = classColor(e.cls)
        val trackName = TrackDef.load(e.track).name.uppercase()
        // left: the event
        val lx = 8f
        val top = h - 76f
        panel(lx, 56f, 190f, top - 56f, col, 0.85f)
        font.draw(batch, "${e.cls}  1/4 MILE", lx + 6f, top - 14f, col, 1, null)
        font.draw(batch, trackName, lx + 6f, top - 26f, Color.WHITE, 1, null)
        font.draw(batch, "POOL", lx + 6f, top - 44f, SOFT, 1, null)
        font.draw(batch, money(e.pool), lx + 6f, top - 60f, YELLOW, 2, null)
        font.draw(batch, "${e.entrants} ENTERED   FEE ${money(e.fee)}", lx + 6f, top - 74f, SOFT, 1, null)
        if (view == View.TODAY) {
            val left = max(0L, e.ends - (System.currentTimeMillis() + events.clockSkew)) / 1000
            font.draw(batch, "ENDS IN %d:%02d:%02d".format(left / 3600, left / 60 % 60, left % 60), lx + 6f, top - 86f, if (left < 3600) ORANGE else SOFT, 1, null)
            val shares = e.prizes.take(3).mapIndexed { i, p -> "${i + 1}. ${money(p)}" }.joinToString("  ")
            font.draw(batch, shares, lx + 6f, top - 98f, GREEN, 1, null)
            val mine = e.mine
            if (mine != null) {
                val best = mine.entry.best?.let { "BEST %.3f".format(it.et) } ?: "NO TIME YET"
                val rank = if (mine.rank > 0) "  #${mine.rank}" else ""
                font.draw(batch, best + rank, lx + 6f, top - 116f, CYAN, 1, null)
                font.draw(batch, "${e.runsLeft} OF ${Tournaments.MAX_RUNS} RUNS LEFT", lx + 6f, top - 128f, SOFT, 1, null)
            } else {
                font.draw(batch, "TOP TEN SPLIT THE POOL", lx + 6f, top - 116f, SOFT, 1, null)
            }
        } else {
            val mine = e.results?.firstOrNull { it.uid == cloud.accountId }
            font.draw(batch, if (e.settled) "FINISHED" else "NOBODY RACED", lx + 6f, top - 86f, SOFT, 1, null)
            if (mine != null) font.draw(batch, if (mine.prize > 0) "YOU WON ${money(mine.prize)}" else "YOU RAN %.3f".format(mine.et), lx + 6f, top - 100f, GREEN, 1, null)
        }

        // right: the board (today) or the results (yesterday)
        val rx = 204f
        val rw = w - rx - 8f
        panel(rx, 56f, rw, top - 56f, CYAN, 0.85f)
        font.draw(batch, if (view == View.TODAY) "LEADERBOARD" else "RESULTS", rx + 6f, top - 14f, CYAN, 1, null)
        val rows: List<Triple<String, String, Pair<Float, Long>>> = if (view == View.TODAY)
            e.board.map { Triple(it.tagged, it.car, (it.best?.et ?: 0f) to 0L) }
        else e.results?.map { Triple(it.tag?.let { t -> "[$t] ${it.name}" } ?: it.name, it.car ?: "", it.et to it.prize) } ?: emptyList()
        val me = e.mine?.entry?.uid ?: cloud.accountId
        val myName = e.mine?.entry?.tagged
        if (rows.isEmpty()) font.drawCentered(batch, if (view == View.TODAY) "NO TIMES YET: SET THE FIRST" else "NO RESULTS", rx + rw / 2f, top / 2f + 30f, SOFT)
        rows.take(10).forEachIndexed { i, (name, carId, v) ->
            val y = top - 30f - i * 12f
            val mineRow = (view == View.TODAY && e.board.getOrNull(i)?.uid == me) || (view == View.YESTERDAY && e.results?.getOrNull(i)?.uid == me) || name == myName
            val c = if (mineRow) PINK else if (i == 0) YELLOW else Color.WHITE
            font.draw(batch, "${i + 1}", rx + 6f, y, c, 1, null)
            font.draw(batch, name.take(16), rx + 20f, y, c, 1, null)
            val carName = runCatching { game.car(carId).name.uppercase() }.getOrDefault("").take(10)
            val right = "%.3f".format(v.first) + if (v.second > 0) "  " + money(v.second) else ""
            font.draw(batch, right, rx + rw - 6f - font.width(right), y, c, 1, null)
            if (rw > 260f) font.draw(batch, carName, rx + 124f, y, SOFT, 1, null)
        }

        // bottom: the car you'd enter with
        if (view == View.TODAY && e.mine == null) {
            val c = car()
            if (c != null) {
                val ccls = classOf(carIndex)
                val name = game.car(c.carId).name.uppercase().take(16)
                font.drawCentered(batch, name, 162f, 21f, Color.WHITE)
                font.drawCentered(batch, if (ccls == e.cls) "$ccls: ELIGIBLE" else "$ccls CLASS: NOT THIS ONE", 162f, 11f, if (ccls == e.cls) GREEN else RED)
            }
            font.draw(batch, "FEE ${money(e.fee)}", w - 128f, 46f, if (save.cash >= e.fee) YELLOW else RED, 1, null)
        } else if (view == View.TODAY) {
            val mine = e.mine!!
            font.drawCentered(batch, "ENTERED: ${game.car(mine.entry.car).name.uppercase()}", 162f, 16f, CYAN)
            if (e.runsLeft <= 0) font.draw(batch, "ALL RUNS USED", w - 128f, 20f, SOFT, 1, null)
        }
    }

    companion object {
        val CLASSES = listOf("STREET", "SPORT", "PRO", "OUTLAW")
    }
}

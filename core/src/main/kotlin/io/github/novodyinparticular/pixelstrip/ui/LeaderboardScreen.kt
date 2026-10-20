package io.github.novodyinparticular.pixelstrip.ui

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import io.github.novodyinparticular.pixelstrip.PixelStrip
import io.github.novodyinparticular.pixelstrip.net.Leaderboards
import io.github.novodyinparticular.pixelstrip.race.DragMath
import kotlin.math.floor

/**
 * Leaderboards: the quickest server-checked ETs by class and distance, the most online wins, and
 * the top crews, each this week or all time (wins and crews). Your own row is highlighted, and
 * your place shown even when you're further down.
 */
class LeaderboardScreen(game: PixelStrip) : MenuScreen(game) {
    private enum class Tab { QUICKEST, WINS, CREWS }

    private val bg = art.tex("scenes/garage.png")
    private val cloud = game.cloud
    private val boards = Leaderboards(cloud)
    override val song = "career"

    private var tab = Tab.QUICKEST
    private var cls = game.save.selectedCar()?.let { game.economy.classFor(DragMath.perfectPass(game.effective(it)).quarter) } ?: "STREET"
    private var quarter = true
    private var week = true
    private var board: Leaderboards.Board? = null
    private var error: String? = if (cloud.configured) null else "LEADERBOARDS NEED THE CLOUD: SET UP FIREBASE (SEE README)"
    private var busy = false
    private var page = 0
    /** Bumped on every request, so a slow answer for an old choice is ignored. */
    private var asked = 0

    private val tabButtons = Tab.entries.map { t -> button(t.name, Color(BTN_PURPLE)) { tab = t; reload() } }
    private val classButtons = TournamentScreen.CLASSES.map { c -> button(c, Color(BTN_PURPLE)) { cls = c; reload() } }
    private val distButton = button("", BTN_TEAL, keys = intArrayOf(Input.Keys.D)) { quarter = !quarter; reload() }
    private val periodButton = button("", BTN_TEAL, keys = intArrayOf(Input.Keys.W)) { week = !week; reload() }
    private val up = button("^", BTN_GREY, keys = intArrayOf(Input.Keys.UP)) { page = (page - 1).coerceAtLeast(0) }
    private val down = button("V", BTN_GREY, keys = intArrayOf(Input.Keys.DOWN)) { if ((page + 1) * ROWS < (board?.rows?.size ?: 0)) page++ }
    private val backButton = button("BACK", BTN_GREY, keys = intArrayOf(Input.Keys.B)) { onBack() }
    private val retry = button("RETRY", BTN_RED, keys = intArrayOf(Input.Keys.R)) { reload() }

    init {
        System.getProperty("pixelstrip.board")?.takeIf { game.shots != null }?.let { b -> Tab.entries.firstOrNull { it.name == b.uppercase() }?.let { tab = it } }
        if (cloud.configured) reload()
    }

    private fun reload() {
        if (!cloud.configured) return
        val ticket = ++asked
        busy = true
        error = null
        page = 0
        val kind = when (tab) { Tab.QUICKEST -> "et"; Tab.WINS -> "wins"; Tab.CREWS -> "crews" }
        boards.load(kind, cls, quarter, week) { b, err ->
            if (ticket != asked) return@load
            busy = false
            board = b
            error = err?.uppercase()?.take(48)
        }
    }

    override fun onBack() = game.go(0) { GarageScreen(game) }

    override fun layout(w: Int, h: Int) {
        tabButtons.forEachIndexed { i, b ->
            b.apply { x = 8f + i * 74f; y = h - 62f; this.w = 70f; this.h = 16f }
            b.face.set(if (Tab.entries[i] == tab) PINK else BTN_GREY)
        }
        classButtons.forEachIndexed { i, b ->
            b.apply { x = 8f + i * 62f; y = h - 82f; this.w = 58f; this.h = 14f; visible = tab == Tab.QUICKEST }
            b.face.set(if (TournamentScreen.CLASSES[i] == cls) classColor(cls).cpy().mul(0.75f, 0.75f, 0.75f, 1f) else BTN_GREY)
        }
        distButton.apply { x = w - 92f; y = h - 62f; this.w = 84f; this.h = 16f; visible = tab == Tab.QUICKEST; label = if (quarter) "1/4 MILE" else "1/8 MILE" }
        periodButton.apply { x = w - 92f; y = h - 62f; this.w = 84f; this.h = 16f; visible = tab != Tab.QUICKEST; label = if (week) "THIS WEEK" else "ALL TIME" }
        val rows = board?.rows?.size ?: 0
        up.apply { x = w - 26f; y = listTop(h) - 16f; this.w = 18f; this.h = 16f; visible = page > 0 }
        down.apply { x = w - 26f; y = 64f; this.w = 18f; this.h = 16f; visible = (page + 1) * ROWS < rows }
        backButton.apply { x = 8f; y = 8f; this.w = 56f; this.h = 22f }
        retry.apply { x = w - 88f; y = 8f; this.w = 80f; this.h = 22f; visible = error != null && cloud.configured }
    }

    private fun listTop(h: Int) = h - 92f

    override fun drawScene(w: Int, h: Int) {
        batch.draw(bg, floor((w - bg.width) / 2f), 0f)
        batch.setColor(0f, 0f, 0.02f, 0.72f)
        batch.draw(white, 0f, 0f, w.toFloat(), h.toFloat())
        batch.color = Color.WHITE
    }

    override fun drawUi(w: Int, h: Int) {
        font.draw(batch, "LEADERBOARDS", 8f, h - 24f, PINK, 2)
        cash(w - 8f, h - 20f)
        val top = listTop(h)
        val px = 8f
        val pw = w - 40f
        panel(px, 60f, pw, top - 56f, if (tab == Tab.QUICKEST) classColor(cls) else CYAN, 0.85f)
        val err = error
        val b = board
        when {
            err != null -> font.drawCentered(batch, err, w / 2f, h / 2f - 20f, RED)
            b == null -> font.drawCentered(batch, "LOADING" + ".".repeat(1 + (time * 2f).toInt() % 3), w / 2f, h / 2f - 20f, CYAN)
            b.rows.isEmpty() -> font.drawCentered(batch, if (tab == Tab.QUICKEST) "NO VERIFIED TIMES YET: RACE ONLINE OR ENTER A TOURNAMENT" else "NOBODY YET",
                w / 2f, h / 2f - 20f, SOFT)
            else -> drawRows(b, px, pw, top)
        }
        // your place, even off the page
        val mine = b?.mine
        val you = when {
            b == null || err != null -> null
            mine == null -> when (tab) {
                Tab.QUICKEST -> "YOU: NO VERIFIED $cls TIME YET"
                Tab.WINS -> "YOU: NO ONLINE WINS ${if (week) "THIS WEEK" else "YET"}"
                Tab.CREWS -> if (cloud.badge == null) "YOU: NOT IN A CREW" else "YOUR CREW: NOT ON THE BOARD"
            }
            else -> "YOU: #${b.place}  " + when (tab) {
                Tab.QUICKEST -> "%.3f".format(mine.et)
                Tab.WINS -> "${mine.wins} WINS"
                Tab.CREWS -> "[${mine.tag}] ${mine.wins} WINS"
            }
        }
        you?.let { font.draw(batch, it, 72f, 16f, if (mine != null) PINK else SOFT, 1, null) }
        if (busy && b != null) font.draw(batch, "...", w - 26f, h - 34f, CYAN, 1, null)
    }

    private fun drawRows(b: Leaderboards.Board, px: Float, pw: Float, top: Float) {
        val me = cloud.accountId
        val myCrew = cloud.badge?.tag
        b.rows.drop(page * ROWS).take(ROWS).forEachIndexed { i, r ->
            val n = page * ROWS + i + 1
            val y = top - 16f - i * 14f
            val mineRow = if (tab == Tab.CREWS) r.tag == myCrew else r.id == me
            val c = when {
                mineRow -> PINK
                n == 1 -> YELLOW
                n <= 3 -> CYAN
                else -> Color.WHITE
            }
            font.draw(batch, "$n", px + 8f, y, c, 1, null)
            var nx = px + 28f
            r.emblem?.let {
                batch.draw(art.emblems.texture(it, 10), nx, y - 2f)
                nx += 14f
            }
            font.draw(batch, r.tagged.take(22), nx, y, c, 1, null)
            val right = when (tab) {
                Tab.QUICKEST -> "%.3f".format(r.et)
                Tab.WINS -> "${r.wins} W"
                Tab.CREWS -> "${r.wins} W  ${r.members}/20"
            }
            font.draw(batch, right, px + pw - 8f - font.width(right), y, c, 1, null)
            if (tab == Tab.QUICKEST && pw > 380f) {
                val car = r.car?.let { id -> runCatching { game.car(id).name.uppercase() }.getOrNull() } ?: ""
                val mph = if (game.settings.metric) "%.0f KMH".format(r.mph * 1.60934f) else "%.0f MPH".format(r.mph)
                font.draw(batch, car.take(14), px + pw * 0.5f, y, SOFT, 1, null)
                font.draw(batch, mph, px + pw - 8f - font.width(right) - 12f - font.width(mph), y, SOFT, 1, null)
            }
        }
        val pages = (b.rows.size + ROWS - 1) / ROWS
        if (pages > 1) font.draw(batch, "${page + 1}/$pages", px + pw + 6f, (top + 60f) / 2f, SOFT, 1, null)
    }

    companion object {
        const val ROWS = 10
    }
}

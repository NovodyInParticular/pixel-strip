package io.github.novodyinparticular.pixelstrip.ui

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.utils.JsonValue
import io.github.novodyinparticular.pixelstrip.PixelStrip
import io.github.novodyinparticular.pixelstrip.net.Cloud
import io.github.novodyinparticular.pixelstrip.net.OnlineRace
import io.github.novodyinparticular.pixelstrip.race.RaceScreen
import io.github.novodyinparticular.pixelstrip.save.SaveGame
import kotlin.math.floor
import kotlin.math.max

/**
 * The online lobby: make sure the server has this garage, pick the stakes (for fun, cash, or pink
 * slips), find an opponent with a similar build staking the same, then both accept before the
 * server takes the stakes and the cars roll to the line.
 */
class OnlineScreen(game: PixelStrip) : MenuScreen(game) {
    private enum class State { SYNCING, CHOOSE, SEARCHING, CONFIRM, FOUND, ERROR }

    private val bg = art.tex("scenes/garage.png")
    private val stage = CarStage()
    private val owned = game.save.selectedCar()!!
    private val cloud = game.cloud
    private val shots = game.shots
    private var state = if (cloud.configured) State.SYNCING else State.ERROR
    private var message = if (cloud.configured) "" else "ONLINE NEEDS THE CLOUD: SET UP FIREBASE (SEE README)"
    private var rating = 0
    private var race: OnlineRace? = null
    private var poll = 0f
    private var waited = 0f
    private var foundTime = 0f
    private var busy = false
    private var syncAsked = false
    override val song = "career"

    /** 0 fun, 1 cash, 2 pink slip; and the bet, stepped through [BETS]. */
    private var stake = 0
    private var bet = 1

    private val cancel = button("CANCEL", BTN_GREY, keys = intArrayOf(Input.Keys.B)) { onBack() }
    private val retry = button("RETRY", BTN_RED, keys = intArrayOf(Input.Keys.R)) { state = State.SYNCING; message = ""; syncAsked = false }
    private val fun_ = button("FOR FUN", Color(BTN_PURPLE), keys = intArrayOf(Input.Keys.NUM_1)) { stake = 0 }
    private val cash = button("CASH BET", Color(BTN_PURPLE), keys = intArrayOf(Input.Keys.NUM_2)) { stake = 1 }
    private val pink = button("PINK SLIP", Color(BTN_PURPLE), keys = intArrayOf(Input.Keys.NUM_3)) { if (game.save.cars.size > 1) stake = 2 }
    private val less = button("-", BTN_GREY, keys = intArrayOf(Input.Keys.MINUS)) { bet = max(0, bet - 1) }
    private val more = button("+", BTN_GREY, keys = intArrayOf(Input.Keys.EQUALS)) { if (bet < BETS.size - 1 && BETS[bet + 1] <= game.save.cash) bet++ }
    private val find = button("RACE", BTN_RED, big = true, keys = intArrayOf(Input.Keys.ENTER)) { join() }
    private val accept = button("ACCEPT", Color(0.2f, 0.55f, 0.3f, 1f), big = true, keys = intArrayOf(Input.Keys.Y)) { answer(true) }
    private val decline = button("DECLINE", BTN_RED, keys = intArrayOf(Input.Keys.N)) { answer(false) }

    override fun onBack() {
        when (state) {
            State.SEARCHING -> cloud.call("cancelMatch", JsonValue(JsonValue.ValueType.`object`)) { _, _ -> }
            State.CONFIRM -> race?.confirm(false) { }
            else -> {}
        }
        game.go(0) { GarageScreen(game) }
    }

    private fun betAmount() = BETS[bet.coerceIn(0, BETS.size - 1)]
    private fun stakeLabel() = when (stake) {
        1 -> "$" + "%,d".format(betAmount())
        2 -> "PINK SLIPS: THIS CAR"
        else -> "FOR FUN"
    }

    override fun layout(w: Int, h: Int) {
        val choosing = state == State.CHOOSE
        cancel.apply { x = 8f; y = 8f; this.w = 70f; this.h = 22f; visible = state != State.FOUND }
        retry.apply { x = w - 88f; y = 8f; this.w = 80f; this.h = 22f; visible = state == State.ERROR && cloud.configured }
        listOf(fun_, cash, pink).forEachIndexed { i, b ->
            b.apply { x = w / 2f - 141f + i * 96f; y = 96f; this.w = 90f; this.h = 18f; visible = choosing }
            b.face.set(if (stake == i) PINK else if (i == 2 && game.save.cars.size < 2) BTN_GREY else BTN_PURPLE)
        }
        less.apply { x = w / 2f - 60f; y = 72f; this.w = 18f; this.h = 16f; visible = choosing && stake == 1 }
        more.apply { x = w / 2f + 42f; y = 72f; this.w = 18f; this.h = 16f; visible = choosing && stake == 1 }
        find.apply { x = w - 128f; y = 8f; this.w = 120f; this.h = 34f; visible = choosing && stakeOk() }
        accept.apply { x = w - 128f; y = 8f; this.w = 120f; this.h = 34f; visible = state == State.CONFIRM && race?.accepted != true }
        decline.apply { x = w - 218f; y = 8f; this.w = 84f; this.h = 22f; visible = state == State.CONFIRM && race?.accepted != true }
    }

    private fun stakeOk() = when (stake) {
        1 -> game.save.cash >= betAmount() && game.save.cash >= 0
        2 -> game.save.cars.size > 1 && game.save.cash >= 0
        else -> true
    }

    override fun update(dt: Float) {
        when (state) {
            State.SYNCING -> {
                if (!syncAsked) {
                    syncAsked = true
                    cloud.requestSync()
                }
                if (cloud.status == Cloud.Status.SYNCED && game.save.pending.isEmpty() && !busy) {
                    state = State.CHOOSE
                    bet = BETS.indexOfLast { it <= game.save.cash }.coerceAtLeast(0).coerceAtMost(2)
                    if (shots != null && System.getProperty("pixelstrip.stake") != "choose") {
                        // screenshot runs pick their stakes from -Dpixelstrip.stake (cash:AMOUNT or pink) and race straight away
                        val s = System.getProperty("pixelstrip.stake", "none")
                        stake = when { s.startsWith("cash") -> 1; s == "pink" -> 2; else -> 0 }
                        s.substringAfter("cash:", "").toLongOrNull()?.let { a -> bet = BETS.indexOfFirst { it >= a }.coerceAtLeast(0) }
                        join()
                    }
                }
                if (cloud.status == Cloud.Status.ERROR) fail(cloud.lastError ?: "CAN'T REACH THE SERVER")
            }
            State.SEARCHING -> {
                waited += dt
                poll -= dt
                if (poll <= 0f && !busy) {
                    poll = 1f
                    val uid = cloud.accountId ?: return
                    busy = true
                    cloud.dbGet("matches/$uid") { id ->
                        busy = false
                        if (id != null && id.isString) openRoom(id.asString())
                    }
                }
            }
            State.CONFIRM -> {
                val r = race!!
                if (shots != null && !r.accepted && !busy) answer(true)
                poll -= dt
                if (poll <= 0f && !busy) {
                    poll = 0.5f
                    busy = true
                    r.pollState { s ->
                        busy = false
                        onState(s)
                    }
                }
                // the server calls it off at the deadline; ask it once the time's up
                if (System.currentTimeMillis() > r.confirmBy + 1500 && !busy) {
                    busy = true
                    cloud.call("raceResult", JsonValue(JsonValue.ValueType.`object`).also { it.addChild("raceId", JsonValue(r.raceId)) }) { _, _ ->
                        busy = false
                        poll = 0f
                    }
                }
            }
            State.FOUND -> {
                foundTime += dt
                val r = race!!
                if (foundTime > VS_HOLD_S && !game.transitioning) game.go(2) { RaceScreen(game, r.track, owned, online = r) }
            }
            State.CHOOSE, State.ERROR -> {}
        }
    }

    private fun fail(why: String) {
        state = State.ERROR
        message = why.uppercase().take(48)
        busy = false
    }

    private fun join() {
        if (busy || !stakeOk()) return
        busy = true
        val data = JsonValue(JsonValue.ValueType.`object`)
        data.addChild("car", JsonValue(owned.uid))
        data.addChild("quarter", JsonValue(true))
        owned.tune?.let { data.addChild("tune", SaveGame.tuneJson(it)) }
        data.addChild("wager", JsonValue(JsonValue.ValueType.`object`).also { w ->
            w.addChild("kind", JsonValue(arrayOf("none", "cash", "pink")[stake]))
            if (stake == 1) w.addChild("amount", JsonValue(betAmount()))
        })
        cloud.call("matchmake", data) { r, err ->
            busy = false
            if (r == null) return@call fail(err ?: "MATCHMAKING FAILED")
            rating = r.getInt("rating", rating)
            val id = r.getString("raceId", null)
            if (id != null) matched(OnlineRace(cloud, id, r.get("room")))
            else {
                state = State.SEARCHING
                waited = 0f
                poll = 0.5f
            }
        }
    }

    private fun openRoom(id: String) {
        busy = true
        cloud.dbGet("races/$id") { room ->
            busy = false
            if (room == null || room.get("players") == null) return@dbGet
            matched(OnlineRace(cloud, id, room))
        }
    }

    private fun matched(r: OnlineRace) {
        race = r
        rating = r.me.rating
        flash = 0.6f
        flashColor.set(YELLOW)
        if (r.wager.none) found() else {
            state = State.CONFIRM
            poll = 0.5f
        }
    }

    private fun found() {
        state = State.FOUND
        foundTime = 0f
    }

    private fun answer(yes: Boolean) {
        val r = race ?: return
        busy = true
        r.confirm(yes) { s ->
            busy = false
            onState(s)
        }
    }

    private fun onState(s: String) {
        when (s) {
            "racing" -> if (state == State.CONFIRM) found()
            "off" -> {
                state = State.CHOOSE
                message = "CALLED OFF: " + (race?.offReason ?: "NO ANSWER").uppercase().take(36)
                race = null
                cloud.requestSync()
            }
        }
    }

    override fun drawScene(w: Int, h: Int) {
        batch.draw(bg, floor((w - bg.width) / 2f), 0f)
        stage.draw(game.effective(owned), owned.paint, w / 2f - 10f, GarageScreen.GROUND, GarageScreen.AMBIENT,
            looks = io.github.novodyinparticular.pixelstrip.race.CarLooks.of(owned, game.parts))
    }

    override fun drawUi(w: Int, h: Int) {
        font.drawCentered(batch, "ONLINE", w / 2f, h - 30f, PINK, 3)
        cash(w - 8f, h - 20f)
        val py = 130f
        panel(w / 2f - 160f, py - 6f, 320f, 64f, if (state == State.ERROR) RED else if (state == State.CONFIRM) YELLOW else CYAN, 0.88f)
        font.drawCentered(batch, game.car(owned.carId).name.uppercase() + "  " + owned.tuneName, w / 2f, py + 46f, SOFT)
        val dots = ".".repeat(1 + (time * 2f).toInt() % 3)
        when (state) {
            State.SYNCING -> font.drawCentered(batch, "SENDING YOUR GARAGE TO THE SERVER$dots", w / 2f, py + 22f, CYAN)
            State.CHOOSE -> {
                font.drawCentered(batch, "WHAT'S ON THE LINE?", w / 2f, py + 34f, YELLOW)
                font.drawCentered(batch, stakeLabel(), w / 2f, py + 14f, if (stakeOk()) Color.WHITE else RED, 2)
                val note = when {
                    message.isNotEmpty() -> message
                    stake == 2 && game.save.cars.size < 2 -> "PINK SLIPS NEED A SECOND CAR"
                    game.save.cash < 0 -> "NEGATIVE BALANCE: NO BETTING"
                    stake == 2 -> "LOSE AND THIS CAR IS THEIRS"
                    stake == 1 -> "WINNER TAKES BOTH STAKES"
                    else -> "NOTHING ON THE LINE"
                }
                font.drawCentered(batch, note, w / 2f, py + 2f, if (message.isNotEmpty()) RED else SOFT)
            }
            State.SEARCHING -> {
                font.drawCentered(batch, "FINDING A RACER$dots", w / 2f, py + 26f, YELLOW, 2)
                font.drawCentered(batch, "RATING $rating   ${stakeLabel()}   ${waited.toInt()} S", w / 2f, py + 12f, SOFT)
                font.drawCentered(batch, "THE SEARCH WIDENS THE LONGER YOU WAIT", w / 2f, py + 2f, SOFT)
            }
            State.CONFIRM -> {
                val r = race!!
                val left = max(0L, (r.confirmBy - System.currentTimeMillis()) / 1000)
                font.drawCentered(batch, "${r.them.name}: ${game.car(r.them.car).name.uppercase()}  RATING ${r.them.rating}", w / 2f, py + 32f, ORANGE)
                font.drawCentered(batch, r.wager.label, w / 2f, py + 16f, Color.WHITE, 2)
                font.drawCentered(batch, if (r.accepted) "ACCEPTED: WAITING FOR THEM$dots" else "ACCEPT IN $left S", w / 2f, py + 2f, if (left < 8) RED else YELLOW)
            }
            State.FOUND -> {
                val r = race!!
                font.drawCentered(batch, r.me.name, w / 2f - 90f, py + 26f, CYAN, 2)
                font.drawCentered(batch, "VS", w / 2f, py + 24f, PINK, 3)
                font.drawCentered(batch, r.them.name, w / 2f + 90f, py + 26f, ORANGE, 2)
                font.drawCentered(batch, game.car(r.them.car).name.uppercase() + "  RATING ${r.them.rating}", w / 2f + 70f, py + 10f, SOFT)
                font.drawCentered(batch, if (r.wager.none) "TO THE LINE" else "STAKES HELD: ${r.wager.label}", w / 2f, py, YELLOW)
            }
            State.ERROR -> font.drawCentered(batch, message, w / 2f, py + 22f, RED)
        }
    }

    companion object {
        const val VS_HOLD_S = 2.4f
        /** Bet steps; any of them up to what you have (there's no cap beyond your own cash). */
        val BETS = longArrayOf(500, 1_000, 2_500, 5_000, 10_000, 25_000, 50_000, 100_000, 250_000, 500_000, 1_000_000,
            2_500_000, 5_000_000, 10_000_000, 25_000_000, 50_000_000, 100_000_000)
    }
}

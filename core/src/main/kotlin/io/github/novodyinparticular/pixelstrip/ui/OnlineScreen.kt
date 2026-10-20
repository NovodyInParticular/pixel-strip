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

/**
 * The online lobby: makes sure the server has this garage, joins matchmaking with the selected car
 * and its tune, waits for an opponent with a similar build, then rolls to the line.
 */
class OnlineScreen(game: PixelStrip) : MenuScreen(game) {
    private enum class State { SYNCING, SEARCHING, FOUND, ERROR }

    private val bg = art.tex("scenes/garage.png")
    private val stage = CarStage()
    private val owned = game.save.selectedCar()!!
    private val cloud = game.cloud
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

    private val cancel = button("CANCEL", BTN_GREY, keys = intArrayOf(Input.Keys.B)) { onBack() }
    private val retry = button("RETRY", BTN_RED, keys = intArrayOf(Input.Keys.ENTER)) { state = State.SYNCING; message = ""; syncAsked = false }

    override fun onBack() {
        if (state == State.SEARCHING) cloud.call("cancelMatch", JsonValue(JsonValue.ValueType.`object`)) { _, _ -> }
        game.go(0) { GarageScreen(game) }
    }

    override fun layout(w: Int, h: Int) {
        cancel.apply { x = 8f; y = 8f; this.w = 70f; this.h = 22f; visible = state != State.FOUND }
        retry.apply { x = w - 88f; y = 8f; this.w = 80f; this.h = 22f; visible = state == State.ERROR && cloud.configured }
    }

    override fun update(dt: Float) {
        when (state) {
            State.SYNCING -> {
                if (!syncAsked) {
                    syncAsked = true
                    cloud.requestSync()
                }
                if (cloud.status == Cloud.Status.SYNCED && game.save.pending.isEmpty() && !busy) join()
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
            State.FOUND -> {
                foundTime += dt
                val r = race!!
                if (foundTime > VS_HOLD_S && !game.transitioning) game.go(2) { RaceScreen(game, r.track, owned, online = r) }
            }
            State.ERROR -> {}
        }
    }

    private fun fail(why: String) {
        state = State.ERROR
        message = why.uppercase().take(44)
        busy = false
    }

    private fun join() {
        busy = true
        val data = JsonValue(JsonValue.ValueType.`object`)
        data.addChild("car", JsonValue(owned.uid))
        data.addChild("quarter", JsonValue(true))
        owned.tune?.let { data.addChild("tune", SaveGame.tuneJson(it)) }
        cloud.call("matchmake", data) { r, err ->
            busy = false
            if (r == null) return@call fail(err ?: "MATCHMAKING FAILED")
            rating = r.getInt("rating", rating)
            val id = r.getString("raceId", null)
            if (id != null) found(OnlineRace(cloud, id, r.get("room")))
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
            found(OnlineRace(cloud, id, room))
        }
    }

    private fun found(r: OnlineRace) {
        race = r
        rating = r.me.rating
        state = State.FOUND
        foundTime = 0f
        flash = 0.6f
        flashColor.set(YELLOW)
    }

    override fun drawScene(w: Int, h: Int) {
        batch.draw(bg, floor((w - bg.width) / 2f), 0f)
        stage.draw(game.effective(owned), owned.paint, w / 2f - 10f, GarageScreen.GROUND, GarageScreen.AMBIENT,
            looks = io.github.novodyinparticular.pixelstrip.race.CarLooks.of(owned, game.parts))
    }

    override fun drawUi(w: Int, h: Int) {
        font.drawCentered(batch, "ONLINE", w / 2f, h - 30f, PINK, 3)
        // an info panel under the garage's neon sign
        val py = 130f
        panel(w / 2f - 160f, py - 6f, 320f, 64f, if (state == State.ERROR) RED else CYAN, 0.88f)
        font.drawCentered(batch, game.car(owned.carId).name.uppercase() + "  " + owned.tuneName, w / 2f, py + 46f, SOFT)
        val dots = ".".repeat(1 + (time * 2f).toInt() % 3)
        when (state) {
            State.SYNCING -> font.drawCentered(batch, "SENDING YOUR GARAGE TO THE SERVER$dots", w / 2f, py + 22f, CYAN)
            State.SEARCHING -> {
                font.drawCentered(batch, "FINDING A RACER$dots", w / 2f, py + 26f, YELLOW, 2)
                font.drawCentered(batch, "RATING $rating   WAITING ${waited.toInt()} S", w / 2f, py + 12f, SOFT)
                font.drawCentered(batch, "THE SEARCH WIDENS THE LONGER YOU WAIT", w / 2f, py + 2f, SOFT)
            }
            State.FOUND -> {
                val r = race!!
                font.drawCentered(batch, r.me.name, w / 2f - 90f, py + 26f, CYAN, 2)
                font.drawCentered(batch, "VS", w / 2f, py + 24f, PINK, 3)
                font.drawCentered(batch, r.them.name, w / 2f + 90f, py + 26f, ORANGE, 2)
                font.drawCentered(batch, game.car(r.them.car).name.uppercase() + "  RATING ${r.them.rating}", w / 2f + 70f, py + 10f, SOFT)
                font.drawCentered(batch, "TO THE LINE", w / 2f, py, YELLOW)
            }
            State.ERROR -> font.drawCentered(batch, message, w / 2f, py + 22f, RED)
        }
    }

    companion object {
        const val VS_HOLD_S = 2.4f
    }
}

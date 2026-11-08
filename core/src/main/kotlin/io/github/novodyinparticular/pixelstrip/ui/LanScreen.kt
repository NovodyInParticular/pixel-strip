package io.github.novodyinparticular.pixelstrip.ui

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.MathUtils
import io.github.novodyinparticular.pixelstrip.PixelStrip
import io.github.novodyinparticular.pixelstrip.data.TrackDef
import io.github.novodyinparticular.pixelstrip.net.Lan
import io.github.novodyinparticular.pixelstrip.net.LanRace
import io.github.novodyinparticular.pixelstrip.net.LanSession
import io.github.novodyinparticular.pixelstrip.net.OnlineRace
import io.github.novodyinparticular.pixelstrip.race.Distance
import io.github.novodyinparticular.pixelstrip.race.RaceScreen
import kotlin.math.floor

/**
 * Head-to-head over the local Wi-Fi, no server needed: one player hosts, the other joins (the
 * host shows up by itself on the same network, or type its address). The host picks the track
 * and distance and starts each race; after a race both come back here for the rematch.
 * Nothing is staked and no XP is given: there's no server to check either run.
 */
class LanScreen(game: PixelStrip, existing: LanSession? = null) : MenuScreen(game) {
    private enum class State { MENU, HOSTING, FINDING, CONNECTING, LOBBY, FOUND, ERROR }

    private val bg = art.tex("scenes/garage.png")
    private val stage = CarStage()
    private val owned = game.save.selectedCar()!!
    private val shots = game.shots
    private val auto = if (shots != null) System.getProperty("pixelstrip.lan") else null
    override val song = "career"

    private var state = if (existing != null) State.LOBBY else State.MENU
    private var message = ""
    private var session: LanSession? = existing
    private var hosting: Lan.Host? = null
    private var finder: Lan.Finder? = null
    private var connecting: Lan.Connect? = null
    private var foundTime = 0f
    private var race: LanRace? = null
    private var helloSent = false
    private var readySent = false

    private var trackIndex = existing?.pickTrack?.let { TrackDef.GENERAL.indexOf(it) }?.takeIf { it >= 0 } ?: 0
    private var dist = existing?.pickDistance ?: Distance.QUARTER
    private val nameEntry = TextEntry(12)
    private val ipEntry = TextEntry(15) { it in '0'..'9' || it == '.' }

    private val back = button("BACK", BTN_GREY, keys = intArrayOf(Input.Keys.B)) { onBack() }
    private val hostBtn = button("HOST", BTN_RED, big = true, keys = intArrayOf(Input.Keys.H)) { host() }
    private val joinBtn = button("JOIN", Color(0.15f, 0.35f, 0.75f, 1f), big = true, keys = intArrayOf(Input.Keys.J)) { find() }
    private val nameBtn = button("NAME", BTN_PURPLE, keys = intArrayOf(Input.Keys.N)) { askName() }
    private val typeIp = button("TYPE ADDRESS", BTN_PURPLE, keys = intArrayOf(Input.Keys.A)) { askIp() }
    private val hostRows = List(ROWS) { i -> button("", BTN_TEAL, keys = intArrayOf(Input.Keys.NUM_1 + i)) { pickHost(i) } }
    private val prevTrack = button("<", BTN_GREY, keys = intArrayOf(Input.Keys.LEFT)) { changeTrack(-1) }
    private val nextTrack = button(">", BTN_GREY, keys = intArrayOf(Input.Keys.RIGHT)) { changeTrack(1) }
    private val distBtn = button("1/4 MILE", Color(0.45f, 0.2f, 0.55f, 1f), keys = intArrayOf(Input.Keys.T)) {
        dist = dist.next()
        session?.sayPick(TrackDef.GENERAL[trackIndex], dist)
    }
    private val raceBtn = button("RACE", BTN_RED, big = true, keys = intArrayOf(Input.Keys.ENTER)) { startRace() }

    private val myName get() = game.settings.lanName.ifEmpty { game.cloud.displayName ?: "RACER" }

    init {
        if (existing != null) game.platform.lanDiscovery(false)
        when (auto) {
            "host" -> host()
            "join" -> find()
        }
    }

    private fun me() = OnlineRace.Player("me", myName, owned.carId, owned.paint, owned.parts.toMap(), owned.cosmetics.toMap(), 0)

    override fun onBack() {
        if (typing?.active == true) return
        closeAll()
        game.go(0) { GarageScreen(game) }
    }

    private fun closeAll() {
        hosting?.close()
        finder?.close()
        session?.leave()
        game.platform.lanDiscovery(false)
    }

    private fun fail(why: String) {
        hosting?.close()
        finder?.close()
        game.platform.lanDiscovery(false)
        session?.leave()
        session = null
        state = State.ERROR
        message = why.uppercase().take(52)
    }

    private fun host() {
        if (state != State.MENU) return
        try {
            hosting = Lan.Host(myName)
            state = State.HOSTING
        } catch (e: Exception) {
            fail("CAN'T HOST: " + (e.message ?: "NETWORK ERROR"))
        }
    }

    private fun find() {
        if (state != State.MENU) return
        game.platform.lanDiscovery(true)
        finder = Lan.Finder()
        state = State.FINDING
    }

    private fun pickHost(i: Int) {
        val f = finder?.list()?.getOrNull(i) ?: return
        if (f.protocol != Lan.PROTOCOL) return fail("THAT GAME IS A DIFFERENT VERSION")
        connect(f.address, f.port)
    }

    private fun connect(address: String, port: Int) {
        finder?.close()
        game.platform.lanDiscovery(false)
        connecting = Lan.Connect(address, port)
        state = State.CONNECTING
    }

    private fun askName() {
        typing = nameEntry
        nameEntry.open(myName) { s ->
            if (s.isNullOrEmpty()) return@open
            game.settings.lanName = s
            game.settings.save()
        }
    }

    private fun askIp() {
        typing = ipEntry
        ipEntry.open(game.settings.lanLastIp) { s ->
            if (s.isNullOrEmpty()) return@open
            if (!Regex("""\d{1,3}(\.\d{1,3}){3}""").matches(s)) return@open run { message = "THAT'S NOT AN ADDRESS (LIKE 192.168.1.20)" }
            game.settings.lanLastIp = s
            game.settings.save()
            connect(s, Lan.PORT)
        }
    }

    private fun changeTrack(step: Int) {
        trackIndex = Math.floorMod(trackIndex + step, TrackDef.GENERAL.size)
        session?.sayPick(TrackDef.GENERAL[trackIndex], dist)
    }

    private fun startRace() {
        val s = session ?: return
        if (!s.host || !s.theyReady || s.them == null || s.mismatch != null) return
        race = s.start(TrackDef.GENERAL[trackIndex], dist, MathUtils.random(0.7f, 1.6f))
        found()
    }

    private fun found() {
        state = State.FOUND
        foundTime = 0f
        flash = 0.6f
        flashColor.set(YELLOW)
    }

    private fun connected(link: Lan.Link) {
        session = LanSession(link, me())
        state = State.LOBBY
        flash = 0.4f
        flashColor.set(CYAN)
    }

    override fun layout(w: Int, h: Int) {
        val s = session
        val lobby = state == State.LOBBY && s?.them != null
        val isHost = s?.host == true
        back.apply { x = 8f; y = 8f; this.w = 60f; this.h = 22f; visible = state != State.FOUND }
        hostBtn.apply { x = w / 2f - 126f; y = 60f; this.w = 120f; this.h = 34f; visible = state == State.MENU }
        joinBtn.apply { x = w / 2f + 6f; y = 60f; this.w = 120f; this.h = 34f; visible = state == State.MENU }
        nameBtn.apply { x = w - 88f; y = 8f; this.w = 80f; this.h = 22f; visible = state == State.MENU }
        typeIp.apply { x = w - 118f; y = 8f; this.w = 110f; this.h = 22f; visible = state == State.FINDING }
        val found = if (state == State.FINDING) finder?.list().orEmpty() else emptyList()
        hostRows.forEachIndexed { i, b ->
            b.apply { x = w / 2f - 110f; y = 116f - i * 22f; this.w = 220f; this.h = 18f; visible = i < found.size }
            found.getOrNull(i)?.let { b.label = "${it.name}  ${it.address}" }
        }
        prevTrack.apply { x = w / 2f - 130f; y = 72f; this.w = 18f; this.h = 16f; visible = lobby && isHost }
        nextTrack.apply { x = w / 2f + 112f; y = 72f; this.w = 18f; this.h = 16f; visible = lobby && isHost }
        distBtn.apply { x = w / 2f - 42f; y = 50f; this.w = 84f; this.h = 16f; visible = lobby && isHost; label = dist.label }
        raceBtn.apply { x = w - 128f; y = 8f; this.w = 120f; this.h = 34f; visible = lobby && isHost && s!!.theyReady && s.mismatch == null }
    }

    override fun update(dt: Float) {
        hosting?.let { hst ->
            hst.error?.let { return fail("HOSTING STOPPED: $it") }
            hst.link?.let { link ->
                hosting = null
                connected(link)
            }
        }
        finder?.error?.let { return fail(it) }
        connecting?.let { c ->
            c.error?.let { connecting = null; return fail("CAN'T CONNECT: $it") }
            c.link?.let { link ->
                connecting = null
                connected(link)
            }
        }
        if (state == State.FINDING && auto == "join") {
            finder?.list()?.firstOrNull()?.let { connect(it.address, it.port) }
        }
        val s = session ?: return
        s.pump { id -> id in game.carIds }
        if (s.closed && state != State.FOUND) return fail(if (s.left) "LEFT THE RACE" else "THE OTHER PLAYER LEFT (OR THE WI-FI DROPPED)")
        if (!helloSent) {
            helloSent = true
            s.sayHello()
            if (s.host) s.sayPick(TrackDef.GENERAL[trackIndex], dist)
        }
        if (state == State.LOBBY && s.them != null && !readySent) {
            readySent = true
            s.sayReady()
            if (s.host) s.sayPick(TrackDef.GENERAL[trackIndex], dist)
        }
        s.mismatch?.let { if (state == State.LOBBY) message = it }
        if (!s.host) {
            s.pickTrack?.let { t -> TrackDef.GENERAL.indexOf(t).takeIf { it >= 0 }?.let { trackIndex = it } }
            dist = s.pickDistance
            s.started?.let { r ->
                s.started = null
                race = r
                found()
            }
        }
        if (state == State.LOBBY && s.host && auto == "host" && s.theyReady && s.them != null && race == null) startRace()
        if (state == State.FOUND) {
            foundTime += dt
            val r = race!!
            if (foundTime > OnlineScreen.VS_HOLD_S && !game.transitioning) game.go(2) { RaceScreen(game, r.track, owned, online = r) }
        }
    }

    override fun drawScene(w: Int, h: Int) {
        batch.draw(bg, floor((w - bg.width) / 2f), 0f)
        stage.draw(game.effective(owned), owned.paint, w / 2f - 10f, GarageScreen.GROUND, GarageScreen.AMBIENT, looks = game.looks(owned))
    }

    override fun drawUi(w: Int, h: Int) {
        font.drawCentered(batch, "WI-FI RACE", w / 2f, h - 30f, PINK, 3)
        val py = 130f
        val dots = ".".repeat(1 + (time * 2f).toInt() % 3)
        val s = session
        when (state) {
            State.MENU -> {
                panel(w / 2f - 160f, py - 30f, 320f, 88f, CYAN, 0.88f)
                font.drawCentered(batch, "RACE A FRIEND ON THE SAME WI-FI", w / 2f, py + 46f, YELLOW)
                font.drawCentered(batch, "ONE OF YOU HOSTS, THE OTHER JOINS", w / 2f, py + 32f, SOFT)
                font.drawCentered(batch, "YOU: $myName  IN THE ${game.car(owned.carId).name.uppercase()}", w / 2f, py + 18f, Color.WHITE)
                font.drawCentered(batch, "FOR FUN: NO STAKES, NO XP", w / 2f, py + 4f, SOFT)
                if (message.isNotEmpty()) font.drawCentered(batch, message, w / 2f, py - 18f, RED)
            }
            State.HOSTING -> {
                panel(w / 2f - 160f, py - 30f, 320f, 88f, YELLOW, 0.88f)
                font.drawCentered(batch, "WAITING FOR A RACER$dots", w / 2f, py + 40f, YELLOW, 2)
                val ips = Lan.localAddresses()
                font.drawCentered(batch, "ON THEIR PHONE: WI-FI RACE, JOIN", w / 2f, py + 20f, SOFT)
                font.drawCentered(batch, if (ips.isEmpty()) "NOT ON A WI-FI NETWORK?" else "OR TYPE  " + ips.take(2).joinToString("  OR  "), w / 2f, py + 6f,
                    if (ips.isEmpty()) RED else Color.WHITE)
                font.drawCentered(batch, "BOTH ON THE SAME WI-FI", w / 2f, py - 8f, SOFT)
            }
            State.FINDING -> {
                val list = finder?.list().orEmpty()
                panel(w / 2f - 124f, py - 74f, 248f, 120f, CYAN, 0.88f)
                font.drawCentered(batch, if (list.isEmpty()) "LOOKING FOR GAMES$dots" else "TAP A GAME TO JOIN", w / 2f, py + 30f, YELLOW)
                if (list.isEmpty()) {
                    font.drawCentered(batch, "THE HOST TAPS HOST FIRST", w / 2f, py + 4f, SOFT)
                    font.drawCentered(batch, "NOT SHOWING UP? TYPE THEIR ADDRESS", w / 2f, py - 10f, SOFT)
                }
                if (message.isNotEmpty()) font.drawCentered(batch, message, w / 2f, py - 60f, RED)
            }
            State.CONNECTING -> {
                panel(w / 2f - 160f, py - 6f, 320f, 50f, CYAN, 0.88f)
                font.drawCentered(batch, "CONNECTING$dots", w / 2f, py + 20f, YELLOW, 2)
            }
            State.LOBBY -> {
                panel(w / 2f - 160f, py - 86f, 320f, 140f, if (message.isNotEmpty()) RED else CYAN, 0.88f)
                val them = s?.them
                if (them == null) {
                    font.drawCentered(batch, "SAYING HELLO$dots", w / 2f, py + 20f, YELLOW, 2)
                    return
                }
                font.drawCentered(batch, s.me.name, w / 2f - 90f, py + 34f, CYAN, 2)
                font.drawCentered(batch, "VS", w / 2f, py + 32f, PINK, 3)
                font.drawCentered(batch, them.name, w / 2f + 90f, py + 34f, ORANGE, 2)
                font.drawCentered(batch, game.carName(s.me.car), w / 2f - 90f, py + 18f, SOFT)
                font.drawCentered(batch, game.carName(them.car), w / 2f + 90f, py + 18f, SOFT)
                font.drawCentered(batch, TrackDef.displayName(TrackDef.GENERAL[trackIndex]), w / 2f, py - 2f, Color.WHITE, 2)
                if (!s.host) font.drawCentered(batch, dist.label, w / 2f, py - 22f, SOFT)
                val note = when {
                    message.isNotEmpty() -> message
                    s.host && !s.theyReady -> "WAITING FOR THEM TO COME BACK$dots"
                    s.host -> "PICK THE TRACK, THEN RACE"
                    else -> "THE HOST PICKS THE TRACK$dots"
                }
                font.drawCentered(batch, note, w / 2f, py - 42f, if (message.isNotEmpty()) RED else YELLOW)
                font.drawCentered(batch, "WANT ANOTHER CAR? BACK, PICK IT, REJOIN", w / 2f, py - 72f, SOFT)
            }
            State.FOUND -> {
                val r = race!!
                panel(w / 2f - 160f, py - 6f, 320f, 64f, YELLOW, 0.88f)
                font.drawCentered(batch, r.me.name, w / 2f - 90f, py + 26f, CYAN, 2)
                font.drawCentered(batch, "VS", w / 2f, py + 24f, PINK, 3)
                font.drawCentered(batch, r.them.name, w / 2f + 90f, py + 26f, ORANGE, 2)
                font.drawCentered(batch, "${TrackDef.displayName(r.track)}  ${r.distance.label}", w / 2f, py + 4f, YELLOW)
            }
            State.ERROR -> {
                panel(w / 2f - 160f, py - 6f, 320f, 50f, RED, 0.88f)
                font.drawCentered(batch, message, w / 2f, py + 20f, RED)
            }
        }
    }

    override fun drawUiOver(w: Int, h: Int) {
        val t = typing?.takeIf { it.active } ?: return
        panel(w / 2f - 110f, h / 2f - 24f, 220f, 48f, YELLOW, 0.95f)
        font.drawCentered(batch, if (t === nameEntry) "YOUR RACING NAME" else "THE HOST'S ADDRESS", w / 2f, h / 2f + 10f, YELLOW)
        font.drawCentered(batch, t.shown(time), w / 2f, h / 2f - 10f, Color.WHITE, 2)
    }

    companion object {
        const val ROWS = 4
    }
}

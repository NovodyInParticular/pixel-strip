package io.github.novodyinparticular.pixelstrip.ui

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.utils.JsonValue
import io.github.novodyinparticular.pixelstrip.PixelStrip
import io.github.novodyinparticular.pixelstrip.data.TrackDef
import io.github.novodyinparticular.pixelstrip.gfx.Emblem
import io.github.novodyinparticular.pixelstrip.net.Cloud
import kotlin.math.floor

/**
 * Crew HQ. Without a crew: crews to join (this week's strongest first, or by tag) and founding
 * your own. In one: the roster with roles and best runs, the leader's and officers' member
 * actions, the emblem and door policy, and the weekly war map of the six districts.
 * Everything goes through the server's crew functions (server/functions/src/crews.ts).
 */
class CrewScreen(game: PixelStrip) : MenuScreen(game) {
    private enum class View { LOADING, ERROR, BROWSE, FOUND, CREW, EMBLEM, WAR }

    class Member(val uid: String, val role: String, val name: String, val joined: Long, val bestCar: String?, val bestEt: Float)
    class Crew(val id: String, val name: String, val tag: String, val emblem: Emblem, val open: Boolean, val count: Int, val wins: Int,
        val score: Int, val districts: Map<String, Int>, val members: List<Member>)
    class Holder(val tag: String, val emblem: Emblem, val wins: Int)

    private val bg = art.tex("scenes/garage.png")
    private val cloud = game.cloud
    override val song = "career"

    private var view = if (cloud.configured) View.LOADING else View.ERROR
    private var back = View.BROWSE
    private var message = if (cloud.configured) "" else "CREWS NEED THE CLOUD: SET UP FIREBASE (SEE README)"
    private var notice = ""
    private var noticeTime = 0f
    private var busy = false

    private var myName = cloud.displayName ?: ""
    private var mine: Crew? = null
    private var list = emptyList<Crew>()
    private var map = emptyMap<String, Holder?>()
    private var week = ""
    private var search: String? = null
    private var page = 0
    private var selected = -1
    private var confirmLeave = 0f

    /** The emblem being designed (founding or editing). */
    private var shape = 0
    private val colors = intArrayOf(1, 2, 4)
    private var icon = 0
    private val crewName = TextEntry(16) { it in 'A'..'Z' || it in '0'..'9' || it == ' ' || it in "'!?.-" }
    private val crewTag = TextEntry(4, TextEntry.TAG)
    private val nameEntry = TextEntry(12)
    private val tagSearch = TextEntry(4, TextEntry.TAG)
    private var draftName = ""
    private var draftTag = ""

    private val rows = List(ROWS) { i -> button("", Color(BTN_GREY)) { pick(i) } }
    private val up = button("^", BTN_GREY, keys = intArrayOf(Input.Keys.UP)) { page = (page - 1).coerceAtLeast(0); selected = -1 }
    private val down = button("V", BTN_GREY, keys = intArrayOf(Input.Keys.DOWN)) { if ((page + 1) * ROWS < rowCount()) page++; selected = -1 }
    private val backButton = button("BACK", BTN_GREY, keys = intArrayOf(Input.Keys.B)) { onBack() }
    private val retry = button("RETRY", BTN_RED, keys = intArrayOf(Input.Keys.R)) { refresh() }
    private val rename = button("NAME", BTN_PURPLE, keys = intArrayOf(Input.Keys.N)) { askName() }
    private val warButton = button("WAR MAP", Color(0.6f, 0.3f, 0.1f, 1f), keys = intArrayOf(Input.Keys.W)) { back = view; view = View.WAR }
    private val findTag = button("FIND TAG", BTN_TEAL, keys = intArrayOf(Input.Keys.F)) { askTag() }
    private val found = button("FOUND A CREW", BTN_RED, big = true, keys = intArrayOf(Input.Keys.C)) { startFounding() }
    private val join = button("JOIN", Color(0.2f, 0.55f, 0.3f, 1f), big = true, keys = intArrayOf(Input.Keys.J)) { joinSelected() }

    private val shapeLess = button("<", BTN_GREY) { shape = (shape + Emblem.SHAPES - 1) % Emblem.SHAPES }
    private val shapeMore = button(">", BTN_GREY) { shape = (shape + 1) % Emblem.SHAPES }
    private val iconLess = button("<", BTN_GREY) { icon = (icon + Emblem.ICON_COUNT - 1) % Emblem.ICON_COUNT }
    private val iconMore = button(">", BTN_GREY) { icon = (icon + 1) % Emblem.ICON_COUNT }
    private val swatches = List(3) { i -> button("", Color(Emblem.PALETTE[colors[i]])) { colors[i] = (colors[i] + 1) % Emblem.PALETTE.size } }
    private val nameField = button("", Color(BTN_PURPLE)) { typing = crewName; crewName.open(draftName) { s -> if (s != null) draftName = s } }
    private val tagField = button("", Color(BTN_PURPLE)) { typing = crewTag; crewTag.open(draftTag) { s -> if (s != null) draftTag = s } }
    private val create = button("FOUND IT", BTN_RED, big = true, keys = intArrayOf(Input.Keys.ENTER)) { foundCrew() }
    private val saveEmblem = button("SAVE", BTN_RED, big = true, keys = intArrayOf(Input.Keys.ENTER)) { saveEmblem() }

    private val promote = button("PROMOTE", BTN_TEAL) { manage("promote") }
    private val demote = button("DEMOTE", BTN_TEAL) { manage("demote") }
    private val kick = button("KICK", BTN_RED) { manage("kick") }
    private val lead = button("HAND LEAD", BTN_PURPLE) { manage("lead") }
    private val emblemButton = button("EMBLEM", BTN_TEAL, keys = intArrayOf(Input.Keys.E)) { editEmblem() }
    private val doorButton = button("", BTN_TEAL, keys = intArrayOf(Input.Keys.O)) { toggleOpen() }
    private val leave = button("LEAVE", Color(0.35f, 0.3f, 0.2f, 1f)) { leaveTapped() }

    init {
        if (cloud.configured) refresh()
    }

    // --- Server calls --------------------------------------------------------------------------

    private fun obj(vararg kv: Pair<String, Any>) = JsonValue(JsonValue.ValueType.`object`).also { o ->
        for ((k, v) in kv) o.addChild(k, when (v) {
            is String -> JsonValue(v)
            is Boolean -> JsonValue(v)
            is JsonValue -> v
            else -> JsonValue(v.toString())
        })
    }

    private fun emblemJson() = Emblem(shape, colors.copyOf(), icon).toJson()

    private fun send(fn: String, data: JsonValue, done: String? = null, then: (JsonValue) -> Unit = { refresh() }) {
        if (busy) return
        busy = true
        cloud.call(fn, data) { r, err ->
            busy = false
            if (r == null) return@call say((err ?: "FAILED").uppercase(), RED)
            done?.let { say(it, GREEN) }
            then(r)
        }
    }

    private fun refresh() {
        if (busy) return
        busy = true
        if (view == View.ERROR) view = View.LOADING
        val data = JsonValue(JsonValue.ValueType.`object`)
        search?.let { data.addChild("tag", JsonValue(it)) }
        cloud.call("crewHome", data) { r, err ->
            busy = false
            if (r == null) {
                view = View.ERROR
                message = (err ?: "CAN'T REACH THE SERVER").uppercase().take(48)
                return@call
            }
            myName = r.getString("name", myName)
            cloud.displayName = myName
            mine = r.get("crew")?.takeIf { it.isObject }?.let(::parseCrew)
            list = r.get("list")?.mapNotNull { parseCrew(it) } ?: emptyList()
            map = TrackDef.GENERAL.associateWith { d ->
                r.get("map")?.get(d)?.takeIf { it.isObject }?.let { h -> Emblem.parse(h.get("emblem"))?.let { Holder(h.getString("tag", ""), it, h.getInt("wins", 0)) } }
            }
            week = r.getString("week", "")
            cloud.rememberBadge(mine?.let { Cloud.Badge(it.tag, it.emblem) })
            if (view == View.LOADING || view == View.CREW || view == View.BROWSE) view = if (mine != null) View.CREW else View.BROWSE
            if (view == View.FOUND && mine != null) view = View.CREW
            if (view == View.EMBLEM && mine == null) view = View.BROWSE
            page = page.coerceAtMost(maxOf(0, (rowCount() - 1) / ROWS))
            if (selected >= 0 && page * ROWS + selected >= rowCount()) selected = -1
            scripted()
        }
    }

    private fun parseCrew(c: JsonValue): Crew? {
        val emblem = Emblem.parse(c.get("emblem")) ?: return null
        val members = c.get("members")?.map { m ->
            val best = m.get("best")?.takeIf { it.isObject }
            Member(m.name, m.getString("role", "member"), m.getString("name", "?"), m.getLong("joined", 0),
                best?.getString("car", null), best?.getFloat("et", 0f) ?: 0f)
        }?.sortedWith(compareBy<Member>({ ROLES.indexOf(it.role) }, { it.joined })) ?: emptyList()
        return Crew(c.getString("id"), c.getString("name", ""), c.getString("tag", ""), emblem, c.getBoolean("open", true),
            c.getInt("memberCount", members.size), c.getInt("wins", 0), c.getInt("score", 0),
            c.get("districts")?.associate { it.name to it.asInt() } ?: emptyMap(), members)
    }

    private fun askName() {
        typing = nameEntry
        nameEntry.open(if (myName.startsWith("RACER ")) "" else myName) { s ->
            if (s.isNullOrEmpty()) return@open
            if (s.length < 3) return@open say("NAMES ARE 3 TO 12 CHARACTERS", RED)
            send("setName", obj("name" to s), "YOU'RE $s")
        }
    }

    private fun askTag() {
        typing = tagSearch
        tagSearch.open(search ?: "") { s ->
            if (s == null) return@open
            search = s.ifEmpty { null }
            page = 0
            selected = -1
            refresh()
        }
    }

    private fun startFounding() {
        draftName = ""
        draftTag = ""
        shape = 0
        icon = 0
        colors[0] = 1; colors[1] = 2; colors[2] = 4
        view = View.FOUND
    }

    private fun foundCrew() {
        if (draftName.length < 3) return say("NAME YOUR CREW (3-16)", RED)
        if (draftTag.length < 2) return say("PICK A TAG (2-4 LETTERS)", RED)
        send("createCrew", obj("name" to draftName, "tag" to draftTag, "emblem" to emblemJson()), "[$draftTag] IS ON THE STREETS")
    }

    private fun joinSelected() {
        val c = list.getOrNull(page * ROWS + selected) ?: return
        send("joinCrew", obj("crewId" to c.id), "WELCOME TO [${c.tag}]") { selected = -1; refresh() }
    }

    private fun editEmblem() {
        val c = mine ?: return
        shape = c.emblem.shape
        icon = c.emblem.icon
        for (i in 0..2) colors[i] = c.emblem.colors.getOrElse(i) { 0 }
        view = View.EMBLEM
    }

    private fun saveEmblem() = send("editCrew", obj("emblem" to emblemJson()), "NEW COLOURS FLYING") { view = View.CREW; refresh() }

    private fun toggleOpen() {
        val c = mine ?: return
        send("editCrew", obj("open" to !c.open), if (c.open) "INVITE ONLY NOW" else "OPEN TO ALL")
    }

    private fun manage(action: String) {
        val m = selectedMember() ?: return
        send("manageMember", obj("action" to action, "uid" to m.uid), when (action) {
            "kick" -> "${m.name} IS OUT"
            "promote" -> "${m.name} MADE OFFICER"
            "demote" -> "${m.name} BACK TO MEMBER"
            else -> "${m.name} LEADS NOW"
        }) { selected = -1; refresh() }
    }

    /** First tap asks, a second within 3 s leaves. */
    private fun leaveTapped() {
        if (confirmLeave <= 0f) {
            confirmLeave = 3f
            return
        }
        confirmLeave = 0f
        val tag = mine?.tag ?: return
        send("leaveCrew", JsonValue(JsonValue.ValueType.`object`), "YOU LEFT [$tag]") { selected = -1; page = 0; refresh() }
    }

    /** Screenshot runs: -Dpixelstrip.crew=found:NAME:TAG (underscores for spaces), join:TAG or war. */
    private var scriptDone = false
    private fun scripted() {
        if (game.shots == null || scriptDone) return
        val s = System.getProperty("pixelstrip.crew") ?: return
        scriptDone = true
        val parts = s.split(':')
        when (parts[0]) {
            "found" -> if (mine == null) {
                startFounding()
                draftName = parts.getOrElse(1) { "NIGHT_SHIFT" }.replace('_', ' ')
                draftTag = parts.getOrElse(2) { "NS" }
                shape = 0; icon = 0; colors[0] = 1; colors[1] = 3; colors[2] = 3
                foundCrew()
            }
            "join" -> if (mine == null) {
                search = parts.getOrElse(1) { "" }
                scriptDone = false
                System.setProperty("pixelstrip.crew", "joinfirst")
                refresh()
            }
            "joinfirst" -> if (mine == null && list.isNotEmpty()) {
                selected = 0
                joinSelected()
            }
            "war" -> { back = view; view = View.WAR }
        }
    }

    // --- Roles ---------------------------------------------------------------------------------

    private fun myRole() = mine?.members?.firstOrNull { it.uid == cloud.accountId }?.role ?: "member"

    private fun selectedMember(): Member? = if (view == View.CREW) mine?.members?.getOrNull(page * ROWS + selected) else null

    /** Same rules as crews.ts allowed(). */
    private fun allowed(action: String, target: String?): Boolean {
        val actor = myRole()
        return when (action) {
            "edit" -> actor == "leader"
            "lead" -> actor == "leader" && target != "leader"
            "promote" -> actor == "leader" && target == "member"
            "demote" -> actor == "leader" && target == "officer"
            "kick" -> if (target == "member") actor == "leader" || actor == "officer" else actor == "leader" && target == "officer"
            else -> false
        }
    }

    private fun rowCount() = when (view) {
        View.CREW -> mine?.members?.size ?: 0
        View.BROWSE -> list.size
        else -> 0
    }

    private fun pick(i: Int) {
        if (page * ROWS + i >= rowCount()) return
        selected = if (selected == i) -1 else i
    }

    private fun say(text: String, color: Color) {
        notice = text.take(50)
        noticeColor.set(color)
        noticeTime = 3f
    }

    private val noticeColor = Color(GREEN)

    override fun onBack() {
        when (view) {
            View.FOUND -> view = View.BROWSE
            View.EMBLEM -> view = View.CREW
            View.WAR -> view = back
            View.BROWSE -> if (search != null) {
                search = null
                page = 0
                refresh()
            } else game.go(0) { GarageScreen(game) }
            else -> game.go(0) { GarageScreen(game) }
        }
    }

    // --- Layout --------------------------------------------------------------------------------

    override fun layout(w: Int, h: Int) {
        val listing = view == View.CREW || view == View.BROWSE
        val designing = view == View.FOUND || view == View.EMBLEM
        val lx = listX(w)
        rows.forEachIndexed { i, b ->
            b.apply { x = lx; y = rowY(h, i); this.w = LIST_W; this.h = ROW_H - 3f; visible = listing && page * ROWS + i < rowCount() }
            b.face.set(if (i == selected) Color(0.55f, 0.25f, 0.45f, 1f) else Color(0.16f, 0.15f, 0.26f, 1f))
        }
        up.apply { x = lx + LIST_W + 4f; y = rowY(h, 0); this.w = 16f; this.h = ROW_H - 3f; visible = listing && page > 0 }
        down.apply { x = lx + LIST_W + 4f; y = rowY(h, ROWS - 1); this.w = 16f; this.h = ROW_H - 3f; visible = listing && (page + 1) * ROWS < rowCount() }

        backButton.apply { x = 8f; y = 8f; this.w = 56f; this.h = 22f }
        retry.apply { x = w - 88f; y = 8f; this.w = 80f; this.h = 22f; visible = view == View.ERROR && cloud.configured }
        rename.apply { x = w - 58f; y = h - 46f; this.w = 50f; this.h = 14f; visible = listing }
        warButton.apply { x = 68f; y = 8f; this.w = 66f; this.h = 22f; visible = listing }
        findTag.apply { x = 138f; y = 8f; this.w = 66f; this.h = 22f; visible = view == View.BROWSE }
        join.apply { x = w - 104f; y = 8f; this.w = 96f; this.h = 34f; visible = view == View.BROWSE && selected >= 0 && list.getOrNull(page * ROWS + selected)?.open == true }
        found.apply { x = if (join.visible) w - 290f else w - 184f; y = 8f; this.w = 176f; this.h = 34f; visible = view == View.BROWSE }

        // emblem designer: preview on the left, controls beside it
        val ex = w / 2f - 150f
        shapeLess.apply { x = ex + 96f; y = h - 92f; this.w = 16f; this.h = 16f; visible = designing }
        shapeMore.apply { x = ex + 186f; y = h - 92f; this.w = 16f; this.h = 16f; visible = designing }
        iconLess.apply { x = ex + 96f; y = h - 114f; this.w = 16f; this.h = 16f; visible = designing }
        iconMore.apply { x = ex + 186f; y = h - 114f; this.w = 16f; this.h = 16f; visible = designing }
        swatches.forEachIndexed { i, b ->
            b.apply { x = ex + 116f + i * 24f; y = h - 136f; this.w = 20f; this.h = 16f; visible = designing }
            b.face.set(Emblem.PALETTE[colors[i]])
        }
        nameField.apply {
            x = ex + 96f; y = h - 166f; this.w = 206f; this.h = 18f; visible = view == View.FOUND
            label = if (crewName.active) crewName.shown(time) else draftName.ifEmpty { "TAP TO NAME IT" }
        }
        tagField.apply {
            x = ex + 96f; y = h - 190f; this.w = 60f; this.h = 18f; visible = view == View.FOUND
            label = if (crewTag.active) crewTag.shown(time) else draftTag.ifEmpty { "TAG" }
        }
        create.apply { x = w - 128f; y = 8f; this.w = 120f; this.h = 34f; visible = view == View.FOUND }
        saveEmblem.apply { x = w - 128f; y = 8f; this.w = 120f; this.h = 34f; visible = view == View.EMBLEM }

        // member actions, for the selected crewmate
        val target = selectedMember()?.takeIf { it.uid != cloud.accountId }
        var ax = w - 8f
        for ((b, act, bw) in listOf(Triple(kick, "kick", 40f), Triple(demote, "demote", 52f), Triple(promote, "promote", 58f), Triple(lead, "lead", 64f))) {
            b.visible = target != null && allowed(act, target.role)
            if (b.visible) {
                ax -= bw
                b.apply { x = ax; y = 36f; this.w = bw; this.h = 16f }
                ax -= 4f
            }
        }
        val leader = view == View.CREW && allowed("edit", null)
        emblemButton.apply { x = 138f; y = 8f; this.w = 56f; this.h = 22f; visible = leader }
        doorButton.apply {
            x = 198f; y = 8f; this.w = 70f; this.h = 22f; visible = leader
            label = if (mine?.open == true) "CLOSE UP" else "OPEN UP"
        }
        leave.apply {
            x = w - 60f; y = 8f; this.w = 52f; this.h = 22f; visible = view == View.CREW
            label = if (confirmLeave > 0f) "SURE?" else "LEAVE"
        }
    }

    private fun listX(w: Int) = floor(w / 2f - LIST_W / 2f - 8f)
    private fun rowY(h: Int, i: Int) = h - 82f - i * ROW_H

    override fun update(dt: Float) {
        confirmLeave = (confirmLeave - dt).coerceAtLeast(0f)
        noticeTime = (noticeTime - dt).coerceAtLeast(0f)
    }

    // --- Drawing -------------------------------------------------------------------------------

    override fun drawScene(w: Int, h: Int) {
        batch.draw(bg, floor((w - bg.width) / 2f), 0f)
        batch.setColor(0f, 0f, 0.02f, 0.72f)
        batch.draw(white, 0f, 0f, w.toFloat(), h.toFloat())
        batch.color = Color.WHITE
    }

    private fun emblem(e: Emblem, x: Float, y: Float, size: Int) = batch.draw(art.emblems.texture(e, size), floor(x), floor(y))

    override fun drawUi(w: Int, h: Int) {
        val title = when (view) {
            View.WAR -> "CREW WAR"
            View.FOUND -> "FOUND A CREW"
            View.EMBLEM -> "CREW EMBLEM"
            else -> "CREWS"
        }
        font.draw(batch, title, 8f, h - 24f, PINK, 2)
        cash(w - 8f, h - 20f)
        val dots = ".".repeat(1 + (time * 2f).toInt() % 3)
        when (view) {
            View.LOADING -> font.drawCentered(batch, "CALLING THE CREWS$dots", w / 2f, h / 2f, CYAN, 2)
            View.ERROR -> {
                panel(w / 2f - 160f, h / 2f - 20f, 320f, 40f, RED)
                font.drawCentered(batch, message, w / 2f, h / 2f - 3f, RED)
            }
            View.BROWSE, View.CREW -> drawListing(w, h)
            View.FOUND, View.EMBLEM -> drawDesigner(w, h)
            View.WAR -> drawWar(w, h)
        }
        if (busy && view != View.LOADING) font.draw(batch, "...", w - 26f, h - 34f, CYAN, 1, null)
        if (noticeTime > 0f) font.drawCentered(batch, notice, w / 2f, 48f, noticeColor)
    }

    private fun drawListing(w: Int, h: Int) {
        font.draw(batch, "YOU: $myName", w - 66f - font.width("YOU: $myName"), h - 42f, SOFT, 1, null)
        val c = mine
        if (view == View.CREW && c != null) {
            emblem(c.emblem, 8f, h - 70f, 24)
            font.draw(batch, "[${c.tag}] ${c.name}", 38f, h - 48f, Color.WHITE, 1, null)
            font.draw(batch, "${c.count}/20  ${if (c.open) "OPEN" else "INVITE ONLY"}  WEEK ${c.score} WINS", 38f, h - 60f, SOFT, 1, null)
        } else {
            val head = search?.let { "TAGS STARTING $it" } ?: "OPEN CREWS, STRONGEST THIS WEEK"
            font.draw(batch, head, 8f, h - 48f, YELLOW, 1, null)
            if (list.isEmpty() && !busy) font.drawCentered(batch, if (search != null) "NO CREW BY THAT TAG" else "NO CREWS YET: FOUND THE FIRST",
                w / 2f, h / 2f, SOFT)
        }
    }

    override fun drawUiOver(w: Int, h: Int) {
        drawRows(w, h)
        // typing your name or a tag to look for: a box in the middle shows it
        val t = typing?.takeIf { it.active && (it === nameEntry || it === tagSearch) } ?: return
        panel(w / 2f - 110f, h / 2f - 24f, 220f, 48f, YELLOW, 0.95f)
        font.drawCentered(batch, if (t === nameEntry) "YOUR RACING NAME" else "FIND A CREW BY TAG", w / 2f, h / 2f + 10f, YELLOW)
        font.drawCentered(batch, t.shown(time), w / 2f, h / 2f - 10f, Color.WHITE, 2)
    }

    private fun drawRows(w: Int, h: Int) {
        if (view != View.BROWSE && view != View.CREW) return
        val lx = listX(w)
        for (i in 0 until ROWS) {
            val n = page * ROWS + i
            if (n >= rowCount()) break
            val y = rowY(h, i)
            if (view == View.BROWSE) {
                val c = list[n]
                emblem(c.emblem, lx + 3f, y + 1f, 14)
                font.draw(batch, "[${c.tag}]", lx + 22f, y + 5f, CYAN, 1, null)
                font.draw(batch, c.name, lx + 56f, y + 5f, Color.WHITE, 1, null)
                val right = if (!c.open) "INVITE ONLY" else if (c.count >= 20) "FULL" else "${c.count}/20  ${c.score} W"
                font.draw(batch, right, lx + LIST_W - 4f - font.width(right), y + 5f, if (c.open && c.count < 20) SOFT else ORANGE, 1, null)
            } else {
                val m = mine!!.members[n]
                val (letter, col) = when (m.role) {
                    "leader" -> "L" to YELLOW
                    "officer" -> "O" to CYAN
                    else -> "M" to SOFT
                }
                font.draw(batch, letter, lx + 5f, y + 5f, col, 1, null)
                font.draw(batch, m.name, lx + 16f, y + 5f, if (m.uid == cloud.accountId) PINK else Color.WHITE, 1, null)
                val best = m.bestCar?.let { "%.3f  %s".format(m.bestEt, game.carName(it).take(14)) } ?: "NO ONLINE WIN YET"
                font.draw(batch, best, lx + LIST_W - 4f - font.width(best), y + 5f, if (m.bestCar != null) GREEN else SOFT, 1, null)
            }
        }
        if (rowCount() > ROWS) font.draw(batch, "${page + 1}/${(rowCount() + ROWS - 1) / ROWS}", lx + LIST_W + 4f, rowY(h, 2) + 2f, SOFT, 1, null)
    }

    private fun drawDesigner(w: Int, h: Int) {
        val ex = w / 2f - 150f
        panel(ex - 8f, h - 200f, 320f, 156f, CYAN, 0.85f)
        val e = Emblem(shape, colors.copyOf(), icon)
        emblem(e, ex, h - 148f, 80)
        font.draw(batch, "SHAPE", ex + 96f, h - 56f, SOFT, 1, null)
        font.drawCentered(batch, Emblem.SHAPE_NAMES[shape], ex + 149f, h - 87f, Color.WHITE)
        font.drawCentered(batch, Emblem.ICON_NAMES[icon], ex + 149f, h - 109f, Color.WHITE)
        font.draw(batch, "FILL EDGE ICON: TAP TO CHANGE", ex + 96f, h - 72f, SOFT, 1, null)
        if (view == View.FOUND) {
            font.draw(batch, "NAME", ex + 60f, h - 160f, SOFT, 1, null)
            font.draw(batch, "TAG", ex + 66f, h - 184f, SOFT, 1, null)
            // how the tag will read on the timeslip and the door
            if (draftTag.isNotEmpty() || crewTag.active) emblem(e, ex + 164f, h - 190f, 18)
        } else font.drawCentered(batch, "THE WHOLE CREW WEARS IT ON THE CREW DECAL", w / 2f, h - 190f, YELLOW)
    }

    private fun drawWar(w: Int, h: Int) {
        font.draw(batch, "WEEK $week: EVERY ONLINE WIN SCORES FOR YOUR CREW WHERE IT WAS RACED", 8f, h - 44f, SOFT, 1, null)
        font.draw(batch, "MOST WINS HOLDS THE DISTRICT. RESETS MONDAY.", 8f, h - 54f, SOFT, 1, null)
        val tw = 150f
        val th = 66f
        val x0 = w / 2f - (tw * 3 + 8f) / 2f
        TrackDef.GENERAL.forEachIndexed { i, d ->
            val x = x0 + (i % 3) * (tw + 4f)
            val y = h - 132f - (i / 3) * (th + 4f)
            val holder = map[d]
            val ours = holder != null && holder.tag == mine?.tag
            panel(x, y, tw, th, if (ours) GREEN else if (holder != null) ORANGE else BTN_GREY, 0.88f)
            font.draw(batch, d.replace('_', ' ').uppercase(), x + 6f, y + th - 12f, YELLOW, 1, null)
            if (holder != null) {
                emblem(holder.emblem, x + 6f, y + 8f, 32)
                font.draw(batch, "[${holder.tag}]", x + 44f, y + 30f, if (ours) GREEN else Color.WHITE, 2, null)
                font.draw(batch, "${holder.wins} WINS", x + 44f, y + 14f, SOFT, 1, null)
            } else font.draw(batch, "UNCLAIMED", x + 6f, y + 26f, SOFT, 2, null)
            mine?.let { c ->
                val n = c.districts[d] ?: 0
                if (!ours && n > 0) font.draw(batch, "US: $n", x + tw - 6f - font.width("US: $n"), y + th - 12f, CYAN, 1, null)
            }
        }
        if (mine == null) font.drawCentered(batch, "JOIN A CREW TO FIGHT FOR THE CITY", w / 2f, 40f, ORANGE)
    }

    companion object {
        const val ROWS = 6
        const val ROW_H = 21f
        const val LIST_W = 300f
        val ROLES = listOf("leader", "officer", "member")
    }
}

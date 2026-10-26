package io.github.novodyinparticular.pixelstrip

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import io.github.novodyinparticular.pixelstrip.data.CarDef
import io.github.novodyinparticular.pixelstrip.data.Economy
import io.github.novodyinparticular.pixelstrip.data.PartCatalog
import io.github.novodyinparticular.pixelstrip.data.Tune
import io.github.novodyinparticular.pixelstrip.data.Upgrades
import io.github.novodyinparticular.pixelstrip.save.OwnedCar
import io.github.novodyinparticular.pixelstrip.gfx.Art
import io.github.novodyinparticular.pixelstrip.gfx.PixelFont
import io.github.novodyinparticular.pixelstrip.gfx.PostFx
import io.github.novodyinparticular.pixelstrip.save.SaveGame
import io.github.novodyinparticular.pixelstrip.ui.DealerScreen
import io.github.novodyinparticular.pixelstrip.ui.GarageScreen
import io.github.novodyinparticular.pixelstrip.ui.SettingsScreen
import io.github.novodyinparticular.pixelstrip.ui.StarterScreen
import io.github.novodyinparticular.pixelstrip.ui.TitleScreen
import io.github.novodyinparticular.pixelstrip.race.RaceScreen

class PixelStrip(val platform: Platform, val shots: ShotPlan? = null, private val startCar: String? = null) : Game() {
    lateinit var art: Art
        private set
    lateinit var settings: Settings
        private set
    lateinit var post: PostFx
        private set
    lateinit var batch: SpriteBatch
        private set
    lateinit var font: PixelFont
        private set
    lateinit var save: SaveGame
        private set
    lateinit var economy: Economy
        private set
    lateinit var audio: io.github.novodyinparticular.pixelstrip.audio.AudioEngine
        private set

    lateinit var parts: PartCatalog
        private set
    lateinit var career: io.github.novodyinparticular.pixelstrip.data.Career
        private set
    lateinit var rules: io.github.novodyinparticular.pixelstrip.save.Rules
        private set
    /** The player's level, from their XP. */
    val level get() = rules.xp.levelOf(save.xp)
    lateinit var cloud: io.github.novodyinparticular.pixelstrip.net.Cloud
        private set
    val tournaments by lazy { io.github.novodyinparticular.pixelstrip.net.Tournaments(cloud) }
    val daily by lazy { io.github.novodyinparticular.pixelstrip.net.Daily(cloud) }
    /** The daily reward was waved off (LATER) this session: don't ask again until the game restarts. */
    var dailyDismissed = false

    /**
     * Every change to cash or the garage goes through here: checked by the ledger rules, applied,
     * saved, and queued for the server. Returns false (nothing changed) if the rules say no.
     */
    fun commit(e: io.github.novodyinparticular.pixelstrip.save.Entry): Boolean {
        if (!io.github.novodyinparticular.pixelstrip.save.Ledger.apply(rules, save, e)) return false
        save.pending.add(e)
        save.write()
        cloud.requestSync()
        return true
    }

    fun now() = System.currentTimeMillis()

    /**
     * Old four-tier builds become the new parts (data/parts_migration.json). Nothing old was ever
     * checked by a server, so the converted save goes up once as a snapshot (legacy import).
     */
    private fun migrateParts() {
        val m = io.github.novodyinparticular.pixelstrip.data.PartsMigration(com.badlogic.gdx.utils.JsonReader().parse(Gdx.files.internal("data/parts_migration.json")))
        for (c in save.cars) {
            if (c.carId in rules.retired.cars) continue      // refunded by retireOld instead
            val next = m.convert(c.parts, c.cosmetics.remove("kit") ?: 0, parts, car(c.carId).drivetrain, c.carId)
            c.parts.clear()
            c.parts.putAll(next)
            c.owned.clear()
            for ((k, v) in next) c.owned[k] = 1 shl v
        }
        save.pending.clear()
        save.legacy = true
        save.oldParts = false
        save.write()
    }

    private val carCache = HashMap<String, CarDef>()

    private val effectiveCache = HashMap<String, CarDef>()

    /** How one of your cars looks, with your crew's emblem for the CREW decal. */
    fun looks(car: OwnedCar) = io.github.novodyinparticular.pixelstrip.race.CarLooks.of(car, parts, cloud.badge?.emblem)

    /** How one of your cars sounds: the sound tags of its parts (what works on its engine). */
    fun sound(car: OwnedCar) = io.github.novodyinparticular.pixelstrip.audio.EngineSound.of(parts, car.parts, effective(car))

    /** The car as it drives: stock stats + installed parts + the active tune. */
    fun effective(car: OwnedCar): CarDef = effectiveWith(car.carId, car.parts, car.cosmetics, car.tune)

    /** Memoized by content, so per-frame callers share one instance (and its cached simulations). */
    fun effectiveWith(carId: String, parts: Map<String, Int>, cosmetics: Map<String, Int>, tune: Tune?): CarDef {
        val key = buildString {
            append(carId).append('|').append(parts.toSortedMap()).append('|').append(cosmetics.toSortedMap()).append('|')
            tune?.let { append(it.copy(name = "", gears = null, boostByGear = null)).append(it.gears?.contentToString()).append(it.boostByGear?.contentToString()) }
        }
        return effectiveCache.getOrPut(key) { Upgrades.apply(car(carId), this.parts, parts, cosmetics, tune) }
    }
    fun car(id: String): CarDef = carCache.getOrPut(id) { CarDef.load(id) }

    /** A car's name for display, also for ids other players' old runs still carry (cars taken out of the game). */
    fun carName(id: String): String = (if (id in carIds) car(id).name else rules.retired.cars[id]?.name ?: id).uppercase()

    /**
     * Cars and parts taken out of the game: each is refunded in full through the ledger (a
     * "retire" entry the server checks too), then shown once (save.refundNotice). A garage left
     * empty gets a free Civic. Runs after loading and after every cloud sync, since another
     * device may still hand back a car this one already retired.
     */
    fun retireOld() {
        for (c in save.cars.toList()) {
            val pay = io.github.novodyinparticular.pixelstrip.save.Ledger.refund(rules, c)
            if (pay <= 0L) continue
            val retiredCar = rules.retired.cars[c.carId]
            val last = retiredCar != null && save.cars.size == 1
            val e = io.github.novodyinparticular.pixelstrip.save.Entry("retire", now(), uid = c.uid, car = if (last) REPLACEMENT_STARTER else null)
            if (!commit(e)) continue
            val what = retiredCar?.name?.uppercase() ?: "${carName(c.carId)}: RETIRED PARTS"
            save.refundNotice.add("$what|$pay")
        }
        if (save.refundNotice.isNotEmpty()) save.write()
    }
    val carIds: List<String> by lazy { CarDef.ids() }

    // Screen transitions: cover (out), swap, uncover (in)
    private enum class Trans { NONE, OUT, IN }
    private var trans = Trans.NONE
    private var transT = 0f
    private var pending: (() -> Screen)? = null
    val transitioning get() = trans != Trans.NONE

    override fun create() {
        settings = Settings()
        art = Art()
        post = PostFx(art)
        post.resize(Gdx.graphics.backBufferWidth, Gdx.graphics.backBufferHeight, settings.quality)
        batch = SpriteBatch(4000)
        font = PixelFont(art.tex("ui/font.png"))
        economy = Economy.load()
        parts = PartCatalog.load()
        career = io.github.novodyinparticular.pixelstrip.data.Career.load()
        rules = io.github.novodyinparticular.pixelstrip.save.Rules.parse(Gdx.files.internal(io.github.novodyinparticular.pixelstrip.save.Rules.PATH).readString())
        // Screenshot runs never touch the real save
        save = SaveGame.load(platform.saveFile(if (shots != null) "save-shots.json" else "save.json"))
        if (save.oldParts) migrateParts()
        if (shots != null) {
            save.cars.clear()
            save.refundNotice.clear()
            save.cash = 250_000
            val c = save.addCar(startCar ?: "mammoth", car(startCar ?: "mammoth").defaultPaint)
            if (shots.loaded) {
                // a pro-mod blower build: the top option everywhere except turbo, plus tubs for the slicks
                val stockDef = car(c.carId)
                for (s in parts.slots) if (s.id != "turbo" && s.id != "final") {
                    val top = s.options.indices.last { it == 0 || s.options[it].carOnly.isEmpty() || c.carId in s.options[it].carOnly }
                    if (top > 0) c.parts[s.id] = top
                }
                if (stockDef.drivetrain != "RWD") c.parts.remove("wheeliebars")
                if (car(c.carId).drivetrain != "RWD") { c.parts.remove("bars"); c.parts.remove("wheeliebars") }
                c.cosmetics.putAll(mapOf("wing" to 3, "rims" to 4, "underglow" to 2, "finish" to 4, "decal" to 2, "decalColor" to 6, "tint" to 1))
            }
            // -Dpixelstrip.beaten=ID,ID: rivals already beaten (barn finds show after their boss)
            save.beaten.clear()
            save.found.clear()
            System.getProperty("pixelstrip.beaten")?.split(",")?.let { save.beaten.addAll(it) }
            // -Dpixelstrip.retired=ID,ID: cars taken out of the game parked in the garage, to see the refund
            System.getProperty("pixelstrip.retired")?.split(",")?.forEach { id ->
                save.addCar(id, 3).apply { parts["intake"] = 2; owned["intake"] = 0b110; cosmetics["wing"] = 1 }
            }
        }
        cloud = io.github.novodyinparticular.pixelstrip.net.Cloud(this, enabled = shots == null || System.getProperty("pixelstrip.emulator") != null)
        if (shots != null && cloud.configured) {
            // emulator check: a fresh garage, then a starter and a win through the real ledger path
            save.cars.clear()
            save.beaten.clear()
            save.pending.clear()
            save.cash = 0
            save.nextUid = 1
            save.lastRaceT = 0
            save.serverSeq = 0
            save.legacy = false
            commit(io.github.novodyinparticular.pixelstrip.save.Entry("start", now(), car = "kite"))
            commit(io.github.novodyinparticular.pixelstrip.save.Entry("race", now() + 10_000, uid = "c1", rival = "dash", won = true))
        }
        if (shots == null || System.getProperty("pixelstrip.retired") != null) retireOld()
        cloud.requestSync()
        Gdx.input.setCatchKey(Input.Keys.BACK, true)
        audio = io.github.novodyinparticular.pixelstrip.audio.AudioEngine(settings)
        audio.start()
        setScreen(firstScreen())
    }

    private fun firstScreen(): Screen = when (shots?.screen) {
        "title" -> TitleScreen(this)
        "garage" -> GarageScreen(this)
        "dealer" -> DealerScreen(this)
        "settings" -> SettingsScreen(this)
        "starter" -> StarterScreen(this)
        "upgrades" -> io.github.novodyinparticular.pixelstrip.ui.UpgradeScreen(this)
        "paint" -> io.github.novodyinparticular.pixelstrip.ui.PaintShopScreen(this)
        "tune" -> io.github.novodyinparticular.pixelstrip.ui.TuneScreen(this)
        "dyno" -> io.github.novodyinparticular.pixelstrip.ui.DynoScreen(this)
        "runs" -> io.github.novodyinparticular.pixelstrip.ui.RunLogScreen(this)
        "career" -> io.github.novodyinparticular.pixelstrip.ui.CareerScreen(this)
        "vs" -> io.github.novodyinparticular.pixelstrip.ui.VsScreen(this, career.rivals.first { it.id == "joe" })
        "online" -> io.github.novodyinparticular.pixelstrip.ui.OnlineScreen(this)
        "crew" -> io.github.novodyinparticular.pixelstrip.ui.CrewScreen(this)
        "tournament" -> io.github.novodyinparticular.pixelstrip.ui.TournamentScreen(this)
        "ranks" -> io.github.novodyinparticular.pixelstrip.ui.LeaderboardScreen(this)
        "boss" -> career.rivals.first { it.id == "joe" }.let { RaceScreen(this, it.track, save.selectedCar()!!, rival = it) }
        null -> if (shots != null) RaceScreen(this, System.getProperty("pixelstrip.track") ?: "neon_city", save.selectedCar()!!) else TitleScreen(this)
        else -> TitleScreen(this)
    }

    /** What the dealer pays: 60% of the car, 30% of what its parts cost. */
    fun saleValue(car: OwnedCar): Long = io.github.novodyinparticular.pixelstrip.save.Ledger.saleValue(rules, car)

    /** Where "start" goes: first launch picks a starter car, otherwise the garage. */
    fun home(): Screen = if (save.isNew) StarterScreen(this) else GarageScreen(this)

    /** Transition to another screen. [kind]: 0 pixel dissolve, 1 wipe, 2 garage door. */
    fun go(kind: Int = 0, make: () -> Screen) {
        if (trans != Trans.NONE) return
        pending = make
        trans = Trans.OUT
        transT = 0f
        post.transitionKind = kind
        audio.play(io.github.novodyinparticular.pixelstrip.audio.Sfx.Kind.WHOOSH, 0.7f)
    }

    override fun render() {
        cloud.update()
        val dt = Gdx.graphics.deltaTime.coerceAtMost(1f / 20f)
        when (trans) {
            Trans.OUT -> {
                transT += dt / TRANSITION_S
                if (transT >= 1f) {
                    transT = 1f
                    val old = screen
                    audio.silenceEngines()
                    if (post.transitionKind == 2) audio.play(io.github.novodyinparticular.pixelstrip.audio.Sfx.Kind.SLAM)
                    setScreen(pending!!.invoke())
                    old?.dispose()
                    pending = null
                    trans = Trans.IN
                }
            }
            Trans.IN -> {
                transT -= dt / TRANSITION_S
                if (transT <= 0f) {
                    transT = 0f
                    trans = Trans.NONE
                }
            }
            Trans.NONE -> {}
        }
        post.transition = transT * transT * (3f - 2f * transT)
        super.render()
    }

    override fun resize(width: Int, height: Int) {
        post.resize(Gdx.graphics.backBufferWidth, Gdx.graphics.backBufferHeight, settings.quality)
        super.resize(width, height)
    }

    override fun pause() {
        cloud.requestSync()                 // tunes and run logs ride along with the profile
        audio.stop()
        super.pause()
    }

    override fun resume() {
        audio.start()
        super.resume()
    }

    override fun dispose() {
        audio.dispose()
        screen?.dispose()
        batch.dispose()
        post.dispose()
        art.dispose()
    }

    companion object {
        const val TRANSITION_S = 0.38f
        const val PARTS_RESALE_PERCENT = 30L
        /** What a garage whose only car was taken out of the game gets instead (the Civic). */
        const val REPLACEMENT_STARTER = "kite"
    }
}

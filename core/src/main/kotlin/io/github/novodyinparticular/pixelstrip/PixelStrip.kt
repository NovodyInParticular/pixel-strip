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

    lateinit var parts: PartCatalog
        private set
    lateinit var career: io.github.novodyinparticular.pixelstrip.data.Career
        private set

    private val carCache = HashMap<String, CarDef>()

    private val effectiveCache = HashMap<String, CarDef>()

    /** The car as it drives: stock stats + installed parts + the active tune. */
    fun effective(car: OwnedCar): CarDef = effectiveWith(car.carId, car.parts, car.cosmetics, car.tune)

    /** Memoized by content, so per-frame callers share one instance (and its cached simulations). */
    fun effectiveWith(carId: String, parts: Map<String, Int>, cosmetics: Map<String, Int>, tune: Tune?): CarDef {
        val key = buildString {
            append(carId).append('|').append(parts.toSortedMap()).append('|').append(cosmetics.toSortedMap()).append('|')
            tune?.let { append(it.gears?.contentToString()).append(it.finalDrive).append(it.launchRpm).append(it.nitrousGear).append(it.tirePressure) }
        }
        return effectiveCache.getOrPut(key) { Upgrades.apply(car(carId), this.parts, parts, cosmetics, tune) }
    }
    fun car(id: String): CarDef = carCache.getOrPut(id) { CarDef.load(id) }
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
        // Screenshot runs never touch the real save
        save = SaveGame.load(platform.saveFile(if (shots != null) "save-shots.json" else "save.json"))
        if (shots != null) {
            save.cars.clear()
            save.cash = 250_000
            val c = save.addCar(startCar ?: "vandal", car(startCar ?: "vandal").defaultPaint)
            if (shots.loaded) {
                parts.slots.filter { it.id != "turbo" }.forEach { c.parts[it.id] = it.tiers.size }
                c.cosmetics.putAll(mapOf("wing" to 3, "kit" to 2, "rims" to 4, "underglow" to 2, "finish" to 4, "decal" to 2, "decalColor" to 6))
            }
        }
        Gdx.input.setCatchKey(Input.Keys.BACK, true)
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
        "boss" -> RaceScreen(this, "neon_city", save.selectedCar()!!, rival = career.rivals.first { it.id == "joe" })
        null -> if (shots != null) RaceScreen(this, "neon_city", save.selectedCar()!!) else TitleScreen(this)
        else -> TitleScreen(this)
    }

    /** What the dealer pays: 60% of the car, 30% of what its parts cost. */
    fun saleValue(car: OwnedCar): Long {
        val def = car(car.carId)
        val stockClass = economy.classFor(io.github.novodyinparticular.pixelstrip.race.DragMath.perfectPass(def).quarter)
        var parts = 0L
        for (slot in this.parts.slots) for (t in 1..(car.parts[slot.id] ?: 0)) parts += this.parts.price(slot, t, stockClass)
        return economy.sellPrice(def.price) + (parts * PARTS_RESALE).toLong()
    }

    /** Where "start" goes: first launch picks a starter car, otherwise the garage. */
    fun home(): Screen = if (save.isNew) StarterScreen(this) else GarageScreen(this)

    /** Transition to another screen. [kind]: 0 pixel dissolve, 1 wipe, 2 garage door. */
    fun go(kind: Int = 0, make: () -> Screen) {
        if (trans != Trans.NONE) return
        pending = make
        trans = Trans.OUT
        transT = 0f
        post.transitionKind = kind
    }

    override fun render() {
        val dt = Gdx.graphics.deltaTime.coerceAtMost(1f / 20f)
        when (trans) {
            Trans.OUT -> {
                transT += dt / TRANSITION_S
                if (transT >= 1f) {
                    transT = 1f
                    val old = screen
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

    override fun dispose() {
        screen?.dispose()
        batch.dispose()
        post.dispose()
        art.dispose()
    }

    companion object {
        const val TRANSITION_S = 0.38f
        const val PARTS_RESALE = 0.3f
    }
}

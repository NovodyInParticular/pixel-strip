package io.github.novodyinparticular.pixelstrip

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import io.github.novodyinparticular.pixelstrip.data.CarDef
import io.github.novodyinparticular.pixelstrip.data.Economy
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

    private val carCache = HashMap<String, CarDef>()
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
        // Screenshot runs never touch the real save
        save = SaveGame.load(platform.saveFile(if (shots != null) "save-shots.json" else "save.json"))
        if (shots != null) {
            save.cars.clear()
            save.cash = 250_000
            save.addCar(startCar ?: "vandal", car(startCar ?: "vandal").defaultPaint)
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
        null -> if (shots != null) RaceScreen(this, "neon_city", save.selectedCar()!!) else TitleScreen(this)
        else -> TitleScreen(this)
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
    }
}

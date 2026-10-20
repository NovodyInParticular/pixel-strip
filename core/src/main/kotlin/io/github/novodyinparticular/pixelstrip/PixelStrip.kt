package io.github.novodyinparticular.pixelstrip

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import io.github.novodyinparticular.pixelstrip.gfx.Art
import io.github.novodyinparticular.pixelstrip.gfx.PixelFont
import io.github.novodyinparticular.pixelstrip.gfx.PostFx
import io.github.novodyinparticular.pixelstrip.race.RaceScreen

class PixelStrip(val platform: Platform, val shots: ShotPlan? = null, private val startCar: String = "vandal") : Game() {
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

    override fun create() {
        settings = Settings()
        art = Art()
        post = PostFx(art)
        post.resize(Gdx.graphics.backBufferWidth, Gdx.graphics.backBufferHeight, settings.quality)
        batch = SpriteBatch(4000)
        font = PixelFont(art.tex("ui/font.png"))
        setScreen(RaceScreen(this, "neon_city", startCar))
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
}

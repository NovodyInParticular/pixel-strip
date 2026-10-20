package io.github.novodyinparticular.pixelstrip.gfx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.utils.Disposable
import io.github.novodyinparticular.pixelstrip.Quality
import kotlin.math.roundToInt

/**
 * The low-res pipeline. The world is drawn into [scene] (270 px tall, width follows the screen's
 * aspect so phones get no black bars), emissive things into [glow], heat-haze offsets into
 * [distort] and the HUD into [ui] (premultiplied alpha). [present] blurs the glow into bloom and
 * composites everything to the screen: zoom, distortion, grade, CA, print look, UI, CRT.
 */
class PostFx(private val art: Art) : Disposable {
    var width = 480
        private set
    val height = FB_HEIGHT

    lateinit var scene: FrameBuffer
        private set
    lateinit var glow: FrameBuffer
        private set
    lateinit var ui: FrameBuffer
        private set
    lateinit var distort: FrameBuffer
        private set
    private var bloomA: FrameBuffer? = null
    private var bloomB: FrameBuffer? = null
    private var quality = Quality.HIGH

    private val quad = Mesh(
        true, 4, 6,
        VertexAttribute(VertexAttributes.Usage.Position, 2, "a_position"),
        VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, "a_texCoord0"),
    ).apply {
        setVertices(floatArrayOf(-1f, -1f, 0f, 0f, 1f, -1f, 1f, 0f, 1f, 1f, 1f, 1f, -1f, 1f, 0f, 1f))
        setIndices(shortArrayOf(0, 1, 2, 2, 3, 0))
    }
    private val blur: ShaderProgram = art.shader("screen.vert", "blur.frag")
    private val copy: ShaderProgram = art.shader("screen.vert", "copy.frag")
    private val composite: ShaderProgram = art.shader("screen.vert", "composite.frag")

    // Per-frame effect parameters, set by the screen before present()
    var bloomGain = 1.1f
    var lutMix = 1f
    var chromatic = 0f
    var radialBlur = 0f
    var crt = false
    var flash = 0f
    val flashColor = Color(1f, 1f, 1f, 1f)
    var zoom = 1f
    var focusX = 0.5f          // zoom centre in 0..1 of the buffer
    var focusY = 0.5f
    var distortAmp = 2f        // heat-haze offset in buffer pixels at full strength
    var hazeY0 = 0f            // asphalt shimmer band (0..1 of the buffer height)
    var hazeY1 = 0f
    var hazeAmp = 0f
    var time = 0f
    var print = 0f             // photo-finish print look 0..1
    var transition = 0f        // screen transition cover 0..1 (set by the game, not the screens)
    var transitionKind = 0     // 0 pixel dissolve, 1 wipe, 2 garage door

    fun resize(screenW: Int, screenH: Int, quality: Quality) {
        val aspect = screenW.toFloat() / screenH.coerceAtLeast(1)
        val w = ((FB_HEIGHT * aspect).roundToInt() and 1.inv()).coerceIn(MIN_WIDTH, MAX_WIDTH)
        if (::scene.isInitialized && w == width && quality == this.quality) return
        disposeBuffers()
        width = w
        this.quality = quality
        scene = buffer(width, height)
        glow = buffer(width, height)
        ui = buffer(width, height)
        distort = buffer(width / 2, height / 2)
        val div = if (quality == Quality.LOW) 4 else 2
        bloomA = buffer(width / div, height / div)
        bloomB = buffer(width / div, height / div)
    }

    private fun buffer(w: Int, h: Int) = FrameBuffer(Pixmap.Format.RGBA8888, w, h, false).apply {
        colorBufferTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
    }

    /** Glow -> bloom -> composite to the back buffer. */
    fun present(lut: Texture) {
        val a = bloomA!!
        val b = bloomB!!
        Gdx.gl.glDisable(GL20.GL_BLEND)

        a.begin()
        copy.bind()
        copy.setUniformi("u_texture", 0)
        copy.setUniformf("u_gain", 1f)
        glow.colorBufferTexture.bind(0)
        quad.render(copy, GL20.GL_TRIANGLES)
        a.end()

        val passes = when (quality) { Quality.LOW -> 1; Quality.MEDIUM -> 1; Quality.HIGH -> 2 }
        blur.bind()
        blur.setUniformi("u_texture", 0)
        for (i in 0 until passes) {
            val spread = 1f + i * 1.5f
            b.begin()
            blur.setUniformf("u_dir", spread / a.width, 0f)
            a.colorBufferTexture.bind(0)
            quad.render(blur, GL20.GL_TRIANGLES)
            b.end()
            a.begin()
            blur.setUniformf("u_dir", 0f, spread / a.height)
            b.colorBufferTexture.bind(0)
            quad.render(blur, GL20.GL_TRIANGLES)
            a.end()
        }

        Gdx.gl.glViewport(0, 0, Gdx.graphics.backBufferWidth, Gdx.graphics.backBufferHeight)
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        lut.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        composite.bind()
        scene.colorBufferTexture.bind(0)
        a.colorBufferTexture.bind(1)
        lut.bind(2)
        ui.colorBufferTexture.bind(3)
        distort.colorBufferTexture.bind(4)
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0)
        composite.setUniformi("u_scene", 0)
        composite.setUniformi("u_bloom", 1)
        composite.setUniformi("u_lut", 2)
        composite.setUniformi("u_ui", 3)
        composite.setUniformi("u_distort", 4)
        composite.setUniformf("u_zoom", zoom)
        composite.setUniformf("u_focus", focusX, focusY)
        composite.setUniformf("u_distortAmp", if (quality == Quality.LOW) 0f else distortAmp)
        composite.setUniformf("u_haze", hazeY0, hazeY1, if (quality == Quality.LOW) 0f else hazeAmp, time)
        composite.setUniformf("u_print", print)
        composite.setUniformf("u_transition", transition)
        composite.setUniformf("u_transKind", transitionKind.toFloat())
        composite.setUniformf("u_src", width.toFloat(), height.toFloat())
        composite.setUniformf("u_dst", Gdx.graphics.backBufferWidth.toFloat(), Gdx.graphics.backBufferHeight.toFloat())
        composite.setUniformf("u_bloomGain", if (quality == Quality.LOW) bloomGain * 0.7f else bloomGain)
        composite.setUniformf("u_lutMix", lutMix)
        composite.setUniformf("u_ca", if (quality == Quality.LOW) 0f else chromatic)
        composite.setUniformf("u_radial", if (quality == Quality.HIGH) radialBlur else 0f)
        composite.setUniformf("u_crt", if (crt) 1f else 0f)
        composite.setUniformf("u_flash", flash)
        composite.setUniformf("u_flashColor", flashColor.r, flashColor.g, flashColor.b)
        quad.render(composite, GL20.GL_TRIANGLES)
        Gdx.gl.glEnable(GL20.GL_BLEND)
    }

    /** Clears the per-frame helper buffers: transparent UI, neutral (no offset) distortion. */
    fun clearUiAndDistort() {
        ui.begin()
        Gdx.gl.glClearColor(0f, 0f, 0f, 0f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        ui.end()
        distort.begin()
        Gdx.gl.glClearColor(0.5f, 0.5f, 0f, 0f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        distort.end()
    }

    /** Low-res pixel position from a screen touch (y up). */
    fun toBufferX(screenX: Int) = screenX.toFloat() / Gdx.graphics.width * width
    fun toBufferY(screenY: Int) = (1f - screenY.toFloat() / Gdx.graphics.height) * height

    private fun disposeBuffers() {
        if (::scene.isInitialized) {
            scene.dispose()
            glow.dispose()
            ui.dispose()
            distort.dispose()
        }
        bloomA?.dispose()
        bloomB?.dispose()
    }

    override fun dispose() {
        disposeBuffers()
        quad.dispose()
    }

    companion object {
        const val FB_HEIGHT = 270
        const val MIN_WIDTH = 480
        const val MAX_WIDTH = 640
    }
}

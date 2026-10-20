package io.github.novodyinparticular.pixelstrip.race

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.MathUtils
import io.github.novodyinparticular.pixelstrip.data.CarDef
import io.github.novodyinparticular.pixelstrip.gfx.Art
import io.github.novodyinparticular.pixelstrip.gfx.Lights
import kotlin.math.PI
import kotlin.math.floor

/**
 * Stacks a car's layers: wheel wells, wheels (rotation or motion-blur frames), the paint-index body
 * through the paint shader, trim, and lamps. Body layers can pitch around the rear contact patch.
 */
class CarRenderer(val def: CarDef, private val art: Art, private val paint: ShaderProgram, private val tint: ShaderProgram, private val ramps: Texture) {
    private val s = def.sprite
    private val body = art.tex("${s.dir}/body.png")
    private val normals = art.tex("${s.dir}/body_n.png")
    private val popupRegion = if (s.popup) TextureRegion(art.tex("${s.dir}/popup.png")) else null
    private val trim = TextureRegion(art.tex("${s.dir}/trim.png"))
    private val lampLayer = TextureRegion(art.tex("${s.dir}/lights.png"))
    private val wells = art.tex("${s.dir}/wells.png")
    private val bodyRegion = TextureRegion(body)
    private val wheelTex = art.tex("${s.dir}/wheel.png")
    private val wheelFrames = Array(s.wheelFrames + s.blurFrames) { TextureRegion(wheelTex, it * s.wheelSize, 0, s.wheelSize, s.wheelSize) }
    private val cone = art.tex("fx/light_cone.png")

    companion object {
        const val SPEC_POWER = 22f
        const val SPEC_STRENGTH = 0.8f
    }

    var paintRow = def.defaultPaint
    var looks: CarLooks = CarLooks.STOCK
        set(value) {
            field = value
            rimFrames = value.rim?.let { rimFramesFor(it) }
        }
    private val layerCache = HashMap<String, TextureRegion>()
    private fun layer(name: String) = layerCache.getOrPut(name) { TextureRegion(art.tex("${s.dir}/$name.png")) }
    private var rimFrames: Array<TextureRegion>? = null
    private val glowTex = art.tex("fx/light_soft.png")

    private fun rimFramesFor(style: String): Array<TextureRegion> {
        val tex = art.tex("wheels/r${s.wheelRadius10}_$style.png")
        return Array(s.wheelFrames + s.blurFrames) { TextureRegion(tex, it * s.wheelSize, 0, s.wheelSize, s.wheelSize) }
    }

    private val spokes get() = looks.rim?.let { CarLooks.RIM_SPOKES[it] } ?: s.spokes
    private val angle = FloatArray(2)    // rear, front
    private val rate = FloatArray(2)     // rad/s

    /** [rollW] = road speed of the free wheels, [driveW] = the driven axle (faster when spinning). */
    fun update(dt: Float, rollW: Float, driveW: Float) {
        rate[0] = if (def.drivetrain == "FWD") rollW else driveW
        rate[1] = if (def.drivetrain == "RWD") rollW else driveW
        for (i in 0..1) angle[i] = (angle[i] + rate[i] * dt) % (2f * PI.toFloat())
    }

    private fun wheelFrame(i: Int): TextureRegion {
        val wheelFrames = rimFrames ?: this.wheelFrames
        val period = 2f * PI.toFloat() / spokes
        val perFrame = rate[i] / 60f
        return when {
            perFrame < period * 0.22f -> wheelFrames[((angle[i] % period) / period * s.wheelFrames).toInt().coerceIn(0, s.wheelFrames - 1)]
            perFrame < period * 0.55f -> wheelFrames[s.wheelFrames]
            perFrame < period * 1.3f -> wheelFrames[s.wheelFrames + 1]
            else -> wheelFrames[s.wheelFrames + 2]
        }
    }

    /**
     * (x, groundY) is the canvas' left edge and the road contact row (y up). pitch is in degrees,
     * positive = nose up; lift raises the body on its springs.
     */
    fun draw(batch: SpriteBatch, x: Float, groundY: Float, time: Float, ghost: Float = 0f, lit: Boolean = true,
             pitch: Float = 0f, lift: Float = 0f, ambient: Color = Color.WHITE, lights: Lights? = null,
             shakeX: Float = 0f, shakeY: Float = 0f, shine: Float = -1f) {
        val bx = floor(x)
        val by = floor(groundY - (s.height - s.groundY))
        val ox = s.rearWheel.first
        val oy = s.height - s.groundY

        val litTrim = lights != null && ghost <= 0f
        batch.shader = tint
        tint.setUniformf("u_ghost", ghost)
        tint.setUniformf("u_time", time)
        tint.setUniformf("u_lit", if (litTrim) 0.55f else 0f)
        if (litTrim) lights!!.apply(tint, shakeX, shakeY)
        if (ghost <= 0f) {
            looks.underglow?.let { c ->
                batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE)
                batch.setColor(c.r, c.g, c.b, 0.55f + 0.1f * kotlin.math.sin(time * 3f))
                batch.draw(glowTex, bx + 6f, groundY - 5f, s.width - 12f, 12f)
                batch.color = Color.WHITE
                batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
            }
            batch.draw(wells, bx, by + lift)
        }
        for ((i, wheel) in listOf(s.rearWheel, s.frontWheel).withIndex()) {
            val (wx, wy) = wheel
            // a wheelstanding car's front wheel leaves the ground with the body
            val raise = if (i == 1 && pitch > 0f) floor((s.frontWheel.first - s.rearWheel.first) * MathUtils.sinDeg(pitch) * 0.6f) else 0f
            batch.draw(wheelFrame(i), floor(bx + wx - s.wheelSize / 2f), floor(by + (s.height - wy) - s.wheelSize / 2f + raise))
        }

        batch.shader = paint
        ramps.bind(1)
        normals.bind(2)
        com.badlogic.gdx.Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0)
        paint.setUniformi("u_ramps", 1)
        paint.setUniformi("u_normal", 2)
        paint.setUniformf("u_row", (paintRow + 0.5f) / ramps.height)
        paint.setUniformf("u_ghost", ghost)
        paint.setUniformf("u_time", time)
        paint.setUniformf("u_shine", shine)
        paint.setUniformf("u_finish", if (ghost > 0f) 0f else looks.finish.toFloat())
        paint.setUniformf("u_ambient", ambient.r, ambient.g, ambient.b)
        paint.setUniformf("u_specPower", SPEC_POWER)
        paint.setUniformf("u_specStrength", SPEC_STRENGTH)
        if (lights != null) lights.apply(paint, shakeX, shakeY) else paint.setUniformi("u_lightCount", 0)
        batch.draw(bodyRegion, bx, by + lift, ox, oy, s.width.toFloat(), s.height.toFloat(), 1f, 1f, pitch)
        looks.kit?.let { batch.draw(layer("kit_$it"), bx, by + lift, ox, oy, s.width.toFloat(), s.height.toFloat(), 1f, 1f, pitch) }
        if (ghost <= 0f) looks.decal?.let { d ->
            batch.shader = tint
            tint.setUniformf("u_ghost", 0f)
            tint.setUniformf("u_lit", if (lights != null) 0.4f else 0f)
            if (lights != null) lights.apply(tint, shakeX, shakeY)
            val c = looks.decalColor
            batch.setColor(c.r * ambient.r * 1.2f, c.g * ambient.g * 1.2f, c.b * ambient.b * 1.2f, 1f)
            batch.draw(layer("decal_$d"), bx, by + lift, ox, oy, s.width.toFloat(), s.height.toFloat(), 1f, 1f, pitch)
            batch.color = Color.WHITE
            batch.shader = paint
        }
        looks.wing?.let { batch.draw(layer("wing_$it"), bx, by + lift, ox, oy, s.width.toFloat(), s.height.toFloat(), 1f, 1f, pitch) }
        if (looks.scoop) batch.draw(layer("scoop"), bx, by + lift, ox, oy, s.width.toFloat(), s.height.toFloat(), 1f, 1f, pitch)
        if (lit && popupRegion != null) batch.draw(popupRegion, bx, by + lift, ox, oy, s.width.toFloat(), s.height.toFloat(), 1f, 1f, pitch)

        batch.shader = tint
        tint.setUniformf("u_ghost", ghost)
        tint.setUniformf("u_time", time)
        tint.setUniformf("u_lit", if (litTrim) 0.55f else 0f)
        if (litTrim) lights!!.apply(tint, shakeX, shakeY)
        batch.draw(trim, bx, by + lift, ox, oy, s.width.toFloat(), s.height.toFloat(), 1f, 1f, pitch)
        if (ghost <= 0f) {
            if (looks.blower) batch.draw(layer("blower"), bx, by + lift, ox, oy, s.width.toFloat(), s.height.toFloat(), 1f, 1f, pitch)
            if (looks.intercooler) batch.draw(layer("intercooler"), bx, by + lift, ox, oy, s.width.toFloat(), s.height.toFloat(), 1f, 1f, pitch)
            if (looks.sidepipe) batch.draw(layer("sidepipe"), bx, by + lift, ox, oy, s.width.toFloat(), s.height.toFloat(), 1f, 1f, pitch)
        }
        if (lit && ghost <= 0f) batch.draw(lampLayer, bx, by + lift, ox, oy, s.width.toFloat(), s.height.toFloat(), 1f, 1f, pitch)
        batch.shader = null
    }

    private fun baseY(groundY: Float) = floor(groundY - (s.height - s.groundY))

    /** Exhaust tip, headlamp and tail lamp positions this frame, in framebuffer pixels. */
    fun exhaustAt(x: Float, groundY: Float): Pair<Float, Float> {
        val e = if (looks.sidepipe) s.sideExhaust ?: s.exhaust else s.exhaust
        return floor(x) + e.first to baseY(groundY) + (s.height - e.second)
    }
    fun headlampAt(x: Float, groundY: Float) = floor(x) + s.headlight.first to baseY(groundY) + (s.height - s.headlight.second)
    fun taillampAt(x: Float, groundY: Float) = floor(x) + s.taillight.first to baseY(groundY) + (s.height - s.taillight.second)

    /** Lamps into the bloom buffer. */
    fun drawGlow(batch: SpriteBatch, x: Float, groundY: Float, pitch: Float = 0f, lift: Float = 0f) {
        val bx = floor(x)
        val by = floor(groundY - (s.height - s.groundY))
        batch.draw(lampLayer, bx, by + lift, s.rearWheel.first, s.height - s.groundY, s.width.toFloat(), s.height.toFloat(), 1f, 1f, pitch)
    }

    /** Headlight beam thrown down the strip ahead of the car (additive). */
    fun drawBeam(batch: SpriteBatch, x: Float, groundY: Float, color: Color, alpha: Float) {
        val bx = floor(x)
        val by = floor(groundY - (s.height - s.groundY))
        val hx = bx + s.headlight.first
        val hy = by + (s.height - s.headlight.second)
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE)
        batch.setColor(color.r, color.g, color.b, alpha)
        batch.draw(cone, hx - 2f, hy - 30f, 170f, 40f)
        batch.color = Color.WHITE
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
    }
}

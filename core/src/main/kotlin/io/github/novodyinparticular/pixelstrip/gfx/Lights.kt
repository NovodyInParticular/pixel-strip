package io.github.novodyinparticular.pixelstrip.gfx

import com.badlogic.gdx.graphics.glutils.ShaderProgram

/** Up to [MAX] point lights per frame (framebuffer pixels), uploaded to the lit shaders. */
class Lights {
    private val pos = FloatArray(MAX * 3)
    private val col = FloatArray(MAX * 4)
    private val shifted = FloatArray(MAX * 3)
    var count = 0
        private set

    fun clear() {
        count = 0
    }

    /** z = height toward the viewer (bigger = flatter, broader light). Ignored once full. */
    fun add(x: Float, y: Float, z: Float, r: Float, g: Float, b: Float, radius: Float) {
        if (count >= MAX) return
        pos[count * 3] = x
        pos[count * 3 + 1] = y
        pos[count * 3 + 2] = z
        col[count * 4] = r
        col[count * 4 + 1] = g
        col[count * 4 + 2] = b
        col[count * 4 + 3] = radius
        count++
    }

    /** The scene buffer is offset by camera shake, so lights shift with it. */
    fun apply(shader: ShaderProgram, offsetX: Float = 0f, offsetY: Float = 0f) {
        for (i in 0 until count) {
            shifted[i * 3] = pos[i * 3] - offsetX
            shifted[i * 3 + 1] = pos[i * 3 + 1] - offsetY
            shifted[i * 3 + 2] = pos[i * 3 + 2]
        }
        shader.setUniform3fv("u_lightPos", shifted, 0, MAX * 3)
        shader.setUniform4fv("u_lightColor", col, 0, MAX * 4)
        shader.setUniformi("u_lightCount", count)
    }

    companion object {
        const val MAX = 8
    }
}

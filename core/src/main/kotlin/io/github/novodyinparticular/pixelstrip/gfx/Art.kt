package io.github.novodyinparticular.pixelstrip.gfx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.utils.Disposable

/** Texture and shader cache. Every texture is nearest-filtered: the pixels stay crisp. */
class Art : Disposable {
    private val textures = HashMap<String, Texture>()
    private val shaders = HashMap<String, ShaderProgram>()
    /** Crew emblems, drawn on demand. */
    val emblems = EmblemCache()

    fun tex(path: String): Texture = textures.getOrPut(path) {
        Texture(Gdx.files.internal(path)).apply {
            setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest)
        }
    }

    fun has(path: String) = Gdx.files.internal(path).exists()

    fun shader(vert: String, frag: String): ShaderProgram = shaders.getOrPut("$vert|$frag") {
        ShaderProgram.pedantic = false
        val s = ShaderProgram(Gdx.files.internal("shaders/$vert"), Gdx.files.internal("shaders/$frag"))
        check(s.isCompiled) { "Shader $vert + $frag failed:\n${s.log}" }
        s
    }

    override fun dispose() {
        emblems.dispose()
        textures.values.forEach { it.dispose() }
        shaders.values.forEach { it.dispose() }
        textures.clear()
        shaders.clear()
    }
}

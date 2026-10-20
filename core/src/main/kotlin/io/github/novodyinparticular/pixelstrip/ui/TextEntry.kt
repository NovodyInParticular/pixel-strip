package io.github.novodyinparticular.pixelstrip.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter

/**
 * One line of typed text (names, crew tags): the hardware keyboard on desktop, the on-screen one on
 * a phone. Upper case only, limited to [allowed] characters and [max] long. While it's open the
 * screen's button shortcuts are off; Enter finishes, Escape (or Back) cancels.
 */
class TextEntry(
    val max: Int,
    private val allowed: (Char) -> Boolean = { it in 'A'..'Z' || it in '0'..'9' || it == ' ' },
) : InputAdapter() {
    var text = ""
        private set
    var active = false
        private set
    private var onDone: ((String?) -> Unit)? = null

    /** Start typing, beginning with [initial]; [then] gets the text, or null if cancelled. */
    fun open(initial: String, then: (String?) -> Unit) {
        text = initial.uppercase().filter(allowed).take(max)
        onDone = then
        active = true
        Gdx.input.inputProcessor = this
        Gdx.input.setOnscreenKeyboardVisible(true)
    }

    private fun finish(ok: Boolean) {
        if (!active) return
        active = false
        Gdx.input.inputProcessor = null
        Gdx.input.setOnscreenKeyboardVisible(false)
        val then = onDone
        onDone = null
        then?.invoke(if (ok) text.trim() else null)
    }

    fun cancel() = finish(false)

    override fun keyDown(keycode: Int): Boolean {
        when (keycode) {
            Input.Keys.ESCAPE, Input.Keys.BACK -> finish(false)
            Input.Keys.ENTER, Input.Keys.NUMPAD_ENTER -> finish(true)
            else -> return false
        }
        return true
    }

    override fun keyTyped(character: Char): Boolean {
        when {
            character == '\b' -> text = text.dropLast(1)
            character == '\r' || character == '\n' -> finish(true)
            else -> {
                val c = character.uppercaseChar()
                if (allowed(c) && text.length < max && !(c == ' ' && (text.isEmpty() || text.endsWith(' ')))) text += c
            }
        }
        return true
    }

    /** Text with a blinking cursor, for drawing. */
    fun shown(time: Float) = if (active && (time * 2.5f).toInt() % 2 == 0) "${text}_" else text

    companion object {
        val TAG: (Char) -> Boolean = { it in 'A'..'Z' || it in '0'..'9' }
    }
}

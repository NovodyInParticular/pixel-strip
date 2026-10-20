package io.github.novodyinparticular.pixelstrip

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle

/** Things the core game asks of the launcher (Android, desktop). */
interface Platform {
    val isMobile: Boolean
    fun vibrate(millis: Int) {}

    /** Where save files live. Android: app-private storage; desktop overrides to stay out of assets/. */
    fun saveFile(name: String): FileHandle = Gdx.files.local(name)
}

/**
 * Desktop test hook: autoplay a race and capture frames at these times (seconds) into [dir],
 * then quit. [close] makes the ghost as quick as the autoplay driver (photo finishes).
 * [screen] starts on a menu screen instead (title, garage, dealer, settings, starter).
 */
class ShotPlan(val dir: String, val times: List<Float>, val close: Boolean = false, val screen: String? = null)

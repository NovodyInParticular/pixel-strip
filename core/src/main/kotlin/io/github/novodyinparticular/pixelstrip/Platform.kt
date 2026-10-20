package io.github.novodyinparticular.pixelstrip

/** Things the core game asks of the launcher (Android, desktop). */
interface Platform {
    val isMobile: Boolean
    fun vibrate(millis: Int) {}
}

/**
 * Desktop test hook: autoplay a race and capture frames at these times (seconds) into [dir],
 * then quit. [close] makes the ghost as quick as the autoplay driver (photo finishes).
 */
class ShotPlan(val dir: String, val times: List<Float>, val close: Boolean = false)

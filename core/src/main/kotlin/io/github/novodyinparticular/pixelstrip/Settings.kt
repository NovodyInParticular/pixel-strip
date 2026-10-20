package io.github.novodyinparticular.pixelstrip

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Preferences

enum class Quality { LOW, MEDIUM, HIGH }

/** Player options, saved through libGDX Preferences. */
class Settings {
    private val prefs: Preferences = Gdx.app.getPreferences("pixel-strip-settings")

    var quality: Quality = runCatching { Quality.valueOf(prefs.getString("quality", "HIGH")) }.getOrDefault(Quality.HIGH)
    var crt: Boolean = prefs.getBoolean("crt", false)
    var sound: Boolean = prefs.getBoolean("sound", true)
    var vibration: Boolean = prefs.getBoolean("vibration", true)
    var metric: Boolean = prefs.getBoolean("metric", false)

    fun save() {
        prefs.putString("quality", quality.name)
        prefs.putBoolean("crt", crt)
        prefs.putBoolean("sound", sound)
        prefs.putBoolean("vibration", vibration)
        prefs.putBoolean("metric", metric)
        prefs.flush()
    }
}

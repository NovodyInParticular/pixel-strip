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
    /** Race from the driver's seat (the first-person view) instead of the side view. */
    var cockpit: Boolean = prefs.getBoolean("cockpit", false)
    /** Career rivals' cars are paced to the player's build so races come down to driving (race/RivalPace). */
    var closeRacing: Boolean = prefs.getBoolean("closeRacing", true)
    /** How hard career rivals push (race/Difficulty). */
    var difficulty: io.github.novodyinparticular.pixelstrip.race.Difficulty =
        io.github.novodyinparticular.pixelstrip.race.Difficulty.of(prefs.getString("difficulty", null))

    /** Wi-Fi races: the name shown to the other player, and the last host address typed in. */
    var lanName: String = prefs.getString("lanName", "")
    var lanLastIp: String = prefs.getString("lanLastIp", "")

    fun save() {
        prefs.putString("quality", quality.name)
        prefs.putBoolean("crt", crt)
        prefs.putBoolean("sound", sound)
        prefs.putBoolean("vibration", vibration)
        prefs.putBoolean("metric", metric)
        prefs.putBoolean("cockpit", cockpit)
        prefs.putBoolean("closeRacing", closeRacing)
        prefs.putString("difficulty", difficulty.name)
        prefs.putString("lanName", lanName)
        prefs.putString("lanLastIp", lanLastIp)
        prefs.flush()
    }
}

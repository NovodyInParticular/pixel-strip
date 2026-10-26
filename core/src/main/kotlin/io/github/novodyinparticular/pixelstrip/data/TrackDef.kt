package io.github.novodyinparticular.pixelstrip.data

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.utils.JsonReader
import com.badlogic.gdx.utils.JsonValue

/**
 * A track, loaded from assets/data/tracks/<id>.json. Vertical positions ("top") are measured in
 * framebuffer pixels from the top of the 270 px screen, the way the art is laid out.
 */
class TrackDef(
    val id: String,
    val name: String,
    val dir: String,
    val sky: String,
    val lut: String,
    val lutMix: Float,
    /** Light on the cars before any point lights: the sky/night colour. */
    val ambient: Color,
    /** Heat shimmer over the asphalt (pixels), 0 = none. */
    val asphaltHaze: Float,
    val roadTop: Float,
    val roadBottom: Float,
    val stars: Int,
    val starsMaxTop: Float,
    val farLaneTop: Float,
    val nearLaneTop: Float,
    val layers: List<Layer>,
    val props: List<Prop>,
    val foreground: Layer?,
    val lampPool: Color?,
    val headlights: Boolean,
    val bloomGain: Float,
    /** Surface grip multiplier (prepped strips ~1.25; wet or snowy less). */
    val trackGrip: Float,
    /** "rain", "snow", "sand", "embers" or null. */
    val weather: String?,
    /** Wet asphalt: cars and lamps reflect in it. */
    val wet: Boolean,
    val song: String,
) {
    class Layer(
        val image: String,
        val glow: String?,
        val top: Float,
        val parallax: Float,
        val tile: Boolean,
        val x: Float,
        val drift: Float,
        val frames: Int,
        val frameTime: Float,
        val chunk: Int,
        val blinkers: String?,
        val lamp: Pair<Float, Float>?,
        /** Pixels per second this layer sinks (a setting sun). */
        val sink: Float,
    )

    /** Placed once along the strip. [at] is metres from the start line; "finish" resolves per race. */
    class Prop(
        val image: String,
        val glow: String?,
        val at: Float?,
        val atFinish: Boolean,
        val dx: Float,
        val top: Float,
        val parallax: Float,
    )

    companion object {
        /**
         * Tracks open to quick and online races, rivals, tournaments and the crew war (the server's
         * TRACK_IDS, server/functions/src/tracks.ts). Joe's boss race is always on San Antonio.
         */
        val GENERAL = listOf("neon_city", "desert", "port", "coast", "airfield", "speedway", "sanantonio")

        private val names = HashMap<String, String>()

        /** A track's name as the game shows it ("SAN ANTONIO"), read once from its file. */
        fun displayName(id: String): String = names.getOrPut(id) {
            runCatching { JsonReader().parse(Gdx.files.internal("data/tracks/$id.json")).getString("name") }.getOrDefault(id.replace('_', ' ')).uppercase()
        }

        fun load(id: String): TrackDef = parse(JsonReader().parse(Gdx.files.internal("data/tracks/$id.json")))

        private fun layer(j: JsonValue, dir: String) = Layer(
            image = "$dir/${j.getString("image")}",
            glow = j.getString("glow", null)?.let { "$dir/$it" },
            top = j.getFloat("top"),
            parallax = j.getFloat("parallax", 1f),
            tile = j.getBoolean("tile", true),
            x = j.getFloat("x", 0f),
            drift = j.getFloat("drift", 0f),
            frames = j.getInt("frames", 1),
            frameTime = j.getFloat("frameTime", 0.2f),
            chunk = j.getInt("chunk", 0),
            blinkers = j.getString("blinkers", null)?.let { "$dir/$it" },
            lamp = j.get("lamp")?.let { it.getFloat(0) to it.getFloat(1) },
            sink = j.getFloat("sink", 0f),
        )

        fun parse(j: JsonValue): TrackDef {
            val dir = j.getString("dir")
            val pool = j.get("lampPool")
            return TrackDef(
                id = j.getString("id"),
                name = j.getString("name"),
                dir = dir,
                sky = "$dir/${j.getString("sky")}",
                lut = "$dir/${j.getString("lut")}",
                lutMix = j.getFloat("lutMix", 1f),
                ambient = j.get("ambient")?.let { Color(it.getFloat(0), it.getFloat(1), it.getFloat(2), 1f) } ?: Color(1f, 1f, 1f, 1f),
                asphaltHaze = j.getFloat("asphaltHaze", 0f),
                roadTop = j.getFloat("roadTop", 203f),
                roadBottom = j.getFloat("roadBottom", 262f),
                stars = j.getInt("stars", 0),
                starsMaxTop = j.getFloat("starsMaxTop", 120f),
                farLaneTop = j.getFloat("farLaneTop"),
                nearLaneTop = j.getFloat("nearLaneTop"),
                layers = j.get("layers").map { layer(it, dir) },
                props = j.get("props")?.map {
                    val at = it.get("at")
                    Prop(
                        image = "$dir/${it.getString("image")}",
                        glow = it.getString("glow", null)?.let { g -> "$dir/$g" },
                        at = if (at.isString) null else at.asFloat(),
                        atFinish = at.isString && at.asString() == "finish",
                        dx = it.getFloat("dx", 0f),
                        top = it.getFloat("top"),
                        parallax = it.getFloat("parallax", 1f),
                    )
                } ?: emptyList(),
                foreground = j.get("foreground")?.let { layer(it, dir) },
                lampPool = pool?.let { Color(it.getFloat(0), it.getFloat(1), it.getFloat(2), it.getFloat(3)) },
                headlights = j.getBoolean("headlights", false),
                bloomGain = j.getFloat("bloomGain", 1f),
                trackGrip = j.getFloat("trackGrip", 1.25f),
                weather = j.getString("weather", null),
                wet = j.getBoolean("wet", false),
                song = j.getString("song", "neon_city"),
            )
        }
    }
}

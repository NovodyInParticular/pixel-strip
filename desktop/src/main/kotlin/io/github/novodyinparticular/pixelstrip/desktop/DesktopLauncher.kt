package io.github.novodyinparticular.pixelstrip.desktop

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import io.github.novodyinparticular.pixelstrip.PixelStrip
import io.github.novodyinparticular.pixelstrip.Platform
import io.github.novodyinparticular.pixelstrip.ShotPlan

/**
 * Development build. `--shots DIR t1,t2,...` autoplays a race and saves frames at those times
 * (seconds since the race screen opened), then quits; add `--close` for an evenly matched ghost.
 * `--size WxH` sets the window size; `--car ID` picks the car (until the garage exists).
 */
fun main(args: Array<String>) {
    val shots = args.indexOf("--shots").takeIf { it >= 0 }?.let {
        ShotPlan(args[it + 1], args[it + 2].split(",").map(String::toFloat), close = "--close" in args,
            screen = args.indexOf("--screen").takeIf { i -> i >= 0 }?.let { i -> args[i + 1] }, loaded = "--loaded" in args)
    }
    val (w, h) = args.indexOf("--size").takeIf { it >= 0 }?.let { args[it + 1].split("x").map(String::toInt) } ?: listOf(1440, 810)
    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle("Pixel Strip")
        setWindowedMode(w, h)
        setForegroundFPS(60)
        useVsync(true)
        setResizable(shots == null)
    }
    val platform = object : Platform {
        override val isMobile = false
        // Desktop saves go to ~/.local/share/pixel-strip, never into the assets folder the game runs from
        override fun saveFile(name: String) = Gdx.files.external(".local/share/pixel-strip/$name")
    }
    val car = args.indexOf("--car").takeIf { it >= 0 }?.let { args[it + 1] }
    Lwjgl3Application(PixelStrip(platform, shots, car), config)
}

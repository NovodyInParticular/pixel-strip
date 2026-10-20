package io.github.novodyinparticular.pixelstrip.android

import android.content.Context
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import io.github.novodyinparticular.pixelstrip.PixelStrip
import io.github.novodyinparticular.pixelstrip.Platform

class AndroidLauncher : AndroidApplication() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        val platform = object : Platform {
            override val isMobile = true
            override fun vibrate(millis: Int) {
                if (vibrator?.hasVibrator() == true) {
                    vibrator.vibrate(VibrationEffect.createOneShot(millis.toLong(), VibrationEffect.DEFAULT_AMPLITUDE))
                }
            }
        }
        val config = AndroidApplicationConfiguration().apply {
            useImmersiveMode = true
            useAccelerometer = false
            useCompass = false
            useGL30 = false
        }
        initialize(PixelStrip(platform), config)
    }
}

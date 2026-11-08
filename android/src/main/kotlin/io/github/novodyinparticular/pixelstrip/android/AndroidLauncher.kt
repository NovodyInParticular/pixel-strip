package io.github.novodyinparticular.pixelstrip.android

import android.content.Context
import android.net.wifi.WifiManager
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
        val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val multicast = wifi?.createMulticastLock("pixelstrip-lan")?.apply { setReferenceCounted(false) }
        val platform = object : Platform {
            override val isMobile = true
            override fun vibrate(millis: Int) {
                if (vibrator?.hasVibrator() == true) {
                    vibrator.vibrate(VibrationEffect.createOneShot(millis.toLong(), VibrationEffect.DEFAULT_AMPLITUDE))
                }
            }

            override fun lanDiscovery(on: Boolean) {
                runCatching { if (on) multicast?.acquire() else if (multicast?.isHeld == true) multicast.release() }
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

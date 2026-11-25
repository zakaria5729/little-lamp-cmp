package com.zakaria5729.flash_table_lamp

import android.app.Application
import android.app.ActivityManager

class LumaApplication : Application() {
    lateinit var flashlight: FlashlightController
        private set
    lateinit var screenLamp: ScreenLampController
        private set
    lateinit var sounds: SoundController
        private set
    var hasOpenedUi = false

    override fun onCreate() {
        super.onCreate()
        featureAction(this) {
            val notifications = getSystemService(android.app.NotificationManager::class.java)
            notifications.cancel(3)
            if (android.os.Build.VERSION.SDK_INT >= 26) notifications.deleteNotificationChannel("after_flashlight")
            deleteSharedPreferences("after_light")
        }
        flashlight = FlashlightController(this)
        sounds = SoundController(this)
        screenLamp = ScreenLampController(this, flashlight)
        FlashlightNotification(this).show()
    }

    @Suppress("DEPRECATION")
    fun resetIdleSession(): Boolean {
        val light = flashlight.state.value
        val sound = sounds.state.value
        if (light.isOn || light.isStarting || light.isStopping || screenLamp.state.value.isOn || sound.isPlaying || sound.isPreparing) return false
        val services = getSystemService(ActivityManager::class.java).getRunningServices(Int.MAX_VALUE)
        if (services.any { (it.started || it.restarting > 0) &&
                (it.service.className == FlashlightService::class.java.name || it.service.className == SoundPlaybackService::class.java.name) }) return false
        FlashlightSessionStore(this).clear()
        sounds.resetIdleControls()
        flashlight.resetIdleControls()
        screenLamp.resetIdleControls()
        FlashlightNotification(this).show()
        return true
    }

    fun stopAll() {
        featureAction(this) { screenLamp.stop() }
        featureAction(this) { flashlight.turnOff(smooth = false) }
        featureAction(this) { sounds.stop() }
    }
}

package com.zakaria5729.flash_table_lamp

import android.content.Context
import android.util.Log
import android.widget.Toast

internal fun featureAction(context: Context, action: () -> Unit) {
    try { action() } catch (error: Exception) {
        Log.e("LittleLight", "Feature action failed", error)
        Toast.makeText(context, "Couldn't complete this action. Please try again.", Toast.LENGTH_LONG).show()
    }
}

internal class GuardedScreenLampActions(private val context: Context, private val delegate: ScreenLampActions) : ScreenLampActions {
    override fun selectPage(page: LampPage) = featureAction(context) { delegate.selectPage(page) }
    override fun setTheme(theme: ThemeMode) = featureAction(context) { delegate.setTheme(theme) }
    override fun setColor(color: Int, secondary: Boolean) = featureAction(context) { delegate.setColor(color, secondary) }
    override fun setBrightness(percent: Int) = featureAction(context) { delegate.setBrightness(percent) }
    override fun setGradient(enabled: Boolean) = featureAction(context) { delegate.setGradient(enabled) }
    override fun setGradientCount(count: Int) = featureAction(context) { delegate.setGradientCount(count) }
    override fun setGradientColor(index: Int, color: Int) = featureAction(context) { delegate.setGradientColor(index, color) }
    override fun setGradientAngle(angle: Int) = featureAction(context) { delegate.setGradientAngle(angle) }
    override fun setZonesEnabled(enabled: Boolean) = featureAction(context) { delegate.setZonesEnabled(enabled) }
    override fun setZoneCount(count: Int) = featureAction(context) { delegate.setZoneCount(count) }
    override fun setZoneLayout(layout: ZoneLayout) = featureAction(context) { delegate.setZoneLayout(layout) }
    override fun setZoneColor(index: Int, color: Int) = featureAction(context) { delegate.setZoneColor(index, color) }
    override fun setZoneBrightness(index: Int, percent: Int) = featureAction(context) { delegate.setZoneBrightness(index, percent) }
    override fun setZoneAnimation(mode: ZoneAnimation) = featureAction(context) { delegate.setZoneAnimation(mode) }
    override fun setCycleColors(enabled: Boolean) = featureAction(context) { delegate.setCycleColors(enabled) }
    override fun setCyclePalette(palette: CyclePalette) = featureAction(context) { delegate.setCyclePalette(palette) }
    override fun setCycleInterval(seconds: Int) = featureAction(context) { delegate.setCycleInterval(seconds) }
    override fun setSmoothTransitions(enabled: Boolean) = featureAction(context) { delegate.setSmoothTransitions(enabled) }
    override fun setTransitionMillis(millis: Int) = featureAction(context) { delegate.setTransitionMillis(millis) }
    override fun setUltraDim(enabled: Boolean) = featureAction(context) { delegate.setUltraDim(enabled) }
    override fun setDual(enabled: Boolean) = featureAction(context) { delegate.setDual(enabled) }
    override fun setCriticalStop(enabled: Boolean) = featureAction(context) { delegate.setCriticalStop(enabled) }
    override fun setEmergencyLed(enabled: Boolean) = featureAction(context) { delegate.setEmergencyLed(enabled) }
    override fun setDim(enabled: Boolean) = featureAction(context) { delegate.setDim(enabled) }
    override fun setMode(mode: DimmingMode) = featureAction(context) { delegate.setMode(mode) }
    override fun setRate(rate: Int) = featureAction(context) { delegate.setRate(rate) }
    override fun setTimer(seconds: Int) = featureAction(context) { delegate.setTimer(seconds) }
    override fun start() = featureAction(context) { delegate.start() }
    override fun stop() = featureAction(context) { delegate.stop() }
    override fun stopSmoothly() = featureAction(context) { delegate.stopSmoothly() }
    override fun showControls() = featureAction(context) { delegate.showControls() }
    override fun setLocked(locked: Boolean) = featureAction(context) { delegate.setLocked(locked) }
    override fun dismissMessage() = featureAction(context) { delegate.dismissMessage() }
    override fun readingMode() = featureAction(context) { delegate.readingMode() }
    override fun emergency() = featureAction(context) { delegate.emergency() }
    override fun savePreset(name: String) = featureAction(context) { delegate.savePreset(name) }
    override fun applyPreset(preset: LightingPreset) = featureAction(context) { delegate.applyPreset(preset) }
    override fun deletePreset(name: String) = featureAction(context) { delegate.deletePreset(name) }
}

internal class GuardedSoundActions(private val context: Context, private val delegate: SoundActions) : SoundActions {
    override fun refreshSoundLibrary() = featureAction(context) { delegate.refreshSoundLibrary() }
    override fun downloadSound(id: String) = featureAction(context) { delegate.downloadSound(id) }
    override fun playWithLight(screenLamp: Boolean) = featureAction(context) { delegate.playWithLight(screenLamp) }
    override fun setPlayWithLight(screenLamp: Boolean, enabled: Boolean) = featureAction(context) { delegate.setPlayWithLight(screenLamp, enabled) }
    override fun selectSound(id: String) = featureAction(context) { delegate.selectSound(id) }
    override fun play() = featureAction(context) { delegate.play() }
    override fun pause() = featureAction(context) { delegate.pause() }
    override fun stop() = featureAction(context) { delegate.stop() }
    override fun setVolume(percent: Int) = featureAction(context) { delegate.setVolume(percent) }
    override fun setTimer(seconds: Int) = featureAction(context) { delegate.setTimer(seconds) }
    override fun setFadeGradually(enabled: Boolean) = featureAction(context) { delegate.setFadeGradually(enabled) }
    override fun setFadeMode(mode: DimmingMode) = featureAction(context) { delegate.setFadeMode(mode) }
    override fun setFadeRate(percent: Int) = featureAction(context) { delegate.setFadeRate(percent) }
    override fun removeSound(id: String) = featureAction(context) { delegate.removeSound(id) }
    override fun dismissMessage() = featureAction(context) { delegate.dismissMessage() }
}

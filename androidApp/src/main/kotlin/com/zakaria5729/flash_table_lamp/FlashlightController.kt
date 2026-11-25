package com.zakaria5729.flash_table_lamp

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class FlashlightController(private val context: Context) {
    private val preferences = context.getSharedPreferences("flashlight", Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(FlashlightState())
    val state = mutableState.asStateFlow()
    val cameraManager = context.getSystemService(CameraManager::class.java)
    var isUiVisible = false
        private set
    var cameraId: String? = null
        private set
    private var defaultStrength = 1

    init {
        try {
            val cameras = cameraManager.cameraIdList.map { it to cameraManager.getCameraCharacteristics(it) }
            val available = cameras.filter { it.second.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
            val camera = available.firstOrNull {
                it.second.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: available.firstOrNull()
            cameraId = camera?.first
            val maximum = if (Build.VERSION.SDK_INT >= 33) {
                camera?.second?.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
            } else 1
            val default = if (Build.VERSION.SDK_INT >= 33) {
                camera?.second?.get(CameraCharacteristics.FLASH_INFO_STRENGTH_DEFAULT_LEVEL) ?: 1
            } else 1
            defaultStrength = default.coerceIn(1, maximum.coerceAtLeast(1))
            mutableState.value = FlashlightState(
                hasFlash = camera != null,
                maxStrength = maximum.coerceAtLeast(1),
                strength = preferences.getInt("strength", default).coerceIn(1, maximum.coerceAtLeast(1)),
                timerSeconds = preferences.getInt("timer", 900).coerceIn(0, 86400),
                dimGradually = preferences.getBoolean("dim_gradually", false) && maximum > 1,
                dimmingMode = DimmingMode.entries.firstOrNull { it.name == preferences.getString("dimming_mode", null) } ?: DimmingMode.Auto,
                dimPercentPerMinute = preferences.getInt("dim_rate", 10).coerceIn(1, 100),
                rhythmEnabled = preferences.getBoolean("rhythm", false),
                rhythmMode = RhythmMode.entries.firstOrNull { it.name == preferences.getString("rhythm_mode", null) } ?: RhythmMode.Auto,
                rhythmOnSeconds = preferences.getInt("rhythm_on", 2).coerceIn(1, 60),
                rhythmOffSeconds = preferences.getInt("rhythm_off", 2).coerceIn(1, 60),
            )
        } catch (_: Exception) {
            mutableState.update { it.copy(hasFlash = false, message = "Couldn't access the flashlight. Close any camera apps and reopen $APP_NAME.") }
        }
    }

    fun turnOn() {
        if (state.value.isOn || state.value.isStarting || !state.value.hasFlash) return
        val screen = (context.applicationContext as LumaApplication).screenLamp.state.value
        if (batteryProtectionTriggered(screen.stopAtCriticalBattery, screen.batteryPercent, screen.charging)) {
            showMessage("Battery protection is enabled. Charge your phone or change the battery setting.")
            return
        }
        mutableState.update { it.copy(isStarting = true, message = null) }
        try {
            ContextCompat.startForegroundService(context, intent(FlashlightService.ACTION_START))
        } catch (_: Exception) {
            stopped("Couldn't start the flashlight. Reopen $APP_NAME and try again.")
        }
    }

    fun turnOff(smooth: Boolean = true) {
        FlashlightSessionStore(context).clear()
        if (smooth && state.value.isOn && !state.value.isStopping) send(FlashlightService.ACTION_STOP)
        else if (!state.value.isStopping || !smooth) context.stopService(Intent(context, FlashlightService::class.java))
    }

    fun setStrength(strength: Int) {
        val level = strength.coerceIn(1, state.value.maxStrength)
        if (level == state.value.strength) return
        preferences.edit { putInt("strength", level) }
        mutableState.update { it.copy(strength = level) }
        if (state.value.isOn) send(FlashlightService.ACTION_STRENGTH) else FlashlightNotification(context).show()
    }

    fun setTimer(seconds: Int) {
        val duration = seconds.coerceIn(0, 86400)
        preferences.edit { putInt("timer", duration) }
        mutableState.update { it.copy(timerSeconds = duration) }
        if (state.value.isOn) send(FlashlightService.ACTION_TIMER) else FlashlightNotification(context).show()
    }

    fun setDimGradually(enabled: Boolean) {
        val dim = enabled && state.value.supportsDimming
        preferences.edit { putBoolean("dim_gradually", dim) }
        mutableState.update { it.copy(dimGradually = dim) }
        if (state.value.isOn) send(FlashlightService.ACTION_DIM_CONFIG) else FlashlightNotification(context).show()
    }

    fun setDimmingMode(mode: DimmingMode) {
        preferences.edit { putString("dimming_mode", mode.name) }
        mutableState.update { it.copy(dimmingMode = mode) }
        if (state.value.isOn) send(FlashlightService.ACTION_DIM_CONFIG) else FlashlightNotification(context).show()
    }

    fun setDimmingRate(percent: Int) {
        val rate = percent.coerceIn(1, 100)
        preferences.edit { putInt("dim_rate", rate) }
        mutableState.update { it.copy(dimPercentPerMinute = rate) }
        if (state.value.isOn) send(FlashlightService.ACTION_DIM_CONFIG) else FlashlightNotification(context).show()
    }

    internal fun refreshNotification() {
        if (state.value.isOn) send(FlashlightService.ACTION_DIM) else FlashlightNotification(context).show()
    }

    fun setRhythm(enabled: Boolean) {
        preferences.edit { putBoolean("rhythm", enabled) }
        mutableState.update { it.copy(rhythmEnabled = enabled) }
        if (state.value.isOn) send(FlashlightService.ACTION_RHYTHM) else FlashlightNotification(context).show()
    }
    fun setRhythmMode(mode: RhythmMode) {
        preferences.edit { putString("rhythm_mode", mode.name) }
        mutableState.update { it.copy(rhythmMode = mode) }
        if (state.value.isOn) send(FlashlightService.ACTION_RHYTHM) else FlashlightNotification(context).show()
    }
    fun setRhythmTimes(onSeconds: Int, offSeconds: Int) {
        val onDurationSeconds = onSeconds.coerceIn(1, 60)
        val offDurationSeconds = offSeconds.coerceIn(1, 60)
        preferences.edit { putInt("rhythm_on", onDurationSeconds); putInt("rhythm_off", offDurationSeconds) }
        mutableState.update { it.copy(rhythmOnSeconds = onDurationSeconds, rhythmOffSeconds = offDurationSeconds) }
        if (state.value.isOn) send(FlashlightService.ACTION_RHYTHM) else FlashlightNotification(context).show()
    }

    fun setUiVisible(visible: Boolean) {
        isUiVisible = visible
        if (state.value.isOn) send(FlashlightService.ACTION_DIM)
    }

    fun showMessage(message: String) {
        mutableState.update { it.copy(message = message) }
    }

    fun dismissMessage() {
        mutableState.update { it.copy(message = null) }
    }

    internal fun started(starting: Boolean = false) {
        val newlyStarted = !state.value.isOn
        mutableState.update { it.copy(isOn = true, isStarting = starting, isStopping = false, message = null) }
        if (newlyStarted) (context.applicationContext as LumaApplication).sounds.lightStarted("flashlight")
    }

    internal fun startupFinished() { mutableState.update { it.copy(isStarting = false) } }
    internal fun stopping() { mutableState.update { it.copy(isStopping = true, isStarting = false) } }

    fun resetIdleControls() {
        val current = state.value
        if (current.isOn || current.isStarting || current.isStopping) return
        preferences.edit { clear() }
        mutableState.value = FlashlightState(hasFlash = current.hasFlash, isAvailable = current.isAvailable,
            maxStrength = current.maxStrength, strength = defaultStrength, currentStrength = defaultStrength)
    }

    internal fun tick(seconds: Int) {
        mutableState.update { it.copy(remainingSeconds = seconds) }
    }

    internal fun appliedStrength(strength: Int) {
        mutableState.update { it.copy(currentStrength = strength, torchLit = strength > 0) }
    }

    internal fun stopped(message: String? = null) {
        (context.applicationContext as LumaApplication).sounds.lightStopped("flashlight")
        mutableState.update {
            it.copy(isOn = false, isStarting = false, isStopping = false, torchLit = false, remainingSeconds = 0, message = message ?: it.message)
        }
    }

    private fun send(action: String) {
        try {
            context.startService(intent(action))
        } catch (_: Exception) {
            turnOff(smooth = false)
            stopped("The flashlight was stopped. Turn it on to try again.")
        }
    }

    private fun intent(action: String) = Intent(context, FlashlightService::class.java).setAction(action)
}

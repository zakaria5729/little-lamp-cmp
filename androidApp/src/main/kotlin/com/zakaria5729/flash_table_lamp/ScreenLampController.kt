package com.zakaria5729.flash_table_lamp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

class ScreenLampController(private val context: Context, private val flashlight: FlashlightController) : ScreenLampActions {
    private val prefs = context.getSharedPreferences("screen_lamp", Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(ScreenLampState())
    val state = mutableState.asStateFlow()
    private val handler = Handler(Looper.getMainLooper())
    private var deadline = 0L
    private var fadeStart = 0L
    private var fadeStartBrightness = 50
    private var brightnessAnimation: Runnable? = null

    private val colorTick = object : Runnable {
        override fun run() {
            val current = state.value
            if (!current.isOn || !current.fullscreen || !current.cycleColors || current.isStopping) return
            mutableState.update { it.copy(cycleIndex = if (it.cycleIndex >= Int.MAX_VALUE - 16) 0 else it.cycleIndex + 1) }
            handler.postDelayed(this, current.cycleInterval * 1000L)
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            val current = state.value
            if (!current.isOn || deadline == 0L || current.isStopping) return
            val now = SystemClock.elapsedRealtime()
            val remaining = remainingSeconds(deadline, now)
            if (remaining == 0) {
                stop()
                message("Screen lamp timer finished.")
                return
            }
            mutableState.update { it.copy(remainingSeconds = remaining) }
            val millis = deadline - now
            if (!current.dim && current.smoothTransitions && millis <= current.transitionMillis && current.fullscreen) {
                fadeToStop(millis.toInt())
                return
            }
            if (brightnessAnimation == null && current.fullscreen) {
                mutableState.update { it.copy(currentBrightness = desiredBrightness()) }
            }
            val untilFade = if (!current.dim && current.smoothTransitions && millis > current.transitionMillis)
                millis - current.transitionMillis else millis
            handler.postDelayed(this, minOf(1000L, untilFade.coerceAtLeast(1)))
        }
    }

    init {
        val old = ScreenLampState(
            color = prefs.getInt("color", 0xFFFFD9A0.toInt()), secondaryColor = prefs.getInt("secondary", 0xFF9FC8FF.toInt()),
            brightness = prefs.getInt("brightness", 50).coerceIn(1, 100), gradient = prefs.getBoolean("gradient", false),
            ultraDim = prefs.getBoolean("ultra_dim", false), dual = prefs.getBoolean("dual", false),
            timerSeconds = prefs.getInt("timer", 900).coerceIn(0, 86400), dim = prefs.getBoolean("dim", false),
            dimmingMode = DimmingMode.entries.firstOrNull { it.name == prefs.getString("mode", null) } ?: DimmingMode.Auto,
            dimRate = prefs.getInt("rate", 10).coerceIn(1, 100),
        )
        val lighting = try { prefs.getString("lighting", null)?.let { JSONObject(it).toLightingPreset() } } catch (_: Exception) { null }
        mutableState.value = (lighting?.let { old.withLightingPreset(it) } ?: old).copy(
            theme = ThemeMode.entries.firstOrNull { it.name == prefs.getString("theme", null) } ?: ThemeMode.System,
            stopAtCriticalBattery = prefs.getBoolean("critical_stop", false), emergencyLed = prefs.getBoolean("emergency_led", false),
            smoothTransitions = prefs.getBoolean("smooth_transitions", true), transitionMillis = prefs.getInt("transition_ms", 800).coerceIn(200, 2000),
            presets = loadPresets(),
        )
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                val percent = if (level >= 0 && scale > 0) level * 100 / scale else -1
                val charging = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
                mutableState.update { it.copy(batteryPercent = percent, charging = charging) }
                if (batteryProtectionTriggered(state.value.stopAtCriticalBattery, percent, charging) &&
                    (state.value.isOn || flashlight.state.value.isOn)) {
                    stop(); flashlight.turnOff()
                    message("Lights stopped to protect your remaining battery.")
                }
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun selectPage(page: LampPage) = update { copy(page = page) }
    override fun setTheme(theme: ThemeMode) = update { copy(theme = theme) }
    override fun setColor(color: Int, secondary: Boolean) {
        update { if (secondary) copy(secondaryColor = color, cycleColors = false) else copy(color = color, cycleColors = false) }
        scheduleColors()
    }
    override fun setGradientColor(index: Int, color: Int) {
        when (index) {
            0 -> setColor(color, false)
            1 -> setColor(color, true)
            in 2..3 -> { update { copy(gradientExtras = gradientExtras.mapIndexed { i, old -> if (i == index - 2) color else old }, cycleColors = false) }; scheduleColors() }
        }
    }
    override fun setGradientCount(count: Int) = update { copy(gradientCount = count.coerceIn(2, 4)) }
    override fun setGradientAngle(angle: Int) = update { copy(gradientAngle = angle.coerceIn(0, 360)) }
    override fun setGradient(enabled: Boolean) = update { copy(gradient = enabled, zonesEnabled = if (enabled) false else zonesEnabled) }
    override fun setZonesEnabled(enabled: Boolean) = update { copy(zonesEnabled = enabled, gradient = if (enabled) false else gradient) }
    override fun setZoneCount(count: Int) = update { copy(zoneCount = count.coerceIn(2, 8), zoneLayout = if (count >= 4) ZoneLayout.Grid else if (zoneLayout == ZoneLayout.Grid) ZoneLayout.Stacked else zoneLayout) }
    override fun setZoneLayout(layout: ZoneLayout) = update { copy(zoneLayout = layout) }
    override fun setZoneColor(index: Int, color: Int) { update { copy(zones = zones.mapIndexed { i, zone -> if (i == index) zone.copy(color = color) else zone }, cycleColors = false) }; scheduleColors() }
    override fun setZoneBrightness(index: Int, percent: Int) = update {
        copy(zones = zones.mapIndexed { i, zone -> if (i == index) zone.copy(brightness = percent.coerceIn(0, 100)) else zone })
    }
    override fun setZoneAnimation(mode: ZoneAnimation) { update { copy(zoneAnimation = mode, cycleIndex = 0) }; scheduleColors() }
    override fun setCycleColors(enabled: Boolean) { update { copy(cycleColors = enabled, cycleIndex = 0) }; scheduleColors() }
    override fun setCyclePalette(palette: CyclePalette) { update { copy(cyclePalette = palette, cycleIndex = 0) }; scheduleColors() }
    override fun setCycleInterval(seconds: Int) { update { copy(cycleInterval = seconds.coerceIn(1, 60)) }; scheduleColors() }
    override fun setSmoothTransitions(enabled: Boolean) = update { copy(smoothTransitions = enabled) }
    override fun setTransitionMillis(millis: Int) = update { copy(transitionMillis = millis.coerceIn(200, 2000)) }
    override fun setBrightness(percent: Int) {
        cancelBrightnessAnimation()
        val brightness = percent.coerceIn(1, 100)
        update { copy(brightness = brightness, currentBrightness = brightness) }
        restartFade()
    }
    override fun setUltraDim(enabled: Boolean) = update { copy(ultraDim = enabled) }
    override fun setDual(enabled: Boolean) = update { copy(dual = enabled && flashlight.state.value.hasFlash) }
    override fun setCriticalStop(enabled: Boolean) {
        update { copy(stopAtCriticalBattery = enabled) }
        if (batteryProtectionTriggered(enabled, state.value.batteryPercent, state.value.charging)) { stop(); flashlight.turnOff() }
    }
    override fun setEmergencyLed(enabled: Boolean) = update { copy(emergencyLed = enabled) }
    override fun setDim(enabled: Boolean) { update { copy(dim = enabled) }; restartFade() }
    override fun setMode(mode: DimmingMode) { update { copy(dimmingMode = mode) }; restartFade() }
    override fun setRate(rate: Int) { update { copy(dimRate = rate.coerceIn(1, 100)) }; restartFade() }
    override fun setTimer(seconds: Int) { update { copy(timerSeconds = seconds.coerceIn(0, 86400)) }; if (state.value.isOn) startTimer() }

    override fun start() {
        val current = state.value
        if (batteryProtectionTriggered(current.stopAtCriticalBattery, current.batteryPercent, current.charging)) {
            message("Battery protection is enabled. Charge your phone or change the battery setting."); return
        }
        if (current.isOn) {
            mutableState.update { it.copy(fullscreen = true, isStopping = false, currentBrightness = if (it.smoothTransitions) 0 else desiredBrightness()) }
            animateBrightness(desiredBrightness())
        } else {
            mutableState.update { it.copy(isOn = true, fullscreen = true, isStopping = false, touchLocked = false,
                currentBrightness = if (it.smoothTransitions) 0 else it.brightness) }
            startTimer()
        }
        if (current.dual && flashlight.state.value.hasFlash && !flashlight.state.value.isOn && !flashlight.state.value.isStarting) {
            flashlight.setTimer(if (deadline > 0) remainingSeconds(deadline, SystemClock.elapsedRealtime()) else 0)
            flashlight.turnOn()
        }
        scheduleColors()
        (context.applicationContext as LumaApplication).sounds.lightStarted("screen")
    }

    override fun stop() {
        (context.applicationContext as LumaApplication).sounds.lightStopped("screen")
        handler.removeCallbacksAndMessages(null)
        brightnessAnimation = null; deadline = 0
        mutableState.update { it.copy(isOn = false, isStopping = false, fullscreen = false, touchLocked = false, remainingSeconds = 0, cycleIndex = 0) }
    }
    override fun stopSmoothly() = fadeToStop(state.value.transitionMillis)
    private fun fadeToStop(duration: Int) {
        val current = state.value
        if (current.isStopping) return
        if (!current.isOn || !current.fullscreen || !current.smoothTransitions) { stop(); return }
        mutableState.update { it.copy(isStopping = true, touchLocked = false) }
        handler.removeCallbacks(tick); handler.removeCallbacks(colorTick)
        animateBrightness(0, duration) { stop() }
    }
    override fun showControls() {
        handler.removeCallbacks(colorTick)
        if (state.value.isOn && state.value.fullscreen && state.value.smoothTransitions) {
            animateBrightness(0) { mutableState.update { it.copy(fullscreen = false, touchLocked = false, currentBrightness = it.brightness) } }
        } else mutableState.update { it.copy(fullscreen = false, touchLocked = false) }
    }
    override fun setLocked(locked: Boolean) { mutableState.update { it.copy(touchLocked = locked) } }
    override fun dismissMessage() { mutableState.update { it.copy(message = null) } }

    override fun readingMode() = switchLight {
        copy(page = LampPage.Screen, color = 0xFFFFD49A.toInt(), gradient = false, zonesEnabled = false, cycleColors = false,
            brightness = 35, ultraDim = false, timerSeconds = 1800, dim = true, dimmingMode = DimmingMode.Auto, dual = false)
    }
    override fun emergency() = switchLight {
        if (emergencyLed && flashlight.state.value.hasFlash) flashlight.setStrength(flashlight.state.value.maxStrength)
        copy(page = LampPage.Screen, color = 0xFFFFFFFF.toInt(), gradient = false, zonesEnabled = false, cycleColors = false,
            brightness = 100, ultraDim = false, dim = false, dual = emergencyLed)
    }
    private fun switchLight(transform: ScreenLampState.() -> ScreenLampState) {
        val apply = { stop(); update(transform); start() }
        if (state.value.isOn && state.value.fullscreen && state.value.smoothTransitions) {
            handler.removeCallbacks(tick); handler.removeCallbacks(colorTick)
            animateBrightness(0, finished = apply)
        } else apply()
    }

    override fun savePreset(name: String) {
        val label = name.trim().take(24).ifBlank { "My light ${state.value.presets.size + 1}" }
        val preset = state.value.toLightingPreset(label)
        update { copy(presets = (presets.filterNot { it.name == label } + preset).takeLast(12)) }
        message("Saved $label")
    }
    override fun applyPreset(preset: LightingPreset) {
        update { withLightingPreset(preset).copy(page = LampPage.Screen) }
        if (state.value.isOn) startTimer()
        scheduleColors()
    }
    override fun deletePreset(name: String) = update { copy(presets = presets.filterNot { it.name == name }) }

    fun resetIdleControls() {
        if (state.value.isOn) return
        stop()
        val old = state.value
        update { ScreenLampState(theme = old.theme, batteryPercent = old.batteryPercent, charging = old.charging,
            stopAtCriticalBattery = old.stopAtCriticalBattery, emergencyLed = old.emergencyLed, presets = old.presets,
            smoothTransitions = old.smoothTransitions, transitionMillis = old.transitionMillis) }
        mutableState.update { it.copy(sessionResetId = old.sessionResetId + 1) }
    }
    private fun desiredBrightness(): Int {
        val current = state.value
        if (!current.dim || deadline == 0L) return current.brightness
        val now = SystemClock.elapsedRealtime()
        return when (current.dimmingMode) {
            DimmingMode.Auto -> fadingStrength(current.brightness, deadline - now, current.timerSeconds * 1000L)
            else -> dimmingStrengthByRate(fadeStartBrightness, 100, current.dimRate, now - fadeStart, current.dimmingMode.intervalMillis)
        }
    }
    private fun restartFade() {
        fadeStart = SystemClock.elapsedRealtime(); fadeStartBrightness = state.value.currentBrightness.coerceAtLeast(1)
        if (state.value.isOn && deadline > 0) { handler.removeCallbacks(tick); handler.post(tick) }
    }
    private fun startTimer() {
        handler.removeCallbacks(tick)
        val current = state.value
        deadline = if (current.timerSeconds > 0) SystemClock.elapsedRealtime() + current.timerSeconds * 1000L else 0
        fadeStart = SystemClock.elapsedRealtime(); fadeStartBrightness = current.brightness
        mutableState.update { it.copy(remainingSeconds = it.timerSeconds, isStopping = false) }
        animateBrightness(current.brightness)
        if (deadline > 0) handler.post(tick)
    }
    private fun animateBrightness(target: Int, duration: Int = state.value.transitionMillis, finished: () -> Unit = {}) {
        cancelBrightnessAnimation()
        val from = state.value.currentBrightness
        if (!state.value.smoothTransitions || from == target || !state.value.fullscreen) {
            mutableState.update { it.copy(currentBrightness = target) }; finished(); return
        }
        val started = SystemClock.elapsedRealtime()
        val animation = object : Runnable {
            override fun run() {
                val progress = ((SystemClock.elapsedRealtime() - started).toFloat() / duration.coerceAtLeast(1)).coerceIn(0f, 1f)
                mutableState.update { it.copy(currentBrightness = (from + (target - from) * progress).roundToInt()) }
                if (progress >= 1f) { brightnessAnimation = null; finished() } else handler.postDelayed(this, 32)
            }
        }
        brightnessAnimation = animation; handler.post(animation)
    }
    private fun cancelBrightnessAnimation() { brightnessAnimation?.let(handler::removeCallbacks); brightnessAnimation = null }
    private fun scheduleColors() {
        handler.removeCallbacks(colorTick)
        val current = state.value
        if (current.cycleColors && current.isOn && current.fullscreen && !current.isStopping)
            handler.postDelayed(colorTick, current.cycleInterval * 1000L)
    }
    private fun message(text: String) { mutableState.update { it.copy(message = text) } }
    private fun update(transform: ScreenLampState.() -> ScreenLampState) {
        mutableState.update { it.transform() }
        val current = state.value
        prefs.edit {
            putString("theme", current.theme.name); putString("lighting", current.toLightingPreset("").toJson().toString())
            putBoolean("dual", current.dual); putBoolean("critical_stop", current.stopAtCriticalBattery)
            putBoolean("emergency_led", current.emergencyLed); putBoolean("smooth_transitions", current.smoothTransitions)
            putInt("transition_ms", current.transitionMillis)
            putString("presets", JSONArray().apply { current.presets.forEach { put(it.toJson()) } }.toString())
        }
    }
    private fun loadPresets(): List<LightingPreset> = try {
        val saved = JSONArray(prefs.getString("presets", "[]"))
        (0 until saved.length()).mapNotNull { index -> try { saved.getJSONObject(index).toLightingPreset() } catch (_: Exception) { null } }
    } catch (_: Exception) { emptyList() }
}

package com.zakaria5729.flash_table_lamp

import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class NotificationControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        featureAction(context) { handle(context, intent) }
    }

    private fun handle(context: Context, intent: Intent) {
        val flashlight = (context.applicationContext as LumaApplication).flashlight
        val state = flashlight.state.value
        when (intent.action) {
            BRIGHTER -> flashlight.setStrength(state.strength + 1)
            DIMMER -> flashlight.setStrength(state.strength - 1)
            TIMER -> flashlight.setTimer(intent.getIntExtra("seconds", state.timerSeconds))
            TOGGLE_DIM -> if (state.timerSeconds > 0) flashlight.setDimGradually(!state.dimGradually)
            TOGGLE_RHYTHM -> flashlight.setRhythm(!state.rhythmEnabled)
            TOGGLE_MODE -> flashlight.setDimmingMode(DimmingMode.entries[(state.dimmingMode.ordinal + 1) % DimmingMode.entries.size])
            RATE_UP -> flashlight.setDimmingRate(state.dimPercentPerMinute + 1)
            RATE_DOWN -> flashlight.setDimmingRate(state.dimPercentPerMinute - 1)
            CUSTOM_TIMER -> {
                val input = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(DURATION)?.toString().orEmpty()
                if (input.trim().startsWith("rhythm", true)) {
                    val rhythm = parseRhythmInput(input)
                    if (rhythm == null) Toast.makeText(context, "Use rhythm auto, rhythm off, or rhythm 2/3. Delays must be 1–60 seconds.", Toast.LENGTH_LONG).show()
                    else {
                        if (rhythm.enabled) { flashlight.setRhythmMode(rhythm.mode); flashlight.setRhythmTimes(rhythm.onSeconds, rhythm.offSeconds) }
                        flashlight.setRhythm(rhythm.enabled)
                    }
                    flashlight.refreshNotification()
                    return
                }
                val seconds = parseTimerInput(input)
                if (seconds != null) flashlight.setTimer(seconds) else {
                    Toast.makeText(context, "Use 15m, 30s, 01:30 or off. Maximum 24 hours.", Toast.LENGTH_LONG).show()
                    flashlight.refreshNotification()
                }
            }
            CUSTOM_RATE -> {
                val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(RATE)?.toString()?.trim()?.lowercase().orEmpty()
                val parts = text.split('/')
                val rate = parts.firstOrNull()?.removeSuffix("%")?.trim()?.toIntOrNull()
                val mode = when (parts.getOrNull(1)?.trim()) {
                    "s", "sec", "second" -> DimmingMode.PerSecond
                    "m", "min", "minute" -> DimmingMode.PerMinute
                    "h", "hr", "hour" -> DimmingMode.PerHour
                    null -> state.dimmingMode.takeUnless { it == DimmingMode.Auto } ?: DimmingMode.PerMinute
                    else -> null
                }
                if (rate != null && rate in 1..100 && mode != null && parts.size <= 2) {
                    flashlight.setDimmingMode(mode)
                    flashlight.setDimmingRate(rate)
                    flashlight.setDimGradually(true)
                } else {
                    Toast.makeText(context, "Use 1–100%, optionally /s, /m or /h.", Toast.LENGTH_LONG).show()
                    flashlight.refreshNotification()
                }
            }
        }
    }

    companion object {
        const val BRIGHTER = "com.zakaria5729.flash_table_lamp.BRIGHTER"
        const val DIMMER = "com.zakaria5729.flash_table_lamp.DIMMER"
        const val TIMER = "com.zakaria5729.flash_table_lamp.SET_TIMER"
        const val TOGGLE_DIM = "com.zakaria5729.flash_table_lamp.TOGGLE_DIM"
        const val TOGGLE_RHYTHM = "com.zakaria5729.flash_table_lamp.TOGGLE_RHYTHM"
        const val CUSTOM_TIMER = "com.zakaria5729.flash_table_lamp.CUSTOM_TIMER"
        const val DURATION = "timer_duration"
        const val RATE = "dim_rate"
        const val TOGGLE_MODE = "com.zakaria5729.flash_table_lamp.TOGGLE_MODE"
        const val RATE_UP = "com.zakaria5729.flash_table_lamp.RATE_UP"
        const val RATE_DOWN = "com.zakaria5729.flash_table_lamp.RATE_DOWN"
        const val CUSTOM_RATE = "com.zakaria5729.flash_table_lamp.CUSTOM_RATE"
    }
}

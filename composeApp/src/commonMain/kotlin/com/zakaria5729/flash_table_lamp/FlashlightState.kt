package com.zakaria5729.flash_table_lamp

import kotlin.math.ceil
import kotlin.math.roundToInt

enum class DimmingMode(val label: String, val unit: String, val intervalMillis: Long) {
    Auto("Auto", "", 0),
    PerSecond("Per Second", "sec", 1000),
    PerMinute("Per Minute", "min", 60_000),
    PerHour("Per Hour", "hour", 3_600_000),
}
enum class RhythmMode { Auto, Custom }
data class RhythmPhase(val lit: Boolean, val nextChangeMillis: Long)
fun rhythmPhase(elapsedMillis: Long, onSeconds: Int, offSeconds: Int): RhythmPhase {
    val onDurationMillis = onSeconds.coerceIn(1, 60) * 1000L
    val offDurationMillis = offSeconds.coerceIn(1, 60) * 1000L
    val position = elapsedMillis.coerceAtLeast(0) % (onDurationMillis + offDurationMillis)
    return if (position < onDurationMillis) RhythmPhase(true, onDurationMillis - position) else RhythmPhase(false, onDurationMillis + offDurationMillis - position)
}
data class RhythmCommand(val enabled: Boolean, val mode: RhythmMode = RhythmMode.Auto, val onSeconds: Int = 1, val offSeconds: Int = 1)
fun parseRhythmInput(input: String): RhythmCommand? {
    val text = input.trim().lowercase().removePrefix("rhythm").trim()
    if (text == "off") return RhythmCommand(false)
    if (text == "auto") return RhythmCommand(true)
    if (!Regex("\\d{1,2}/\\d{1,2}").matches(text)) return null
    val parts = text.split('/').map(String::toInt)
    return if (parts.all { it in 1..60 }) RhythmCommand(true, RhythmMode.Custom, parts[0], parts[1]) else null
}

data class FlashlightState(
    val isOn: Boolean = false,
    val isStarting: Boolean = false,
    val isStopping: Boolean = false,
    val torchLit: Boolean = false,
    val rhythmEnabled: Boolean = false,
    val rhythmMode: RhythmMode = RhythmMode.Auto,
    val rhythmOnSeconds: Int = 2,
    val rhythmOffSeconds: Int = 2,
    val hasFlash: Boolean = true,
    val isAvailable: Boolean = true,
    val maxStrength: Int = 1,
    val strength: Int = 1,
    val currentStrength: Int = 1,
    val dimGradually: Boolean = false,
    val dimmingMode: DimmingMode = DimmingMode.Auto,
    val dimPercentPerMinute: Int = 10,
    val timerSeconds: Int = 900,
    val remainingSeconds: Int = 0,
    val message: String? = null,
) {
    val supportsDimming: Boolean get() = maxStrength > 1
    val brightnessPercent: Int get() = (strength * 100f / maxStrength).roundToInt()
    val currentBrightnessPercent: Int get() = (currentStrength * 100f / maxStrength).roundToInt()
    val isFading: Boolean get() = isOn && dimGradually && timerSeconds > 0 && supportsDimming
    val onSeconds: Int get() = if (rhythmMode == RhythmMode.Auto) 1 else rhythmOnSeconds
    val offSeconds: Int get() = if (rhythmMode == RhythmMode.Auto) 1 else rhythmOffSeconds
}

fun formatCountdown(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val minutes = safe / 60
    val remainder = (safe % 60).toString().padStart(2, '0')
    return if (minutes >= 60) {
        "${minutes / 60}:${(minutes % 60).toString().padStart(2, '0')}:$remainder"
    } else {
        "${minutes.toString().padStart(2, '0')}:$remainder"
    }
}

fun durationLabel(seconds: Int): String = when {
    seconds == 0 -> "No timer"
    seconds < 60 -> "$seconds sec"
    seconds % 60 == 0 -> "${seconds / 60} min"
    else -> "${seconds / 60}m ${seconds % 60}s"
}

fun remainingSeconds(deadlineMillis: Long, nowMillis: Long): Int =
    ceil((deadlineMillis - nowMillis).coerceAtLeast(0) / 1000.0).toInt()

fun fadingStrength(startStrength: Int, remainingMillis: Long, durationMillis: Long): Int {
    val start = startStrength.coerceAtLeast(1)
    if (durationMillis <= 0) return start
    val fraction = (remainingMillis.toDouble() / durationMillis).coerceIn(0.0, 1.0)
    return (1 + (start - 1) * fraction).roundToInt().coerceIn(1, start)
}

fun dimmingStrengthPerMinute(startStrength: Int, maxStrength: Int, percentPerMinute: Int, elapsedMillis: Long): Int {
    return dimmingStrengthByRate(startStrength, maxStrength, percentPerMinute, elapsedMillis, 60_000)
}

fun dimmingStrengthByRate(startStrength: Int, maxStrength: Int, percent: Int, elapsedMillis: Long, intervalMillis: Long): Int {
    val start = startStrength.coerceAtLeast(1)
    val reduction = maxStrength * (percent.coerceIn(1, 100) / 100.0) *
        (elapsedMillis.coerceAtLeast(0).toDouble() / intervalMillis.coerceAtLeast(1))
    return (start - reduction).roundToInt().coerceIn(1, start)
}

fun parseTimerInput(input: String): Int? {
    val value = input.trim().lowercase()
    if (value == "off" || value == "0") return 0
    val seconds = when {
        Regex("\\d{1,4}:\\d{1,2}").matches(value) -> {
            val parts = value.split(':')
            val remainder = parts[1].toInt()
            if (remainder > 59) return null
            parts[0].toLong() * 60 + remainder
        }
        Regex("\\d{1,5}m").matches(value) -> value.dropLast(1).toLong() * 60
        Regex("\\d{1,5}s").matches(value) -> value.dropLast(1).toLong()
        Regex("\\d{1,4}").matches(value) -> value.toLong() * 60
        else -> return null
    }
    return seconds.takeIf { it in 1..86400 }?.toInt()
}

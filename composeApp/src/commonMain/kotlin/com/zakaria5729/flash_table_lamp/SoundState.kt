package com.zakaria5729.flash_table_lamp

import kotlin.math.roundToInt

data class AmbientSound(
    val id: String,
    val title: String,
    val uri: String,
    val imported: Boolean = false,
    val remote: Boolean = false,
    val downloaded: Boolean = false,
    val downloading: Boolean = false,
)
data class SoundState(
    val sounds: List<AmbientSound> = emptyList(),
    val selectedId: String = "",
    val withFlashlight: Boolean = false,
    val withScreenLamp: Boolean = false,
    val linkedLights: Set<String> = emptySet(),
    val isPlaying: Boolean = false,
    val isPreparing: Boolean = false,
    val volume: Int = 30,
    val currentVolume: Int = 30,
    val fadeGradually: Boolean = false,
    val fadeMode: DimmingMode = DimmingMode.Auto,
    val fadePercent: Int = 10,
    val timerSeconds: Int = 0,
    val remainingSeconds: Int = 0,
    val message: String? = null,
    val unavailableSoundId: String? = null,
    val remoteLibraryLoading: Boolean = false,
    val remoteLibraryError: String? = null,
) {
    val selected: AmbientSound? get() = sounds.firstOrNull { it.id == selectedId }
}

interface SoundActions {
    fun refreshSoundLibrary()
    fun downloadSound(id: String)
    fun setPlayWithLight(screenLamp: Boolean, enabled: Boolean)
    fun selectSound(id: String)
    fun playWithLight(screenLamp: Boolean)
    fun play()
    fun pause()
    fun stop()
    fun setVolume(percent: Int)
    fun setTimer(seconds: Int)
    fun setFadeGradually(enabled: Boolean)
    fun setFadeMode(mode: DimmingMode)
    fun setFadeRate(percent: Int)
    fun removeSound(id: String)
    fun dismissMessage()
}

fun fadingSoundVolume(startVolume: Int, elapsedMillis: Long, durationMillis: Long, mode: DimmingMode, percent: Int): Int {
    val start = startVolume.coerceIn(0, 100)
    if (durationMillis <= 0) return start
    val elapsed = elapsedMillis.coerceAtLeast(0)
    if (elapsed >= durationMillis) return 0
    val volume = if (mode == DimmingMode.Auto) start * (1.0 - elapsed.toDouble() / durationMillis)
        else start - percent.coerceIn(1, 100) * elapsed.toDouble() / mode.intervalMillis
    val minimum = if (mode == DimmingMode.Auto && start > 0) 1 else 0
    return volume.roundToInt().coerceIn(minimum, start)
}

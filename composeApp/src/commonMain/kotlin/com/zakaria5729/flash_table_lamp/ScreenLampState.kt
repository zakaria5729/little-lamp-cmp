package com.zakaria5729.flash_table_lamp

enum class ThemeMode { System, Light, Dark }
enum class LampPage(val title: String) {
    Flashlight("Flashlight"), Screen("Screen lamp"), Colors("Colors & gradients"),
    Zones("Lighting zones"), Sounds("Relaxing sounds"),
    Presets("Saved presets"), Settings("Settings"),
}

enum class ZoneLayout(val label: String) { Stacked("Stacked"), SideBySide("Side by side"), Grid("Grid") }
enum class ZoneAnimation(val label: String) { Together("All zones"), Sequential("One zone at a time"), Random("Random colors") }

enum class CyclePalette(val label: String, val colors: List<Int>) {
    Rainbow("Rainbow", listOf(0xFFFF7777.toInt(), 0xFFFFC970.toInt(), 0xFF9CDBAA.toInt(), 0xFF8BCBFF.toInt(), 0xFFAC9BFF.toInt(), 0xFFE6A0DD.toInt())),
    Warm("Warm", listOf(0xFFFFD9A0.toInt(), 0xFFFFB17A.toInt(), 0xFFFFE8BA.toInt(), 0xFFFF9B88.toInt())),
}
data class LightingZone(val color: Int, val brightness: Int = 100)
fun defaultLightingZones() = listOf(LightingZone(0xFFFFD9A0.toInt()), LightingZone(0xFF9FC8FF.toInt()), LightingZone(0xFFE6A0DD.toInt()), LightingZone(0xFF9CDBAA.toInt()), LightingZone(0xFFFFC970.toInt()), LightingZone(0xFF8BCBFF.toInt()), LightingZone(0xFFFF9B88.toInt()), LightingZone(0xFFAC9BFF.toInt()))

data class LightingPreset(
    val name: String,
    val color: Int,
    val secondaryColor: Int,
    val brightness: Int,
    val gradient: Boolean,
    val ultraDim: Boolean,
    val timerSeconds: Int,
    val dim: Boolean,
    val mode: DimmingMode,
    val rate: Int,
    val zonesEnabled: Boolean = false,
    val zoneCount: Int = 2,
    val zoneLayout: ZoneLayout = ZoneLayout.Stacked,
    val zones: List<LightingZone> = defaultLightingZones(),
    val gradientCount: Int = 2,
    val gradientExtras: List<Int> = listOf(0xFFE6A0DD.toInt(), 0xFF9CDBAA.toInt()),
    val gradientAngle: Int = 45,
    val cycleColors: Boolean = false,
    val cyclePalette: CyclePalette = CyclePalette.Rainbow,
    val cycleInterval: Int = 10,
    val zoneAnimation: ZoneAnimation = ZoneAnimation.Together,
)

data class ScreenLampState(
    val isOn: Boolean = false,
    val isStopping: Boolean = false,
    val fullscreen: Boolean = false,
    val touchLocked: Boolean = false,
    val page: LampPage = LampPage.Flashlight,
    val theme: ThemeMode = ThemeMode.System,
    val color: Int = 0xFFFFD9A0.toInt(),
    val secondaryColor: Int = 0xFF9FC8FF.toInt(),
    val brightness: Int = 50,
    val currentBrightness: Int = 50,
    val gradient: Boolean = false,
    val gradientCount: Int = 2,
    val gradientExtras: List<Int> = listOf(0xFFE6A0DD.toInt(), 0xFF9CDBAA.toInt()),
    val gradientAngle: Int = 45,
    val zonesEnabled: Boolean = false,
    val zoneCount: Int = 2,
    val zoneLayout: ZoneLayout = ZoneLayout.Stacked,
    val zones: List<LightingZone> = defaultLightingZones(),
    val cycleColors: Boolean = false,
    val cyclePalette: CyclePalette = CyclePalette.Rainbow,
    val cycleInterval: Int = 10,
    val zoneAnimation: ZoneAnimation = ZoneAnimation.Together,
    val cycleIndex: Int = 0,
    val ultraDim: Boolean = false,
    val dual: Boolean = false,
    val timerSeconds: Int = 900,
    val remainingSeconds: Int = 0,
    val dim: Boolean = false,
    val dimmingMode: DimmingMode = DimmingMode.Auto,
    val dimRate: Int = 10,
    val batteryPercent: Int = -1,
    val charging: Boolean = false,
    val stopAtCriticalBattery: Boolean = false,
    val emergencyLed: Boolean = false,
    val smoothTransitions: Boolean = true,
    val transitionMillis: Int = 800,
    val presets: List<LightingPreset> = emptyList(),
    val message: String? = null,
    val sessionResetId: Int = 0,
)

fun batteryProtectionTriggered(enabled: Boolean, percent: Int, charging: Boolean): Boolean =
    enabled && percent in 0..5 && !charging

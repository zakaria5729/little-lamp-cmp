package com.zakaria5729.flash_table_lamp

import org.json.JSONArray
import org.json.JSONObject

fun ScreenLampState.toLightingPreset(name: String) = LightingPreset(
    name, color, secondaryColor, brightness, gradient, ultraDim, timerSeconds, dim, dimmingMode, dimRate,
    zonesEnabled, zoneCount, zoneLayout, zones, gradientCount, gradientExtras, gradientAngle,
    cycleColors, cyclePalette, cycleInterval, zoneAnimation,
)

fun ScreenLampState.withLightingPreset(p: LightingPreset) = copy(
    color = p.color, secondaryColor = p.secondaryColor, brightness = p.brightness, gradient = p.gradient,
    ultraDim = p.ultraDim, timerSeconds = p.timerSeconds, dim = p.dim, dimmingMode = p.mode, dimRate = p.rate,
    zonesEnabled = p.zonesEnabled, zoneCount = p.zoneCount, zoneLayout = p.zoneLayout, zones = p.zones,
    gradientCount = p.gradientCount, gradientExtras = p.gradientExtras, gradientAngle = p.gradientAngle,
    cycleColors = p.cycleColors, cyclePalette = p.cyclePalette, cycleInterval = p.cycleInterval, zoneAnimation = p.zoneAnimation,
)

fun LightingPreset.toJson() = JSONObject().apply {
    put("name", name); put("color", color); put("secondary", secondaryColor); put("brightness", brightness)
    put("gradient", gradient); put("ultra", ultraDim); put("timer", timerSeconds); put("dim", dim)
    put("mode", mode.name); put("rate", rate); put("zones_enabled", zonesEnabled); put("zone_count", zoneCount)
    put("zone_layout", zoneLayout.name)
    put("zones", JSONArray().apply { zones.forEach { put(JSONObject().put("color", it.color).put("brightness", it.brightness)) } })
    put("gradient_count", gradientCount); put("gradient_extras", JSONArray(gradientExtras)); put("gradient_angle", gradientAngle)
    put("zone_animation", zoneAnimation.name)
    put("cycle_colors", cycleColors); put("cycle_palette", cyclePalette.name); put("cycle_interval", cycleInterval)
}

fun JSONObject.toLightingPreset(): LightingPreset {
    val defaults = ScreenLampState()
    val savedZones = optJSONArray("zones")
    val zones = defaultLightingZones().mapIndexed { index, zone ->
        savedZones?.optJSONObject(index)?.let {
            LightingZone(it.optInt("color", zone.color), it.optInt("brightness", 100).coerceIn(0, 100))
        } ?: zone
    }
    val extras = optJSONArray("gradient_extras")
    return LightingPreset(
        optString("name", "My light"), optInt("color", defaults.color), optInt("secondary", defaults.secondaryColor),
        optInt("brightness", 50).coerceIn(1, 100), optBoolean("gradient"), optBoolean("ultra"),
        optInt("timer", 900).coerceIn(0, 86400), optBoolean("dim"),
        DimmingMode.entries.firstOrNull { it.name == optString("mode") } ?: DimmingMode.Auto,
        optInt("rate", 10).coerceIn(1, 100), optBoolean("zones_enabled"), optInt("zone_count", 2).coerceIn(2, 8),
        ZoneLayout.entries.firstOrNull { it.name == optString("zone_layout") } ?: ZoneLayout.Stacked, zones,
        optInt("gradient_count", 2).coerceIn(2, 4), defaults.gradientExtras.mapIndexed { index, color -> extras?.optInt(index, color) ?: color },
        optInt("gradient_angle", 45).coerceIn(0, 360), optBoolean("cycle_colors"),
        CyclePalette.entries.firstOrNull { it.name == optString("cycle_palette") } ?: CyclePalette.Rainbow,
        optInt("cycle_interval", 10).coerceIn(1, 60),
        ZoneAnimation.entries.firstOrNull { it.name == optString("zone_animation") } ?: ZoneAnimation.Together,
    )
}

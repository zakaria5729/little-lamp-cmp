package com.zakaria5729.flash_table_lamp

import kotlin.math.roundToInt
import kotlin.random.Random

data class LightPattern(val colors: List<Int>, val zones: Boolean, val layout: ZoneLayout, val angle: Int)

fun scaleLightingColor(color: Int, brightness: Int, ultraDim: Boolean): Int {
    val factor = brightness.coerceIn(0, 100) / 100f * if (ultraDim) .06f else 1f
    fun channel(shift: Int) = (((color ushr shift) and 255) * factor).roundToInt().coerceIn(0, 255)
    return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
}

fun screenLightPattern(state: ScreenLampState): LightPattern {
    val count = if (state.zonesEnabled) state.zoneCount else if (state.gradient) state.gradientCount else 1
    val configured = if (state.zonesEnabled) state.zones.map { it.color }
        else listOf(state.color, state.secondaryColor) + state.gradientExtras
    val step = state.cycleIndex.coerceAtLeast(0)
    val cycling = state.cycleColors && state.isOn
    val sequential = cycling && state.zonesEnabled && state.zoneAnimation == ZoneAnimation.Sequential
    val palette = state.cyclePalette.colors
    val colors = (0 until count).map { index ->
        val color = when {
            !cycling -> configured[index]
            state.zonesEnabled && state.zoneAnimation == ZoneAnimation.Random -> {
                val random = Random(step.toLong() * 1_000_003 + index * 97L + 5729)
                (0xFF shl 24) or (random.nextInt(64, 256) shl 16) or (random.nextInt(64, 256) shl 8) or random.nextInt(64, 256)
            }
            sequential -> {
                val lastTurn = if (step >= index) step - (step - index) % count else -1
                if (lastTurn <= 0) configured[index] else palette[(lastTurn / count + index) % palette.size]
            }
            else -> palette[(step % palette.size + index) % palette.size]
        }
        val brightness = if (state.zonesEnabled) state.zones[index].brightness else 100
        val intensity = if (sequential && index != step % count) brightness * 20 / 100 else brightness
        scaleLightingColor(color, intensity, state.ultraDim)
    }
    return LightPattern(colors, state.zonesEnabled, state.zoneLayout, state.gradientAngle)
}

fun colorTransitionMillis(state: ScreenLampState, animate: Boolean = true): Int = when {
    !animate -> 0
    state.cycleColors && state.isOn && state.fullscreen -> minOf(state.transitionMillis, state.cycleInterval.coerceAtLeast(1) * 800).coerceAtLeast(1)
    state.smoothTransitions -> state.transitionMillis
    else -> 0
}

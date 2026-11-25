package com.zakaria5729.flash_table_lamp

import kotlin.test.*

class LightingPatternTest {
    @Test fun independentZonesKeepTheirOwnColorsAndIntensity() {
        val state = ScreenLampState(zonesEnabled = true, zoneCount = 3, zoneLayout = ZoneLayout.SideBySide,
            zones = listOf(LightingZone(0xFFFF0000.toInt()), LightingZone(0xFF00FF00.toInt(), 50), LightingZone(0xFF0000FF.toInt(), 0)))
        val pattern = screenLightPattern(state)
        assertEquals(listOf(0xFFFF0000.toInt(), 0xFF008000.toInt(), 0xFF000000.toInt()), pattern.colors)
        assertTrue(pattern.zones)
        assertEquals(ZoneLayout.SideBySide, pattern.layout)
    }
    @Test fun gradientKeepsEveryStopAndItsDirection() {
        val state = ScreenLampState(gradient = true, gradientCount = 4, gradientAngle = 90,
            color = 0xFFFF0000.toInt(), secondaryColor = 0xFF00FF00.toInt(), gradientExtras = listOf(0xFF0000FF.toInt(), 0xFFFFFF00.toInt()))
        assertEquals(listOf(state.color, state.secondaryColor) + state.gradientExtras, screenLightPattern(state).colors)
        assertEquals(90, screenLightPattern(state).angle)
    }
    @Test fun colorCyclesWrapAndDoNotReplaceSavedManualColors() {
        val state = ScreenLampState(isOn = true, cycleColors = true, cycleIndex = 5, zonesEnabled = true, zoneCount = 3)
        assertEquals(listOf(CyclePalette.Rainbow.colors[5], CyclePalette.Rainbow.colors[0], CyclePalette.Rainbow.colors[1]), screenLightPattern(state).colors)
        assertEquals(state.zones.take(3).map { it.color }, screenLightPattern(state.copy(isOn = false)).colors)
    }
    @Test fun ultraDimScalesEveryColorAndClampsBrightness() {
        assertEquals(0xFF0F0F0F.toInt(), scaleLightingColor(0xFFFFFFFF.toInt(), 100, true))
        assertEquals(0xFF000000.toInt(), scaleLightingColor(0xFFFFFFFF.toInt(), -1, false))
        assertEquals(0xFFFFFFFF.toInt(), scaleLightingColor(0xFFFFFFFF.toInt(), 200, false))
    }
    @Test fun rhythmUsesExactBoundariesAndMinimumOneSecondPhases() {
        assertEquals(RhythmPhase(true, 1000), rhythmPhase(0, 0, 1))
        assertEquals(RhythmPhase(true, 1), rhythmPhase(999, 1, 1))
        assertEquals(RhythmPhase(false, 1000), rhythmPhase(1000, 1, 1))
        assertEquals(RhythmPhase(true, 1000), rhythmPhase(2000, 1, 1))
        assertEquals(RhythmPhase(true, 1), rhythmPhase(1999, 2, 2))
        assertEquals(RhythmPhase(false, 2000), rhythmPhase(2000, 2, 2))
        assertEquals(RhythmPhase(true, 2000), rhythmPhase(4000, 2, 2))
        assertEquals(RhythmPhase(false, 3000), rhythmPhase(2000, 2, 3))
        assertEquals(RhythmPhase(true, 2000), rhythmPhase(5000, 2, 3))
    }
    @Test fun notificationRhythmCommandsEnforceMinimumAndMaximum() {
        assertEquals(RhythmCommand(true, onSeconds = 1, offSeconds = 1), parseRhythmInput("rhythm auto"))
        assertEquals(1, FlashlightState().onSeconds)
        assertEquals(1, FlashlightState().offSeconds)
        assertEquals(RhythmCommand(false), parseRhythmInput("rhythm off"))
        assertEquals(RhythmCommand(true, RhythmMode.Custom, 2, 3), parseRhythmInput("rhythm 2/3"))
        assertEquals(RhythmCommand(true, RhythmMode.Custom, 1, 3), parseRhythmInput("rhythm 1/3"))
        assertEquals(RhythmCommand(true, RhythmMode.Custom, 1, 1), parseRhythmInput("rhythm 1/1"))
        assertNull(parseRhythmInput("rhythm 0/3"))
        assertNull(parseRhythmInput("rhythm 2/61"))
        assertNull(parseRhythmInput("rhythm 2/3/4"))
    }
    @Test fun fourZonesRetainIndependentColorsAndGridLayout() {
        val zones = listOf(0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFFFFFF00).map { LightingZone(it.toInt()) }
        val pattern = screenLightPattern(ScreenLampState(zonesEnabled = true, zoneCount = 4, zoneLayout = ZoneLayout.Grid, zones = zones))
        assertEquals(zones.map { it.color }, pattern.colors)
        assertEquals(ZoneLayout.Grid, pattern.layout)
        assertEquals(8, defaultLightingZones().size)
    }
    @Test fun automaticColorChangesAlwaysFadeWithinTheSelectedInterval() {
        val state = ScreenLampState(isOn = true, fullscreen = true, cycleColors = true,
            cycleInterval = 1, smoothTransitions = false, transitionMillis = 2000)
        assertEquals(800, colorTransitionMillis(state))
        assertEquals(0, colorTransitionMillis(state, animate = false))
        assertEquals(0, colorTransitionMillis(state.copy(cycleColors = false)))
    }
    @Test fun sequentialZonesTakeTurnsAndKeepOthersDimmed() {
        val state = ScreenLampState(isOn = true, zonesEnabled = true, zoneCount = 8,
            zones = List(8) { LightingZone(0xFFFFFFFF.toInt()) }, cycleColors = true, zoneAnimation = ZoneAnimation.Sequential)
        for (step in 0..16) {
            val colors = screenLightPattern(state.copy(cycleIndex = step)).colors
            colors.forEachIndexed { index, color ->
                val channels = listOf((color ushr 16) and 255, (color ushr 8) and 255, color and 255)
                if (index == step % 8) assertTrue(channels.max() > 100)
                else assertTrue(channels.max() <= 51)
            }
        }
        assertTrue(screenLightPattern(state).colors[0] != screenLightPattern(state.copy(cycleIndex = 8)).colors[0])
        assertEquals(state.zones.map { it.color }, screenLightPattern(state.copy(cycleColors = false)).colors)
    }
    @Test fun sixAndEightZonesHaveIndependentStableRandomColors() {
        for (count in listOf(6, 8)) {
            val state = ScreenLampState(isOn = true, zonesEnabled = true, zoneCount = count, cycleColors = true,
                zoneAnimation = ZoneAnimation.Random)
            val first = screenLightPattern(state).colors
            val next = screenLightPattern(state.copy(cycleIndex = 1)).colors
            assertEquals(count, first.size)
            assertEquals(count, first.toSet().size)
            assertEquals(first, screenLightPattern(state).colors)
            assertTrue(first.zip(next).all { (a, b) -> a != b })
        }
    }
}

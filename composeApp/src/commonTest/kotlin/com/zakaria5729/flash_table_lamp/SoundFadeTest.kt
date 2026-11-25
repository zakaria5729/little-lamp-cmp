package com.zakaria5729.flash_table_lamp

import kotlin.test.Test
import kotlin.test.assertEquals

class SoundFadeTest {
    @Test fun automaticFadeReachesSilenceAtDeadline() {
        assertEquals(80, fadingSoundVolume(80, 0, 10_000, DimmingMode.Auto, 10))
        assertEquals(40, fadingSoundVolume(80, 5000, 10_000, DimmingMode.Auto, 10))
        assertEquals(0, fadingSoundVolume(80, 10_000, 10_000, DimmingMode.Auto, 10))
        assertEquals(0, fadingSoundVolume(80, 20_000, 10_000, DimmingMode.Auto, 10))
        assertEquals(1, fadingSoundVolume(5, 3_500_000, 3_600_000, DimmingMode.Auto, 10))
    }
    @Test fun manualRateUsesPercentagePointsAndSelectedInterval() {
        for (mode in listOf(DimmingMode.PerSecond, DimmingMode.PerMinute, DimmingMode.PerHour)) {
            assertEquals(60, fadingSoundVolume(80, mode.intervalMillis * 2, mode.intervalMillis * 10, mode, 10))
        }
        assertEquals(0, fadingSoundVolume(15, 2000, 30_000, DimmingMode.PerSecond, 10))
    }
    @Test fun noTimerAndInvalidInputsRemainSafe() {
        assertEquals(30, fadingSoundVolume(30, 1000, 0, DimmingMode.Auto, 10))
        assertEquals(100, fadingSoundVolume(150, -1000, 10_000, DimmingMode.Auto, 10))
        assertEquals(0, fadingSoundVolume(-10, 1000, 10_000, DimmingMode.PerSecond, 0))
    }
}

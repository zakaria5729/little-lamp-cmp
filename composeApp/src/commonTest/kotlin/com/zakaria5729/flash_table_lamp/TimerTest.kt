package com.zakaria5729.flash_table_lamp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class TimerTest {
    @Test
    fun countdownRoundsUpAndExpiresAtDeadline() {
        assertEquals(2, remainingSeconds(2000, 1))
        assertEquals(1, remainingSeconds(2000, 1000))
        assertEquals(1, remainingSeconds(2000, 1999))
        assertEquals(0, remainingSeconds(2000, 2000))
        assertEquals(0, remainingSeconds(2000, 3000))
    }

    @Test
    fun countdownFormatsHoursAndClampsNegativeValues() {
        assertEquals("00:00", formatCountdown(-1))
        assertEquals("00:09", formatCountdown(9))
        assertEquals("15:00", formatCountdown(900))
        assertEquals("1:01:01", formatCountdown(3661))
        assertEquals("24:00:00", formatCountdown(86400))
    }

    @Test
    fun fadeMovesFromSelectedBrightnessToMinimum() {
        assertEquals(45, fadingStrength(45, 10_000, 10_000))
        assertEquals(23, fadingStrength(45, 5_000, 10_000))
        assertEquals(1, fadingStrength(45, 0, 10_000))
        assertEquals(1, fadingStrength(45, -100, 10_000))
        assertEquals(45, fadingStrength(45, 20_000, 10_000))
    }

    @Test
    fun fadeHandlesMinimumBrightnessAndDisabledTimer() {
        assertEquals(1, fadingStrength(1, 5_000, 10_000))
        assertEquals(21, fadingStrength(21, 0, 0))
        assertEquals(1, fadingStrength(0, 5_000, 10_000))
    }

    @Test
    fun notificationTimerAcceptsMinutesSecondsAndClockFormat() {
        assertEquals(0, parseTimerInput("off"))
        assertEquals(0, parseTimerInput("0"))
        assertEquals(900, parseTimerInput("15m"))
        assertEquals(900, parseTimerInput("15"))
        assertEquals(30, parseTimerInput(" 30S "))
        assertEquals(90, parseTimerInput("01:30"))
        assertEquals(86400, parseTimerInput("1440m"))
    }

    @Test
    fun notificationTimerRejectsInvalidAndOutOfRangeDurations() {
        listOf("", "-1", "1.5", "abc", "10:99", "1441m", "86401s", "999999999999", "0s").forEach {
            assertNull(parseTimerInput(it), it)
        }
    }

    @Test
    fun manualRateDropsConfiguredBrightnessPercentageEachMinute() {
        assertEquals(100, dimmingStrengthPerMinute(100, 100, 10, 0))
        assertEquals(95, dimmingStrengthPerMinute(100, 100, 10, 30_000))
        assertEquals(90, dimmingStrengthPerMinute(100, 100, 10, 60_000))
        assertEquals(45, dimmingStrengthPerMinute(50, 100, 5, 60_000))
        assertEquals(1, dimmingStrengthPerMinute(45, 45, 100, 120_000))
        assertEquals(21, dimmingStrengthPerMinute(21, 45, 10, -100))
    }

    @Test
    fun rateUnitsUseTheirOwnTimeInterval() {
        assertEquals(90, dimmingStrengthByRate(100, 100, 10, 1000, DimmingMode.PerSecond.intervalMillis))
        assertEquals(90, dimmingStrengthByRate(100, 100, 10, 60_000, DimmingMode.PerMinute.intervalMillis))
        assertEquals(90, dimmingStrengthByRate(100, 100, 10, 3_600_000, DimmingMode.PerHour.intervalMillis))
        assertEquals(100, dimmingStrengthByRate(100, 100, 10, 1000, DimmingMode.PerHour.intervalMillis))
    }

    @Test
    fun batteryProtectionRequiresOptInKnownCriticalLevelAndNoCharger() {
        assertTrue(batteryProtectionTriggered(true, 5, false))
        assertTrue(batteryProtectionTriggered(true, 0, false))
        assertFalse(batteryProtectionTriggered(false, 5, false))
        assertFalse(batteryProtectionTriggered(true, 5, true))
        assertFalse(batteryProtectionTriggered(true, 6, false))
        assertFalse(batteryProtectionTriggered(true, -1, false))
    }
}

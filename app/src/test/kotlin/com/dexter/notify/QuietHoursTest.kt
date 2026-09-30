package com.dexter.notify

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuietHoursTest {
    @Test
    fun aRangeThatCrossesMidnightCoversBothSides() {
        assertTrue(isQuietHour(22, 22, 7))
        assertTrue(isQuietHour(23, 22, 7))
        assertTrue(isQuietHour(0, 22, 7))
        assertTrue(isQuietHour(6, 22, 7))
        assertFalse(isQuietHour(7, 22, 7))
        assertFalse(isQuietHour(12, 22, 7))
        assertFalse(isQuietHour(21, 22, 7))
    }

    @Test
    fun aSameDayRangeStopsAtItsEnd() {
        assertTrue(isQuietHour(13, 13, 15))
        assertTrue(isQuietHour(14, 13, 15))
        assertFalse(isQuietHour(15, 13, 15))
        assertFalse(isQuietHour(12, 13, 15))
    }

    @Test
    fun equalHoursMeanNoQuietRange() {
        assertFalse(isQuietHour(5, 8, 8))
    }
}

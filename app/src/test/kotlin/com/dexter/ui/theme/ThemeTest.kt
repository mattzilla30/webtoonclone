package com.dexter.ui.theme

import com.dexter.data.ThemeMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTest {
    @Test
    fun darkAndBlackAreAlwaysDark() {
        assertTrue(isDark(ThemeMode.Dark, systemDark = false))
        assertTrue(isDark(ThemeMode.Black, systemDark = false))
    }

    @Test
    fun lightIsNeverDark() {
        assertFalse(isDark(ThemeMode.Light, systemDark = true))
    }

    @Test
    fun systemFollowsThePhone() {
        assertTrue(isDark(ThemeMode.System, systemDark = true))
        assertFalse(isDark(ThemeMode.System, systemDark = false))
    }
}

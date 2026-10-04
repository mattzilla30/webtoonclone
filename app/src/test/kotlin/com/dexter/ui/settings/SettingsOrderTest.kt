package com.dexter.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsOrderTest {
    @Test
    fun cardsSortAToZ() {
        val ids = listOf(SettingKey("zoom"), SettingKey("Auto"), SettingKey("middle"))
        assertEquals(listOf(1, 2, 0), settingsOrder(ids))
    }

    @Test
    fun unnamedChildStaysAfterTheCardBeforeIt() {
        val ids = listOf(SettingKey("b"), null, SettingKey("a"))
        assertEquals(listOf(2, 0, 1), settingsOrder(ids))
    }

    @Test
    fun emptyNoteIsLeftOut() {
        assertEquals(listOf(0), settingsOrder(listOf(SettingKey("a"), EmptyNote)))
        assertEquals(emptyList<Int>(), settingsOrder(listOf(EmptyNote)))
    }
}

class SettingsSearchTest {
    @Test
    fun blankQueryMatchesEverything() {
        assertTrue(matchesQuery("  ", "Theme"))
    }

    @Test
    fun everyWordMustStartAWord() {
        assertTrue(matchesQuery("app lock", "App lock", "Ask for your fingerprint"))
        assertTrue(matchesQuery("lock app", "App lock"))
        assertFalse(matchesQuery("ock", "App lock"))
        assertFalse(matchesQuery("app theme", "App lock"))
    }

    @Test
    fun punctuationAndSpellingDoNotMatter() {
        assertTrue(matchesQuery("wifi", "Download on Wi-Fi only"))
        assertTrue(matchesQuery("colour blind", "Color-blind-safe theme"))
        assertTrue(matchesQuery("color", "Colour vision"))
    }

    @Test
    fun keywordsCount() {
        assertTrue(matchesQuery("dark mode", "Theme", null, "dark mode"))
    }
}

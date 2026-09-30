package com.dexter.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun defaultsKeepTheAppAsItWasBeforeSettings() {
        val s = Settings()
        assertEquals(ThemeMode.Dark, s.theme)
        assertFalse(s.dataSaver)
        assertFalse(s.originalTitles)
        assertTrue(s.reportImageLoads)
    }

    @Test
    fun aSavedSettingsFileWithMissingFieldsStillLoads() {
        val loaded = json.decodeFromString<Settings>("""{"theme":"Light","dataSaver":true}""")
        assertEquals(ThemeMode.Light, loaded.theme)
        assertTrue(loaded.dataSaver)
        assertEquals(22, loaded.quietStartHour)
    }
}

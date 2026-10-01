package com.dexter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsMigrationTest {
    @Test
    fun freshInstallGetsSetupAndSaferRatings() {
        val settings = migrateSettings(null, Settings())
        assertFalse(settings.setupDone)
        assertEquals(NewInstallRatings, settings.contentRatings)
    }

    @Test
    fun oldInstallSkipsSetupAndKeepsAllRatings() {
        val settings = migrateSettings("""{"theme":"Light"}""", Settings(theme = ThemeMode.Light))
        assertTrue(settings.setupDone)
        assertEquals(ContentRatings.toSet(), settings.contentRatings)
    }

    @Test
    fun oldInstallKeepsItsOwnRatings() {
        val settings = migrateSettings("""{"contentRatings":["safe"]}""", Settings(contentRatings = setOf("safe")))
        assertEquals(setOf("safe"), settings.contentRatings)
    }

    @Test
    fun finishedSetupIsLeftAlone() {
        val decoded = Settings(setupDone = true)
        assertEquals(decoded, migrateSettings("""{"setupDone":true}""", decoded))
    }

    @Test
    fun savedTextRoundTrips() {
        val saved = StoredJson.encodeToString(Settings.serializer(), Settings(setupDone = true))
        val back = migrateSettings(saved, StoredJson.decodeFromString(Settings.serializer(), saved))
        assertEquals(NewInstallRatings, back.contentRatings)
    }
}

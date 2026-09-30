package com.webtoonclone.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryDataTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun libraryStoredBeforeTheSwitchExistedKeepsNotificationsOn() {
        val old = json.decodeFromString<LibraryData>("""{"recent":[],"subscribed":[],"searches":["a"]}""")
        assertTrue(old.notificationsEnabled)
    }

    @Test
    fun switchRoundTripsThroughStorage() {
        val off = LibraryData(notificationsEnabled = false)
        val back = json.decodeFromString<LibraryData>(json.encodeToString(LibraryData.serializer(), off))
        assertFalse(back.notificationsEnabled)
    }

    @Test
    fun sortChoiceDefaultsToRecentAndRoundTrips() {
        assertFalse(json.decodeFromString<LibraryData>("{}").sortAlphabetical)
        val az = LibraryData(sortAlphabetical = true)
        val back = json.decodeFromString<LibraryData>(json.encodeToString(LibraryData.serializer(), az))
        assertTrue(back.sortAlphabetical)
    }
}

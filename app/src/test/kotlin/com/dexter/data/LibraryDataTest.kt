package com.dexter.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
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

    @Test
    fun searchOrderDefaultsToPopularAndRoundTrips() {
        assertEquals("Popular", json.decodeFromString<LibraryData>("{}").searchOrder)
        val newest = LibraryData(searchOrder = Order.Newest.name)
        val back = json.decodeFromString<LibraryData>(json.encodeToString(LibraryData.serializer(), newest))
        assertEquals(Order.Newest, Order.valueOf(back.searchOrder))
    }

    @Test
    fun everyOrderNameSurvivesValueOf() {
        // The saved name is parsed back on launch, so renaming an Order would silently reset it.
        Order.entries.forEach { assertEquals(it, Order.valueOf(it.name)) }
    }

    @Test
    fun knownSeriesFindsRecentAndSubscribedEntries() {
        val library = LibraryData(
            recent = listOf(SavedSeries("a", "Alpha", "cover-a")),
            subscribed = listOf(SavedSeries("b", "Beta", "cover-b")),
        )
        assertEquals("Alpha", library.knownSeries("a")?.title)
        assertEquals("cover-b", library.knownSeries("b")?.coverUrl)
        assertEquals(null, library.knownSeries("c"))
    }

    @Test
    fun homeContentSurvivesTheOfflineCache() {
        val series = SeriesSummary("id", "Title", "cover", genre = "Drama", author = "Author", description = "About", follows = 7)
        val home = HomeContent(hero = series, newSeries = listOf(series), picks = listOf(series, series))
        val back = json.decodeFromString<HomeContent>(json.encodeToString(HomeContent.serializer(), home))
        assertEquals(home, back)
    }
}

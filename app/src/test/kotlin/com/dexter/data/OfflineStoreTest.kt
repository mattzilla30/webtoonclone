package com.dexter.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class OfflineStoreTest {
    private fun cached(key: String, savedAt: Long = 1) = CachedSearch(key, listOf(SeriesSummary("id", "Title", null)), savedAt)

    @Test
    fun theKeyIgnoresCaseAndSpacingButNotTheOrder() {
        assertEquals(searchKey(" Solo ", null, Order.Popular), searchKey("solo", null, Order.Popular))
        assertEquals(false, searchKey("solo", null, Order.Popular) == searchKey("solo", null, Order.Newest))
        assertEquals(false, searchKey("drama", null, Order.Popular) == searchKey(null, "drama", Order.Popular))
    }

    @Test
    fun theNewestSearchGoesFirstAndOldOnesDropOff() {
        val old = (1..10).map { cached("k$it") }
        val merged = mergeSearches(old, cached("new"))
        assertEquals(10, merged.size)
        assertEquals("new", merged.first().key)
        assertEquals(false, "k10" in merged.map { it.key })
    }

    @Test
    fun repeatingASearchReplacesItsCopy() {
        val merged = mergeSearches(listOf(cached("a", 1), cached("b", 1)), cached("b", 2))
        assertEquals(listOf("b", "a"), merged.map { it.key })
        assertEquals(2L, merged.first().savedAt)
    }

    @Test
    fun savedUpdatesReadBackIdentically() {
        val json = Json { ignoreUnknownKeys = true }
        val updates = CachedUpdates(listOf(UpdateEntry(SeriesSummary("s", "T", "c", genre = "Drama"), "12", "2026-01-01T00:00:00+00:00")), 5)
        assertEquals(updates, json.decodeFromString(CachedUpdates.serializer(), json.encodeToString(CachedUpdates.serializer(), updates)))
    }

    @Test
    fun aCachedUpdatesCopyKeepsItsLanguageAndSurvivesAnUnknownValue() {
        val updates = CachedUpdates(emptyList(), 5, language = "fr")
        val text = StoredJson.encodeToString(CachedUpdates.serializer(), updates)
        assertEquals("fr", StoredJson.decodeFromString(CachedUpdates.serializer(), text).language)
        // A status name from a newer version reads as no status instead of failing the whole copy.
        val raw = """{"entries":[{"series":{"id":"s","title":"T","coverUrl":null},"chapterNumber":"1","publishedAt":"x"}],"savedAt":1,"language":null}"""
        assertEquals("en", StoredJson.decodeFromString(CachedUpdates.serializer(), raw).language)
    }
}

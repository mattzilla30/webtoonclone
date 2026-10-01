package com.dexter.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SeriesCacheTest {
    private fun cached(id: String, savedAt: Long = 1) = CachedSeries(
        detail = SeriesDetail(SeriesSummary(id, "Title $id", "cover"), status = "ongoing", tags = listOf("Drama"), rating = 8.5),
        chapters = listOf(Chapter("c1", "1", "First", "2026-01-01T00:00:00+00:00")),
        savedAt = savedAt,
    )

    @Test
    fun eachSeriesAndLanguageGetsItsOwnFile() {
        assertEquals("abc_en.json", cacheFileName("abc", "en"))
        assertEquals(false, cacheFileName("abc", "en") == cacheFileName("abc", "es-la"))
    }

    @Test
    fun theOldestFilesDropOffAtTheLimit() {
        val files = (1..MAX_CACHED_SERIES + 2).map { "s$it.json" to it.toLong() }
        assertEquals(listOf("s2.json", "s1.json"), cacheFilesToDrop(files))
    }

    @Test
    fun nothingDropsBelowTheLimit() {
        assertEquals(emptyList<String>(), cacheFilesToDrop(listOf("a.json" to 1L, "b.json" to 2L)))
    }

    @Test
    fun aSavedSeriesReadsBackIdentically() {
        val original = cached("a")
        val back = StoredJson.decodeFromString(CachedSeries.serializer(), StoredJson.encodeToString(CachedSeries.serializer(), original))
        assertEquals(original, back)
    }
}

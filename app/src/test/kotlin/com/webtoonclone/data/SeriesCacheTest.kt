package com.webtoonclone.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class SeriesCacheTest {
    private fun cached(id: String, savedAt: Long = 1) = CachedSeries(
        detail = SeriesDetail(SeriesSummary(id, "Title $id", "cover"), status = "ongoing", tags = listOf("Drama"), rating = 8.5),
        chapters = listOf(Chapter("c1", "1", "First", "2026-01-01T00:00:00+00:00")),
        savedAt = savedAt,
    )

    private fun ids(list: List<CachedSeries>) = list.map { it.detail.summary.id }

    @Test
    fun theNewestSeriesGoesFirst() {
        assertEquals(listOf("c", "a", "b"), ids(mergeCache(listOf(cached("a"), cached("b")), cached("c"))))
    }

    @Test
    fun reopeningASeriesReplacesItsOldCopy() {
        val merged = mergeCache(listOf(cached("a", 1), cached("b", 1)), cached("b", 2))
        assertEquals(listOf("b", "a"), ids(merged))
        assertEquals(2L, merged.first().savedAt)
    }

    @Test
    fun theOldestSeriesDropOffAtTheLimit() {
        val old = (1..MAX_CACHED_SERIES).map { cached("s$it") }
        val merged = mergeCache(old, cached("new"))
        assertEquals(MAX_CACHED_SERIES, merged.size)
        assertEquals("new", merged.first().detail.summary.id)
        assertEquals(false, "s$MAX_CACHED_SERIES" in ids(merged))
    }

    @Test
    fun aSavedSeriesReadsBackIdentically() {
        val json = Json { ignoreUnknownKeys = true }
        val original = cached("a")
        val back = json.decodeFromString<CachedSeries>(json.encodeToString(CachedSeries.serializer(), original))
        assertEquals(original, back)
    }
}

package com.dexter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FiltersTest {
    @Test
    fun noLimitsMeansEmpty() {
        assertTrue(SearchFilters().isEmpty)
        assertEquals(0, SearchFilters().activeCount)
    }

    @Test
    fun everyLimitCountsOnce() {
        val filters = SearchFilters(
            included = listOf("Romance", "Drama"),
            excluded = listOf("Horror"),
            status = listOf("ongoing"),
            demographics = listOf("seinen"),
            originalLanguages = listOf("ko"),
            year = 2020,
        )
        assertEquals(7, filters.activeCount)
        assertFalse(filters.isEmpty)
    }

    @Test
    fun aTagCyclesThroughIncludedExcludedAndOff() {
        val included = SearchFilters().cycleTag("Romance")
        assertEquals(listOf("Romance"), included.included)
        val excluded = included.cycleTag("Romance")
        assertEquals(emptyList<String>(), excluded.included)
        assertEquals(listOf("Romance"), excluded.excluded)
        assertTrue(excluded.cycleTag("Romance").isEmpty)
    }

    @Test
    fun toggleAddsAndRemovesAValue() {
        val filters = SearchFilters()
        assertEquals(listOf("ongoing"), filters.toggle(filters.status, "ongoing"))
        assertEquals(emptyList<String>(), filters.toggle(listOf("ongoing"), "ongoing"))
    }

    @Test
    fun similarSeriesUseUpToTwoGenresFirst() {
        assertEquals(listOf("Romance", "Drama"), similarTags(listOf("Romance", "School Life", "Drama", "Comedy")))
    }

    @Test
    fun aThemeFillsTheGapWhenThereIsOnlyOneGenre() {
        assertEquals(listOf("Action", "Magic"), similarTags(listOf("Action", "Magic", "Vampires")))
    }

    @Test
    fun aSeriesWithNoKnownTagsHasNothingSimilar() {
        assertEquals(emptyList<String>(), similarTags(listOf("Full Color", "Web Comic")))
    }

    @Test
    fun everyLimitBecomesARemovableChip() {
        val filters = SearchFilters(included = listOf("Action"), excluded = listOf("Gore"), status = listOf("ongoing"), year = 2020)
        val chips = activeFilters(filters)
        assertEquals(listOf("+ Action", "\u2212 Gore", "Ongoing", "2020"), chips.map { it.label })
        assertEquals(filters.copy(year = null), chips.last().without)
        assertEquals(3, chips.size - 1)
    }

    @Test
    fun noLimitsMeansNoChips() {
        assertTrue(activeFilters(SearchFilters()).isEmpty())
    }
}

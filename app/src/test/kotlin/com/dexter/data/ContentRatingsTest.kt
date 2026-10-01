package com.dexter.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ContentRatingsTest {
    @Test
    fun newInstallShowsSafeAndSuggestive() {
        assertEquals(listOf("safe", "suggestive"), ratingsFor(Settings().contentRatings))
    }

    @Test
    fun ratingsKeepMangaDexOrder() {
        assertEquals(listOf("safe", "erotica"), ratingsFor(setOf("erotica", "safe")))
    }

    @Test
    fun emptyChoiceFallsBackToSafe() {
        assertEquals(listOf("safe"), ratingsFor(emptySet()))
    }
}

package com.dexter.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FactsLineTest {
    private val summary = SeriesSummary(id = "a", title = "A", coverUrl = null)

    @Test
    fun joinsWhatIsKnown() {
        val detail = SeriesDetail(summary, status = "ongoing", tags = emptyList(), rating = null, year = 2019, demographic = "Seinen", originalLanguage = "ja")
        assertEquals("Ongoing \u00b7 2019 \u00b7 Seinen \u00b7 Japanese", factsLine(detail))
    }

    @Test
    fun skipsMissingParts() {
        val detail = SeriesDetail(summary, status = "completed", tags = emptyList(), rating = null)
        assertEquals("Completed", factsLine(detail))
    }
}

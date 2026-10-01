package com.dexter.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MuteTest {
    private fun series(id: String, status: ReadingStatus? = null) = SavedSeries(id, id, null, null, null, status = status)

    private val library = LibraryData(
        lists = listOf(series("a", ReadingStatus.Dropped), series("b", ReadingStatus.Reading)),
        collections = mapOf("Seasonal" to listOf(series("c"))),
    )

    @Test
    fun mutedStatusSilencesItsSeries() {
        val settings = Settings(mutedStatuses = setOf(ReadingStatus.Dropped))
        assertTrue(isMuted("a", library, settings))
        assertFalse(isMuted("b", library, settings))
    }

    @Test
    fun mutedCollectionSilencesItsSeries() {
        val settings = Settings(mutedCollections = setOf("Seasonal"))
        assertTrue(isMuted("c", library, settings))
        assertFalse(isMuted("a", library, settings))
    }
}

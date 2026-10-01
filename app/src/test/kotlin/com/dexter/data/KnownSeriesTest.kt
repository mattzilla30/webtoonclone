package com.dexter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KnownSeriesTest {
    private fun series(id: String) = SavedSeries(id = id, title = "Series $id")

    @Test
    fun findsASeriesInEveryList() {
        val library = LibraryData(
            recent = listOf(series("a")),
            subscribed = listOf(series("b")),
            lists = listOf(series("c")),
            collections = mapOf("Favorites" to listOf(series("d"))),
        )
        listOf("a", "b", "c", "d").forEach { id ->
            assertEquals("Series $id", library.knownSeries(id)?.title)
        }
    }

    @Test
    fun unknownSeriesIsNull() {
        assertNull(LibraryData(recent = listOf(series("a"))).knownSeries("z"))
    }

    @Test
    fun recentWinsOverLaterLists() {
        val library = LibraryData(
            recent = listOf(SavedSeries(id = "a", title = "Recent copy")),
            lists = listOf(SavedSeries(id = "a", title = "List copy")),
        )
        assertEquals("Recent copy", library.knownSeries("a")?.title)
    }
}

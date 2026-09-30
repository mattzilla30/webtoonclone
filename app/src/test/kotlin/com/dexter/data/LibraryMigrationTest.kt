package com.dexter.data

import com.dexter.data.db.toEntity
import com.dexter.data.db.toSaved
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryMigrationTest {
    private val series = SavedSeries(
        id = "s1",
        title = "Title",
        coverUrl = "cover",
        chapterId = "c1",
        chapterNumber = "12.5",
        at = 99L,
        knownChapterId = "c9",
        knownChapterNumber = "13",
        notify = false,
        status = ReadingStatus.PlanToRead,
    )

    @Test
    fun aSavedSeriesSurvivesTheTripThroughTheDatabaseRow() {
        assertEquals(series, series.toEntity("subscribed", 3).toSaved())
    }

    @Test
    fun theRowRemembersItsListAndPosition() {
        val row = series.toEntity("recent", 7)
        assertEquals("recent", row.listName)
        assertEquals(7, row.position)
    }

    @Test
    fun anUnknownStatusNameReadsAsNoStatus() {
        assertNull(series.toEntity("lists", 0).copy(status = "FromTheFuture").toSaved().status)
    }

    @Test
    fun aSeriesWithNoOptionalFieldsRoundTrips() {
        val plain = SavedSeries(id = "p", title = "Plain")
        assertEquals(plain, plain.toEntity("recent", 0).toSaved())
    }

    @Test
    fun migrationRunsOnceForAnEmptyDatabaseWithOldData() {
        val legacy = LibraryData(recent = listOf(series))
        assertTrue(shouldMigrate(legacy, alreadyMigrated = false, databaseIsEmpty = true))
    }

    @Test
    fun migrationNeverRunsTwice() {
        assertFalse(shouldMigrate(LibraryData(recent = listOf(series)), alreadyMigrated = true, databaseIsEmpty = true))
    }

    @Test
    fun migrationNeverOverwritesADatabaseThatHasRows() {
        assertFalse(shouldMigrate(LibraryData(recent = listOf(series)), alreadyMigrated = false, databaseIsEmpty = false))
    }

    @Test
    fun migrationSkipsAnEmptyOldStore() {
        assertFalse(shouldMigrate(LibraryData(), alreadyMigrated = false, databaseIsEmpty = true))
    }

    @Test
    fun searchesAloneAreWorthMigrating() {
        assertTrue(shouldMigrate(LibraryData(searches = listOf("one")), alreadyMigrated = false, databaseIsEmpty = true))
    }

    @Test
    fun knownSeriesAlsoSearchesTheReadingLists() {
        val listed = LibraryData(lists = listOf(series.copy(id = "only-listed")))
        assertEquals("only-listed", listed.knownSeries("only-listed")?.id)
    }
}

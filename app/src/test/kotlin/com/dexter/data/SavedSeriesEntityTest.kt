package com.dexter.data

import com.dexter.data.db.toEntity
import com.dexter.data.db.toSaved
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SavedSeriesEntityTest {
    private val full = SavedSeries(
        id = "s1",
        title = "Title",
        coverUrl = "https://example.org/c.jpg",
        chapterId = "c1",
        chapterNumber = "12.5",
        at = 99L,
        knownChapterId = "c2",
        knownChapterNumber = "13",
        notify = false,
        status = ReadingStatus.PlanToRead,
    )

    @Test
    fun roundTripKeepsEveryField() {
        assertEquals(full, full.toEntity("lists", 3).toSaved())
    }

    @Test
    fun entityRecordsListAndPosition() {
        val entity = full.toEntity("lists", 3)
        assertEquals("lists", entity.listName)
        assertEquals(3, entity.position)
    }

    @Test
    fun minimalSeriesRoundTrips() {
        val minimal = SavedSeries(id = "s2", title = "Bare")
        assertEquals(minimal, minimal.toEntity("recent", 0).toSaved())
    }

    @Test
    fun unknownStatusReadsAsNone() {
        val entity = full.toEntity("lists", 0).copy(status = "FromTheFuture")
        assertNull(entity.toSaved().status)
    }
}

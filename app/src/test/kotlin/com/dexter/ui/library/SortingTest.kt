package com.dexter.ui.library

import com.dexter.data.SavedSeries
import org.junit.Assert.assertEquals
import org.junit.Test

class SortingTest {
    private val items = listOf(
        SavedSeries("1", "zebra"),
        SavedSeries("2", "Apple"),
        SavedSeries("3", "mango"),
    )

    @Test
    fun defaultKeepsTheSavedOrder() {
        assertEquals(listOf("1", "2", "3"), sortSaved(items, LibrarySort.Recent).map { it.id })
    }

    @Test
    fun alphabeticalIgnoresCase() {
        assertEquals(listOf("2", "3", "1"), sortSaved(items, LibrarySort.Alphabetical).map { it.id })
    }

    @Test
    fun unreadFirstKeepsTheSavedOrderWithinGroups() {
        val sorted = sortSaved(items, LibrarySort.UnreadFirst) { it.id == "3" || it.id == "1" }
        assertEquals(listOf("1", "3", "2"), sorted.map { it.id })
    }

    @Test
    fun tappingCyclesThroughEveryMode() {
        assertEquals(LibrarySort.Alphabetical, LibrarySort.Recent.next())
        assertEquals(LibrarySort.UnreadFirst, LibrarySort.Alphabetical.next())
        assertEquals(LibrarySort.Recent, LibrarySort.UnreadFirst.next())
    }

    @Test
    fun savedFlagsMapToAMode() {
        assertEquals(LibrarySort.Recent, sortModeOf(false, false))
        assertEquals(LibrarySort.Alphabetical, sortModeOf(true, false))
        assertEquals(LibrarySort.UnreadFirst, sortModeOf(true, true))
    }
}

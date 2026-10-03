package com.dexter.ui.library

import com.dexter.data.ReadingStatus
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
        val sorted = sortSaved(items, LibrarySort.UnreadFirst, hasUnread = { it.id == "3" || it.id == "1" })
        assertEquals(listOf("1", "3", "2"), sorted.map { it.id })
    }

    @Test
    fun tappingCyclesThroughEveryMode() {
        assertEquals(LibrarySort.Alphabetical, LibrarySort.Recent.next())
        assertEquals(LibrarySort.UnreadFirst, LibrarySort.Alphabetical.next())
        assertEquals(LibrarySort.Updated, LibrarySort.UnreadFirst.next())
        assertEquals(LibrarySort.Status, LibrarySort.Updated.next())
        assertEquals(LibrarySort.LatestRead, LibrarySort.Status.next())
        assertEquals(LibrarySort.UnreadCount, LibrarySort.LatestRead.next())
        assertEquals(LibrarySort.DateAdded, LibrarySort.UnreadCount.next())
        assertEquals(LibrarySort.ChapterCount, LibrarySort.DateAdded.next())
        assertEquals(LibrarySort.Recent, LibrarySort.ChapterCount.next())
    }

    @Test
    fun recentlyUpdatedPutsTheNewestChangeFirst() {
        val stamped = listOf(SavedSeries("1", "a", at = 5), SavedSeries("2", "b", at = 9), SavedSeries("3", "c", at = 1))
        assertEquals(listOf("2", "1", "3"), sortSaved(stamped, LibrarySort.Updated).map { it.id })
    }

    @Test
    fun byStatusGroupsInListOrderAndKeepsTheSavedOrderWithin() {
        val listed = listOf(
            SavedSeries("1", "a", status = ReadingStatus.Completed),
            SavedSeries("2", "b", status = ReadingStatus.Reading),
            SavedSeries("3", "c"),
            SavedSeries("4", "d", status = ReadingStatus.Reading),
        )
        assertEquals(listOf("2", "4", "1", "3"), sortSaved(listed, LibrarySort.Status).map { it.id })
    }

    @Test
    fun aNamedSortWinsOverTheOldFlags() {
        assertEquals(LibrarySort.Status, sortModeOf("Status", alphabetical = true, unreadFirst = false))
        assertEquals(LibrarySort.Alphabetical, sortModeOf(null, alphabetical = true, unreadFirst = false))
        assertEquals(LibrarySort.Recent, sortModeOf("Unknown", alphabetical = false, unreadFirst = false))
    }

    @Test
    fun savedFlagsMapToAMode() {
        assertEquals(LibrarySort.Recent, sortModeOf(false, false))
        assertEquals(LibrarySort.Alphabetical, sortModeOf(true, false))
        assertEquals(LibrarySort.UnreadFirst, sortModeOf(true, true))
    }
}

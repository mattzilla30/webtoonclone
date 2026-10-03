package com.dexter.ui.library

import com.dexter.data.SavedSeries
import org.junit.Assert.assertEquals
import org.junit.Test

class FilteringTest {
    private val items = listOf(SavedSeries("1", "One Piece"), SavedSeries("2", "Berserk"), SavedSeries("3", "Piece of Cake"))

    @Test
    fun blankQueryKeepsEverything() {
        assertEquals(3, filterSaved(items, "  ", false, hasUnread = { true }).size)
    }

    @Test
    fun queryMatchesAnywhereIgnoringCase() {
        assertEquals(listOf("1", "3"), filterSaved(items, "PIECE", false, hasUnread = { true }).map { it.id })
    }

    @Test
    fun unreadOnlyUsesThePredicate() {
        assertEquals(listOf("2"), filterSaved(items, "", true, hasUnread = { it.id == "2" }).map { it.id })
    }
}

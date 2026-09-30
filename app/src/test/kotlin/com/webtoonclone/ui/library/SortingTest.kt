package com.webtoonclone.ui.library

import com.webtoonclone.data.SavedSeries
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
        assertEquals(listOf("1", "2", "3"), sortSaved(items, alphabetical = false).map { it.id })
    }

    @Test
    fun alphabeticalIgnoresCase() {
        assertEquals(listOf("2", "3", "1"), sortSaved(items, alphabetical = true).map { it.id })
    }
}

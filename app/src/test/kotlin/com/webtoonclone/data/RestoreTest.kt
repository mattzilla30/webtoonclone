package com.webtoonclone.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RestoreTest {
    private fun ids(list: List<SavedSeries>) = list.map { it.id }

    @Test
    fun restoringAfterDeleteAllBringsBackTheOldOrder() {
        val snapshot = listOf(SavedSeries("a", "A"), SavedSeries("b", "B"), SavedSeries("c", "C"))
        assertEquals(listOf("a", "b", "c"), ids(mergeRestore(emptyList(), snapshot)))
    }

    @Test
    fun seriesAddedMeanwhileStayOnTop() {
        val snapshot = listOf(SavedSeries("a", "A"), SavedSeries("b", "B"))
        val current = listOf(SavedSeries("new", "New"))
        assertEquals(listOf("new", "a", "b"), ids(mergeRestore(current, snapshot)))
    }

    @Test
    fun aSeriesStillPresentIsNotDuplicated() {
        val snapshot = listOf(SavedSeries("a", "A"), SavedSeries("b", "B"))
        val current = listOf(SavedSeries("b", "B"))
        assertEquals(listOf("a", "b"), ids(mergeRestore(current, snapshot)))
    }
}

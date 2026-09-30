package com.dexter.notify

import org.junit.Assert.assertEquals
import org.junit.Test

class OpenRoutesTest {
    @Test
    fun aNewChapterOpensTheReaderOnTopOfTheSeries() {
        assertEquals(listOf("series/s1", "series/s1/c9"), openRoutes("s1", "c9"))
    }

    @Test
    fun noChapterOpensTheSeriesOnly() {
        assertEquals(listOf("series/s1"), openRoutes("s1", null))
    }
}

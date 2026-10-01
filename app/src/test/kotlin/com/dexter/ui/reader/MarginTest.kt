package com.dexter.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class MarginTest {
    @Test
    fun marginsAreCountedFromBothEnds() {
        val margin = setOf(0, 1, 8, 9)
        assertEquals(2 to 2, marginRun(10, 5) { it in margin })
    }

    @Test
    fun theCropStopsAtTheCap() {
        assertEquals(3 to 3, marginRun(10, 3) { true })
    }

    @Test
    fun aPageWithNoMarginIsLeftWhole() {
        assertEquals(0 to 0, marginRun(10, 3) { false })
    }
}

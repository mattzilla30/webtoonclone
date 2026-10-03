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

class ContentTrimTest {
    /**
     * A 400 by 200 page: a framed panel from x 80 to 319 and y 20 to 179, a page number in the bottom
     * margin, and a three-line translator's note made of separate letters in the right margin.
     */
    private fun page(x: Int, y: Int): Boolean {
        val frame = ((x in 80..81 || x in 318..319) && y in 20..179) || ((y in 20..21 || y in 178..179) && x in 80..319)
        val art = x in 120..280 && y in 60..140
        val pageNumber = x in 10..14 && y in 185..192 && x != 12
        val noteLine = y in 90..96 || y in 100..106 || y in 110..116
        val note = x in 330..390 && noteLine && x % 4 != 0
        return frame || art || pageNumber || note
    }

    @org.junit.Test
    fun pageNumbersAndNotesInTheMarginAreTrimmed() {
        val (left, top, right, bottom) = contentTrim(400, 200) { x, y -> page(x, y) }.toList()
        // Kept: the panel and a sliver of margin (2 px across, 1 px down). Gone: the page number and the note.
        org.junit.Assert.assertEquals(78, left)
        org.junit.Assert.assertEquals(78, right)
        org.junit.Assert.assertEquals(19, top)
        org.junit.Assert.assertEquals(19, bottom)
    }

    @org.junit.Test
    fun artToTheEdgesStaysWhole() {
        org.junit.Assert.assertArrayEquals(intArrayOf(0, 0, 0, 0), contentTrim(400, 200) { _, _ -> true })
    }

    @org.junit.Test
    fun aBlankPageIsNotCutPastTheCap() {
        val (left, _, right, _) = contentTrim(400, 200) { _, _ -> false }.toList()
        org.junit.Assert.assertEquals(98, left)
        org.junit.Assert.assertEquals(98, right)
    }

    @org.junit.Test
    fun samplingStepsMapBackToPixels() {
        val (left, top, right, bottom) = contentTrim(400, 200, stepX = 2, stepY = 2) { x, y -> page(x, y) }.toList()
        org.junit.Assert.assertEquals(78, left)
        org.junit.Assert.assertEquals(78, right)
        org.junit.Assert.assertEquals(19, top)
        org.junit.Assert.assertEquals(19, bottom)
    }
}

package com.dexter.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SpreadsTest {
    @Test
    fun theCoverStandsAloneAndTheRestPairUp() {
        assertEquals(listOf(0), pagesOfSpread(0, 10))
        assertEquals(listOf(1, 2), pagesOfSpread(1, 10))
        assertEquals(listOf(9), pagesOfSpread(5, 10))
        assertEquals(6, spreadCount(10))
        assertEquals(1, spreadCount(1))
    }

    @Test
    fun eachPageFindsItsSpread() {
        assertEquals(0, spreadOf(0))
        assertEquals(1, spreadOf(1))
        assertEquals(1, spreadOf(2))
        assertEquals(2, spreadOf(3))
    }

    @Test
    fun aDefaultModeSitsBetweenTheSeriesChoiceAndDetection() {
        assertEquals(ReadingMode.PagedRtl, resolveMode(ReadingMode.PagedRtl, ReadingMode.Vertical, ReadingMode.PagedLtr))
        assertEquals(ReadingMode.PagedLtr, resolveMode(ReadingMode.Auto, ReadingMode.Vertical, ReadingMode.PagedLtr))
        assertEquals(ReadingMode.Vertical, resolveMode(null, ReadingMode.Vertical, ReadingMode.Auto))
    }
}

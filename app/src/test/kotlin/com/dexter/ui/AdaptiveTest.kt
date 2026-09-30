package com.dexter.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveTest {
    @Test
    fun phonesGetTwoColumns() {
        assertEquals(2, adaptiveColumns(360f))
        assertEquals(2, adaptiveColumns(411f))
    }

    @Test
    fun tabletsGetMoreUpToSix() {
        assertEquals(4, adaptiveColumns(720f))
        assertEquals(6, adaptiveColumns(1600f))
    }

    @Test
    fun tinyWindowsKeepTwo() {
        assertEquals(2, adaptiveColumns(100f))
    }
}

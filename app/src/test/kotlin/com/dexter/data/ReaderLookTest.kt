package com.dexter.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderLookTest {
    private val base = Settings(readerDim = 10, readerBackground = ReaderBackground.Dark)

    @Test
    fun aSeriesWithoutALookSeesTheGlobalOne() {
        assertEquals(base, effectiveLook(base, "a"))
    }

    @Test
    fun aSeriesLookOverridesDimmingAndBackground() {
        val settings = withSeriesLook(base, "a", true).let { applyLookChange(it, "a") { s -> s.copy(readerDim = 40, readerBackground = ReaderBackground.Black) } }
        val seen = effectiveLook(settings, "a")
        assertEquals(40, seen.readerDim)
        assertEquals(ReaderBackground.Black, seen.readerBackground)
        assertEquals(10, settings.readerDim)
        assertEquals(ReaderBackground.Dark, settings.readerBackground)
    }

    @Test
    fun otherChangesStillApplyGlobally() {
        val settings = withSeriesLook(base, "a", true).let { applyLookChange(it, "a") { s -> s.copy(volumeKeys = true) } }
        assertEquals(true, settings.volumeKeys)
    }

    @Test
    fun aSeriesWithoutALookChangesTheGlobalOne() {
        assertEquals(30, applyLookChange(base, "b") { it.copy(readerDim = 30) }.readerDim)
    }

    @Test
    fun turningTheLookOffRestoresTheGlobalOne() {
        val on = withSeriesLook(base, "a", true)
        assertEquals(base, withSeriesLook(on, "a", false))
    }
}

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

class ResetReaderSettingsTest {
    @Test
    fun resetsReaderOptionsAndKeepsTheRest() {
        val changed = Settings(
            readerBackground = ReaderBackground.White,
            readerDim = 40,
            autoScrollLevel = 3,
            volumeKeys = true,
            keepScreenOn = false,
            pageGap = 8,
            prefetchPages = 6,
            dailyGoal = 5,
            blockedTags = setOf("x"),
            seriesLooks = mapOf("s" to SeriesLook(dim = 10, background = ReaderBackground.White)),
            seriesReadingModes = mapOf("s" to ReadingMode.PagedRtl),
        )
        val reset = resetReaderSettings(changed)
        val defaults = Settings()
        assertEquals(defaults.readerBackground, reset.readerBackground)
        assertEquals(defaults.readerDim, reset.readerDim)
        assertEquals(defaults.autoScrollLevel, reset.autoScrollLevel)
        assertEquals(defaults.volumeKeys, reset.volumeKeys)
        assertEquals(defaults.keepScreenOn, reset.keepScreenOn)
        assertEquals(defaults.pageGap, reset.pageGap)
        assertEquals(defaults.prefetchPages, reset.prefetchPages)
        assertEquals(5, reset.dailyGoal)
        assertEquals(setOf("x"), reset.blockedTags)
        assertEquals(changed.seriesLooks, reset.seriesLooks)
        assertEquals(changed.seriesReadingModes, reset.seriesReadingModes)
    }
}

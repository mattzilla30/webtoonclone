package com.dexter.ui.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoomTest {
    private val size = IntSize(1000, 2000)

    @Test
    fun theScaleStaysBetweenOutAndTheMaximum() {
        assertEquals(1f, clampScale(0.3f), 0f)
        assertEquals(MAX_SCALE, clampScale(9f), 0f)
        assertEquals(2f, clampScale(2f), 0f)
    }

    @Test
    fun zoomingAroundAPointKeepsThatPointStill() {
        // The content point at x=500 sits at screen x=500. After 2x zoom about 500 it must still be at 500.
        val offset = zoomAbout(offset = 0f, focus = 500f, oldScale = 1f, newScale = 2f)
        assertEquals(500f, 500f * 2f + offset, 0.001f)
    }

    @Test
    fun theOffsetCannotSlidePastTheEdges() {
        assertEquals(-1000f, clampOffset(-5000f, extent = 1000f, scale = 2f), 0f)
        assertEquals(0f, clampOffset(300f, extent = 1000f, scale = 2f), 0f)
        assertEquals(0f, clampOffset(-50f, extent = 1000f, scale = 1f), 0f)
    }

    @Test
    fun aPinchZoomsInAndAPinchBackOutResets() {
        val zoom = ZoomState()
        zoom.transform(2f, Offset.Zero, Offset(500f, 1000f), size)
        assertTrue(zoom.isZoomed)
        assertEquals(2f, zoom.scale, 0.001f)
        zoom.transform(0.4f, Offset.Zero, Offset(500f, 1000f), size)
        assertFalse(zoom.isZoomed)
        assertEquals(0f, zoom.offsetX, 0f)
    }

    @Test
    fun doubleTapZoomsInThenOut() {
        val zoom = ZoomState()
        zoom.toggle(Offset(500f, 1000f), size)
        assertEquals(DOUBLE_TAP_SCALE, zoom.scale, 0.001f)
        zoom.toggle(Offset(500f, 1000f), size)
        assertFalse(zoom.isZoomed)
    }

    @Test
    fun sidewaysPanOnlyWorksWhileZoomed() {
        val zoom = ZoomState()
        zoom.panX(-100f, size)
        assertEquals(0f, zoom.offsetX, 0f)
        zoom.transform(2f, Offset.Zero, Offset.Zero, size)
        zoom.panX(-100f, size)
        assertEquals(-100f, zoom.offsetX, 0.001f)
    }
}

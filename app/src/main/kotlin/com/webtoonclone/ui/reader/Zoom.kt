package com.webtoonclone.ui.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.IntSize

const val MIN_SCALE = 1f
const val MAX_SCALE = 4f
const val DOUBLE_TAP_SCALE = 2.5f

/** Keeps a zoom factor between fully out and the most the reader allows. */
fun clampScale(scale: Float): Float = scale.coerceIn(MIN_SCALE, MAX_SCALE)

/**
 * The new offset along one axis after zooming from [oldScale] to [newScale] around [focus], so the
 * point under the fingers stays put. The content is anchored at its top-left corner.
 */
fun zoomAbout(offset: Float, focus: Float, oldScale: Float, newScale: Float): Float =
    focus - (focus - offset) * (newScale / oldScale)

/** Stops the content from sliding past its edges. At scale 1 the only valid offset is 0. */
fun clampOffset(offset: Float, extent: Float, scale: Float): Float = offset.coerceIn(extent * (1f - scale), 0f)

/** The zoom and pan of the reader, shared by the vertical and paged layouts. */
class ZoomState {
    var scale by mutableFloatStateOf(1f)
        private set
    var offsetX by mutableFloatStateOf(0f)
        private set
    var offsetY by mutableFloatStateOf(0f)
        private set

    val isZoomed: Boolean get() = scale > 1.01f

    fun reset() {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
    }

    /** Applies a pinch: zoom by [zoomChange] around [focus], then move by [pan]. */
    fun transform(zoomChange: Float, pan: Offset, focus: Offset, size: IntSize) {
        val newScale = clampScale(scale * zoomChange)
        if (newScale <= MIN_SCALE + 0.01f) {
            reset()
            return
        }
        offsetX = clampOffset(zoomAbout(offsetX, focus.x, scale, newScale) + pan.x, size.width.toFloat(), newScale)
        offsetY = clampOffset(zoomAbout(offsetY, focus.y, scale, newScale) + pan.y, size.height.toFloat(), newScale)
        scale = newScale
    }

    /** One-finger sideways pan while zoomed. Up and down still scroll the content underneath. */
    fun panX(dx: Float, size: IntSize) {
        if (isZoomed) offsetX = clampOffset(offsetX + dx, size.width.toFloat(), scale)
    }

    /** Double tap: zoom in around the tap, or back out when already zoomed. */
    fun toggle(focus: Offset, size: IntSize) {
        if (isZoomed) reset() else transform(DOUBLE_TAP_SCALE, Offset.Zero, focus, size)
    }
}

/**
 * Two-finger pinch and pan. It listens first, before the scrolling content, and takes the touches only
 * while two fingers are down, so a single finger still scrolls. While zoomed, a single finger also
 * pans sideways without taking the touch.
 */
fun Modifier.zoomGestures(state: ZoomState, size: () -> IntSize): Modifier = pointerInput(state) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var pinching = false
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val down = event.changes.count { it.pressed }
            if (down >= 2) {
                pinching = true
                val zoomChange = event.calculateZoom()
                val pan = event.calculatePan()
                if (zoomChange != 1f || pan != Offset.Zero) {
                    state.transform(zoomChange, pan, event.calculateCentroid(useCurrent = true), size())
                }
                event.changes.forEach { if (it.positionChanged()) it.consume() }
            } else if (!pinching && state.isZoomed && down == 1) {
                state.panX(event.calculatePan().x, size())
            }
        } while (event.changes.any { it.pressed })
    }
}

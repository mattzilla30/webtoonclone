package com.dexter.ui.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntSize

/**
 * Session state for guided panel stepping: which region of the current page the camera shows, or -1
 * for the whole page. A page turn that the stepping itself started sets [armed] first, so the
 * page-change reset below does not wipe the region the new page should open on.
 */
class GuidedState {
    var region by mutableIntStateOf(-1)
        private set
    var armed: Boolean = false

    fun reset() {
        region = -1
    }

    fun show(region: Int) {
        this.region = region
    }

    val isWholePage: Boolean get() = region < 0
}

/**
 * The region grid for a page shown in [container]: 3 by 3 on tall portrait screens, 2 by 2 otherwise.
 * The page's own aspect is not known until its image loads, so the screen's shape stands in for it.
 */
fun guidedGrid(container: IntSize): Int =
    if (container.width > 0 && container.height > container.width * 1.5f) 3 else 2

/** The zoom that shows one region of the grid: its scale and its top-left offset. */
data class GuidedTarget(val scale: Float, val offsetX: Float, val offsetY: Float)

/**
 * The zoom that centres region [region] of an [n] by [n] grid on [container], clamped to the page's
 * edges. Regions run in reading order, mirrored for [rtl]. The page is assumed to fill the container,
 * as it does with whole-page fit; with other fits the camera may sit slightly off.
 */
fun guidedTarget(region: Int, n: Int, rtl: Boolean, container: IntSize): GuidedTarget {
    val w = container.width.toFloat().coerceAtLeast(1f)
    val h = container.height.toFloat().coerceAtLeast(1f)
    val size = n.coerceAtLeast(1)
    val column = if (rtl) size - 1 - (region % size) else region % size
    val row = (region / size).coerceIn(0, size - 1)
    // The centre of the region, as a fraction of the page.
    val cx = (column + 0.5f) / size
    val cy = (row + 0.5f) / size
    val scale = clampScale(size.toFloat())
    // The page is anchored top-left at the offset, so putting the region's centre on screen and
    // clamping to the edges frames it.
    val offsetX = clampOffset(w / 2f - cx * w * scale, w, scale)
    val offsetY = clampOffset(h / 2f - cy * h * scale, h, scale)
    return GuidedTarget(scale, offsetX, offsetY)
}

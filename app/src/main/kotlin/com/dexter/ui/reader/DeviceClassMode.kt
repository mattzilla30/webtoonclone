package com.dexter.ui.reader

import com.dexter.data.ReadingMode
import com.dexter.ui.RAIL_MIN_WIDTH_DP

/**
 * The reading mode for [widthDp] when per-device-class mode is on: phone-sized windows read as a
 * vertical strip, tablet-sized windows (including an unfolded foldable) use [tabletMode].
 */
fun deviceClassReadingMode(widthDp: Float, tabletMode: ReadingMode): ReadingMode =
    if (widthDp >= RAIL_MIN_WIDTH_DP) {
        when (tabletMode) {
            ReadingMode.PagedLtr, ReadingMode.PagedRtl -> tabletMode
            // A strip or Auto choice is no override at all on a tablet; pages read better.
            else -> ReadingMode.PagedLtr
        }
    } else {
        ReadingMode.Vertical
    }

/**
 * The mode to read with. A series' own choice always wins; otherwise, with per-device-class mode on,
 * the window's device class decides; otherwise the resolved mode stands.
 */
fun effectiveReadingMode(
    seriesChoice: ReadingMode,
    resolved: ReadingMode,
    deviceClassOn: Boolean,
    widthDp: Float,
    tabletMode: ReadingMode,
): ReadingMode = if (deviceClassOn && seriesChoice == ReadingMode.Auto) {
    deviceClassReadingMode(widthDp, tabletMode)
} else {
    resolved
}

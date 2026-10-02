package com.dexter.ui.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.dexter.data.TapAction
import com.dexter.data.TapZoneLayout

/** Swaps the navigation direction of a tap action, leaving the bars toggle alone. */
private fun TapAction.mirrored(): TapAction = when (this) {
    TapAction.Previous -> TapAction.Next
    TapAction.Next -> TapAction.Previous
    TapAction.ToggleBars -> TapAction.ToggleBars
}

/**
 * Decides a tap in paged mode under [layout]:
 * - Default: thirds of the width. Left goes back, the middle toggles the bars, right goes forward.
 * - Kindle: a wider middle, so the outer quarters turn and everything else toggles the bars.
 * - LShaped: an L along the left edge and the bottom goes back, a centered square toggles the bars,
 *   everything else goes forward. The L stays on the left in right-to-left mode, so [rtl] does not mirror it.
 * - Edge: thin strips along the sides turn, the rest of the screen toggles the bars.
 *
 * Right-to-left reading mirrors the left-right layouts, and [invert] mirrors every layout. With
 * [oneHanded], taps in the top half only toggle the bars, so navigation stays near the thumb.
 */
fun tapZoneAction(
    offset: Offset,
    size: IntSize,
    layout: TapZoneLayout,
    rtl: Boolean,
    invert: Boolean = false,
    oneHanded: Boolean = false,
): TapAction {
    if (size.width <= 0 || size.height <= 0) return TapAction.ToggleBars
    if (oneHanded && offset.y < size.height / 2f) return TapAction.ToggleBars
    val x = offset.x / size.width
    val y = offset.y / size.height
    val action = when (layout) {
        TapZoneLayout.Default -> when {
            x < 1f / 3f -> TapAction.Previous
            x > 2f / 3f -> TapAction.Next
            else -> TapAction.ToggleBars
        }
        TapZoneLayout.Kindle -> when {
            x < 0.25f -> TapAction.Previous
            x > 0.75f -> TapAction.Next
            else -> TapAction.ToggleBars
        }
        TapZoneLayout.LShaped -> when {
            x < 0.2f || y > 0.8f -> TapAction.Previous
            x in 0.35f..0.65f && y in 0.35f..0.65f -> TapAction.ToggleBars
            else -> TapAction.Next
        }
        TapZoneLayout.Edge -> when {
            x < 0.12f -> TapAction.Previous
            x > 0.88f -> TapAction.Next
            else -> TapAction.ToggleBars
        }
    }
    val mirror = invert || (rtl && layout != TapZoneLayout.LShaped)
    return if (mirror) action.mirrored() else action
}

/**
 * Tap zones for the vertical strip: the top third scrolls up, the bottom third scrolls down, and the
 * middle toggles the bars. With [oneHanded], the top half only toggles the bars and the bottom half
 * scrolls down, so the thumb never has to reach. Inversion does not apply here: swapping up and down
 * reads as a bug, not a mirror.
 */
fun webtoonTapAction(y: Float, height: Float, oneHanded: Boolean = false): TapAction {
    if (height <= 0f) return TapAction.ToggleBars
    if (oneHanded) return if (y < height / 2f) TapAction.ToggleBars else TapAction.Next
    return when {
        y < height / 3f -> TapAction.Previous
        y > 2f * height / 3f -> TapAction.Next
        else -> TapAction.ToggleBars
    }
}

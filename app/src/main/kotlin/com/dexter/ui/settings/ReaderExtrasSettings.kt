package com.dexter.ui.settings

import androidx.compose.runtime.Composable
import com.dexter.data.Settings
import com.dexter.data.TapZoneLayout

/**
 * Extra reader input and comfort settings: tap-zone layouts, haptics, motion, e-ink mode, guided
 * panels, smart background, and one-handed mode. Wired into the Settings screen by calling
 * [ReaderExtrasSection] next to the other sections; see the insertion snippet in the task report.
 */
@Composable
internal fun ReaderExtrasSection(settings: Settings, update: ((Settings) -> Settings) -> Unit) {
    SettingsBlock("Reader extras") {
        ChoiceRow(
            "Tap zones",
            listOf(
                TapZoneLayout.Default to "Default",
                TapZoneLayout.Kindle to "Kindle style",
                TapZoneLayout.LShaped to "L-shaped",
                TapZoneLayout.Edge to "Edge",
            ),
            settings.tapZoneLayout,
            keywords = listOf("touch", "controls", "kindle"),
        ) { choice -> update { it.copy(tapZoneLayout = choice) } }
        SwitchRow("Mirror tap zones", "Swap the previous and next zones left to right.", settings.invertTapZones) { on ->
            update { it.copy(invertTapZones = on) }
        }
        SwitchRow(
            "Tap zones in the vertical strip",
            "Tap near the top or bottom of the strip to scroll by a screen. Off by default, so taps never fight scrolling.",
            settings.tapZonesInWebtoon,
        ) { on -> update { it.copy(tapZonesInWebtoon = on) } }
        SwitchRow("Vibrate on page turn", "A light buzz each time the page changes.", settings.hapticPageTurn) { on ->
            update { it.copy(hapticPageTurn = on) }
        }
        SwitchRow(
            "Reduce motion",
            "Turn page animations and guided zooms into instant cuts. Also follows the system's animator scale.",
            settings.reduceMotion,
        ) { on -> update { it.copy(reduceMotion = on) } }
        SwitchRow(
            "E-ink mode",
            "No animations, no dimming or colour filters, and no auto-scroll smoothing: high contrast for e-ink screens.",
            settings.eInkMode,
        ) { on -> update { it.copy(eInkMode = on) } }
        SwitchRow(
            "Guided panels",
            "In paged mode, taps step through regions of the page with a zooming camera instead of turning whole pages.",
            settings.guidedPanels,
        ) { on -> update { it.copy(guidedPanels = on) } }
        SwitchRow(
            "Smart background",
            "Tint the reader background toward each page's own colours as you read.",
            settings.smartBackground,
        ) { on -> update { it.copy(smartBackground = on) } }
        SwitchRow(
            "One-handed mode",
            "Tap zones and controls move to the bottom half, near the thumb. The top bar hides while it is on.",
            settings.oneHandedMode,
        ) { on -> update { it.copy(oneHandedMode = on) } }
    }
}

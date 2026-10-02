package com.dexter.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dexter.data.ReaderUi
import com.dexter.data.ReaderUiPrefs
import com.dexter.data.ReadingMode
import com.dexter.data.tabletReadingMode
import com.dexter.ui.CardRow
import com.dexter.ui.ChoiceChip
import com.dexter.ui.reader.StripBackground
import com.dexter.ui.reader.ToolbarAction
import com.dexter.ui.reader.defaultBottomActions
import com.dexter.ui.reader.defaultTopActions
import com.dexter.ui.reader.parseStripBackground
import com.dexter.ui.reader.serializeToolbarActions
import com.dexter.ui.reader.toolbarActionsOrDefault
import kotlinx.coroutines.launch

/**
 * The batch-A reader UI settings: chapter thumbnails, the customizable toolbar, binge mode,
 * predictive back, stylus input, per-device reading mode, spread pairing, strip styling, color page
 * detection, the sleep timer, and automatic data saver on metered connections. Reads [ReaderUiPrefs]
 * directly, so it needs no changes to the shared settings screen wiring beyond one call; see the
 * insertion snippet in the task report.
 */
@Composable
internal fun ReaderUiSection() {
    val context = LocalContext.current
    val prefs = remember { ReaderUiPrefs(context) }
    val ui by prefs.ui.collectAsStateWithLifecycle(initialValue = ReaderUi())
    val scope = rememberCoroutineScope()
    fun set(change: suspend ReaderUiPrefs.() -> Unit) = scope.launch { prefs.change() }

    SectionTitle("Chapter navigation")
    SwitchRow(
        "Thumbnail grid",
        "Tap the page counter to open a thumbnail grid of the chapter instead of the go-to-page dialog.",
        ui.thumbnailsEnabled,
    ) { on -> set { setThumbnailsEnabled(on) } }
    SwitchRow(
        "Scrubber previews",
        "While dragging the page slider, show the page under your thumb.",
        ui.scrubberPreview,
    ) { on -> set { setScrubberPreview(on) } }

    SectionTitle("Reader toolbar")
    Searchable("Reader toolbar", "buttons", "reorder", "customize") {
        Text(
            "Choose which buttons the reader's bars show, and in what order.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
    ToolbarEditor(
        top = toolbarActionsOrDefault(ui.topActionsCsv, defaultTopActions),
        bottom = toolbarActionsOrDefault(ui.bottomActionsCsv, defaultBottomActions),
        onTopChange = { actions -> set { setTopActions(serializeToolbarActions(actions)) } },
        onBottomChange = { actions -> set { setBottomActions(serializeToolbarActions(actions)) } },
    )

    SectionTitle("Binge mode")
    SwitchRow(
        "Binge mode",
        "At the end of a chapter, count down and open the next episode automatically, like the next-episode prompt on TV.",
        ui.bingeMode,
    ) { on -> set { setBingeMode(on) } }
    ChoiceRow(
        "Countdown length",
        listOf(3 to "3 seconds", 5 to "5 seconds", 10 to "10 seconds"),
        ui.bingeSeconds,
    ) { seconds -> set { setBingeSeconds(seconds) } }

    SectionTitle("Back gesture")
    SwitchRow(
        "Predictive back",
        "On Android 14 and newer, the reader shrinks away with your swipe instead of closing abruptly.",
        ui.predictiveBack,
    ) { on -> set { setPredictiveBack(on) } }

    SectionTitle("Stylus")
    SwitchRow(
        "Pen button page turns",
        "The S Pen barrel button turns pages: the primary button forward, the secondary button back.",
        ui.stylusPenButton,
    ) { on -> set { setStylusPenButton(on) } }
    SwitchRow(
        "Hover peek zoom",
        "Hovering the pen over a page shows a 2x magnifier under its tip.",
        ui.stylusHoverPeek,
    ) { on -> set { setStylusHoverPeek(on) } }

    SectionTitle("Device class")
    SwitchRow(
        "Per-device reading mode",
        "Phones read as a vertical strip; tablets and unfolded foldables use the tablet mode below. A series' own mode choice always wins.",
        ui.deviceClassMode,
    ) { on -> set { setDeviceClassMode(on) } }
    ChoiceRow(
        "Tablet mode",
        listOf(ReadingMode.PagedLtr to "Pages, left to right", ReadingMode.PagedRtl to "Pages, right to left"),
        ui.tabletReadingMode(),
    ) { mode -> set { setTabletMode(mode) } }

    SectionTitle("Two-page spreads")
    SwitchRow(
        "Spread-aware pairing",
        "Wide pages are detected as two-page spreads and get a full-width slot instead of being paired up.",
        ui.spreadAware,
    ) { on -> set { setSpreadAware(on) } }
    Searchable("spreads", "pairing", "shift") {
        Text(
            "When a chapter still pairs up wrong, the reader options sheet has a page-pairing shift that moves every pair by one page.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }

    SectionTitle("Strip style")
    ChoiceRow(
        "Space between pages",
        listOf(-1 to "Follow reader setting", 0 to "None", 8 to "Small", 16 to "Medium", 32 to "Large"),
        ui.stripGapDp,
    ) { gap -> set { setStripGapDp(gap) } }
    ChoiceRow(
        "Page corners",
        listOf(0 to "Square", 4 to "Slightly rounded", 8 to "Rounded", 16 to "Very rounded"),
        ui.stripCornerDp,
    ) { corners -> set { setStripCornerDp(corners) } }
    ChoiceRow(
        "Strip background",
        StripBackground.entries.map { it to it.name },
        parseStripBackground(ui.stripBg),
    ) { choice -> set { setStripBg(choice.name) } }

    SectionTitle("Color pages")
    SwitchRow(
        "Exempt color pages from night filters",
        "Pages detected as color skip the greyscale and tint filters, so night reading never washes them out.",
        ui.colorPageExempt,
    ) { on -> set { setColorPageExempt(on) } }

    SectionTitle("Sleep timer")
    ChoiceRow(
        "Sleep timer",
        listOf(0 to "Off", 15 to "15 minutes", 30 to "30 minutes", 45 to "45 minutes", 60 to "1 hour"),
        ui.sleepTimerMinutes,
    ) { minutes -> set { setSleepTimerMinutes(minutes) } }
    Searchable("Sleep timer") {
        Text(
            "When the time is up, auto-scroll stops and the screen dims. Changing the length restarts the countdown. The toolbar can hold a sleep timer button too.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }

    SectionTitle("Data saver")
    SwitchRow(
        "Data saver on metered connections",
        "Automatically load the smaller page images on mobile data or metered Wi-Fi. Combines with the manual Data saver toggle.",
        ui.dataSaverAutoMetered,
    ) { on -> set { setDataSaverAutoMetered(on) } }
}

/** Reorders and toggles the reader's top and bottom bar buttons. */
@Composable
private fun ToolbarEditor(
    top: List<ToolbarAction>,
    bottom: List<ToolbarAction>,
    onTopChange: (List<ToolbarAction>) -> Unit,
    onBottomChange: (List<ToolbarAction>) -> Unit,
) {
    if (!matchesQuery(LocalSettingsQuery.current, "Reader toolbar", "buttons", "reorder", "customize")) return
    Text("Top bar", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
    ActionList(top, onTopChange)
    Text("Bottom bar", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
    ActionList(bottom, onBottomChange)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionList(actions: List<ToolbarAction>, onChange: (List<ToolbarAction>) -> Unit) {
    CardRow {
        Column(Modifier.padding(vertical = 4.dp)) {
            actions.forEachIndexed { index, action ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(action.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(start = 8.dp))
                    IconButton(onClick = { onChange(actions.swap(index, index - 1)) }, enabled = index > 0) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up")
                    }
                    IconButton(onClick = { onChange(actions.swap(index, index + 1)) }, enabled = index < actions.lastIndex) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down")
                    }
                    IconButton(onClick = { onChange(actions - action) }) {
                        Icon(Icons.Default.Close, contentDescription = "Remove")
                    }
                }
            }
            val remaining = ToolbarAction.entries - actions.toSet()
            if (remaining.isNotEmpty()) {
                FlowRow(
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    remaining.forEach { action ->
                        ChoiceChip(action.label, false) { onChange(actions + action) }
                    }
                }
            }
        }
    }
}

/** [actions] with the entries at [from] and [to] swapped. */
private fun List<ToolbarAction>.swap(from: Int, to: Int): List<ToolbarAction> {
    if (from !in indices || to !in indices) return this
    return toMutableList().also { list ->
        val entry = list[from]
        list[from] = list[to]
        list[to] = entry
    }
}

package com.dexter.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dexter.R
import com.dexter.data.Chapter
import com.dexter.data.PageFit
import com.dexter.data.PageTransition
import com.dexter.data.ReaderBackground
import com.dexter.data.ReaderFilter
import com.dexter.data.ReaderOrientation
import com.dexter.data.ReadingMode
import com.dexter.data.Settings
import com.dexter.ui.ChoiceChip
import com.dexter.ui.SyncedSlider
import kotlin.math.roundToInt

/** Every readable chapter, oldest first, opened at the current one. Tapping one jumps to it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChapterPicker(chapters: List<Chapter>, currentId: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = chapters.indexOfFirst { it.id == currentId }.coerceAtLeast(0))
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(stringResource(R.string.chapters), style = MaterialTheme.typography.titleLargeEmphasized, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        LazyColumn(Modifier.heightIn(max = 480.dp), state = listState) {
            itemsIndexed(chapters, key = { _, c -> c.id }) { _, chapter ->
                val current = chapter.id == currentId
                Surface(
                    onClick = { onSelect(chapter.id) },
                    color = if (current) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    contentColor = if (current) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Text(
                            buildString {
                                append("Ep. ${chapter.number}")
                                if (chapter.title.isNotBlank()) append(" · ${chapter.title}")
                            },
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        chapter.group?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
}

private val modeLabels = listOf(
    ReadingMode.Auto to "Auto",
    ReadingMode.Vertical to "Vertical strip",
    ReadingMode.PagedLtr to "Pages, left to right",
    ReadingMode.PagedRtl to "Pages, right to left",
)

/** Reading mode, dimming, background, auto-scroll speed, and volume-key paging, saved as you change them. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderOptions(
    settings: Settings,
    mode: ReadingMode,
    chosenMode: ReadingMode,
    seriesLook: Boolean,
    onSeriesLook: (Boolean) -> Unit,
    zen: Boolean,
    onZen: (Boolean) -> Unit,
    seriesOrientation: ReaderOrientation,
    onSeriesOrientation: (ReaderOrientation) -> Unit,
    onChange: ((Settings) -> Settings) -> Unit,
    onMode: (ReadingMode) -> Unit,
    onDismiss: () -> Unit,
    /** Spread-aware pairing shift: 0 is the cover alone, 1 pairs it forward. Null hides the control. */
    pairShift: Int = 0,
    onPairShift: (() -> Unit)? = null,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text(stringResource(R.string.reader_options), style = MaterialTheme.typography.titleLargeEmphasized)

            Text(stringResource(R.string.reading_mode_for_this_series), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp))
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                modeLabels.forEach { (value, label) -> ChoiceChip(label, chosenMode == value) { onMode(value) } }
            }

            Text("Reading mode for every other series", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp))
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                modeLabels.forEach { (value, label) ->
                    ChoiceChip(label, settings.defaultReadingMode == value) { onChange { it.copy(defaultReadingMode = value) } }
                }
            }

            OptionSwitch(
                "Zen reading mode: hide everything, volume keys turn pages, long-press to exit",
                zen,
                onZen,
            )

            Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Separate look for this series", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = seriesLook, onCheckedChange = onSeriesLook)
            }

            Text(stringResource(R.string.dimming), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp))
            SyncedSlider(
                value = settings.readerDim.toFloat(),
                onValueChange = { value -> onChange { it.copy(readerDim = value.roundToInt()) } },
                valueRange = 0f..70f,
            )

            Text("Brightness", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChoiceChip("System", settings.readerBrightness !in 1..100) { onChange { it.copy(readerBrightness = -1) } }
                SyncedSlider(
                    value = settings.readerBrightness.coerceIn(1, 100).toFloat(),
                    onValueChange = { value -> onChange { it.copy(readerBrightness = value.roundToInt().coerceIn(1, 100)) } },
                    valueRange = 1f..100f,
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                )
            }

            Text("Colour filter", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ReaderFilter.entries.forEach { choice ->
                    ChoiceChip(choice.name, settings.readerFilter == choice) { onChange { it.copy(readerFilter = choice) } }
                }
            }

            Text(stringResource(R.string.background), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ReaderBackground.entries.forEach { choice ->
                    ChoiceChip(choice.name, settings.readerBackground == choice) { onChange { it.copy(readerBackground = choice) } }
                }
            }

            if (mode == ReadingMode.Vertical) {
                Text(stringResource(R.string.auto_scroll), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp))
                FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    (0..5).forEach { speed ->
                        ChoiceChip(if (speed == 0) "Off" else speed.toString(), settings.autoScrollLevel == speed) {
                            onChange { it.copy(autoScrollLevel = speed) }
                        }
                    }
                }
            }

            if (mode == ReadingMode.Vertical) {
                Text(stringResource(R.string.space_between_pages), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp))
                FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "None", 8 to "Small", 24 to "Large").forEach { (gap, label) ->
                        ChoiceChip(label, settings.pageGap == gap) { onChange { it.copy(pageGap = gap) } }
                    }
                }
            }

            if (mode != ReadingMode.Vertical) {
                Text("Page fit", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp))
                FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(PageFit.Screen to "Whole page", PageFit.Width to "Fit width", PageFit.Height to "Fit height").forEach { (fit, label) ->
                        ChoiceChip(label, settings.pageFit == fit) { onChange { it.copy(pageFit = fit) } }
                    }
                }
                Text("Page turn", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp))
                FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PageTransition.entries.forEach { choice ->
                        ChoiceChip(choice.name, settings.pageTransition == choice) { onChange { it.copy(pageTransition = choice) } }
                    }
                }
                OptionSwitch("Two pages side by side when sideways", settings.spreads) { on -> onChange { it.copy(spreads = on) } }
                if (settings.spreads && onPairShift != null) {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Page pairing", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = onPairShift) {
                            Text(if (pairShift == 0) "Cover alone" else "Shifted by one")
                        }
                    }
                    Text(
                        "When two-page spreads pair up wrong, shift the pairing by one page.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                OptionSwitch("Next episode follows on below", settings.continuousScroll) { on -> onChange { it.copy(continuousScroll = on) } }
                OptionSwitch("Tap top or bottom to scroll", settings.tapToScroll) { on -> onChange { it.copy(tapToScroll = on) } }
            }
            OptionSwitch("Trim unused page edges, page numbers and margin notes included", settings.cropBorders) { on -> onChange { it.copy(cropBorders = on) } }
            OptionSwitch("Hide the bars after a few seconds", settings.autoHideBars) { on -> onChange { it.copy(autoHideBars = on) } }
            OptionSwitch("Clock and battery with the page count", settings.showClock) { on -> onChange { it.copy(showClock = on) } }
            OptionSwitch("Incognito: keep no history or stats", settings.incognito) { on -> onChange { it.copy(incognito = on) } }

            Text("Screen direction", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp))
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ReaderOrientation.entries.forEach { choice ->
                    ChoiceChip(choice.name, settings.readerOrientation == choice) { onChange { it.copy(readerOrientation = choice) } }
                }
            }

            Text("Screen direction for this series", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp))
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Auto follows the global choice above.
                ReaderOrientation.entries.forEach { choice ->
                    ChoiceChip(if (choice == ReaderOrientation.Auto) "Follow global" else choice.name, seriesOrientation == choice) {
                        onSeriesOrientation(choice)
                    }
                }
            }

            Text(stringResource(R.string.load_next_chapter_ahead), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp))
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "Off", 3 to "First pages", 100 to "Whole chapter").forEach { (count, label) ->
                    ChoiceChip(label, settings.prefetchPages == count) { onChange { it.copy(prefetchPages = count) } }
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.keep_screen_on), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = settings.keepScreenOn, onCheckedChange = { on -> onChange { it.copy(keepScreenOn = on) } })
            }

            Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (mode == ReadingMode.Vertical) "Volume keys scroll" else "Volume keys turn pages", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = settings.volumeKeys, onCheckedChange = { on -> onChange { it.copy(volumeKeys = on) } })
            }
        }
    }
}

/** One labelled switch in the reader options. */
@Composable
private fun OptionSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dexter.R
import com.dexter.data.Chapter
import com.dexter.data.ReaderBackground
import com.dexter.data.ReaderOrientation
import com.dexter.data.ReadingMode
import com.dexter.data.Settings
import com.dexter.ui.ChoiceChip
import com.dexter.ui.SyncedSlider
import kotlinx.coroutines.flow.first
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
    onChange: ((Settings) -> Settings) -> Unit,
    onMode: (ReadingMode) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text(stringResource(R.string.reader_options), style = MaterialTheme.typography.titleLargeEmphasized)

            Text(stringResource(R.string.reading_mode_for_this_series), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp))
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                modeLabels.forEach { (value, label) -> ChoiceChip(label, chosenMode == value) { onMode(value) } }
            }

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

            Text("Screen direction", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp))
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ReaderOrientation.entries.forEach { choice ->
                    ChoiceChip(choice.name, settings.readerOrientation == choice) { onChange { it.copy(readerOrientation = choice) } }
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

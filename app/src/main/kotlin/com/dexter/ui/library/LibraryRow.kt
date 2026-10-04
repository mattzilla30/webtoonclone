package com.dexter.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dexter.R
import com.dexter.data.LibraryList
import com.dexter.data.SavedSeries
import com.dexter.ui.Cover
import com.dexter.ui.timeAgo
import java.time.Instant

/**
 * One series in a My Series list: cover, title, and where you are. A long press starts selecting, and while
 * [selecting], a tap ticks the row instead of opening it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LibraryRow(
    series: SavedSeries,
    tab: LibraryList,
    showNew: Boolean,
    selected: Boolean,
    selecting: Boolean,
    onOpen: () -> Unit,
    onSelect: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    newCount: Int? = null,
    /** Blur the cover until the series is started, when spoiler-safe blur is on. */
    blurCover: Boolean = false,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        // The page's own colour reads as no card at all, yet stays opaque so a swipe's action shows only beside the row.
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(
                onClick = { if (selecting) onSelect(!selected) else onOpen() },
                onClickLabel = if (selecting) "Select" else "Open series",
                onLongClick = { onSelect(!selected) },
                onLongClickLabel = "Select",
            ),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (blurCover) {
                SpoilerSafeCover(
                    blurred = true,
                    modifier = Modifier.width(44.dp).aspectRatio(2f / 3f).clip(MaterialTheme.shapes.small),
                ) {
                    Cover(series.coverUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, thumb = true, sharedKey = series.id)
                }
            } else {
                Cover(series.coverUrl, null, Modifier.width(44.dp).aspectRatio(2f / 3f).clip(MaterialTheme.shapes.small), contentScale = ContentScale.Crop, thumb = true, sharedKey = series.id)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                if (showNew) {
                    Text(newCount?.let { "$it new" } ?: stringResource(R.string.new_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                Text(series.title, style = MaterialTheme.typography.titleSmallEmphasized)
                if (tab == LibraryList.Lists) {
                    series.status?.let { Text(it.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
                }
                series.chapterNumber?.let {
                    val readAt = if (tab == LibraryList.Recent && series.at > 0) " · " + timeAgo(Instant.ofEpochMilli(series.at)) else ""
                    Text("Ep. $it$readAt", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (selecting) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = onSelect,
                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

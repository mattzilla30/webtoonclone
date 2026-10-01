package com.dexter.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
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

/** One series in a My Series list: cover, title, where you are, and a checkbox for removing it. */
@Composable
internal fun LibraryRow(
    series: SavedSeries,
    tab: LibraryList,
    showNew: Boolean,
    selected: Boolean,
    onOpen: () -> Unit,
    onSelect: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        onClick = onOpen,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Cover(series.coverUrl, series.title, Modifier.width(44.dp).aspectRatio(2f / 3f).clip(MaterialTheme.shapes.small), contentScale = ContentScale.Crop, thumb = true)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                if (showNew) {
                    Text(stringResource(R.string.new_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
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
            Checkbox(
                checked = selected,
                onCheckedChange = onSelect,
                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
            )
        }
    }
}

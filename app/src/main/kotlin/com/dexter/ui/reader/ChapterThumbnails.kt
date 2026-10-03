package com.dexter.ui.reader

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlin.math.roundToInt

/** The thumbnail column width in the grid. */
private const val THUMBNAIL_COLUMNS_DP = 96

/**
 * A thumbnail grid of the chapter, opened from the page counter. Tapping a thumbnail jumps to that
 * page; the header offers the go-to-page dialog for an exact number.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChapterThumbnailSheet(
    pages: List<String>,
    position: Int,
    onSelect: (Int) -> Unit,
    onGoToPage: () -> Unit,
    onDismiss: () -> Unit,
) {
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = (position - 6).coerceAtLeast(0))
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${pages.size} pages",
                style = MaterialTheme.typography.titleMediumEmphasized,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onGoToPage) { Text("Go to page") }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(THUMBNAIL_COLUMNS_DP.dp),
            state = gridState,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 24.dp),
        ) {
            items(pages.size, key = { it }) { index ->
                val current = index == position
                AsyncImage(
                    model = pages[index],
                    contentDescription = "Page ${index + 1}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .padding(4.dp)
                        .aspectRatio(0.72f)
                        .clip(MaterialTheme.shapes.small)
                        .then(if (current) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small) else Modifier)
                        .clickable { onSelect(index) },
                )
            }
        }
    }
}

/**
 * The reader's page slider with a live preview: while dragging, a thumbnail of the page under the
 * thumb floats above the slider and follows it. Releasing seeks to that page.
 */
@Composable
internal fun PageScrubber(
    position: Int,
    count: Int,
    pageUrl: (Int) -> String?,
    onSeek: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf(false) }
    var scrub by remember { mutableFloatStateOf(position.toFloat()) }
    // While the thumb is held, it shows the dragged value; otherwise it follows the reader.
    LaunchedEffect(position) { if (!dragging) scrub = position.toFloat() }
    val previewIndex = scrub.roundToInt().coerceIn(0, (count - 1).coerceAtLeast(0))
    // The caller mirrors the layout direction for right-to-left reading; the popup follows the thumb.
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    BoxWithConstraints(modifier) {
        val previewWidth = 120.dp
        // The popup tracks the thumb: fraction along the slider, mirrored for right-to-left.
        val fraction = if (count > 1) previewIndex / (count - 1).toFloat() else 0f
        val directed = if (isRtl) 1f - fraction else fraction
        val popupOffset = ((maxWidth - previewWidth) * directed).coerceAtLeast(0.dp)
        if (dragging) {
            Surface(
                tonalElevation = 6.dp,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .size(width = previewWidth, height = 160.dp)
                    .offset { IntOffset(popupOffset.roundToPx(), -170.dp.roundToPx()) }
                    .align(Alignment.TopStart),
            ) {
                val url = pageUrl(previewIndex)
                if (url != null) {
                    AsyncImage(
                        model = url,
                        contentDescription = "Page ${previewIndex + 1}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium),
                    )
                } else {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("${previewIndex + 1}", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        Column {
            Slider(
                value = scrub,
                onValueChange = {
                    scrub = it
                    dragging = true
                },
                onValueChangeFinished = {
                    dragging = false
                    onSeek(previewIndex)
                },
                valueRange = 0f..(count - 1).coerceAtLeast(0).toFloat(),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Page ${previewIndex + 1} of $count",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

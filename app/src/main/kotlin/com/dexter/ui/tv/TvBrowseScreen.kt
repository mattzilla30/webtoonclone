package com.dexter.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dexter.data.LibraryData
import com.dexter.data.LibraryStore
import com.dexter.data.SavedSeries
import com.dexter.ui.Cover
import org.koin.compose.koinInject

/**
 * The 10-foot UI for Android TV: a continue-reading row and the library grid, all navigable with a
 * d-pad. Every card is focusable and shows a focus ring; the first continue-reading card takes
 * initial focus so the d-pad works immediately. Declared for the Leanback launcher; see the
 * manifest snippet in the task report.
 */
@Composable
fun TvBrowseScreen(
    onOpenSeries: (String) -> Unit,
    onContinueReading: (seriesId: String, chapterId: String) -> Unit,
    onBack: () -> Unit,
) {
    val libraryStore: LibraryStore = koinInject()
    val library by libraryStore.data.collectAsStateWithLifecycle(initialValue = LibraryData())
    val firstFocus = remember { FocusRequester() }
    BackHandler(onBack = onBack)

    val continueReading = remember(library) { library.recent.filter { it.chapterId != null } }
    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 32.dp)) {
        Text("Dexter", style = MaterialTheme.typography.displaySmallEmphasized)
        if (continueReading.isNotEmpty()) {
            Text(
                "Continue reading",
                style = MaterialTheme.typography.titleLargeEmphasized,
                modifier = Modifier.padding(top = 24.dp, bottom = 12.dp),
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(continueReading, key = { it.id }) { saved ->
                    TvCard(
                        saved = saved,
                        subtitle = saved.chapterNumber?.let { "Ep. $it" },
                        onClick = { onContinueReading(saved.id, saved.chapterId!!) },
                        modifier = if (saved == continueReading.first()) Modifier.focusRequester(firstFocus) else Modifier,
                    )
                }
            }
            LaunchedEffect(Unit) { firstFocus.requestFocus() }
        }
        Text(
            "My series",
            style = MaterialTheme.typography.titleLargeEmphasized,
            modifier = Modifier.padding(top = 24.dp, bottom = 12.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(200.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(library.subscribed, key = { it.id }) { saved ->
                TvCard(
                    saved = saved,
                    subtitle = saved.chapterNumber?.let { "Ep. $it" } ?: "Subscribed",
                    onClick = { onOpenSeries(saved.id) },
                )
            }
        }
    }
}

/** One d-pad-focusable series card with a focus ring. */
@Composable
private fun TvCard(
    saved: SavedSeries,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier
            .width(200.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .border(
                4.dp,
                if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
                RoundedCornerShape(16.dp),
            )
            .padding(8.dp),
    ) {
        Cover(
            saved.coverUrl,
            saved.title,
            Modifier.fillMaxWidth().aspectRatio(3f / 4f),
            contentScale = ContentScale.Crop,
        )
        Text(
            saved.title,
            style = MaterialTheme.typography.titleMediumEmphasized,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        subtitle?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

package com.dexter.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dexter.R
import com.dexter.data.ContentTags
import com.dexter.data.Formats
import com.dexter.data.Genres
import com.dexter.data.SavedSearch
import com.dexter.data.SeriesSummary
import com.dexter.data.SuggestiveTags
import com.dexter.data.Themes
import com.dexter.ui.CardRow
import com.dexter.ui.Cover
import com.dexter.ui.GenreLabel

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Idle(
    recent: List<String>,
    saved: List<SavedSearch>,
    suggestions: List<SeriesSummary>,
    message: String?,
    viewModel: SearchViewModel,
    onOpenSeries: (String) -> Unit,
    onBrowse: (String) -> Unit,
    onTag: (String) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        if (message != null) {
            item { Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp)) }
        }
        // Titles that match what is being typed, before the browse lists.
        items(suggestions, key = { it.id }) { series ->
            CardRow(onClick = { onOpenSeries(series.id) }) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Cover(series.coverUrl, series.title, Modifier.width(40.dp).aspectRatio(2f / 3f).clip(MaterialTheme.shapes.extraSmall), contentScale = ContentScale.Crop, thumb = true)
                    Column(Modifier.padding(start = 12.dp)) {
                        GenreLabel(series.genre)
                        Text(series.title, style = MaterialTheme.typography.titleSmallEmphasized)
                    }
                }
            }
        }
        if (saved.isNotEmpty()) {
            item {
                Text(stringResource(R.string.saved_searches), style = MaterialTheme.typography.titleMediumEmphasized, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    saved.forEach { search ->
                        InputChip(
                            selected = false,
                            onClick = { viewModel.openSaved(search) },
                            label = { Text(search.name) },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = stringResource(R.string.remove),
                                    modifier = Modifier.size(InputChipDefaults.IconSize).clickable { viewModel.deleteSaved(search.name) },
                                )
                            },
                        )
                    }
                }
            }
        }
        if (recent.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.recent_searches), style = MaterialTheme.typography.titleMediumEmphasized)
                    TextButton(onClick = { viewModel.clearSearches() }) { Text(stringResource(R.string.delete_all)) }
                }
                FlowRow(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    recent.sortedBy { it.lowercase() }.forEach { term ->
                        InputChip(
                            selected = false,
                            onClick = { viewModel.search(term) },
                            label = { Text(term) },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = stringResource(R.string.remove),
                                    modifier = Modifier.size(InputChipDefaults.IconSize).clickable { viewModel.removeSearch(term) },
                                )
                            },
                        )
                    }
                }
            }
        }
        // Sections and their tags are in alphabetical order.
        tagSection("Browse", BrowseOptions, onBrowse)
        tagSection("Content", ContentTags, onTag)
        tagSection("Formats", Formats, onTag)
        tagSection("Genres", Genres.map { it.name }, onTag)
        tagSection("Suggestive", SuggestiveTags, onTag)
        tagSection("Themes", Themes, onTag)
        item { Box(Modifier.padding(bottom = 24.dp)) }
    }
}

/** A titled group of tappable tag chips. */
@OptIn(ExperimentalLayoutApi::class)
private fun LazyListScope.tagSection(title: String, tags: List<String>, onTag: (String) -> Unit) {
    item {
        Text(title, style = MaterialTheme.typography.titleMediumEmphasized, modifier = Modifier.padding(top = 16.dp, bottom = 10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tags.sortedBy { it.lowercase() }.forEach { tag ->
                AssistChip(onClick = { onTag(tag) }, label = { Text(tag) })
            }
        }
    }
}

/** Lists that need no search. Random opens one series, and the others list every series in an order. */
private val BrowseOptions = listOf("Random", "Recently added", "Top rated", "Completed")

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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.dexter.R
import com.dexter.data.AuthorSummary
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
import com.dexter.ui.discover.HiddenGem
import com.dexter.ui.discover.HiddenGemRow

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Idle(
    recent: List<String>,
    saved: List<SavedSearch>,
    suggestions: List<SeriesSummary>,
    authors: List<AuthorSummary>,
    message: String?,
    viewModel: SearchViewModel,
    onOpenSeries: (String) -> Unit,
    onOpenAuthor: (id: String, name: String) -> Unit,
    onBrowse: (String) -> Unit,
    onTag: (String) -> Unit,
    /** The hidden-gems feed, null until loaded; hidden entirely when [gemsEnabled] is false. */
    gems: List<HiddenGem>?,
    gemsEnabled: Boolean,
    onLoadGems: () -> Unit,
) {
    // Which tag sections are open. Browse and Genres start open, the long lists start closed.
    var open by rememberSaveable { mutableStateOf(setOf("Browse", "Genres")) }
    val toggle: (String) -> Unit = { title -> open = if (title in open) open - title else open + title }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        if (message != null) {
            item { Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp)) }
        }
        // Authors and artists whose name matches what is being typed.
        items(authors, key = { "author-${it.id}" }) { author ->
            CardRow(onClick = { onOpenAuthor(author.id, author.name) }) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(author.name, style = MaterialTheme.typography.titleSmallEmphasized)
                        Text("Author or artist", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
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
                            selected = search.notify,
                            onClick = { viewModel.openSaved(search) },
                            label = { Text(search.name) },
                            // The bell turns notifications for new matches on and off.
                            leadingIcon = {
                                Icon(
                                    if (search.notify) Icons.Default.Notifications else Icons.Outlined.Notifications,
                                    contentDescription = if (search.notify) "Stop notifying for ${search.name}" else "Notify about new matches for ${search.name}",
                                    modifier = Modifier.size(InputChipDefaults.IconSize).clickable { viewModel.toggleSavedNotify(search) },
                                )
                            },
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
                    // Newest first, as they were searched.
                    recent.forEach { term ->
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
        // Sections and their tags are in alphabetical order. Tapping a heading opens or closes it.
        tagSection("Browse", BrowseOptions, "Browse" in open, toggle, onBrowse)
        tagSection("Content", ContentTags, "Content" in open, toggle, onTag)
        tagSection("Formats", Formats, "Formats" in open, toggle, onTag)
        tagSection("Genres", Genres.map { it.name }, "Genres" in open, toggle, onTag)
        tagSection("Suggestive", SuggestiveTags, "Suggestive" in open, toggle, onTag)
        tagSection("Themes", Themes, "Themes" in open, toggle, onTag)
        // High-rated but little-followed series, loaded on demand.
        if (gemsEnabled) {
            val gemsOpen = "Hidden gems" in open
            item(key = "section-gems") {
                Row(
                    Modifier.fillMaxWidth().clickable {
                        toggle("Hidden gems")
                        if (!gemsOpen) onLoadGems()
                    }.padding(top = 16.dp, bottom = 10.dp)
                        .semantics { role = Role.Button; stateDescription = if (gemsOpen) "Open" else "Closed" },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Hidden gems", style = MaterialTheme.typography.titleMediumEmphasized, modifier = Modifier.weight(1f))
                    Icon(if (gemsOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null)
                }
                if (gemsOpen) {
                    val list = gems
                    when {
                        list == null -> Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                        }
                        list.isEmpty() -> Text(
                            "No hidden gems right now. Check back later.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                }
            }
            if (gemsOpen) {
                items(gems.orEmpty(), key = { "gem-${it.detail.summary.id}" }) { gem ->
                    HiddenGemRow(gem, onOpenSeries)
                }
            }
        }
        item { Box(Modifier.padding(bottom = 24.dp)) }
    }
}

/** A titled group of tappable tag chips. The heading opens and closes the group. */
@OptIn(ExperimentalLayoutApi::class)
private fun LazyListScope.tagSection(title: String, tags: List<String>, expanded: Boolean, onToggle: (String) -> Unit, onTag: (String) -> Unit) {
    item(key = "section-$title") {
        Row(
            Modifier.fillMaxWidth().clickable { onToggle(title) }.padding(top = 16.dp, bottom = 10.dp)
                .semantics { role = Role.Button; stateDescription = if (expanded) "Open" else "Closed" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMediumEmphasized, modifier = Modifier.weight(1f))
            Text("${tags.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null)
        }
        if (expanded) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tags.sortedBy { it.lowercase() }.forEach { tag ->
                    AssistChip(onClick = { onTag(tag) }, label = { Text(tag) })
                }
            }
        }
    }
}

/** Lists that need no search. Random opens one series, and the others list every series in an order. */
private val BrowseOptions = listOf("Random", "Recently added", "Top rated", "Completed")

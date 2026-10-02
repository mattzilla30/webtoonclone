package com.dexter.ui.discover

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dexter.data.MangaDexRepository
import com.dexter.data.Order
import com.dexter.data.SeriesDetail
import com.dexter.ui.Cover
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** A top-rated but little-followed series: the hidden-gems feed entry. */
data class HiddenGem(
    val detail: SeriesDetail,
    /** The rating that qualified it. */
    val rating: Double,
    /** How many people follow it: low by definition here. */
    val follows: Int,
)

/** Minimum rating to count as a gem. */
const val HIDDEN_GEM_MIN_RATING = 8.5

/** A series with more follows than this is too well known to be a gem. */
const val HIDDEN_GEM_MAX_FOLLOWS = 2_000

/**
 * Finds hidden gems: high-rated but low-readership series. MangaDex can sort by rating but the
 * rating only ships on the series detail, so the top-rated page is fetched first and details are
 * loaded for the candidates in parallel, then filtered to rating >= [HIDDEN_GEM_MIN_RATING] with
 * follows <= [HIDDEN_GEM_MAX_FOLLOWS]. Series already in [excludeIds] are skipped.
 */
suspend fun fetchHiddenGems(
    repository: MangaDexRepository,
    excludeIds: Set<String> = emptySet(),
    candidateCount: Int = 30,
    limit: Int = 20,
): List<HiddenGem> = coroutineScope {
    val candidates = repository.browse(order = Order.TopRated, limit = candidateCount, withStats = true)
        .filter { it.id !in excludeIds }
    val details = candidates.map { summary ->
        async {
            runCatching { repository.series(summary.id) }.getOrNull()
        }
    }.awaitAll().filterNotNull()
    details.mapNotNull { detail ->
        val rating = detail.rating ?: return@mapNotNull null
        val follows = detail.summary.follows ?: Int.MAX_VALUE
        if (rating >= HIDDEN_GEM_MIN_RATING && follows <= HIDDEN_GEM_MAX_FOLLOWS) {
            HiddenGem(detail, rating, follows)
        } else {
            null
        }
    }.sortedByDescending { it.rating }.take(limit)
}

/**
 * The hidden-gems feed: high-rated, low-readership series the reader probably missed. Wired into
 * Discover behind the [com.dexter.data.QolPrefs.hiddenGemsEnabled] toggle; see the integration
 * snippet in the task report.
 */
@Composable
fun HiddenGemsList(gems: List<HiddenGem>, onOpenSeries: (String) -> Unit, modifier: Modifier = Modifier) {
    if (gems.isEmpty()) {
        Text(
            "No hidden gems right now. Check back later.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.fillMaxSize().padding(32.dp),
        )
        return
    }
    LazyColumn(modifier.fillMaxSize()) {
        items(gems, key = { "gem-${it.detail.summary.id}" }) { gem -> HiddenGemRow(gem, onOpenSeries) }
    }
}

/** One hidden-gem row: cover, title, rating and follows, and a few tags. */
@Composable
fun HiddenGemRow(gem: HiddenGem, onOpenSeries: (String) -> Unit, modifier: Modifier = Modifier) {
    val summary = gem.detail.summary
    Row(
        modifier.fillMaxWidth().clickable { onOpenSeries(summary.id) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(
            summary.coverUrl,
            summary.title,
            Modifier.width(48.dp).aspectRatio(2f / 3f).clip(MaterialTheme.shapes.extraSmall),
            contentScale = ContentScale.Crop,
            thumb = true,
        )
        Column(Modifier.padding(start = 12.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(summary.title, style = MaterialTheme.typography.titleSmallEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "\u2605 ${"%.1f".format(gem.rating)} \u00b7 ${gem.follows} follows",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val tags = gem.detail.tags.take(3)
            if (tags.isNotEmpty()) {
                Text(
                    tags.joinToString(" \u00b7 "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

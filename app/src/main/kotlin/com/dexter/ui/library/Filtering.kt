package com.dexter.ui.library

import com.dexter.data.LibrarySeriesMeta
import com.dexter.data.SavedSeries
import com.dexter.data.isAdvancedQuery
import com.dexter.data.parseLibraryQuery

/**
 * Keeps the series matching [query] and, when [tagFilter] is not empty, carrying every one of those
 * tags. With [unreadOnly] set, only series with unread chapters stay.
 *
 * A plain query matches titles, as before. A query with `&&`, `||`, `-`, parentheses, or a field
 * prefix (`title:`, `author:`, `genre:`, `status:`, `source:`) runs through the query language
 * instead: `genre:isekai && -status:dropped`. The author, genre, and source fields read [meta],
 * which comes from the series details cached on the device.
 */
fun filterSaved(
    items: List<SavedSeries>,
    query: String,
    unreadOnly: Boolean,
    hasUnread: (SavedSeries) -> Boolean,
    tagFilter: Set<String> = emptySet(),
    seriesTags: (SavedSeries) -> List<String> = { emptyList() },
    meta: (SavedSeries) -> LibrarySeriesMeta? = { null },
): List<SavedSeries> {
    val text = query.trim()
    val parsed = if (isAdvancedQuery(text)) parseLibraryQuery(text) else null
    val needle = text.lowercase()
    return items.filter { series ->
        val matchesQuery = when {
            text.isEmpty() -> true
            parsed != null -> parsed.matches(series, meta(series))
            else -> series.title.lowercase().contains(needle)
        }
        val matchesTags = tagFilter.isEmpty() || tagFilter.all { it in seriesTags(series) }
        matchesQuery && matchesTags && (!unreadOnly || hasUnread(series))
    }
}

package com.dexter.ui.library

import com.dexter.data.SavedSeries

/** Keeps the series whose title contains [query] (ignoring case), and with unread chapters when [unreadOnly] is set. */
fun filterSaved(items: List<SavedSeries>, query: String, unreadOnly: Boolean, hasUnread: (SavedSeries) -> Boolean): List<SavedSeries> {
    val needle = query.trim().lowercase()
    return items.filter { series ->
        (needle.isEmpty() || series.title.lowercase().contains(needle)) && (!unreadOnly || hasUnread(series))
    }
}

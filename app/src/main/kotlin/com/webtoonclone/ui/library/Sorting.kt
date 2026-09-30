package com.webtoonclone.ui.library

import com.webtoonclone.data.SavedSeries

/** The saved order is newest first. Alphabetical sorts by title, ignoring case. */
fun sortSaved(items: List<SavedSeries>, alphabetical: Boolean): List<SavedSeries> =
    if (alphabetical) items.sortedBy { it.title.lowercase() } else items

package com.dexter.ui.library

import com.dexter.data.SavedSeries

/** How My Series lists are ordered. */
enum class LibrarySort(val label: String) {
    Recent("Recent"),
    Alphabetical("A-Z"),
    UnreadFirst("Unread first"),
    Updated("Recently updated"),
    Status("By reading status"),
    ;

    /** The mode a tap on the sort button switches to. */
    fun next(): LibrarySort = entries[(ordinal + 1) % entries.size]
}

/** The saved flags as a sort mode. Unread first wins when both are set. */
fun sortModeOf(alphabetical: Boolean, unreadFirst: Boolean): LibrarySort = when {
    unreadFirst -> LibrarySort.UnreadFirst
    alphabetical -> LibrarySort.Alphabetical
    else -> LibrarySort.Recent
}

/** The saved sort: the named one when set, or the one the older two flags describe. */
fun sortModeOf(name: String?, alphabetical: Boolean, unreadFirst: Boolean): LibrarySort =
    LibrarySort.entries.firstOrNull { it.name == name } ?: sortModeOf(alphabetical, unreadFirst)

/**
 * The saved order is newest first. Alphabetical sorts by title, ignoring case. Unread first puts
 * series with unread chapters ahead, keeping the saved order within each group.
 */
fun sortSaved(items: List<SavedSeries>, mode: LibrarySort, hasUnread: (SavedSeries) -> Boolean = { false }): List<SavedSeries> = when (mode) {
    LibrarySort.Recent -> items
    // Lowercase each title once, not on every comparison.
    LibrarySort.Alphabetical -> items.map { it to it.title.lowercase() }.sortedBy { it.second }.map { it.first }
    LibrarySort.UnreadFirst -> items.sortedByDescending { hasUnread(it) }
    // Newest change first. For subscriptions that is a new chapter, for recent reads the last read.
    LibrarySort.Updated -> items.sortedByDescending { it.at }
    // Reading, Plan to read, Completed, Dropped, then series without a status, each group in its saved order.
    LibrarySort.Status -> items.sortedBy { it.status?.ordinal ?: Int.MAX_VALUE }
}

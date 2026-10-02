package com.dexter.ui.library

import com.dexter.data.SavedSeries

/** How My Series lists are ordered. */
enum class LibrarySort(val label: String) {
    Recent("Recent"),
    Alphabetical("A-Z"),
    UnreadFirst("Unread first"),
    Updated("Recently updated"),
    Status("By reading status"),
    LatestRead("Latest read"),
    UnreadCount("Unread count"),
    DateAdded("Date added"),
    ChapterCount("Chapter count"),
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
 * series with unread chapters ahead, keeping the saved order within each group. Latest read uses
 * [lastReadAt], which falls back to the series' own time when it was never opened here. Unread
 * count puts the biggest backlog first. Date added uses [addedAt], falling back the same way for
 * series saved before the time was kept. Chapter count orders by the newest known chapter number,
 * the closest the library knows to a chapter count.
 */
fun sortSaved(
    items: List<SavedSeries>,
    mode: LibrarySort,
    hasUnread: (SavedSeries) -> Boolean = { false },
    unreadCount: (SavedSeries) -> Int = { if (hasUnread(it)) 1 else 0 },
    lastReadAt: (SavedSeries) -> Long = { it.at },
    addedAt: Map<String, Long> = emptyMap(),
): List<SavedSeries> = when (mode) {
    LibrarySort.Recent -> items
    // Lowercase each title once, not on every comparison.
    LibrarySort.Alphabetical -> items.map { it to it.title.lowercase() }.sortedBy { it.second }.map { it.first }
    LibrarySort.UnreadFirst -> items.sortedByDescending { hasUnread(it) }
    // Newest change first. For subscriptions that is a new chapter, for recent reads the last read.
    LibrarySort.Updated -> items.sortedByDescending { it.at }
    // Reading, Plan to read, Completed, Dropped, then series without a status, each group in its saved order.
    LibrarySort.Status -> items.sortedBy { it.status?.ordinal ?: Int.MAX_VALUE }
    LibrarySort.LatestRead -> items.sortedByDescending(lastReadAt)
    LibrarySort.UnreadCount -> items.sortedByDescending(unreadCount)
    LibrarySort.DateAdded -> items.sortedByDescending { addedAt[it.id] ?: it.at }
    LibrarySort.ChapterCount -> items.sortedByDescending { it.knownChapterNumber?.toDoubleOrNull() ?: 0.0 }
}

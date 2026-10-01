package com.dexter.data

/** A plain text version of your library for sharing: subscriptions, each reading list, and each collection, by title. */
fun libraryText(library: LibraryData): String = buildString {
    fun section(title: String, series: List<SavedSeries>) {
        if (series.isEmpty()) return
        if (isNotEmpty()) append("\n")
        append(title).append(" (").append(series.size).append(")\n")
        series.sortedBy { it.title.lowercase() }.forEach { append("- ").append(it.title).append('\n') }
    }
    section("Subscribed", library.subscribed)
    ReadingStatus.entries.forEach { status -> section(status.label, library.lists.filter { it.status == status }) }
    library.collections.toSortedMap().forEach { (name, series) -> section(name, series) }
}.trimEnd()

package com.dexter.data

/** A plain text version of your library for sharing: subscriptions, each reading list, each collection, and what you read lately, by title. */
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
    // Recent reads come last. Someone who only reads, without subscribing or listing, would otherwise share nothing.
    section("Recently read", library.recent)
}.trimEnd()

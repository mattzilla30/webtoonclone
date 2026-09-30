package com.dexter.ui.library

import com.dexter.data.LibraryData
import com.dexter.ui.series.hasUnreadChapters

/** How many subscribed series have chapters newer than the last one you read. */
fun unreadSeriesCount(library: LibraryData): Int {
    val lastRead = library.recent.associate { it.id to it.chapterNumber }
    return library.subscribed.count { hasUnreadChapters(it.knownChapterNumber, lastRead[it.id]) }
}

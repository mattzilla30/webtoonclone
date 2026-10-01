package com.dexter.ui.series

import com.dexter.data.Chapter

/**
 * True when [number] is at or before the last chapter you opened. Chapter numbers can be
 * fractional ("12.5"), so this compares them as numbers. Unnumbered chapters such as "Oneshot"
 * are never marked read.
 */
fun isChapterRead(number: String, lastReadNumber: String?): Boolean {
    val chapter = number.toDoubleOrNull() ?: return false
    val lastRead = lastReadNumber?.toDoubleOrNull() ?: return false
    return chapter <= lastRead
}

/**
 * True when the newest chapter the app has seen is later than the one you last read. Needs both
 * numbers. A series you never opened has nothing "unread" to point at, so it stays unmarked.
 */
fun hasUnreadChapters(knownNumber: String?, lastReadNumber: String?): Boolean {
    val known = knownNumber?.toDoubleOrNull() ?: return false
    val lastRead = lastReadNumber?.toDoubleOrNull() ?: return false
    return known > lastRead
}

/**
 * For every chapter, keyed by chapter id, the chapter that becomes "last read" when you mark it and
 * everything after it unread: the next older readable chapter. The list is newest first. Built in one
 * pass, so the series page does not scan the whole list for each row on screen.
 */
fun previousReadableMap(chapters: List<Chapter>): Map<String, Chapter?> {
    val result = HashMap<String, Chapter?>(chapters.size * 2)
    var previous: Chapter? = null
    // Oldest first, so the readable chapter seen last is the next older one.
    for (chapter in chapters.asReversed()) {
        result[chapter.id] = previous
        if (chapter.externalUrl == null) previous = chapter
    }
    return result
}

/**
 * How many readable chapters come after the last one you read. The list is newest first, so the
 * unread ones are always among the chapters loaded first. Zero when nothing has been read yet.
 */
fun unreadChapterCount(chapters: List<Chapter>, lastReadNumber: String?): Int {
    val last = lastReadNumber?.toDoubleOrNull() ?: return 0
    return chapters.count { it.externalUrl == null && (it.number.toDoubleOrNull() ?: return@count false) > last }
}

package com.webtoonclone.ui.series

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
 * The chapter that becomes "last read" when you mark [chapter] and everything after it unread: the
 * next older readable chapter. The list is newest first, so older chapters come later in it.
 */
fun previousReadable(chapters: List<com.webtoonclone.data.Chapter>, chapter: com.webtoonclone.data.Chapter): com.webtoonclone.data.Chapter? =
    chapters.dropWhile { it.id != chapter.id }.drop(1).firstOrNull { it.externalUrl == null }

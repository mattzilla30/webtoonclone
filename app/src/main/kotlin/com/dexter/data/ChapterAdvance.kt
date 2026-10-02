package com.dexter.data

/**
 * The index of the chapter after [index] in [chapters], oldest first. With [skipRead] on, chapters at
 * or below [lastReadNumber] are skipped, so advancing never lands on one you already read. Returns
 * [chapters].size when nothing follows. A chapter whose number does not parse is never skipped, so it
 * is always offered.
 */
fun nextChapterIndex(chapters: List<Chapter>, index: Int, lastReadNumber: String?, skipRead: Boolean): Int {
    if (!skipRead) return index + 1
    val last = lastReadNumber?.toDoubleOrNull() ?: return index + 1
    var next = index + 1
    while (next < chapters.size && (chapters[next].number.toDoubleOrNull() ?: Double.MAX_VALUE) <= last) next++
    return next
}

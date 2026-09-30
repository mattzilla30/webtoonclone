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

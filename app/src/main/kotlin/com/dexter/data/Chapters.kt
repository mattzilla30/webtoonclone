package com.dexter.data

/**
 * Picks the upload to show when several groups uploaded the same chapter. The preferred group wins
 * when it uploaded one. Otherwise an upload that opens in the reader beats one that only links out,
 * and the first listed wins a tie. The others stay on the result as [Chapter.alternates].
 */
fun pickUpload(uploads: List<Chapter>, preferredGroup: String?): Chapter {
    val primary = uploads.firstOrNull { preferredGroup != null && it.group == preferredGroup && it.externalUrl == null }
        ?: uploads.firstOrNull { it.externalUrl == null }
        ?: uploads.first()
    return primary.copy(alternates = uploads.filter { it.id != primary.id })
}

/** A chapter list entry, or the heading that starts a volume. */
sealed interface ChapterListItem {
    data class VolumeHeader(val label: String) : ChapterListItem

    data class Entry(val chapter: Chapter) : ChapterListItem
}

/**
 * Inserts a heading wherever the volume changes. [chapters] keeps its order, so a newest-first list
 * gets the newest volume first. A list where no chapter has a volume gets no headings.
 */
fun groupByVolume(chapters: List<Chapter>): List<ChapterListItem> {
    if (chapters.none { it.volume != null }) return chapters.map { ChapterListItem.Entry(it) }
    val items = mutableListOf<ChapterListItem>()
    var current: String? = "\u0000"
    for (chapter in chapters) {
        if (chapter.volume != current) {
            current = chapter.volume
            items += ChapterListItem.VolumeHeader(chapter.volume?.let { "Volume $it" } ?: "No volume")
        }
        items += ChapterListItem.Entry(chapter)
    }
    return items
}

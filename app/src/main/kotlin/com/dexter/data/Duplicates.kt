package com.dexter.data

import com.dexter.data.db.DownloadEntity
import java.text.Normalizer

// Cross-source duplicate detection. Titles are normalised aggressively (case, punctuation, leading
// articles, diacritics) so "Solo Leveling", "solo-leveling", and "The Solo Leveling" all match, and
// downloads are matched on series plus chapter number.

/** "The Solo-Leveling!" becomes "solo leveling". Letters and digits survive; scripts are kept. */
fun normalizeTitle(title: String): String {
    val folded = Normalizer.normalize(title, Normalizer.Form.NFKD).replace(Regex("\\p{Mn}+"), "")
    return folded.lowercase()
        .replace(Regex("^(the|a|an)\\s+"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")
}

/** "012" and "12" are the same chapter; "12.5" stays distinct from "12". */
fun normalizeChapterNumber(number: String): String {
    val trimmed = number.trim().lowercase()
    return trimmed.toDoubleOrNull()?.let {
        if (it == kotlin.math.floor(it)) it.toLong().toString() else it.toString()
    } ?: trimmed
}

/** The same series saved more than once, each entry being a candidate copy. */
data class DuplicateGroup(val key: String, val series: List<SavedSeries>)

/**
 * Series whose normalised titles match but whose ids differ: the same story added twice, for
 * example after a re-add or from another source. Groups are sorted largest first.
 */
fun findDuplicateSeries(all: List<SavedSeries>): List<DuplicateGroup> =
    all.groupBy { normalizeTitle(it.title) }
        .mapNotNull { (key, list) ->
            val distinct = list.distinctBy { it.id }
            if (key.isNotBlank() && distinct.size > 1) DuplicateGroup(key, distinct) else null
        }
        .sortedByDescending { it.series.size }

/**
 * Which copy of a duplicate group to keep: the one with the most reading progress recorded, then
 * the earliest added (the original). The others are safe to remove.
 */
fun suggestedKeep(group: DuplicateGroup): SavedSeries =
    group.series.maxWithOrNull(
        compareBy<SavedSeries> { it.knownChapterNumber != null }
            .thenBy { it.chapterNumber != null }
            .thenBy { it.coverUrl != null }
            // Negated so the earliest added (the original copy) wins the tie-break.
            .thenBy { -it.at },
    ) ?: group.series.first()

/** One chapter number saved more than once for the same series. */
data class DuplicateChapters(val seriesId: String, val seriesTitle: String, val number: String, val chapterIds: List<String>)

/** Downloaded chapters that repeat a series plus chapter number, sorted by wasted bytes. */
fun findDuplicateDownloads(downloads: List<DownloadEntity>): List<DuplicateChapters> =
    downloads.groupBy { it.seriesId to normalizeChapterNumber(it.number) }
        .mapNotNull { (key, list) ->
            val ids = list.map { it.chapterId }.distinct()
            if (ids.size > 1) DuplicateChapters(key.first, list.first().seriesTitle, list.first().number, ids) else null
        }
        .sortedByDescending { group -> downloads.filter { it.chapterId in group.chapterIds }.sumOf { it.bytes } }

package com.dexter.data

import com.dexter.data.db.DownloadEntity
import java.io.File

// Storage analysis for the downloads directory. Sizes come from the chapter folders on disk, so the
// numbers match what the system storage screen shows; the database rows only supply titles.

/** One downloaded chapter's footprint. */
data class ChapterStorage(val chapterId: String, val number: String, val bytes: Long)

/** One series' footprint, with its largest chapters first. */
data class SeriesStorage(
    val seriesId: String,
    val seriesTitle: String,
    val bytes: Long,
    val chapters: List<ChapterStorage>,
)

/** The whole downloads directory, series sorted largest first. */
data class StorageReport(val series: List<SeriesStorage>, val totalBytes: Long)

/** A cleanup action the report suggests, with how much it would free. */
data class CleanupSuggestion(val title: String, val detail: String, val bytes: Long)

/**
 * Walks [downloadsRoot] (one folder per chapter id, as [DownloadStore] lays it out) and attributes
 * every folder to a series via [rows]. Folders with no matching row land in an "Unknown" bucket so
 * orphaned data still shows up. Runs on the caller; call from IO.
 */
fun analyzeStorage(downloadsRoot: File, rows: List<DownloadEntity>): StorageReport {
    val byChapter = rows.associateBy { it.chapterId }
    val chapterSizes = HashMap<String, Long>()
    downloadsRoot.listFiles()?.forEach { dir ->
        if (dir.isDirectory) chapterSizes[dir.name] = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
    val bySeries = HashMap<String, MutableList<ChapterStorage>>()
    val titles = HashMap<String, String>()
    for ((chapterId, bytes) in chapterSizes) {
        val row = byChapter[chapterId]
        val seriesId = row?.seriesId ?: "unknown"
        titles[seriesId] = row?.seriesTitle ?: titles[seriesId] ?: "Unknown"
        bySeries.getOrPut(seriesId) { ArrayList() } += ChapterStorage(chapterId, row?.number ?: "?", bytes)
    }
    val series = bySeries.map { (seriesId, chapters) ->
        val sorted = chapters.sortedByDescending { it.bytes }
        SeriesStorage(seriesId, titles[seriesId] ?: "Unknown", sorted.sumOf { it.bytes }, sorted)
    }.sortedByDescending { it.bytes }
    return StorageReport(series, series.sumOf { it.bytes })
}

/**
 * Cleanup suggestions from a [StorageReport]: finished series hoarding space, the single largest
 * chapters, and orphaned folders. [finishedSeriesIds] are series marked Completed in the library.
 */
fun StorageReport.suggestions(finishedSeriesIds: Set<String>): List<CleanupSuggestion> {
    val out = ArrayList<CleanupSuggestion>()
    val finished = series.filter { it.seriesId in finishedSeriesIds && it.bytes > 0 }
    if (finished.isNotEmpty()) {
        val bytes = finished.sumOf { it.bytes }
        out += CleanupSuggestion(
            title = "Finished series",
            detail = "${finished.size} completed ${if (finished.size == 1) "series uses" else "series use"} ${formatBytes(bytes)} and can be removed or exported to CBZ.",
            bytes = bytes,
        )
    }
    series.flatMap { s -> s.chapters.take(3).map { it to s } }
        .sortedByDescending { (chapter, _) -> chapter.bytes }
        .take(5)
        .filter { (chapter, _) -> chapter.bytes > 50L * 1024 * 1024 }
        .forEach { (chapter, s) ->
            out += CleanupSuggestion(
                title = "Large chapter",
                detail = "${s.seriesTitle} ch. ${chapter.number} uses ${formatBytes(chapter.bytes)}.",
                bytes = chapter.bytes,
            )
        }
    series.firstOrNull { it.seriesId == "unknown" }?.let {
        if (it.bytes > 0) out += CleanupSuggestion(
            title = "Orphaned data",
            detail = "${formatBytes(it.bytes)} with no matching download entry. Safe to delete.",
            bytes = it.bytes,
        )
    }
    return out.sortedByDescending { it.bytes }
}

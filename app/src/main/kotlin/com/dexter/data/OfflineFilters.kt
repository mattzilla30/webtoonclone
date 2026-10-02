package com.dexter.data

/**
 * The series in [series] with at least one saved chapter, for offline-only library views.
 * [savedSeriesIds] comes from [DownloadStore.savedSeriesIds].
 */
fun offlineSeries(series: List<SavedSeries>, savedSeriesIds: Set<String>): List<SavedSeries> =
    series.filter { it.id in savedSeriesIds }

/** The chapters in [chapters] that are saved on the device, for offline-only chapter lists. */
fun offlineChapters(chapters: List<Chapter>, savedChapterIds: Set<String>): List<Chapter> =
    chapters.filter { it.id in savedChapterIds }

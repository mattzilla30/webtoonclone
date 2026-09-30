package com.webtoonclone.data

import kotlinx.serialization.Serializable

data class SeriesSummary(
    val id: String,
    val title: String,
    val coverUrl: String?,
    val genre: String? = null,
    val author: String? = null,
    val description: String = "",
    val follows: Int? = null,
)

data class SeriesDetail(
    val summary: SeriesSummary,
    val status: String,
    val tags: List<String>,
    val rating: Double?,
)

/** A series with its newest chapter, for the Updates tab. */
data class UpdateEntry(val series: SeriesSummary, val chapterNumber: String, val publishedAt: String)

data class ChapterPage(val chapters: List<Chapter>, val nextOffset: Int?)

data class Chapter(
    val id: String,
    val number: String,
    val title: String,
    val publishedAt: String,
    /** Set when the publisher hosts the chapter. The app opens it in the browser. */
    val externalUrl: String? = null,
)

data class ReadingProgress(
    val chapterId: String,
    val page: Int,
)

data class HomeContent(
    val hero: SeriesSummary?,
    val newSeries: List<SeriesSummary>,
    val picks: List<SeriesSummary>,
)

/** A series saved on this device, either as recent history or as a subscription. */
@Serializable
data class SavedSeries(
    val id: String,
    val title: String,
    val coverUrl: String? = null,
    val chapterId: String? = null,
    val chapterNumber: String? = null,
    val at: Long = 0,
    /** For subscriptions: the newest chapter seen so far. A newer one triggers a notification. */
    val knownChapterId: String? = null,
)

@Serializable
data class LibraryData(
    val recent: List<SavedSeries> = emptyList(),
    val subscribed: List<SavedSeries> = emptyList(),
    val searches: List<String> = emptyList(),
    /** When off, the background check still tracks new chapters but posts no notification. */
    val notificationsEnabled: Boolean = true,
    /** My Series sorts A-Z when true, newest first when false. Kept between launches. */
    val sortAlphabetical: Boolean = false,
    /** The last search sort chosen, as an [Order] name. */
    val searchOrder: String = "Popular",
) {
    /** A saved copy of this series, if you have read or subscribed to it before. */
    fun knownSeries(id: String): SavedSeries? = (recent + subscribed).firstOrNull { it.id == id }
}

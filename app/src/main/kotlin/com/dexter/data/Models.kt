package com.dexter.data

import kotlinx.serialization.Serializable

@Serializable
data class SeriesSummary(
    val id: String,
    val title: String,
    val coverUrl: String?,
    val genre: String? = null,
    val author: String? = null,
    val description: String = "",
    val follows: Int? = null,
    /** MangaDex id of the first author, for the author page. */
    val authorId: String? = null,
)

@Serializable
data class SeriesDetail(
    val summary: SeriesSummary,
    val status: String,
    val tags: List<String>,
    val rating: Double?,
    val altTitles: List<String> = emptyList(),
    val year: Int? = null,
    val demographic: String? = null,
    /** MangaDex language code of the original work, such as "ja". */
    val originalLanguage: String = "",
    val links: List<SeriesLink> = emptyList(),
    /** Related series: sequels, prequels, spin-offs, and so on. */
    val relations: List<SeriesRelation> = emptyList(),
    /** How many people gave each score from 1 to 10. Empty when there are no ratings. */
    val ratingDistribution: Map<Int, Int> = emptyMap(),
)

/** A related series and how it relates, with [kind] as MangaDex names it, such as "spin_off". */
@Serializable
data class SeriesRelation(val id: String, val kind: String)

/** One of a series' covers. [volume] is null for covers that belong to no volume. */
@Serializable
data class SeriesCover(val url: String, val volume: String?)

/** A series with its newest chapter, for the Updates tab. */
@Serializable
data class UpdateEntry(val series: SeriesSummary, val chapterNumber: String, val publishedAt: String)

data class ChapterPage(val chapters: List<Chapter>, val nextOffset: Int?)

@Serializable
data class Chapter(
    val id: String,
    val number: String,
    val title: String,
    val publishedAt: String,
    /** Set when the publisher hosts the chapter. The app opens it in the browser. */
    val externalUrl: String? = null,
    /** The scanlation group that uploaded it, when MangaDex names one. */
    val group: String? = null,
    val volume: String? = null,
    /** Other uploads of the same chapter by other groups. The primary one is this chapter. */
    val alternates: List<Chapter> = emptyList(),
)

data class ReadingProgress(
    val chapterId: String,
    val page: Int,
)

/** Home content saved on the device, with the time it was saved. */
data class CachedHome(val content: HomeContent, val savedAt: Long)

@Serializable
data class HomeContent(
    val hero: SeriesSummary?,
    val newSeries: List<SeriesSummary>,
    val picks: List<SeriesSummary>,
)

/** A search you kept under a name: the words or tag, the filters, and the sort. */
@Serializable
data class SavedSearch(
    val name: String,
    val title: String? = null,
    val tag: String? = null,
    val filters: SearchFilters = SearchFilters(),
    val order: String = "Popular",
)

/** An author or artist you follow. [knownIds] are the series already seen, so only later ones notify. */
@Serializable
data class FollowedAuthor(val id: String, val name: String, val knownIds: List<String> = emptyList())

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
    /** The number of that chapter, so unread chapters can be counted by number. */
    val knownChapterNumber: String? = null,
    /** Whether new chapters of this subscribed series notify. */
    val notify: Boolean = true,
    /** Where the series sits in your reading lists, when you put it in one. */
    val status: ReadingStatus? = null,
)

/** The reading lists a series can be in. */
@Serializable
enum class ReadingStatus(val label: String) {
    Reading("Reading"),
    PlanToRead("Plan to read"),
    Completed("Completed"),
    Dropped("Dropped"),
}

/** The lists of saved series the library keeps. [key] names the list in the database. */
enum class LibraryList(val key: String) {
    Recent("recent"),
    Subscribed("subscribed"),
    Lists("lists"),
}

@Serializable
data class LibraryData(
    val recent: List<SavedSeries> = emptyList(),
    val subscribed: List<SavedSeries> = emptyList(),
    val searches: List<String> = emptyList(),
    /** When off, the background check still tracks new chapters but posts no notification. */
    val notificationsEnabled: Boolean = true,
    /** My Series sorts A-Z when true, newest first when false. Kept between launches. */
    val sortAlphabetical: Boolean = false,
    /** My Series puts series with unread chapters first when true. Wins over [sortAlphabetical]. */
    val sortUnreadFirst: Boolean = false,
    /** The last search sort chosen, as an [Order] name. */
    val searchOrder: String = "Popular",
    /** Series you put in reading lists, each with its status. */
    val lists: List<SavedSeries> = emptyList(),
    /** Searches you saved by name. */
    val savedSearches: List<SavedSearch> = emptyList(),
    /** Authors and artists whose new series notify. */
    val followedAuthors: List<FollowedAuthor> = emptyList(),
    /** Your own named collections, each a list of series. */
    val collections: Map<String, List<SavedSeries>> = emptyMap(),
    /** True once the lists moved from the old single file into the database. */
    val roomMigrated: Boolean = false,
) {
    /** A saved copy of this series, if you have read, subscribed to, or listed it before. */
    fun knownSeries(id: String): SavedSeries? = (recent + subscribed + lists + collections.values.flatten()).firstOrNull { it.id == id }
}

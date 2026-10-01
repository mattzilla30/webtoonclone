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
    /** The year it started, when MangaDex knows it. */
    val year: Int? = null,
)

/** An author or artist found by name. */
data class AuthorSummary(val id: String, val name: String)

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
data class UpdateEntry(
    val series: SeriesSummary,
    val chapterNumber: String,
    val publishedAt: String,
    /** The chapter itself, so a long press can open it. Empty in copies saved before it was kept. */
    val chapterId: String = "",
    val chapterTitle: String = "",
    /** The scanlation group, when MangaDex names one. */
    val group: String? = null,
)

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
    /** How far down [page] you were, from 0 to 1. Tall webtoon pages need it to resume in place. */
    val fraction: Float = 0f,
    /** Pages in the chapter, or 0 when the position was saved before the app kept it. */
    val total: Int = 0,
) {
    /** How far through the chapter you are, from 0 to 1, or null when the page count is unknown. */
    val share: Float? get() = if (total > 0) ((page + fraction) / total).coerceIn(0f, 1f) else null
}

/** Reads a saved position: "chapterId:page", "chapterId:page:permille", or "chapterId:page:permille:total". */
fun parseProgress(raw: String): ReadingProgress {
    val parts = raw.split(":")
    return ReadingProgress(
        chapterId = parts[0],
        page = parts.getOrNull(1)?.toIntOrNull() ?: 0,
        fraction = (parts.getOrNull(2)?.toIntOrNull() ?: 0).coerceIn(0, 999) / 1000f,
        total = parts.getOrNull(3)?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
    )
}

/** The saved form of a position, read back by [parseProgress]. */
fun formatProgress(chapterId: String, page: Int, fraction: Float, total: Int = 0): String {
    val permille = (fraction * 1000).toInt().coerceIn(0, 999)
    return when {
        total > 0 -> "$chapterId:$page:$permille:$total"
        permille == 0 -> "$chapterId:$page"
        else -> "$chapterId:$page:$permille"
    }
}

/**
 * About how many chapters came out after the one you read: the gap between the two chapter numbers, when
 * both are whole numbers. Null when either is unknown or fractional, since 12.5 could be one chapter or none.
 */
fun newChapterEstimate(knownNumber: String?, lastReadNumber: String?): Int? {
    val known = knownNumber?.toDoubleOrNull() ?: return null
    val last = lastReadNumber?.toDoubleOrNull() ?: return null
    if (known != Math.floor(known) || last != Math.floor(last)) return null
    return (known - last).toInt().takeIf { it > 0 }
}

/** Home content saved on the device, with the time it was saved. */
data class CachedHome(val content: HomeContent, val savedAt: Long)

@Serializable
data class HomeContent(
    val hero: SeriesSummary?,
    val newSeries: List<SeriesSummary>,
    val picks: List<SeriesSummary>,
)

/** One bookmarked page. [page] counts from 0. */
@Serializable
data class Bookmark(
    val seriesId: String,
    val seriesTitle: String,
    val chapterId: String,
    val chapterNumber: String,
    val page: Int,
    val at: Long = 0,
)

/** A search you kept under a name: the words or tag, the filters, and the sort. */
@Serializable
data class SavedSearch(
    val name: String,
    val title: String? = null,
    val tag: String? = null,
    val filters: SearchFilters = SearchFilters(),
    val order: String = "Popular",
    /** Whether a new series matching this search notifies, as a followed author does. */
    val notify: Boolean = false,
    /** Series already seen for this search, so only later ones notify. */
    val knownIds: List<String> = emptyList(),
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
    /** The newest upload, in any language, the background check saw for each subscribed series. */
    val uploadMarks: Map<String, String> = emptyMap(),
    /** When the background check last read every subscribed series' feed. */
    val fullCheckAt: Long = 0,
    /** When the background check last finished, for Settings to show. */
    val lastCheckAt: Long = 0,
    /** My Series shows covers in a grid instead of rows. */
    val libraryGrid: Boolean = false,
    /** The My Series sort, as a LibrarySort name. Null falls back to the two older sort flags. */
    val librarySort: String? = null,
    /** Your own note on a series, by series id. */
    val notes: Map<String, String> = emptyMap(),
    /** Pages you bookmarked, newest first. */
    val bookmarks: List<Bookmark> = emptyList(),
) {
    /** A saved copy of this series, if you have read, subscribed to, or listed it before. */
    fun knownSeries(id: String): SavedSeries? =
        recent.firstOrNull { it.id == id }
            ?: subscribed.firstOrNull { it.id == id }
            ?: lists.firstOrNull { it.id == id }
            ?: collections.values.firstNotNullOfOrNull { members -> members.firstOrNull { it.id == id } }
}

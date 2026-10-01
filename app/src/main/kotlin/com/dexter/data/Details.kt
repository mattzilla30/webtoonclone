package com.dexter.data

import kotlinx.serialization.Serializable

/** A link to a series on another site. */
@Serializable
data class SeriesLink(val label: String, val url: String)

/**
 * Other names for a series, from its main title and every alternate, without repeats and without
 * the title already shown. At most [limit] are returned.
 */
fun alternateTitles(
    main: Map<String, String>,
    altTitles: List<Map<String, String>>,
    shown: String,
    limit: Int = 6,
): List<String> =
    (main.values + altTitles.flatMap { it.values })
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.equals(shown, ignoreCase = true) }
        .distinct()
        .take(limit)

/** Links MangaDex stores as ids or addresses: AniList, MyAnimeList, and the official English release. */
fun buildLinks(links: Map<String, String>?): List<SeriesLink> = buildList {
    links?.get("al")?.takeIf { it.isNotBlank() }?.let { add(SeriesLink("AniList", "https://anilist.co/manga/$it")) }
    links?.get("mal")?.takeIf { it.isNotBlank() }?.let { add(SeriesLink("MyAnimeList", "https://myanimelist.net/manga/$it")) }
    links?.get("engtl")?.takeIf { it.startsWith("http") }?.let { add(SeriesLink("Official English release", it)) }
}

/** A readable name for a MangaDex language code. Codes the app does not list are shown in capitals. */
fun languageName(code: String): String = when {
    code.isEmpty() -> ""
    else -> Languages.firstOrNull { it.code.equals(code, ignoreCase = true) }?.name ?: code.uppercase()
}

/** "shounen" becomes "Shounen". */
fun demographicLabel(code: String?): String? = code?.takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercase() }

/** A reader-friendly name for a MangaDex relation kind, such as "spin_off" to "Spin-off". */
fun relationLabel(kind: String): String = when (kind) {
    "sequel" -> "Sequel"
    "prequel" -> "Prequel"
    "spin_off" -> "Spin-off"
    "side_story" -> "Side story"
    "main_story" -> "Main story"
    "adapted_from" -> "Adapted from"
    "based_on" -> "Based on"
    "colored" -> "Colored"
    "monochrome" -> "Black and white"
    "preserialization" -> "Preserialization"
    "serialization" -> "Serialization"
    "same_franchise" -> "Same franchise"
    "shared_universe" -> "Shared universe"
    "alternate_story" -> "Alternate story"
    "doujinshi" -> "Doujinshi"
    else -> kind.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

/** Turns MangaDex's score counts ("1" to "10") into counts keyed by score, ignoring anything else. */
fun ratingCounts(distribution: Map<String, Int>): Map<Int, Int> =
    distribution.mapNotNull { (score, count) -> score.toIntOrNull()?.takeIf { it in 1..10 }?.let { it to count } }.toMap().toSortedMap(reverseOrder())

/** A short line of the main facts about a series: status, year, demographic, and original language. */
fun factsLine(detail: SeriesDetail): String = listOfNotNull(
    detail.status.takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercase() },
    detail.year?.toString(),
    detail.demographic,
    languageName(detail.originalLanguage).takeIf { it.isNotBlank() },
).joinToString(" \u00b7 ")

/**
 * When the next chapter is likely out: the latest release plus the usual gap between recent releases (the
 * middle value of the last few gaps). Null for a series that is not ongoing, has too few dated chapters, or
 * releases too unevenly to guess.
 */
fun nextChapterEstimate(publishedAt: List<String>, status: String, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): java.time.LocalDate? {
    if (!status.equals("ongoing", ignoreCase = true)) return null
    val days = publishedAt
        .mapNotNull { runCatching { java.time.OffsetDateTime.parse(it).atZoneSameInstant(zone).toLocalDate() }.getOrNull() }
        .distinct()
        .sortedDescending()
        .take(ESTIMATE_RELEASES)
    if (days.size < 3) return null
    val gaps = days.zipWithNext { newer, older -> java.time.temporal.ChronoUnit.DAYS.between(older, newer) }.sorted()
    val usual = gaps[gaps.size / 2]
    if (usual < 1 || usual > MAX_USUAL_GAP_DAYS) return null
    return days.first().plusDays(usual)
}

/** How many recent release days the estimate reads, and the longest usual gap it still trusts. */
private const val ESTIMATE_RELEASES = 8
private const val MAX_USUAL_GAP_DAYS = 120L

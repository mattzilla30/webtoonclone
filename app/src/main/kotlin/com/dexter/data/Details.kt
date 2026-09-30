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

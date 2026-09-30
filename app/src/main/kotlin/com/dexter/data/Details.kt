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

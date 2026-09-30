package com.dexter.data

import kotlinx.serialization.Serializable

/** The publication statuses MangaDex knows, and the demographics and original languages worth filtering by. */
val StatusOptions = listOf("ongoing", "completed", "hiatus", "cancelled")
val DemographicOptions = listOf("shounen", "shoujo", "seinen", "josei")
val OriginalLanguageOptions = listOf("ja", "ko", "zh", "en")

/** Everything the advanced search can narrow by. Empty lists and a null year mean no limit. */
@Serializable
data class SearchFilters(
    val included: List<String> = emptyList(),
    val excluded: List<String> = emptyList(),
    val status: List<String> = emptyList(),
    val demographics: List<String> = emptyList(),
    val originalLanguages: List<String> = emptyList(),
    val year: Int? = null,
    /** True needs every included tag. False needs any one of them. */
    val matchAll: Boolean = true,
) {
    val isEmpty: Boolean get() = activeCount == 0

    /** How many separate limits are set, for a "Filters (3)" label. */
    val activeCount: Int
        get() = included.size + excluded.size + status.size + demographics.size + originalLanguages.size + (if (year != null) 1 else 0)

    /** Moves a tag through off, included, excluded, and back to off. */
    fun cycleTag(name: String): SearchFilters = when (name) {
        in included -> copy(included = included - name, excluded = excluded + name)
        in excluded -> copy(excluded = excluded - name)
        else -> copy(included = included + name)
    }

    /** Adds [value] to a list, or removes it if already there. */
    fun toggle(list: List<String>, value: String): List<String> = if (value in list) list - value else list + value
}

/**
 * The tags to search by when looking for series like one with [tags]. Up to two genres come first,
 * since they describe a series best, and themes fill any gap.
 */
fun similarTags(tags: List<String>): List<String> {
    val genreNames = Genres.map { it.name }.toSet()
    val genres = tags.filter { it in genreNames }.take(2)
    val themes = tags.filter { it in Themes }.take(2 - genres.size)
    return genres + themes
}

/** The tags to exclude from a request: its own exclusions plus your blocked tags, minus any tag the request includes on purpose. */
fun effectiveExcluded(excluded: List<String>, blocked: Set<String>, included: List<String>): List<String> {
    val wanted = included.map { it.lowercase() }.toSet()
    return (excluded + blocked).distinctBy { it.lowercase() }.filter { it.lowercase() !in wanted || it in excluded }
}

/** One active limit shown as a removable chip: what to show, and the filters left after removing it. */
data class ActiveFilter(val label: String, val without: SearchFilters)

/** Every limit set in [filters] as a chip, so each can be removed on its own. */
fun activeFilters(filters: SearchFilters): List<ActiveFilter> = buildList {
    filters.included.forEach { add(ActiveFilter("+ $it", filters.copy(included = filters.included - it))) }
    filters.excluded.forEach { add(ActiveFilter("\u2212 $it", filters.copy(excluded = filters.excluded - it))) }
    filters.status.forEach { add(ActiveFilter(it.replaceFirstChar { c -> c.uppercase() }, filters.copy(status = filters.status - it))) }
    filters.demographics.forEach { add(ActiveFilter(it.replaceFirstChar { c -> c.uppercase() }, filters.copy(demographics = filters.demographics - it))) }
    filters.originalLanguages.forEach { add(ActiveFilter(languageName(it), filters.copy(originalLanguages = filters.originalLanguages - it))) }
    filters.year?.let { add(ActiveFilter(it.toString(), filters.copy(year = null))) }
}

package com.dexter.ui.search

import com.dexter.data.ContentTags
import com.dexter.data.Formats
import com.dexter.data.Genres
import com.dexter.data.SeriesSummary
import com.dexter.data.SuggestiveTags
import com.dexter.data.Themes

private const val MIN_SUGGEST_LENGTH = 2

/** Suggestions start at two characters, since one letter matches almost everything. */
fun shouldSuggest(text: String): Boolean = text.trim().length >= MIN_SUGGEST_LENGTH

/** Every tag name the search box can treat as a theme, across every tag group. */
private val themeNames: List<String> by lazy {
    Genres.map { it.name } + Themes + Formats + ContentTags + SuggestiveTags
}

/**
 * The tag whose name [term] is, when it names one: "isekai" is the Isekai genre, "vampires" a theme.
 * Lets a keyword search match themes, not just titles.
 */
fun themeTagFor(term: String): String? = themeNames.firstOrNull { it.equals(term.trim(), ignoreCase = true) }

/** Theme matches first, then title matches, without repeats. */
fun mergeSearchResults(tagged: List<SeriesSummary>, titled: List<SeriesSummary>): List<SeriesSummary> {
    val seen = tagged.mapTo(HashSet()) { it.id }
    return tagged + titled.filter { it.id !in seen }
}

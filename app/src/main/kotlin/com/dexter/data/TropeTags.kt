package com.dexter.data

/**
 * One trope tag for fine-grained discovery: "overpowered MC", "enemies to lovers", and so on.
 * [matchTags] names the MangaDex tags that imply the trope; [matchGenres] names matching genres.
 * A series earns the trope when any of its tags or genres hits either set.
 */
data class TropeTag(
    /** Stable id for persistence, e.g. "overpowered-mc". */
    val id: String,
    val label: String,
    val category: String,
    val matchTags: Set<String> = emptySet(),
    val matchGenres: Set<String> = emptySet(),
)

/** Trope categories, for grouping the picker. */
object TropeCategories {
    const val PROTAGONIST = "Protagonist"
    const val ROMANCE = "Romance"
    const val STORY = "Story"
    const val SETTING = "Setting"
}

/**
 * The local trope taxonomy, mapped from MangaDex tag and genre names. MangaDex tag names must
 * match exactly (see [Themes], [Genres]); tropes with no direct tag map from a genre instead.
 */
val TropeTags = listOf(
    TropeTag(
        "overpowered-mc", "Overpowered MC", TropeCategories.PROTAGONIST,
        matchTags = setOf("Overpowered Protagonist"),
        matchGenres = setOf("Isekai", "Fantasy", "Action"),
    ),
    TropeTag(
        "weak-to-strong", "Weak to strong", TropeCategories.PROTAGONIST,
        matchTags = setOf("Weak to Strong"),
    ),
    TropeTag(
        "villainess", "Villainess", TropeCategories.PROTAGONIST,
        matchTags = setOf("Villainess"),
    ),
    TropeTag(
        "reincarnation", "Reincarnation", TropeCategories.PROTAGONIST,
        matchTags = setOf("Reincarnation"),
    ),
    TropeTag(
        "second-chance", "Second chance", TropeCategories.PROTAGONIST,
        matchTags = setOf("Time Travel", "Regression"),
    ),
    TropeTag(
        "enemies-to-lovers", "Enemies to lovers", TropeCategories.ROMANCE,
        matchTags = setOf("Enemies to Lovers"),
        matchGenres = setOf("Romance"),
    ),
    TropeTag(
        "harem", "Harem", TropeCategories.ROMANCE,
        matchTags = setOf("Harem", "Reverse Harem"),
    ),
    TropeTag(
        "love-triangle", "Love triangle", TropeCategories.ROMANCE,
        matchTags = setOf("Love Triangle"),
    ),
    TropeTag(
        "slow-burn", "Slow burn", TropeCategories.ROMANCE,
        matchTags = setOf("Slow Burn"),
        matchGenres = setOf("Romance", "Drama", "Slice of Life"),
    ),
    TropeTag(
        "tower-climb", "Tower climbing", TropeCategories.STORY,
        matchTags = setOf("Tower"),
    ),
    TropeTag(
        "dungeon-crawl", "Dungeons", TropeCategories.STORY,
        matchTags = setOf("Dungeons"),
    ),
    TropeTag(
        "system-levels", "System / levels", TropeCategories.STORY,
        matchTags = setOf("Video Games", "Virtual Reality"),
    ),
    TropeTag(
        "survival-game", "Survival game", TropeCategories.STORY,
        matchTags = setOf("Survival"),
        matchGenres = setOf("Thriller", "Horror"),
    ),
    TropeTag(
        "revenge", "Revenge", TropeCategories.STORY,
        matchTags = setOf("Revenge"),
    ),
    TropeTag(
        "post-apocalyptic", "Post-apocalyptic", TropeCategories.SETTING,
        matchTags = setOf("Post-Apocalyptic"),
    ),
    TropeTag(
        "school-life", "School life", TropeCategories.SETTING,
        matchTags = setOf("School Life"),
        matchGenres = setOf("Slice of Life"),
    ),
    TropeTag(
        "office-romance", "Office setting", TropeCategories.SETTING,
        matchTags = setOf("Office Workers"),
    ),
    TropeTag(
        "supernatural", "Supernatural", TropeCategories.SETTING,
        matchTags = setOf("Supernatural", "Ghosts", "Demons"),
        matchGenres = setOf("Horror"),
    ),
    TropeTag(
        "martial-arts", "Martial arts", TropeCategories.SETTING,
        matchTags = setOf("Martial Arts"),
        matchGenres = setOf("Wuxia"),
    ),
    TropeTag(
        "cooking", "Cooking", TropeCategories.SETTING,
        matchTags = setOf("Cooking"),
    ),
)

/** Trope ids by id, for restoring persisted picks. */
val TropeTagsById: Map<String, TropeTag> = TropeTags.associateBy { it.id }

/**
 * The tropes a series earns from its MangaDex [tags] and [genres]. Matching is case-insensitive on
 * both sides, so "isekai" and "Isekai" both hit.
 */
fun tropesForSeries(tags: List<String>, genres: List<String>): List<TropeTag> {
    val tagSet = tags.map { it.lowercase() }.toSet()
    val genreSet = genres.map { it.lowercase() }.toSet()
    return TropeTags.filter { trope ->
        trope.matchTags.any { it.lowercase() in tagSet } ||
            trope.matchGenres.any { it.lowercase() in genreSet }
    }
}

/** True when a series with [tags] and [genres] earns every trope in [tropeIds]. */
fun seriesMatchesTropes(tags: List<String>, genres: List<String>, tropeIds: Set<String>): Boolean {
    if (tropeIds.isEmpty()) return true
    val earned = tropesForSeries(tags, genres).mapTo(HashSet()) { it.id }
    return tropeIds.all { it in earned }
}

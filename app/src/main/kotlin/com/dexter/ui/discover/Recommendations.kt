package com.dexter.ui.discover

/**
 * What the user's library says they like: how much each genre, tag, and author shows up, weighted
 * by how recently and how often the series behind them were read. Built on the device from the
 * library and reading history. Nothing about it leaves the phone.
 */
data class TasteProfile(
    val genres: Map<String, Double> = emptyMap(),
    val tags: Map<String, Double> = emptyMap(),
    val authors: Map<String, Double> = emptyMap(),
) {
    /** True when the library gave the profile nothing to work with, such as on a fresh install. */
    fun isEmpty(): Boolean = genres.isEmpty() && tags.isEmpty() && authors.isEmpty()
}

/** One library series' contribution to the profile. Higher [weight] means it counts more. */
data class ProfileEntry(
    val genre: String?,
    val tags: List<String>,
    val author: String?,
    val weight: Double,
)

/** Sums the entries' weights by genre, tag, and author. */
fun buildTasteProfile(entries: List<ProfileEntry>): TasteProfile {
    val genres = HashMap<String, Double>()
    val tags = HashMap<String, Double>()
    val authors = HashMap<String, Double>()
    for (entry in entries) {
        entry.genre?.let { genres[it] = (genres[it] ?: 0.0) + entry.weight }
        entry.tags.forEach { tags[it] = (tags[it] ?: 0.0) + entry.weight }
        entry.author?.let { authors[it] = (authors[it] ?: 0.0) + entry.weight }
    }
    return TasteProfile(genres, tags, authors)
}

/**
 * Scores a candidate series against [profile], on the device. Genre overlap counts most, then a
 * favorite author, then each shared tag. Returns 0 when nothing overlaps.
 */
fun scoreCandidate(genre: String?, tags: List<String>, author: String?, profile: TasteProfile): Double {
    var score = 0.0
    genre?.let { score += 3.0 * (profile.genres[it] ?: 0.0) }
    author?.let { score += 2.0 * (profile.authors[it] ?: 0.0) }
    tags.forEach { score += profile.tags[it] ?: 0.0 }
    return score
}

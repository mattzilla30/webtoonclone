package com.webtoonclone.data

/**
 * MangaDex stores the romanized original name as the main title and the translated English name
 * in altTitles. This picks the title to show. English-original works keep their own title, other
 * works use the first English alternate, and a work with none keeps its main title. With
 * [preferOriginal] the main (romanized) title always wins.
 */
fun chooseTitle(
    main: Map<String, String>,
    altTitles: List<Map<String, String>>,
    originalLanguage: String,
    preferOriginal: Boolean = false,
    lang: String = "en",
): String {
    fun Map<String, String>.pick() = this[lang] ?: values.firstOrNull().orEmpty()
    val translated = altTitles.firstNotNullOfOrNull { it[lang] }
    return when {
        preferOriginal -> main.pick()
        originalLanguage == lang -> main[lang] ?: translated ?: main.pick()
        translated != null -> translated
        else -> main.pick()
    }
}

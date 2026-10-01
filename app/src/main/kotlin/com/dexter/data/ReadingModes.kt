package com.dexter.data

/**
 * The reading mode to use when the choice is Auto. A Long Strip tag means a vertical scroll. Japanese
 * works are paged right to left, and other paged works read left to right. With no language known the
 * reader keeps its original vertical scroll.
 */
fun detectReadingMode(tags: List<String>, originalLanguage: String): ReadingMode = when {
    "Long Strip" in tags -> ReadingMode.Vertical
    originalLanguage == "ja" -> ReadingMode.PagedRtl
    originalLanguage.isNotEmpty() -> ReadingMode.PagedLtr
    else -> ReadingMode.Vertical
}

/**
 * The mode to show: the series' own choice, then your default for every series, then the detected one when
 * both are Auto or missing.
 */
fun resolveMode(chosen: ReadingMode?, detected: ReadingMode, default: ReadingMode = ReadingMode.Auto): ReadingMode = when {
    chosen != null && chosen != ReadingMode.Auto -> chosen
    default != ReadingMode.Auto -> default
    else -> detected
}

/** In two-page spreads, the first page stands alone as the cover, and the rest pair up. The spread that shows [page]. */
fun spreadOf(page: Int): Int = if (page <= 0) 0 else (page + 1) / 2

/** The pages a spread shows, one or two, never past [count]. */
fun pagesOfSpread(spread: Int, count: Int): List<Int> =
    (if (spread <= 0) listOf(0) else listOf(2 * spread - 1, 2 * spread)).filter { it in 0 until count }

/** How many spreads [count] pages make. */
fun spreadCount(count: Int): Int = if (count <= 0) 0 else spreadOf(count - 1) + 1

/** What a tap on the reader does. */
enum class TapAction { Previous, Next, ToggleBars }

/**
 * Decides a tap in paged mode: the outer thirds turn the page and the middle shows or hides the bars.
 * Turning follows the reading direction, so in right-to-left mode the left side goes forward.
 */
fun tapAction(x: Float, width: Float, rtl: Boolean): TapAction {
    if (width <= 0f) return TapAction.ToggleBars
    val fraction = x / width
    return when {
        fraction < 1f / 3f -> if (rtl) TapAction.Next else TapAction.Previous
        fraction > 2f / 3f -> if (rtl) TapAction.Previous else TapAction.Next
        else -> TapAction.ToggleBars
    }
}

/**
 * Finds [chapterId] in a reader's chapter list, either as a listed chapter or as one of another
 * group's uploads of it. Returns the list position and the chapter that matched, or null.
 */
fun findChapter(list: List<Chapter>, chapterId: String): Pair<Int, Chapter>? {
    list.forEachIndexed { index, chapter ->
        if (chapter.id == chapterId) return index to chapter
        chapter.alternates.firstOrNull { it.id == chapterId }?.let { return index to it }
    }
    return null
}

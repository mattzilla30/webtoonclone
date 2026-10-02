package com.dexter.data

/** A page is a two-page spread when it is at least this much wider than tall. */
const val SPREAD_ASPECT = 1.3f

/** True when a page with this width-to-height ratio is a two-page spread. */
fun isSpreadAspect(aspect: Float): Boolean = aspect >= SPREAD_ASPECT

/**
 * Pairs [count] pages for double-page spread mode. Pages flagged by [isSpread] keep a full-width slot
 * of their own instead of sharing one; the rest pair up in order. With [shift] set, the cover pairs
 * forward instead of standing alone, which fixes chapters whose pairing starts one page off.
 *
 * With no spreads and no shift this matches the old pairing exactly: the cover alone, then
 * (1, 2), (3, 4), and so on.
 */
fun pairPages(count: Int, isSpread: (Int) -> Boolean, shift: Int = 0): List<List<Int>> {
    val pairs = mutableListOf<List<Int>>()
    var i = 0
    if (shift == 0 && count > 0) {
        // The cover stands alone, as before.
        pairs.add(listOf(0))
        i = 1
    }
    while (i < count) {
        if (isSpread(i)) {
            pairs.add(listOf(i))
            i++
        } else {
            val next = i + 1
            if (next < count && !isSpread(next)) {
                pairs.add(listOf(i, next))
                i += 2
            } else {
                pairs.add(listOf(i))
                i++
            }
        }
    }
    return pairs
}

/** The pair that shows [page], or -1 when [pairs] does not cover it. */
fun pairIndexOf(pairs: List<List<Int>>, page: Int): Int = pairs.indexOfFirst { page in it }

/** The first page the pair at [index] shows, or [count] - 1 when it is missing. */
fun firstPageOfPair(pairs: List<List<Int>>, index: Int, count: Int): Int =
    pairs.getOrNull(index)?.firstOrNull() ?: (count - 1).coerceAtLeast(0)

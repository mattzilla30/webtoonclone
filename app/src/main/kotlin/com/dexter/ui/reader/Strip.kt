package com.dexter.ui.reader

import com.dexter.data.pagesOfSpread
import com.dexter.data.spreadOf

/** One item in the vertical strip: a chapter's heading, one of its pages, or the card after the last chapter. */
sealed interface StripItem {
    /** The place of its chapter in [ReaderPage.segments]. */
    val segment: Int
    val key: String

    data class Divider(override val segment: Int, val chapterId: String) : StripItem {
        override val key get() = "d:$chapterId"
    }

    data class Page(override val segment: Int, val page: Int, val chapterId: String) : StripItem {
        override val key get() = "p:$chapterId:$page"
    }

    data class End(override val segment: Int) : StripItem {
        override val key get() = "end"
    }
}

/** A place in the reader: a page of one of the chapters in [ReaderPage.segments]. */
data class Cursor(val segment: Int, val page: Int)

/** The strip for [segments]: each chapter's pages, a heading before every chapter after the first, and an end card. */
fun buildStrip(segments: List<ChapterSegment>): List<StripItem> = buildList {
    segments.forEachIndexed { s, segment ->
        if (s > 0) add(StripItem.Divider(s, segment.chapter.id))
        segment.pages.indices.forEach { add(StripItem.Page(s, it, segment.chapter.id)) }
    }
    if (segments.isNotEmpty()) add(StripItem.End(segments.lastIndex))
}

/**
 * The place shown when [index] is the top item of [strip]. A heading counts as the first page of its chapter,
 * and the end card as the last page of the last one.
 */
fun cursorAt(strip: List<StripItem>, index: Int, pageCount: (Int) -> Int): Cursor = when (val item = strip.getOrNull(index)) {
    is StripItem.Page -> Cursor(item.segment, item.page)
    is StripItem.Divider -> Cursor(item.segment, 0)
    is StripItem.End -> Cursor(item.segment, (pageCount(item.segment) - 1).coerceAtLeast(0))
    null -> Cursor(0, 0)
}

/** Where [page] of the chapter at [segment] sits in [strip], or the nearest earlier item when it is missing. */
fun stripIndexOf(strip: List<StripItem>, segment: Int, page: Int): Int {
    val exact = strip.indexOfFirst { it is StripItem.Page && it.segment == segment && it.page == page }
    if (exact >= 0) return exact
    return strip.indexOfFirst { it.segment == segment }.coerceAtLeast(0)
}

/** The pager page that shows [page], with or without two-page spreads. */
fun pagerIndexOf(page: Int, spreads: Boolean): Int = if (spreads) spreadOf(page) else page

/** The first page a pager page shows, with or without two-page spreads. */
fun pageOfPager(index: Int, count: Int, spreads: Boolean): Int =
    if (spreads) pagesOfSpread(index, count).firstOrNull() ?: (count - 1).coerceAtLeast(0) else index

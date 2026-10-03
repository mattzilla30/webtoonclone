package com.dexter.ui.reader

/**
 * The buttons the user can place in the reader's top or bottom bar, in any order. Actions that only
 * make sense in one bar are ignored in the other.
 */
enum class ToolbarAction(val label: String) {
    Back("Back"),
    Title("Series and episode title"),
    Bookmark("Bookmark"),
    ReaderOptions("Reader options"),
    Share("Share"),
    Cast("Cast"),
    Pip("Picture in picture"),
    PageSlider("Page slider"),
    PageCounter("Page counter"),
    ChapterList("Chapter list"),
    PrevChapter("Previous episode"),
    NextChapter("Next episode"),
    Thumbnails("Chapter thumbnails"),
    SleepTimer("Sleep timer"),
    Binge("Binge mode"),
    Narration("Read aloud"),
}

/** The top bar as it shipped: back, title, bookmark, options, share, cast, picture in picture. */
val defaultTopActions: List<ToolbarAction> = listOf(
    ToolbarAction.Back,
    ToolbarAction.Title,
    ToolbarAction.Bookmark,
    ToolbarAction.ReaderOptions,
    ToolbarAction.Share,
    ToolbarAction.Cast,
    ToolbarAction.Pip,
)

/** The bottom bar as it shipped: slider, page counter, chapter list, previous, next. */
val defaultBottomActions: List<ToolbarAction> = listOf(
    ToolbarAction.PageSlider,
    ToolbarAction.PageCounter,
    ToolbarAction.ChapterList,
    ToolbarAction.PrevChapter,
    ToolbarAction.NextChapter,
)

/** The stored layout, or [default] when nothing is stored yet. Unknown names are dropped. */
fun toolbarActionsOrDefault(csv: String, default: List<ToolbarAction>): List<ToolbarAction> {
    if (csv.isBlank()) return default
    val parsed = parseToolbarActions(csv)
    return parsed.ifEmpty { default }
}

/** Parses a comma-separated list of [ToolbarAction] names, dropping anything unknown or repeated. */
fun parseToolbarActions(csv: String): List<ToolbarAction> =
    csv.split(",").mapNotNull { name ->
        name.trim().takeIf { it.isNotEmpty() }?.let { runCatching { ToolbarAction.valueOf(it) }.getOrNull() }
    }.distinct()

/** Serializes [actions] for storage. */
fun serializeToolbarActions(actions: List<ToolbarAction>): String = actions.joinToString(",") { it.name }

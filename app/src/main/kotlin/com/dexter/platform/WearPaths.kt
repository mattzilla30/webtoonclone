package com.dexter.platform

/**
 * Wearable Data Layer message paths shared by the phone app and the wear module. Both sides use
 * these exact strings; keep them in sync with `wear/src/main/kotlin/com/dexter/wear/`.
 */
object WearPaths {
    /** Wear -> phone: turn one page backward in the open reader. */
    const val PAGE_PREVIOUS = "/dexter/page/previous"

    /** Wear -> phone: turn one page forward in the open reader. */
    const val PAGE_NEXT = "/dexter/page/next"

    /** Wear -> phone: ask what is currently being read. */
    const val PROGRESS_REQUEST = "/dexter/progress/request"

    /**
     * Phone -> wear: the current reading state, as `seriesTitle|chapterNumber|page|total`.
     * Empty payload means nothing is being read.
     */
    const val PROGRESS_REPLY = "/dexter/progress"
}

/** Packs a progress reply payload for [WearPaths.PROGRESS_REPLY]. */
fun wearProgressPayload(seriesTitle: String, chapterNumber: String, page: Int, total: Int): String =
    listOf(seriesTitle, chapterNumber, page.toString(), total.toString()).joinToString("|")

/** Unpacks a [WearPaths.PROGRESS_REPLY] payload. Null when the payload is empty or malformed. */
fun parseWearProgress(payload: String): WearProgress? {
    if (payload.isBlank()) return null
    val parts = payload.split("|")
    if (parts.size != 4) return null
    return WearProgress(
        seriesTitle = parts[0],
        chapterNumber = parts[1],
        page = parts[2].toIntOrNull() ?: return null,
        total = parts[3].toIntOrNull() ?: return null,
    )
}

/** What the watch shows at a glance. */
data class WearProgress(
    val seriesTitle: String,
    val chapterNumber: String,
    val page: Int,
    val total: Int,
) {
    /** How far through the chapter the reader is, 0 to 1. */
    val share: Float get() = if (total > 0) (page + 1f) / total else 0f
}

package com.dexter.notify

/**
 * The screens to open, in order, when a notification is tapped. A new chapter opens the reader on
 * top of its series page, so Back returns to the series. With no chapter it opens the series only.
 */
fun openRoutes(seriesId: String, chapterId: String?): List<String> =
    if (chapterId == null) listOf("series/$seriesId") else listOf("series/$seriesId", "series/$seriesId/$chapterId")

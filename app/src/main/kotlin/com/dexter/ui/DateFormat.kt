package com.dexter.ui

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** A chapter's publish date in the phone's language and time zone, such as "Sep 30, 2026" or "30.09.2026". */
fun formatChapterDate(
    iso: String,
    locale: Locale = Locale.getDefault(),
    zone: ZoneId = ZoneId.systemDefault(),
): String = runCatching {
    OffsetDateTime.parse(iso).atZoneSameInstant(zone).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
}.getOrDefault("")

/**
 * The heading for the day [iso] falls on: "Today", "Yesterday", the weekday for the last week, and the date
 * before that. An unreadable time falls under "Earlier".
 */
fun dayHeading(iso: String, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String {
    val day = runCatching { OffsetDateTime.parse(iso).atZoneSameInstant(zone).toLocalDate() }.getOrNull() ?: return "Earlier"
    val daysAgo = java.time.temporal.ChronoUnit.DAYS.between(day, today)
    return when {
        daysAgo <= 0L -> "Today"
        daysAgo == 1L -> "Yesterday"
        daysAgo < 7L -> day.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, locale)
        else -> day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
    }
}

/** "5 min ago", "3 h ago", "2 d ago", or a date for anything older than a month. */
fun timeAgo(iso: String, now: Instant = Instant.now()): String {
    val time = runCatching { OffsetDateTime.parse(iso).toInstant() }.getOrNull() ?: return ""
    return timeAgo(time, now)
}

fun timeAgo(time: Instant, now: Instant = Instant.now()): String {
    val minutes = Duration.between(time, now).toMinutes().coerceAtLeast(0)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 60 * 24 -> "${minutes / 60} h ago"
        minutes < 60 * 24 * 30 -> "${minutes / (60 * 24)} d ago"
        else -> DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .format(LocalDate.ofInstant(time, ZoneId.systemDefault()))
    }
}

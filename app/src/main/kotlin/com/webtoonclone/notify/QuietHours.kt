package com.webtoonclone.notify

/**
 * Whether [hour] (0 to 23) falls in the quiet range from [startHour] up to but not including
 * [endHour]. A range that crosses midnight, such as 22 to 7, is handled. Equal hours mean no range.
 */
fun isQuietHour(hour: Int, startHour: Int, endHour: Int): Boolean = when {
    startHour == endHour -> false
    startHour < endHour -> hour in startHour until endHour
    else -> hour >= startHour || hour < endHour
}

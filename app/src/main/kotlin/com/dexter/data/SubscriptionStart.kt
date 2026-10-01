package com.dexter.data

import kotlinx.coroutines.CancellationException

/**
 * The series as a new subscription starts: from its newest chapter now, so only chapters that come later
 * notify. This uses the same lookup as the background check, since the chapter list orders differently.
 * When the lookup fails the subscription still starts, and the first check records the newest chapter.
 */
suspend fun MangaDexRepository.subscriptionStart(id: String, title: String, coverUrl: String?): SavedSeries {
    val newest = try {
        latestChapter(id)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
    return SavedSeries(id, title, coverUrl, knownChapterId = newest?.id, knownChapterNumber = newest?.number)
}

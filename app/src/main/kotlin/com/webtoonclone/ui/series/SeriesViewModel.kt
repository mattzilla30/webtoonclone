package com.webtoonclone.ui.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.webtoonclone.data.CachedSeries
import com.webtoonclone.data.Chapter
import com.webtoonclone.data.LibraryStore
import com.webtoonclone.data.MAX_CACHED_CHAPTERS
import com.webtoonclone.data.MangaDexRepository
import com.webtoonclone.data.SavedSeries
import com.webtoonclone.data.SeriesCacheStore
import com.webtoonclone.data.SeriesDetail
import com.webtoonclone.ui.Load
import com.webtoonclone.ui.friendlyError
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Chapters are newest first. [hasMore] is true while older chapters remain on the server. */
data class SeriesPage(
    val detail: SeriesDetail,
    val chapters: List<Chapter>,
    val hasMore: Boolean,
)

class SeriesViewModel(
    private val seriesId: String,
    private val repository: MangaDexRepository,
    private val libraryStore: LibraryStore,
    private val seriesCache: SeriesCacheStore,
) : ViewModel() {
    private val _state = MutableStateFlow<Load<SeriesPage>>(Load.Loading)
    val state: StateFlow<Load<SeriesPage>> = _state

    private val _loadingMore = MutableStateFlow(false)
    val loadingMore: StateFlow<Boolean> = _loadingMore

    private var nextOffset: Int? = 0
    private val seen = mutableSetOf<String>()

    /** The chapter this device read last, which may be older than the loaded pages. */
    val lastRead: StateFlow<SavedSeries?> = libraryStore.data
        .map { lib -> lib.recent.firstOrNull { it.id == seriesId && it.chapterId != null } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val subscribed: StateFlow<Boolean> = libraryStore.data.map { lib -> lib.subscribed.any { it.id == seriesId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Whether new chapters of this series notify. Always true until you switch it off. */
    val notifyEnabled: StateFlow<Boolean> = libraryStore.data
        .map { lib -> lib.subscribed.firstOrNull { it.id == seriesId }?.notify ?: true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setNotify(enabled: Boolean) {
        viewModelScope.launch { libraryStore.setSeriesNotify(seriesId, enabled) }
    }

    /** Sets [chapter] as the last one read, so it and every earlier chapter show as read. */
    fun markReadUpTo(chapter: Chapter, detail: SeriesDetail) {
        viewModelScope.launch {
            libraryStore.recordRecent(SavedSeries(seriesId, detail.summary.title, detail.summary.coverUrl, chapter.id, chapter.number))
        }
    }

    /** Moves the last-read mark back to [previous], or clears it when there is nothing earlier. */
    fun markUnreadFrom(previous: Chapter?, detail: SeriesDetail) {
        viewModelScope.launch {
            if (previous != null) {
                libraryStore.recordRecent(SavedSeries(seriesId, detail.summary.title, detail.summary.coverUrl, previous.id, previous.number))
            } else {
                libraryStore.removeRecent(setOf(seriesId))
            }
        }
    }

    init { load() }

    fun toggleSubscribed(detail: SeriesDetail) {
        viewModelScope.launch {
            // Start from the newest chapter now, so only chapters that come later notify. This uses
            // the same lookup as the background check, since the chapter list orders differently.
            val newest = runCatching { repository.latestChapter(seriesId) }.getOrNull()
            libraryStore.toggleSubscribed(
                SavedSeries(
                    seriesId,
                    detail.summary.title,
                    detail.summary.coverUrl,
                    knownChapterId = newest?.id,
                    knownChapterNumber = newest?.number,
                ),
            )
        }
    }

    /** When the page shows a saved copy because the network failed, the time it was saved. */
    private val _offlineSavedAt = MutableStateFlow<Long?>(null)
    val offlineSavedAt: StateFlow<Long?> = _offlineSavedAt

    fun load() {
        _state.value = Load.Loading
        seen.clear()
        viewModelScope.launch {
            try {
                val ready = coroutineScope {
                    val detail = async { repository.series(seriesId) }
                    val first = async { repository.chapterPage(seriesId, 0, seen) }
                    val page = first.await()
                    nextOffset = page.nextOffset
                    SeriesPage(detail.await(), page.chapters, page.nextOffset != null)
                }
                _offlineSavedAt.value = null
                _state.value = Load.Ready(ready)
                val saved = CachedSeries(ready.detail, ready.chapters.take(MAX_CACHED_CHAPTERS), System.currentTimeMillis())
                runCatching { seriesCache.save(saved) }
            } catch (e: Exception) {
                // Fall back to the last copy of this series, if you opened it before.
                val saved = runCatching { seriesCache.load(seriesId) }.getOrNull()
                if (saved != null) {
                    nextOffset = null
                    _offlineSavedAt.value = saved.savedAt
                    _state.value = Load.Ready(SeriesPage(saved.detail, saved.chapters, hasMore = false))
                } else {
                    _state.value = Load.Error(friendlyError(e, "Could not load series"))
                }
            }
        }
    }

    /** Appends the next page of older chapters. Called when the list scrolls near its end. */
    fun loadMore() {
        val offset = nextOffset ?: return
        val current = (_state.value as? Load.Ready)?.value ?: return
        if (_loadingMore.value) return
        _loadingMore.value = true
        viewModelScope.launch {
            try {
                val page = repository.chapterPage(seriesId, offset, seen)
                nextOffset = page.nextOffset
                _state.value = Load.Ready(
                    current.copy(chapters = current.chapters + page.chapters, hasMore = page.nextOffset != null),
                )
            } catch (e: Exception) {
                // Keep the list as is. Scrolling again retries.
            } finally {
                _loadingMore.value = false
            }
        }
    }
}

package com.dexter.ui.series

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dexter.data.CachedSeries
import com.dexter.data.Chapter
import com.dexter.data.DownloadStore
import com.dexter.data.LibraryStore
import com.dexter.data.MAX_CACHED_CHAPTERS
import com.dexter.data.MangaDexRepository
import com.dexter.data.ReadingStatus
import com.dexter.data.SavedSeries
import com.dexter.data.SeriesCacheStore
import com.dexter.data.SeriesCover
import com.dexter.data.SeriesDetail
import com.dexter.data.SeriesSummary
import com.dexter.data.SettingsStore
import com.dexter.data.chaptersToDownload
import com.dexter.data.relationLabel
import com.dexter.notify.DownloadWorker
import com.dexter.ui.Load
import com.dexter.ui.friendlyError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
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
    private val settingsStore: SettingsStore,
    private val downloads: DownloadStore,
    private val context: Application,
) : ViewModel() {
    private val _state = MutableStateFlow<Load<SeriesPage>>(Load.Loading)
    val state: StateFlow<Load<SeriesPage>> = _state

    private val _loadingMore = MutableStateFlow(false)
    val loadingMore: StateFlow<Boolean> = _loadingMore

    private var nextOffset: Int? = 0
    private val seen = mutableSetOf<String>()
    private var loadJob: Job? = null
    private var moreJob: Job? = null

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

    /** Ids of this series' chapters saved on the device. */
    val downloaded: StateFlow<Set<String>> = downloads.saved
        .map { rows -> rows.filter { it.seriesId == seriesId }.mapTo(mutableSetOf()) { it.chapterId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Ids of chapters waiting or being saved now. */
    val downloading: StateFlow<Set<String>> = downloads.active
        .map { it.keys }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun download(detail: SeriesDetail, chapter: Chapter) {
        viewModelScope.launch {
            val wifiOnly = settingsStore.current().downloadWifiOnly
            DownloadWorker.enqueue(context, downloads, seriesId, detail.summary.title, detail.summary.coverUrl, chapter, wifiOnly)
        }
    }

    fun removeDownload(chapterId: String) {
        viewModelScope.launch { downloads.delete(chapterId) }
    }

    /** Saves the next [count] unread chapters after the last one you read, or every unread one when null. */
    fun downloadUnread(detail: SeriesDetail, count: Int?) {
        viewModelScope.launch {
            val all = runCatching { repository.allChapters(seriesId, settingsStore.current().preferredGroups[seriesId]) }.getOrNull() ?: return@launch
            val last = lastRead.value?.chapterNumber
            chaptersToDownload(all.asReversed(), last, downloaded.value, count).forEach { download(detail, it) }
        }
    }

    /** The reading list this series is in, or null when it is in none. */
    val status: StateFlow<ReadingStatus?> = libraryStore.data
        .map { lib -> lib.lists.firstOrNull { it.id == seriesId }?.status }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Names of your collections, with whether this series is in each. */
    val collections: StateFlow<Map<String, Boolean>> = libraryStore.data
        .map { lib -> lib.collections.mapValues { (_, members) -> members.any { it.id == seriesId } } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun toggleCollection(detail: SeriesDetail, name: String) {
        viewModelScope.launch {
            libraryStore.toggleCollection(name, SavedSeries(seriesId, detail.summary.title, detail.summary.coverUrl))
        }
    }

    fun hideSeries() {
        viewModelScope.launch { settingsStore.update { it.copy(hiddenSeries = it.hiddenSeries + seriesId) } }
    }

    fun blockGroup(group: String) {
        viewModelScope.launch { settingsStore.update { it.copy(blockedGroups = it.blockedGroups + group) } }
    }

    fun setStatus(detail: SeriesDetail, status: ReadingStatus?) {
        viewModelScope.launch {
            libraryStore.setStatus(SavedSeries(seriesId, detail.summary.title, detail.summary.coverUrl), status)
        }
    }

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

    /** Series with the same leading tags, loaded once the series page is ready. Empty until then. */
    private val _similar = MutableStateFlow<List<SeriesSummary>>(emptyList())
    val similar: StateFlow<List<SeriesSummary>> = _similar

    /** The group whose uploads this series prefers, or null when none is chosen. */
    val preferredGroup: StateFlow<String?> = settingsStore.settings
        .map { it.preferredGroups[seriesId] }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Saves [group] as the preferred uploader for this series, or clears it when null, then reloads the list. */
    fun setPreferredGroup(group: String?) {
        viewModelScope.launch {
            settingsStore.update { settings ->
                settings.copy(preferredGroups = if (group == null) settings.preferredGroups - seriesId else settings.preferredGroups + (seriesId to group))
            }
            load()
        }
    }

    val language: String get() = repository.language

    init {
        load()
        // A new content language changes the titles and chapters, so the page loads again.
        viewModelScope.launch { repository.contentVersion.drop(1).collect { load() } }
    }

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
        // A reload replaces the page, so an older load or a chapter page still loading must not land on top of it.
        moreJob?.cancel()
        loadJob?.cancel()
        _loadingMore.value = false
        _state.value = Load.Loading
        seen.clear()
        loadJob = viewModelScope.launch {
            try {
                val group = settingsStore.current().preferredGroups[seriesId]
                val ready = coroutineScope {
                    val detail = async { repository.series(seriesId) }
                    val first = async { repository.chapterPage(seriesId, 0, seen, group) }
                    val page = first.await()
                    nextOffset = page.nextOffset
                    SeriesPage(detail.await(), page.chapters, page.nextOffset != null)
                }
                _offlineSavedAt.value = null
                _state.value = Load.Ready(ready)
                loadSimilar(ready.detail)
                val saved = CachedSeries(ready.detail, ready.chapters.take(MAX_CACHED_CHAPTERS), System.currentTimeMillis(), repository.language)
                runCatching { seriesCache.save(saved) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Fall back to the last copy of this series, if you opened it before.
                val saved = runCatching { seriesCache.load(seriesId, repository.language) }.getOrNull()
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

    private val relatedState = MutableStateFlow<List<Pair<String, SeriesSummary>>>(emptyList())

    /** Related series with how each relates, such as "Sequel". */
    val related: StateFlow<List<Pair<String, SeriesSummary>>> = relatedState

    private val coversState = MutableStateFlow<Load<List<SeriesCover>>?>(null)

    /** The cover gallery: null until opened, then loading, then the covers. */
    val covers: StateFlow<Load<List<SeriesCover>>?> = coversState

    fun openCovers() {
        coversState.value = Load.Loading
        viewModelScope.launch {
            coversState.value = try {
                Load.Ready(repository.covers(seriesId))
            } catch (e: Exception) {
                Load.Error(friendlyError(e, "Could not load covers"))
            }
        }
    }

    fun closeCovers() {
        coversState.value = null
    }

    private fun loadSimilar(detail: SeriesDetail) {
        _similar.value = emptyList()
        relatedState.value = emptyList()
        viewModelScope.launch {
            val list = runCatching { repository.relatedSeries(detail.relations) }.getOrDefault(emptyList())
            val kinds = detail.relations.associate { it.id to relationLabel(it.kind) }
            relatedState.value = list.map { (kinds[it.id] ?: "Related") to it }
        }
        viewModelScope.launch {
            _similar.value = runCatching { repository.similar(seriesId, detail.tags) }.getOrDefault(emptyList())
        }
    }

    /** Appends the next page of older chapters. Called when the list scrolls near its end. */
    fun loadMore() {
        val offset = nextOffset ?: return
        val current = (_state.value as? Load.Ready)?.value ?: return
        if (_loadingMore.value) return
        _loadingMore.value = true
        moreJob = viewModelScope.launch {
            try {
                val page = repository.chapterPage(seriesId, offset, seen, settingsStore.current().preferredGroups[seriesId])
                nextOffset = page.nextOffset
                _state.value = Load.Ready(
                    current.copy(chapters = current.chapters + page.chapters, hasMore = page.nextOffset != null),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Keep the list as is. Scrolling again retries.
            } finally {
                _loadingMore.value = false
            }
        }
    }
}

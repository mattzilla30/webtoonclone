package com.dexter.ui.series

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dexter.data.Bookmark
import com.dexter.data.CachedSeries
import com.dexter.data.Chapter
import com.dexter.data.DownloadStore
import com.dexter.data.ImageExport
import com.dexter.data.LibraryStore
import com.dexter.data.MAX_CACHED_CHAPTERS
import com.dexter.data.MangaDexRepository
import com.dexter.data.ProgressStore
import com.dexter.data.ReadingProgress
import com.dexter.data.ReadingStatus
import com.dexter.data.SavedSeries
import com.dexter.data.SeriesCacheStore
import com.dexter.data.SeriesCover
import com.dexter.data.SeriesDetail
import com.dexter.data.SeriesSummary
import com.dexter.data.SettingsStore
import com.dexter.data.chaptersToDownload
import com.dexter.data.relationLabel
import com.dexter.data.subscriptionStart
import com.dexter.notify.DownloadWorker
import com.dexter.ui.Load
import com.dexter.ui.LogFailures
import com.dexter.ui.catching
import com.dexter.ui.friendlyError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
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
    progressStore: ProgressStore,
    private val imageExport: ImageExport,
    private val context: Application,
) : ViewModel() {
    /** Where you are in the series: the chapter, page, and page count you left off at. */
    val progress: StateFlow<ReadingProgress?> = progressStore.observe(seriesId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Your own note on this series, or empty. */
    val note: StateFlow<String> = libraryStore.stateOf(viewModelScope) { lib -> lib.notes[seriesId].orEmpty() }

    /** Pages you bookmarked in this series, newest first. */
    val bookmarks: StateFlow<List<Bookmark>> = libraryStore.stateOf(viewModelScope) { lib -> lib.bookmarks.filter { it.seriesId == seriesId } }

    fun removeBookmark(bookmark: Bookmark) {
        viewModelScope.launch(LogFailures) { libraryStore.removeBookmark(bookmark) }
    }

    fun setNote(text: String) {
        viewModelScope.launch(LogFailures) { libraryStore.setNote(seriesId, text) }
    }

    private val _toast = MutableStateFlow<String?>(null)

    /** A short confirmation or failure, such as after saving the cover. */
    val toast: StateFlow<String?> = _toast

    fun clearToast() {
        _toast.value = null
    }

    /** Keeps [tag] out of lists and search from now on. */
    fun blockTag(tag: String) {
        viewModelScope.launch(LogFailures) {
            settingsStore.update { it.copy(blockedTags = it.blockedTags + tag) }
            _toast.value = "$tag is blocked. Settings can unblock it."
        }
    }

    /** Opens the chapter's discussion thread through [open], or says there is none yet. */
    fun openComments(chapter: Chapter, open: (String) -> Unit) {
        viewModelScope.launch(LogFailures) {
            val url = catching { repository.chapterCommentsUrl(chapter.id) }
            when {
                url.isFailure -> _toast.value = "Could not look up the comments"
                url.getOrNull() == null -> _toast.value = "No comments on Ep. ${chapter.number} yet"
                else -> open(url.getOrNull()!!)
            }
        }
    }

    /** Saves [url], a cover, to Pictures/Dexter. */
    fun saveCover(url: String, title: String) {
        viewModelScope.launch(LogFailures) {
            _toast.value = catching {
                imageExport.saveToGallery(imageExport.bytes(url), "$title cover")
                "Saved to Pictures/Dexter"
            }.getOrElse { "Could not save the cover" }
        }
    }

    /** Queues each of [chapters] that opens in the reader and is not saved or queued yet. */
    fun downloadMany(detail: SeriesDetail, chapters: List<Chapter>) {
        viewModelScope.launch(LogFailures) {
            val wifiOnly = settingsStore.current().downloadWifiOnly
            val skip = downloaded.value + downloading.value.keys
            val picked = chapters.filter { it.externalUrl == null && it.id !in skip }
            picked.forEach { enqueueDownload(detail, it, wifiOnly) }
            _toast.value = if (picked.isEmpty()) "Those chapters are already saved" else "Queued ${picked.size} chapters"
        }
    }

    /** Loads every remaining page of chapters, for the oldest-first order, filters, and jumping to a chapter. */
    fun loadAll() {
        if (allJob?.isActive == true) return
        allJob = viewModelScope.launch(LogFailures) {
            while (nextOffset != null && (_state.value as? Load.Ready)?.value?.hasMore == true) {
                val before = nextOffset
                loadMore()
                // Each page loads in its own job. Wait for it before asking for the next.
                moreJob?.join()
                // A page that failed leaves the offset where it was. Stop rather than ask again and again.
                if (nextOffset == before) break
            }
        }
    }

    private var allJob: Job? = null
    private val _state = MutableStateFlow<Load<SeriesPage>>(Load.Loading)
    val state: StateFlow<Load<SeriesPage>> = _state

    private val _loadingMore = MutableStateFlow(false)
    val loadingMore: StateFlow<Boolean> = _loadingMore

    private var nextOffset: Int? = 0
    private val seen = mutableSetOf<String>()
    private var loadJob: Job? = null
    private var moreJob: Job? = null

    /** The chapter this device read last, which may be older than the loaded pages. */
    val lastRead: StateFlow<SavedSeries?> = libraryStore.stateOf(viewModelScope) { lib -> lib.recent.firstOrNull { it.id == seriesId && it.chapterId != null } }

    val subscribed: StateFlow<Boolean> = libraryStore.stateOf(viewModelScope) { lib -> lib.subscribed.any { it.id == seriesId } }

    /** Whether new chapters of this series notify. Always true until you switch it off. */
    val notifyEnabled: StateFlow<Boolean> = libraryStore.stateOf(viewModelScope) { lib -> lib.subscribed.firstOrNull { it.id == seriesId }?.notify ?: true }

    /** Ids of this series' chapters saved on the device. */
    val downloaded: StateFlow<Set<String>> = downloads.saved
        .map { rows -> rows.filter { it.seriesId == seriesId }.mapTo(mutableSetOf()) { it.chapterId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Chapters waiting or being saved, by id, with progress from 0 to 1. A chapter still waiting reads 0. */
    val downloading: StateFlow<Map<String, Float>> = combine(downloads.queue, downloads.active) { queue, active ->
        queue.associate { it.chapterId to (active[it.chapterId] ?: 0f) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Stops saving [chapterId], whether it is waiting or being saved now. */
    fun cancelDownload(chapterId: String) {
        viewModelScope.launch(LogFailures) { downloads.cancel(chapterId) }
    }

    fun download(detail: SeriesDetail, chapter: Chapter) {
        viewModelScope.launch(LogFailures) { enqueueDownload(detail, chapter, settingsStore.current().downloadWifiOnly) }
    }

    private suspend fun enqueueDownload(detail: SeriesDetail, chapter: Chapter, wifiOnly: Boolean) {
        DownloadWorker.enqueue(context, downloads, seriesId, detail.summary.title, detail.summary.coverUrl, chapter, wifiOnly)
    }

    fun removeDownload(chapterId: String) {
        viewModelScope.launch(LogFailures) { downloads.delete(chapterId) }
    }

    /** Saves the next [count] unread chapters after the last one you read, or every unread one when null. */
    fun downloadUnread(detail: SeriesDetail, count: Int?) {
        viewModelScope.launch(LogFailures) {
            val all = catching { repository.allChapters(seriesId, settingsStore.current().preferredGroups[seriesId]) }.getOrNull() ?: return@launch
            val last = lastRead.value?.chapterNumber
            val wifiOnly = settingsStore.current().downloadWifiOnly
            chaptersToDownload(all.asReversed(), last, downloaded.value, count).forEach { enqueueDownload(detail, it, wifiOnly) }
        }
    }

    /** The reading list this series is in, or null when it is in none. */
    val status: StateFlow<ReadingStatus?> = libraryStore.stateOf(viewModelScope) { lib -> lib.lists.firstOrNull { it.id == seriesId }?.status }

    /** Names of your collections, with whether this series is in each. */
    val collections: StateFlow<Map<String, Boolean>> = libraryStore.stateOf(viewModelScope) { lib -> lib.collections.mapValues { (_, members) -> members.any { it.id == seriesId } } }

    fun toggleCollection(detail: SeriesDetail, name: String) {
        viewModelScope.launch(LogFailures) {
            libraryStore.toggleCollection(name, SavedSeries(seriesId, detail.summary.title, detail.summary.coverUrl))
        }
    }

    fun hideSeries() {
        viewModelScope.launch(LogFailures) { settingsStore.update { it.copy(hiddenSeries = it.hiddenSeries + seriesId) } }
    }

    fun blockGroup(group: String) {
        viewModelScope.launch(LogFailures) { settingsStore.update { it.copy(blockedGroups = it.blockedGroups + group) } }
    }

    fun setStatus(detail: SeriesDetail, status: ReadingStatus?) {
        viewModelScope.launch(LogFailures) {
            libraryStore.setStatus(SavedSeries(seriesId, detail.summary.title, detail.summary.coverUrl), status)
        }
    }

    fun setNotify(enabled: Boolean) {
        viewModelScope.launch(LogFailures) { libraryStore.setSeriesNotify(seriesId, enabled) }
    }

    /** Sets [chapter] as the last one read, so it and every earlier chapter show as read. */
    fun markReadUpTo(chapter: Chapter, detail: SeriesDetail) {
        viewModelScope.launch(LogFailures) {
            libraryStore.recordRecent(SavedSeries(seriesId, detail.summary.title, detail.summary.coverUrl, chapter.id, chapter.number))
        }
    }

    /** Moves the last-read mark back to [previous], or clears it when there is nothing earlier. */
    fun markUnreadFrom(previous: Chapter?, detail: SeriesDetail) {
        viewModelScope.launch(LogFailures) {
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
        viewModelScope.launch(LogFailures) {
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
        viewModelScope.launch(LogFailures) { repository.contentVersion.drop(1).collect { load() } }
    }

    fun toggleSubscribed(detail: SeriesDetail) {
        viewModelScope.launch(LogFailures) {
            // Unsubscribing needs only the id. Subscribing starts from the newest chapter.
            libraryStore.toggleSubscribed(
                if (subscribed.value) {
                    SavedSeries(seriesId, detail.summary.title, detail.summary.coverUrl)
                } else {
                    repository.subscriptionStart(seriesId, detail.summary.title, detail.summary.coverUrl)
                },
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
        loadJob = viewModelScope.launch(LogFailures) {
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
                catching { seriesCache.save(saved) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Fall back to the last copy of this series, if you opened it before.
                val saved = catching { seriesCache.load(seriesId, repository.language) }.getOrNull()
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
        viewModelScope.launch(LogFailures) {
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

    private var relatedJob: Job? = null
    private var similarJob: Job? = null

    private fun loadSimilar(detail: SeriesDetail) {
        // A reload starts these again, so the ones still running for the old page must not land afterwards.
        relatedJob?.cancel()
        similarJob?.cancel()
        _similar.value = emptyList()
        relatedState.value = emptyList()
        relatedJob = viewModelScope.launch(LogFailures) {
            val list = catching { repository.relatedSeries(detail.relations) }.getOrDefault(emptyList())
            val kinds = detail.relations.associate { it.id to relationLabel(it.kind) }
            relatedState.value = list.map { (kinds[it.id] ?: "Related") to it }
        }
        similarJob = viewModelScope.launch(LogFailures) {
            _similar.value = catching { repository.similar(seriesId, detail.tags) }.getOrDefault(emptyList())
        }
    }

    /** Appends the next page of older chapters. Called when the list scrolls near its end. */
    fun loadMore() {
        val offset = nextOffset ?: return
        val current = (_state.value as? Load.Ready)?.value ?: return
        if (_loadingMore.value) return
        _loadingMore.value = true
        moreJob = viewModelScope.launch(LogFailures) {
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

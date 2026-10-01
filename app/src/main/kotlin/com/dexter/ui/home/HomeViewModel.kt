package com.dexter.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dexter.data.HomeContent
import com.dexter.data.LibraryStore
import com.dexter.data.MangaDexRepository
import com.dexter.data.SavedSeries
import com.dexter.data.SeriesCacheStore
import com.dexter.data.SeriesSummary
import com.dexter.data.SettingsStore
import com.dexter.ui.Load
import com.dexter.ui.friendlyError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: MangaDexRepository,
    private val libraryStore: LibraryStore,
    private val settingsStore: SettingsStore,
    private val seriesCache: SeriesCacheStore,
) : ViewModel() {
    private val becauseState = MutableStateFlow<Pair<String, List<SeriesSummary>>?>(null)

    /** The series you read last and a few like it, for the "Because you read" row. Null until found. */
    val becauseYouRead: StateFlow<Pair<String, List<SeriesSummary>>?> = becauseState

    private var becauseFor: String? = null

    /** Finds series like the one you read last. Skips series you already have, and does nothing when nothing changed. */
    fun refreshBecause() {
        viewModelScope.launch {
            val library = libraryStore.data.first()
            val last = library.recent.firstOrNull { it.chapterId != null } ?: return@launch
            if (becauseFor == last.id && becauseState.value != null) return@launch
            val detail = seriesCache.load(last.id, repository.language)?.detail ?: return@launch
            val known = (library.recent + library.subscribed + library.lists).map { it.id }.toSet()
            val like = runCatching { repository.similar(last.id, detail.tags, limit = 14) }.getOrNull().orEmpty().filter { it.id !in known }
            becauseFor = last.id
            becauseState.value = if (like.isEmpty()) null else last.title to like.take(10)
        }
    }

    /** Series you read recently, newest first, for the Continue Reading row. */
    val recent: StateFlow<List<SavedSeries>> = libraryStore.data
        .map { lib -> lib.recent.filter { it.chapterId != null }.take(10) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _state = MutableStateFlow<Load<HomeContent>>(Load.Loading)
    val state: StateFlow<Load<HomeContent>> = _state

    /** Ids of series you subscribe to, so tiles can show a marker. */
    val subscribedIds: StateFlow<Set<String>> = libraryStore.data
        .map { lib -> lib.subscribed.map { it.id }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val _toast = MutableStateFlow<String?>(null)

    /** A short confirmation after a long press. Cleared by the screen once shown. */
    val toast: StateFlow<String?> = _toast

    fun clearToast() {
        _toast.value = null
    }

    /** Subscribes to a series, or unsubscribes if you already do. Starts from its newest chapter. */
    fun toggleSubscribe(series: SeriesSummary) {
        viewModelScope.launch {
            val already = series.id in subscribedIds.value
            // Same lookup as the background check, so only later chapters notify.
            val newest = if (already) null else runCatching { repository.latestChapter(series.id) }.getOrNull()
            libraryStore.toggleSubscribed(
                SavedSeries(
                    series.id,
                    series.title,
                    series.coverUrl,
                    knownChapterId = newest?.id,
                    knownChapterNumber = newest?.number,
                ),
            )
            _toast.value = subscriptionMessage(series.title, nowSubscribed = !already)
        }
    }

    /** When the shown content is a saved copy because the network failed, the time it was saved. */
    private val _offlineSavedAt = MutableStateFlow<Long?>(null)
    val offlineSavedAt: StateFlow<Long?> = _offlineSavedAt

    init {
        // A new content language changes every title and cover, so the home screen loads again.
        viewModelScope.launch { repository.contentVersion.drop(1).collect { load(showSpinner = true) } }
    }

    private var seenOpen = 0
    private var busy = false

    /**
     * Reloads with fresh random picks when the app has been opened since the last load.
     * Coming back from a series page keeps the same open count, so the home screen holds still.
     */
    fun refreshIfNewOpen(openCount: Int) {
        if (openCount == seenOpen) return
        seenOpen = openCount
        load(showSpinner = _state.value !is Load.Ready)
    }

    fun retry() = load(showSpinner = true)

    /** Re-checks for newly started series without touching the rest of the screen. */
    fun refreshNewSeries() {
        val current = (_state.value as? Load.Ready)?.value ?: return
        viewModelScope.launch {
            val fresh = runCatching { repository.newSeries() }.getOrNull() ?: return@launch
            if (fresh.map { it.id } != current.newSeries.map { it.id }) {
                val latest = (_state.value as? Load.Ready)?.value ?: return@launch
                _state.value = Load.Ready(latest.copy(newSeries = fresh))
            }
        }
    }

    private fun load(showSpinner: Boolean) {
        if (busy) return
        busy = true
        if (showSpinner) _state.value = Load.Loading
        viewModelScope.launch {
            try {
                val content = repository.home()
                _state.value = Load.Ready(content)
                _offlineSavedAt.value = null
                runCatching { libraryStore.saveHome(content) }
            } catch (e: Exception) {
                // A silent refresh keeps the old content when the network fails. A first load falls
                // back to the last saved home, and shows the error only when nothing is saved.
                if (_state.value !is Load.Ready) {
                    val saved = runCatching { libraryStore.loadHome() }.getOrNull()
                    if (saved != null) {
                        _offlineSavedAt.value = saved.savedAt
                        _state.value = Load.Ready(saved.content)
                    } else {
                        _state.value = Load.Error(friendlyError(e, "Could not load series"))
                    }
                }
            } finally {
                busy = false
            }
        }
    }
}

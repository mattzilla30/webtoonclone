package com.dexter.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dexter.data.HomeContent
import com.dexter.data.LibraryStore
import com.dexter.data.MangaDexRepository
import com.dexter.data.ProgressStore
import com.dexter.data.ReadingProgress
import com.dexter.data.SavedSeries
import com.dexter.data.SeriesCacheStore
import com.dexter.data.SeriesSummary
import com.dexter.data.SettingsStore
import com.dexter.data.newChapterEstimate
import com.dexter.data.subscriptionStart
import com.dexter.ui.Load
import com.dexter.ui.LogFailures
import com.dexter.ui.catching
import com.dexter.ui.friendlyError
import com.dexter.ui.series.hasUnreadChapters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A subscribed series with chapters you have not read, for the "From your subscriptions" row. */
data class UnreadSeries(val series: SavedSeries, val newCount: Int?)

class HomeViewModel(
    private val repository: MangaDexRepository,
    private val libraryStore: LibraryStore,
    private val settingsStore: SettingsStore,
    private val seriesCache: SeriesCacheStore,
    progressStore: ProgressStore,
) : ViewModel() {
    private val becauseState = MutableStateFlow<Pair<String, List<SeriesSummary>>?>(null)

    /** A series you read lately and a few like it, for the "Because you read" row. Null until found. */
    val becauseYouRead: StateFlow<Pair<String, List<SeriesSummary>>?> = becauseState

    /**
     * Finds series like one you read lately. Each app open picks one of your last five, so the row changes
     * instead of always following the newest. Series you already have are left out.
     */
    fun refreshBecause() {
        viewModelScope.launch(LogFailures) {
            val library = libraryStore.current()
            val candidates = library.recent.filter { it.chapterId != null }.take(BECAUSE_POOL).shuffled()
            val known = (library.recent + library.subscribed + library.lists).mapTo(HashSet()) { it.id }
            for (pick in candidates) {
                val detail = catching { seriesCache.load(pick.id, repository.language) }.getOrNull()?.detail ?: continue
                val like = catching { repository.similar(pick.id, detail.tags, limit = 14) }.getOrNull().orEmpty().filter { it.id !in known }
                if (like.isNotEmpty()) {
                    becauseState.value = pick.title to like.take(10)
                    return@launch
                }
            }
        }
    }

    /** Series you read recently, newest first, for the Continue Reading row. */
    val recent: StateFlow<List<SavedSeries>> = libraryStore.stateOf(viewModelScope) { lib -> lib.recent.filter { it.chapterId != null }.take(10) }

    /** Saved positions by series id, for the progress bars under Continue Reading covers. */
    val progress: StateFlow<Map<String, ReadingProgress>> = progressStore.all
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * For each recent series you subscribe to that has newer chapters: about how many, or null for "new"
     * when the gap cannot be counted.
     */
    val newCounts: StateFlow<Map<String, Int?>> = libraryStore.stateOf(viewModelScope) { lib ->
        val lastRead = lib.recent.associate { it.id to it.chapterNumber }
        lib.subscribed
            .filter { hasUnreadChapters(it.knownChapterNumber, lastRead[it.id]) }
            .associate { it.id to newChapterEstimate(it.knownChapterNumber, lastRead[it.id]) }
    }

    /** Subscribed series with unread chapters, each opening at its newest chapter. */
    val fromSubscriptions: StateFlow<List<UnreadSeries>> = libraryStore.stateOf(viewModelScope) { lib ->
        val lastRead = lib.recent.associate { it.id to it.chapterNumber }
        lib.subscribed
            .filter { it.knownChapterId != null && hasUnreadChapters(it.knownChapterNumber, lastRead[it.id]) }
            .map { UnreadSeries(it, newChapterEstimate(it.knownChapterNumber, lastRead[it.id])) }
    }

    /** Whether to show the tip that a long press on a cover subscribes. */
    val showLongPressTip: StateFlow<Boolean> = settingsStore.settings.map { !it.longPressTipSeen }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), !settingsStore.latest.longPressTipSeen)

    fun dismissLongPressTip() {
        viewModelScope.launch(LogFailures) { settingsStore.update { it.copy(longPressTipSeen = true) } }
    }

    /** Takes a series off the Continue Reading row and out of the Recent list. */
    fun removeFromHistory(seriesId: String) {
        viewModelScope.launch(LogFailures) { libraryStore.removeRecent(setOf(seriesId)) }
    }

    /** Marks a subscribed series read up to its newest known chapter. */
    fun markCaughtUp(seriesId: String) {
        viewModelScope.launch(LogFailures) {
            val series = libraryStore.current().subscribed.firstOrNull { it.id == seriesId } ?: return@launch
            val chapterId = series.knownChapterId ?: return@launch
            libraryStore.recordRecent(SavedSeries(series.id, series.title, series.coverUrl, chapterId, series.knownChapterNumber))
            _toast.value = "Marked ${series.title} as read"
        }
    }

    private val _state = MutableStateFlow<Load<HomeContent>>(Load.Loading)
    val state: StateFlow<Load<HomeContent>> = _state

    private val _refreshing = MutableStateFlow(false)

    /** True while a pull to refresh reloads the screen. The old content stays visible meanwhile. */
    val refreshing: StateFlow<Boolean> = _refreshing

    /** Ids of series you subscribe to, so tiles can show a marker. */
    val subscribedIds: StateFlow<Set<String>> = libraryStore.stateOf(viewModelScope) { lib -> lib.subscribed.mapTo(HashSet()) { it.id } }

    private val _toast = MutableStateFlow<String?>(null)

    /** A short confirmation after a long press. Cleared by the screen once shown. */
    val toast: StateFlow<String?> = _toast

    fun clearToast() {
        _toast.value = null
    }

    /** Subscribes to a series, or unsubscribes if you already do. Starts from its newest chapter. */
    fun toggleSubscribe(series: SeriesSummary) {
        viewModelScope.launch(LogFailures) {
            val already = series.id in subscribedIds.value
            // Unsubscribing needs only the id. Subscribing starts from the newest chapter.
            libraryStore.toggleSubscribed(
                if (already) SavedSeries(series.id, series.title, series.coverUrl) else repository.subscriptionStart(series.id, series.title, series.coverUrl),
            )
            _toast.value = subscriptionMessage(series.title, nowSubscribed = !already)
        }
    }

    /** When the shown content is a saved copy because the network failed, the time it was saved. */
    private val _offlineSavedAt = MutableStateFlow<Long?>(null)
    val offlineSavedAt: StateFlow<Long?> = _offlineSavedAt

    init {
        // A new content language changes every title and cover, so the home screen loads again.
        viewModelScope.launch(LogFailures) { repository.contentVersion.drop(1).collect { load(showSpinner = true, force = true) } }
    }

    private var seenOpen = 0
    private var loadJob: Job? = null

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

    /** Pull to refresh and shuffle: new picks, with the current ones on screen until they arrive. */
    fun refresh() {
        if (_state.value !is Load.Ready) return retry()
        _refreshing.value = true
        load(showSpinner = false, force = true)
        loadJob?.invokeOnCompletion { _refreshing.value = false }
        refreshBecause()
    }

    /** Re-checks for newly started series without touching the rest of the screen. */
    fun refreshNewSeries() {
        val current = (_state.value as? Load.Ready)?.value ?: return
        viewModelScope.launch(LogFailures) {
            val fresh = catching { repository.newSeries() }.getOrNull() ?: return@launch
            if (fresh.map { it.id } != current.newSeries.map { it.id }) {
                val latest = (_state.value as? Load.Ready)?.value ?: return@launch
                _state.value = Load.Ready(latest.copy(newSeries = fresh))
            }
        }
    }

    /** Loads the home screen. A load already running is left alone, unless [force] replaces it, as a language change does. */
    private fun load(showSpinner: Boolean, force: Boolean = false) {
        if (loadJob?.isActive == true) {
            if (!force) return
            loadJob?.cancel()
        }
        if (showSpinner) _state.value = Load.Loading
        loadJob = viewModelScope.launch(LogFailures) {
            try {
                val content = repository.home()
                _state.value = Load.Ready(content)
                _offlineSavedAt.value = null
                catching { libraryStore.saveHome(content) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A silent refresh keeps the old content when the network fails. A first load falls
                // back to the last saved home, and shows the error only when nothing is saved.
                if (_state.value !is Load.Ready) {
                    val saved = catching { libraryStore.loadHome() }.getOrNull()
                    if (saved != null) {
                        _offlineSavedAt.value = saved.savedAt
                        _state.value = Load.Ready(saved.content)
                    } else {
                        _state.value = Load.Error(friendlyError(e, "Could not load series"))
                    }
                } else if (!showSpinner) {
                    _toast.value = friendlyError(e, "Could not refresh")
                }
            }
        }
    }
}

/** How many recent series "Because you read" picks from. */
private const val BECAUSE_POOL = 5

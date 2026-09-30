package com.webtoonclone.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.webtoonclone.data.CrashLog
import com.webtoonclone.data.HomeContent
import com.webtoonclone.data.LibraryStore
import com.webtoonclone.data.MangaDexRepository
import com.webtoonclone.data.SavedSeries
import com.webtoonclone.data.SeriesSummary
import com.webtoonclone.data.SettingsStore
import com.webtoonclone.data.shouldShowWelcome
import com.webtoonclone.ui.Load
import com.webtoonclone.ui.friendlyError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: MangaDexRepository,
    private val libraryStore: LibraryStore,
    private val settingsStore: SettingsStore,
    private val crashLog: CrashLog,
) : ViewModel() {
    /** True until the first-launch walkthrough is finished. Starts false so it never flashes before storage loads. */
    val showWelcome: StateFlow<Boolean> = combine(settingsStore.settings, libraryStore.data) { settings, library ->
        shouldShowWelcome(settings.welcomeDone, library.hintDismissed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun finishWelcome() {
        viewModelScope.launch {
            settingsStore.update { it.copy(welcomeDone = true) }
            // The walkthrough covers the older one-time tip, so both are done.
            libraryStore.dismissHint()
        }
    }

    private val _crashReport = MutableStateFlow(crashLog.pending())

    /** The report saved by the last crash, if crash reports are on and one happened. */
    val crashReport: StateFlow<String?> = _crashReport

    fun dismissCrashReport() {
        crashLog.clear()
        _crashReport.value = null
    }

    /** Series you read recently, newest first, for the Continue Reading row. */
    val recent: StateFlow<List<SavedSeries>> = libraryStore.data
        .map { lib -> lib.recent.filter { it.chapterId != null }.take(10) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _state = MutableStateFlow<Load<HomeContent>>(Load.Loading)
    val state: StateFlow<Load<HomeContent>> = _state

    /** True until the first-launch tip is dismissed. Starts false so it never flashes before storage loads. */
    val showHint: StateFlow<Boolean> = libraryStore.data
        .map { !it.hintDismissed }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

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

    fun dismissHint() {
        viewModelScope.launch { libraryStore.dismissHint() }
    }

    /** When the shown content is a saved copy because the network failed, the time it was saved. */
    private val _offlineSavedAt = MutableStateFlow<Long?>(null)
    val offlineSavedAt: StateFlow<Long?> = _offlineSavedAt

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

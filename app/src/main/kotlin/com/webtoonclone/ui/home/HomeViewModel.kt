package com.webtoonclone.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.webtoonclone.data.HomeContent
import com.webtoonclone.data.LibraryStore
import com.webtoonclone.data.MangaDexRepository
import com.webtoonclone.data.SavedSeries
import com.webtoonclone.ui.Load
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: MangaDexRepository,
    libraryStore: LibraryStore,
) : ViewModel() {

    /** Series you read recently, newest first, for the Continue Reading row. */
    val recent: StateFlow<List<SavedSeries>> = libraryStore.data
        .map { lib -> lib.recent.filter { it.chapterId != null }.take(10) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())


    private val _state = MutableStateFlow<Load<HomeContent>>(Load.Loading)
    val state: StateFlow<Load<HomeContent>> = _state

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
                _state.value = Load.Ready(repository.home())
            } catch (e: Exception) {
                // A silent refresh keeps the old content when the network fails.
                if (_state.value !is Load.Ready) _state.value = Load.Error(e.message ?: "Could not load series")
            } finally {
                busy = false
            }
        }
    }
}

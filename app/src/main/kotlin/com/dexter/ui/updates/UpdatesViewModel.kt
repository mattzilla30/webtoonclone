package com.dexter.ui.updates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dexter.data.LibraryStore
import com.dexter.data.MangaDexRepository
import com.dexter.data.OfflineStore
import com.dexter.data.UpdateEntry
import com.dexter.ui.Load
import com.dexter.ui.friendlyError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class UpdatesViewModel(
    private val repository: MangaDexRepository,
    private val offline: OfflineStore,
    libraryStore: LibraryStore,
) : ViewModel() {
    /** Ids of the series you subscribe to, so their rows can carry a marker. */
    val subscribedIds: StateFlow<Set<String>> = libraryStore.data
        .map { lib -> lib.subscribed.mapTo(mutableSetOf()) { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** When the list is a saved copy because the network failed, the time it was saved. */
    private val _offlineSavedAt = MutableStateFlow<Long?>(null)
    val offlineSavedAt: StateFlow<Long?> = _offlineSavedAt

    private val _state = MutableStateFlow<Load<List<UpdateEntry>>>(Load.Loading)
    val state: StateFlow<Load<List<UpdateEntry>>> = _state

    private val _loadingMore = MutableStateFlow(false)
    val loadingMore: StateFlow<Boolean> = _loadingMore

    private var page = 0

    private var loadJob: Job? = null
    private var moreJob: Job? = null

    init {
        load()
        // A new content language changes which chapters are listed.
        viewModelScope.launch { repository.contentVersion.drop(1).collect { load() } }
    }

    fun load() {
        // A refresh replaces the list, so an older refresh or a page still loading must not land on top of it.
        moreJob?.cancel()
        loadJob?.cancel()
        _loadingMore.value = false
        _state.value = Load.Loading
        page = 0
        loadJob = viewModelScope.launch {
            try {
                val entries = repository.latestUpdates(0)
                _offlineSavedAt.value = null
                _state.value = Load.Ready(entries)
                runCatching { offline.saveUpdates(entries) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Fall back to the last first page, if there is one.
                val saved = runCatching { offline.loadUpdates() }.getOrNull()
                if (saved != null) {
                    _offlineSavedAt.value = saved.savedAt
                    _state.value = Load.Ready(saved.entries)
                } else {
                    _state.value = Load.Error(friendlyError(e, "Could not load updates"))
                }
            }
        }
    }

    /** Appends the next page. Series already listed keep their newer entry. */
    fun loadMore() {
        val current = (_state.value as? Load.Ready)?.value ?: return
        // A saved copy has no next page to fetch.
        if (_loadingMore.value || _offlineSavedAt.value != null) return
        _loadingMore.value = true
        moreJob = viewModelScope.launch {
            try {
                val more = repository.latestUpdates(page + 1)
                page += 1
                val seen = current.mapTo(mutableSetOf()) { it.series.id }
                _state.value = Load.Ready(current + more.filter { it.series.id !in seen })
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

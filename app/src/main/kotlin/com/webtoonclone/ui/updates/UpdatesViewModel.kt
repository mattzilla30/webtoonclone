package com.webtoonclone.ui.updates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.webtoonclone.data.MangaDexRepository
import com.webtoonclone.data.UpdateEntry
import com.webtoonclone.ui.Load
import com.webtoonclone.ui.friendlyError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class UpdatesViewModel(private val repository: MangaDexRepository) : ViewModel() {

    private val _state = MutableStateFlow<Load<List<UpdateEntry>>>(Load.Loading)
    val state: StateFlow<Load<List<UpdateEntry>>> = _state

    private val _loadingMore = MutableStateFlow(false)
    val loadingMore: StateFlow<Boolean> = _loadingMore

    private var page = 0

    init { load() }

    fun load() {
        _state.value = Load.Loading
        page = 0
        viewModelScope.launch {
            _state.value = try {
                Load.Ready(repository.latestUpdates(0))
            } catch (e: Exception) {
                Load.Error(friendlyError(e, "Could not load updates"))
            }
        }
    }

    /** Appends the next page. Series already listed keep their newer entry. */
    fun loadMore() {
        val current = (_state.value as? Load.Ready)?.value ?: return
        if (_loadingMore.value) return
        _loadingMore.value = true
        viewModelScope.launch {
            try {
                val more = repository.latestUpdates(page + 1)
                page += 1
                val seen = current.mapTo(mutableSetOf()) { it.series.id }
                _state.value = Load.Ready(current + more.filter { it.series.id !in seen })
            } catch (e: Exception) {
                // Keep the list as is. Scrolling again retries.
            } finally {
                _loadingMore.value = false
            }
        }
    }
}

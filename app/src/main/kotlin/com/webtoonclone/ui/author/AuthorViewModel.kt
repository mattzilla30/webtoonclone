package com.webtoonclone.ui.author

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.webtoonclone.data.MangaDexRepository
import com.webtoonclone.data.Order
import com.webtoonclone.data.SeriesSummary
import com.webtoonclone.ui.Load
import com.webtoonclone.ui.friendlyError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** The series one author or artist worked on, most followed first, loaded a page at a time. */
class AuthorViewModel(private val authorId: String, private val repository: MangaDexRepository) : ViewModel() {
    private val _state = MutableStateFlow<Load<List<SeriesSummary>>>(Load.Loading)
    val state: StateFlow<Load<List<SeriesSummary>>> = _state

    private val _loadingMore = MutableStateFlow(false)
    val loadingMore: StateFlow<Boolean> = _loadingMore

    private var page = 0
    private var endReached = false

    init { load() }

    fun load() {
        _state.value = Load.Loading
        page = 0
        endReached = false
        viewModelScope.launch {
            _state.value = try {
                val first = repository.browse(page = 0, order = Order.Popular, authorId = authorId, withStats = true)
                endReached = first.isEmpty()
                Load.Ready(first)
            } catch (e: Exception) {
                Load.Error(friendlyError(e, "Could not load this author"))
            }
        }
    }

    fun loadMore() {
        val current = (_state.value as? Load.Ready)?.value ?: return
        if (_loadingMore.value || endReached) return
        _loadingMore.value = true
        viewModelScope.launch {
            try {
                val more = repository.browse(page = page + 1, order = Order.Popular, authorId = authorId, withStats = true)
                page += 1
                val seen = current.mapTo(mutableSetOf()) { it.id }
                endReached = more.isEmpty()
                _state.value = Load.Ready(current + more.filter { it.id !in seen })
            } catch (e: Exception) {
                // Keep the list as is. Scrolling again retries.
            } finally {
                _loadingMore.value = false
            }
        }
    }
}

package com.dexter.ui.author

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dexter.data.LibraryStore
import com.dexter.data.MangaDexRepository
import com.dexter.data.Order
import com.dexter.data.SeriesSummary
import com.dexter.ui.Load
import com.dexter.ui.catching
import com.dexter.ui.friendlyError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** The series one author or artist worked on, most followed first, loaded a page at a time. */
class AuthorViewModel(
    private val authorId: String,
    private val repository: MangaDexRepository,
    private val library: LibraryStore,
) : ViewModel() {
    val following: StateFlow<Boolean> = library.stateOf(viewModelScope) { data -> data.followedAuthors.any { it.id == authorId } }

    fun toggleFollow(name: String) {
        viewModelScope.launch {
            val shown = (state.value as? Load.Ready)?.value.orEmpty().map { it.id }
            // Series beyond the first page count as seen too, so following never floods you with old ones.
            val newest = catching { repository.browse(order = Order.Newest, authorId = authorId, limit = 30).map { it.id } }.getOrDefault(emptyList())
            library.toggleAuthor(authorId, name, (shown + newest).distinct())
        }
    }

    private val _state = MutableStateFlow<Load<List<SeriesSummary>>>(Load.Loading)
    val state: StateFlow<Load<List<SeriesSummary>>> = _state

    private val _loadingMore = MutableStateFlow(false)
    val loadingMore: StateFlow<Boolean> = _loadingMore

    private var page = 0
    private var endReached = false

    private var loadJob: Job? = null

    init { load() }

    fun load() {
        loadJob?.cancel()
        _state.value = Load.Loading
        page = 0
        endReached = false
        loadJob = viewModelScope.launch {
            _state.value = try {
                val first = repository.browse(page = 0, order = Order.Popular, authorId = authorId, withStats = true)
                endReached = first.isEmpty()
                Load.Ready(first)
            } catch (e: CancellationException) {
                throw e
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

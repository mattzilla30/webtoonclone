package com.webtoonclone.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.webtoonclone.data.LibraryStore
import com.webtoonclone.data.MangaDexRepository
import com.webtoonclone.data.SeriesSummary
import com.webtoonclone.ui.Load
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

val SearchGenres = listOf(
    "Romance" to "💕", "Fantasy" to "✨", "Drama" to "🎭", "Action" to "⚔️",
    "Sports" to "🏀", "Comedy" to "😄", "Slice of Life" to "☀️", "Superhero" to "🦸",
    "Sci-Fi" to "🚀", "Thriller" to "🔪", "Supernatural" to "👻", "Mystery" to "🔍",
    "Historical" to "🏛️", "Horror" to "💀",
)

class SearchViewModel(
    private val repository: MangaDexRepository,
    private val library: LibraryStore,
) : ViewModel() {

    /** Null while the user has not searched yet. */
    private val _results = MutableStateFlow<Load<List<SeriesSummary>>?>(null)
    val results: StateFlow<Load<List<SeriesSummary>>?> = _results

    private val _loadingMore = MutableStateFlow(false)
    val loadingMore: StateFlow<Boolean> = _loadingMore

    /** Fetches one page of the current search or genre. Null when nothing is showing. */
    private var source: (suspend (page: Int) -> List<SeriesSummary>)? = null
    private var page = 0
    private var endReached = false

    val recentSearches: StateFlow<List<String>> = library.data.map { it.searches }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var query = ""
        private set

    fun search(text: String) {
        query = text.trim()
        if (query.isEmpty()) return clear()
        val term = query
        viewModelScope.launch {
            library.addSearch(term)
            start { repository.browse(title = term, page = it) }
        }
    }

    fun openGenre(genre: String) {
        query = genre
        viewModelScope.launch { start { repository.browse(genre = genre, page = it, withStats = true) } }
    }

    fun clear() {
        query = ""
        source = null
        _results.value = null
    }

    /** Appends the next page. Called when the list scrolls near its end. */
    fun loadMore() {
        val fetch = source ?: return
        val current = (_results.value as? Load.Ready)?.value ?: return
        if (_loadingMore.value || endReached) return
        _loadingMore.value = true
        viewModelScope.launch {
            try {
                val more = fetch(page + 1)
                // The source may have changed while this request ran.
                if (source === fetch) {
                    page += 1
                    val seen = current.mapTo(mutableSetOf()) { it.id }
                    endReached = more.isEmpty()
                    _results.value = Load.Ready(current + more.filter { it.id !in seen })
                }
            } catch (e: Exception) {
                // Keep the list as is. Scrolling again retries.
            } finally {
                _loadingMore.value = false
            }
        }
    }

    fun removeSearch(text: String) {
        viewModelScope.launch { library.removeSearch(text) }
    }

    fun clearSearches() {
        viewModelScope.launch { library.clearSearches() }
    }

    private suspend fun start(fetch: suspend (page: Int) -> List<SeriesSummary>) {
        source = fetch
        page = 0
        endReached = false
        _results.value = Load.Loading
        _results.value = try {
            val first = fetch(0)
            endReached = first.isEmpty()
            Load.Ready(first)
        } catch (e: Exception) {
            Load.Error(e.message ?: "Search failed")
        }
    }
}

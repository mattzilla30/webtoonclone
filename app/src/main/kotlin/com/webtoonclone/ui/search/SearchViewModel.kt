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

    val recentSearches: StateFlow<List<String>> = library.data.map { it.searches }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var query = ""
        private set

    fun search(text: String) {
        query = text.trim()
        if (query.isEmpty()) return clear()
        viewModelScope.launch {
            library.addSearch(query)
            load { repository.browse(title = query) }
        }
    }

    fun openGenre(genre: String) {
        query = genre
        viewModelScope.launch { load { repository.browse(genre = genre, withStats = true) } }
    }

    fun clear() {
        query = ""
        _results.value = null
    }

    fun removeSearch(text: String) {
        viewModelScope.launch { library.removeSearch(text) }
    }

    fun clearSearches() {
        viewModelScope.launch { library.clearSearches() }
    }

    private suspend fun load(block: suspend () -> List<SeriesSummary>) {
        _results.value = Load.Loading
        _results.value = try {
            Load.Ready(block())
        } catch (e: Exception) {
            Load.Error(e.message ?: "Search failed")
        }
    }
}

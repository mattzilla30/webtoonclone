package com.dexter.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dexter.data.LibraryStore
import com.dexter.data.MangaDexRepository
import com.dexter.data.OfflineStore
import com.dexter.data.Order
import com.dexter.data.SavedSearch
import com.dexter.data.SearchFilters
import com.dexter.data.SeriesSummary
import com.dexter.data.searchKey
import com.dexter.ui.Load
import com.dexter.ui.LogFailures
import com.dexter.ui.catching
import com.dexter.ui.friendlyError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SearchViewModel(
    private val repository: MangaDexRepository,
    private val library: LibraryStore,
    private val offline: OfflineStore,
) : ViewModel() {
    /** When the results are a saved copy because the network failed, the time it was saved. */
    private val _offlineSavedAt = MutableStateFlow<Long?>(null)
    val offlineSavedAt: StateFlow<Long?> = _offlineSavedAt

    /** Reruns the current search, for the offline banner. */
    fun retry() {
        val current = request ?: return
        begin(current)
    }

    /** Null while the user has not searched yet. */
    private val _results = MutableStateFlow<Load<List<SeriesSummary>>?>(null)
    val results: StateFlow<Load<List<SeriesSummary>>?> = _results

    private val _loadingMore = MutableStateFlow(false)
    val loadingMore: StateFlow<Boolean> = _loadingMore

    private val typed = MutableStateFlow("")

    /** Titles matching what is being typed. Empty until two characters have been typed and paused. */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val suggestions: StateFlow<List<SeriesSummary>> = typed
        .debounce(350)
        .distinctUntilChanged()
        .mapLatest { text ->
            if (!shouldSuggest(text)) emptyList()
            else catching { repository.browse(title = text.trim(), limit = 5) }.getOrDefault(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onTyping(text: String) {
        typed.value = text
    }

    private val _message = MutableStateFlow<String?>(null)

    /** A short notice, such as a failed Random. Cleared by the next action. */
    val message: StateFlow<String?> = _message

    private val _sort = MutableStateFlow(Order.Popular)
    val sort: StateFlow<Order> = _sort

    /** What the current results came from, so a new sort can rerun it. */
    private var request: Request? = null

    private val _filters = MutableStateFlow(SearchFilters())

    /** The advanced limits applied to the current results. */
    val filters: StateFlow<SearchFilters> = _filters

    /** Applies [next] to the current results, or starts a filtered listing when nothing is showing. */
    fun setFilters(next: SearchFilters) {
        if (next == _filters.value) return
        _filters.value = next
        val current = request
        when {
            current != null -> begin(current)
            !next.isEmpty -> {
                query = "Filtered"
                begin(Request(title = null, tag = null))
            }
        }
    }

    val savedSearches: StateFlow<List<SavedSearch>> = library.stateOf(viewModelScope) { it.savedSearches }

    /** Whether the results on screen can be saved: there is a search, tag, or filter behind them. */
    val canSave: Boolean get() = request != null

    fun saveCurrent(name: String) {
        val current = request ?: return
        viewModelScope.launch(LogFailures) {
            library.saveSearch(SavedSearch(name.trim(), current.title, current.tag, _filters.value, _sort.value.name))
        }
    }

    fun openSaved(saved: SavedSearch) {
        query = saved.title ?: saved.tag ?: saved.name
        _sort.value = runCatching { Order.valueOf(saved.order) }.getOrDefault(Order.Popular)
        _filters.value = saved.filters
        begin(Request(saved.title, saved.tag))
    }

    fun deleteSaved(name: String) {
        viewModelScope.launch(LogFailures) { library.deleteSavedSearch(name) }
    }

    private class Request(val title: String?, val tag: String?)

    /** Fetches one page of the current search or genre. Null when nothing is showing. */
    private var source: (suspend (page: Int) -> List<SeriesSummary>)? = null
    private var page = 0
    private var endReached = false

    val recentSearches: StateFlow<List<String>> = library.stateOf(viewModelScope) { it.searches }

    var query = ""
        private set

    init {
        // Start from the sort you chose last time.
        viewModelScope.launch(LogFailures) {
            val saved = runCatching { Order.valueOf(library.current().searchOrder) }.getOrNull()
            if (saved != null && request == null) _sort.value = saved
        }
    }

    fun search(text: String) {
        query = text.trim()
        if (query.isEmpty()) return clear()
        val term = query
        viewModelScope.launch(LogFailures) { library.addSearch(term) }
        begin(Request(title = term, tag = null))
    }

    /** Lists series with a MangaDex tag: a genre, theme, format, or content tag. */
    fun openTag(name: String) {
        query = name
        begin(Request(title = null, tag = name))
    }

    /** Lists every series in one order, such as top rated or recently added. */
    fun openBrowse(label: String, order: Order) {
        query = label
        _sort.value = order
        begin(Request(title = null, tag = null))
    }

    /** Finds a random series with English chapters and passes its id to [onFound]. */
    fun openRandom(onFound: (String) -> Unit) {
        _message.value = null
        viewModelScope.launch(LogFailures) {
            val series = catching { repository.randomSeries() }.getOrNull()
            if (series != null) onFound(series.id) else _message.value = "Could not find a series. Try again."
        }
    }

    /** Reruns the current results in a new order. */
    fun setSort(order: Order) {
        if (order == _sort.value) return
        _sort.value = order
        viewModelScope.launch(LogFailures) { library.setSearchOrder(order.name) }
        val current = request ?: return
        begin(current)
    }

    fun clear() {
        searchJob?.cancel()
        query = ""
        _filters.value = SearchFilters()
        source = null
        request = null
        _offlineSavedAt.value = null
        _results.value = null
    }

    /** Appends the next page. Called when the list scrolls near its end. */
    fun loadMore() {
        val fetch = source ?: return
        val current = (_results.value as? Load.Ready)?.value ?: return
        if (_loadingMore.value || endReached) return
        _loadingMore.value = true
        viewModelScope.launch(LogFailures) {
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
        viewModelScope.launch(LogFailures) { library.removeSearch(text) }
    }

    fun clearSearches() {
        viewModelScope.launch(LogFailures) { library.clearSearches() }
    }

    private var searchJob: Job? = null

    /** Runs [req] as the one current search. A slower older request can no longer overwrite a newer one. */
    private fun begin(req: Request) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch(LogFailures) { start(req) }
    }

    private suspend fun start(req: Request) {
        val order = _sort.value
        val filters = _filters.value
        val fetch: suspend (page: Int) -> List<SeriesSummary> = { p ->
            repository.browse(title = req.title, tag = req.tag, page = p, order = order, withStats = req.title == null, filters = filters)
        }
        request = req
        _message.value = null
        source = fetch
        page = 0
        endReached = false
        _results.value = Load.Loading
        val key = searchKey(req.title, req.tag, order, filters, "${repository.language}|${repository.contentRatings.joinToString(",")}")
        _results.value = try {
            val first = fetch(0)
            _offlineSavedAt.value = null
            endReached = first.isEmpty()
            if (first.isNotEmpty()) catching { offline.saveSearch(key, first) }
            currentCoroutineContext().ensureActive()
            Load.Ready(first)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Fall back to the last first page of this same search, if there is one.
            val saved = catching { offline.loadSearch(key) }.getOrNull()
            if (saved != null) {
                _offlineSavedAt.value = saved.savedAt
                endReached = true
                Load.Ready(saved.series)
            } else {
                Load.Error(friendlyError(e, "Search failed"))
            }
        }
    }
}

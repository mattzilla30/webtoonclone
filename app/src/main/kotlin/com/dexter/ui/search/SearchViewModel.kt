package com.dexter.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dexter.data.AuthorSummary
import com.dexter.data.LibraryStore
import com.dexter.data.MangaDexRepository
import com.dexter.data.OfflineStore
import com.dexter.data.Order
import com.dexter.data.SavedSearch
import com.dexter.data.SavedSeries
import com.dexter.data.SearchFilters
import com.dexter.data.SeriesSummary
import com.dexter.data.SettingsStore
import com.dexter.data.searchKey
import com.dexter.data.subscriptionStart
import com.dexter.ui.Load
import com.dexter.ui.LogFailures
import com.dexter.ui.catching
import com.dexter.ui.friendlyError
import com.dexter.ui.home.subscriptionMessage
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SearchViewModel(
    private val repository: MangaDexRepository,
    private val library: LibraryStore,
    private val offline: OfflineStore,
    private val settingsStore: SettingsStore,
) : ViewModel() {
    /** Results as rows with details instead of a grid of covers. */
    val asList: StateFlow<Boolean> = settingsStore.settings.map { it.resultsAsList }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), settingsStore.latest.resultsAsList)

    fun setAsList(on: Boolean) {
        viewModelScope.launch(LogFailures) { settingsStore.update { it.copy(resultsAsList = on) } }
    }

    /** Ids of series you subscribe to, so results can show a marker. */
    val subscribedIds: StateFlow<Set<String>> = library.stateOf(viewModelScope) { lib -> lib.subscribed.mapTo(HashSet()) { it.id } }

    private val _toast = MutableStateFlow<String?>(null)

    /** A short confirmation shown over the results, such as after a long press subscribes. */
    val toast: StateFlow<String?> = _toast

    fun clearToast() {
        _toast.value = null
    }

    /** Subscribes to [series] from a long press on a result, or unsubscribes when you already do. */
    fun toggleSubscribe(series: SeriesSummary) {
        viewModelScope.launch(LogFailures) {
            val already = series.id in subscribedIds.value
            library.toggleSubscribed(
                if (already) SavedSeries(series.id, series.title, series.coverUrl) else repository.subscriptionStart(series.id, series.title, series.coverUrl),
            )
            _toast.value = subscriptionMessage(series.title, nowSubscribed = !already)
        }
    }

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

    /** Authors and artists matching what is being typed, shown above the title suggestions. */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val authorSuggestions: StateFlow<List<AuthorSummary>> = typed
        .debounce(350)
        .distinctUntilChanged()
        .mapLatest { text ->
            if (!shouldSuggest(text)) emptyList()
            else catching { repository.searchAuthors(text.trim(), limit = 3) }.getOrDefault(emptyList())
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

    /** Saves the current search under [name]. With [notify], new matches later notify, starting after what is listed now. */
    fun saveCurrent(name: String, notify: Boolean = false) {
        val current = request ?: return
        val shown = (_results.value as? Load.Ready)?.value.orEmpty().map { it.id }
        viewModelScope.launch(LogFailures) {
            library.saveSearch(
                SavedSearch(name.trim(), current.title, current.tag, _filters.value, _sort.value.name, notify = notify, knownIds = if (notify) shown else emptyList()),
            )
        }
    }

    /** Turns notifications for a saved search on or off. Turning them on records what matches now, so only later series notify. */
    fun toggleSavedNotify(saved: SavedSearch) {
        viewModelScope.launch(LogFailures) {
            val now = if (saved.notify) {
                emptyList()
            } else {
                catching {
                    repository.browse(title = saved.title, tag = saved.tag, order = Order.Newest, filters = saved.filters, limit = 30).map { it.id }
                }.getOrDefault(emptyList())
            }
            library.setSearchNotify(saved.name, !saved.notify, now)
            _toast.value = if (saved.notify) "No longer notifying for ${saved.name}" else "New matches for ${saved.name} will notify"
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
        // A keyword that names a tag searches the theme too, not just titles.
        begin(Request(title = term, tag = themeTagFor(term)))
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
                // A year range drops series from each page, so a page can come back empty with more after it.
                // A few empty pages in a row are read past before the list counts as finished.
                var next = page + 1
                var more = fetch(next)
                var tries = 1
                while (more.isEmpty() && _filters.value.yearRange != null && tries < RANGE_EMPTY_PAGES) {
                    next += 1
                    more = fetch(next)
                    tries++
                }
                // The source may have changed while this request ran.
                if (source === fetch) {
                    page = next
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
            if (req.title != null && req.tag != null) {
                // Theme matches list first, then title matches; both pages stay in step for load-more.
                mergeSearchResults(
                    repository.browse(tag = req.tag, page = p, order = order, filters = filters),
                    repository.browse(title = req.title, page = p, order = order, filters = filters),
                )
            } else {
                repository.browse(title = req.title, tag = req.tag, page = p, order = order, withStats = req.title == null, filters = filters)
            }
        }
        request = req
        _message.value = null
        source = fetch
        page = 0
        endReached = false
        _results.value = Load.Loading
        val key = searchKey(req.title, req.tag, order, filters, "${repository.language}|${repository.contentRatings.joinToString(",")}")
        _results.value = try {
            var first = fetch(0)
            var tries = 1
            while (first.isEmpty() && filters.yearRange != null && tries < RANGE_EMPTY_PAGES) {
                page += 1
                first = fetch(page)
                tries++
            }
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
                // No cached copy: the previous search's offline banner must not linger over this error.
                _offlineSavedAt.value = null
                Load.Error(friendlyError(e, "Search failed"))
            }
        }
    }
}

/** How many empty pages in a row a year range reads past before the list counts as finished. */
private const val RANGE_EMPTY_PAGES = 5

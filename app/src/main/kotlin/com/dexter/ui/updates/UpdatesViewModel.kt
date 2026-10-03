package com.dexter.ui.updates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dexter.data.LibraryStore
import com.dexter.data.MangaDexRepository
import com.dexter.data.OfflineStore
import com.dexter.data.SavedSeries
import com.dexter.data.UpdateEntry
import com.dexter.ui.Load
import com.dexter.ui.LogFailures
import com.dexter.ui.catching
import com.dexter.ui.discover.SeriesSchedule
import com.dexter.ui.discover.typicalWeekday
import com.dexter.ui.friendlyError
import com.dexter.ui.series.isChapterRead
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class UpdatesViewModel(
    private val repository: MangaDexRepository,
    private val offline: OfflineStore,
    private val libraryStore: LibraryStore,
) : ViewModel() {
    /** Ids of the series you subscribe to, so their rows can carry a marker. */
    val subscribedIds: StateFlow<Set<String>> = libraryStore.stateOf(viewModelScope) { lib -> lib.subscribed.mapTo(mutableSetOf()) { it.id } }

    /** The last chapter you read of each series, by series id, for the unread dots. */
    val lastRead: StateFlow<Map<String, String?>> = libraryStore.stateOf(viewModelScope) { lib -> lib.recent.associate { it.id to it.chapterNumber } }

    /** When the list is a saved copy because the network failed, the time it was saved. */
    private val _offlineSavedAt = MutableStateFlow<Long?>(null)
    val offlineSavedAt: StateFlow<Long?> = _offlineSavedAt

    private val _state = MutableStateFlow<Load<List<UpdateEntry>>>(Load.Loading)
    val state: StateFlow<Load<List<UpdateEntry>>> = _state

    private val _loadingMore = MutableStateFlow(false)
    val loadingMore: StateFlow<Boolean> = _loadingMore

    private val _subscribedOnly = MutableStateFlow(false)

    /** True when the list shows only your subscriptions, each with its newest chapter. */
    val subscribedOnly: StateFlow<Boolean> = _subscribedOnly

    private val _subscribedState = MutableStateFlow<Load<List<UpdateEntry>>>(Load.Loading)

    /** Your subscriptions' newest chapters, loaded when you switch to them. */
    val subscribedState: StateFlow<Load<List<UpdateEntry>>> = _subscribedState

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast

    fun clearToast() {
        _toast.value = null
    }

    private var page = 0

    private var loadJob: Job? = null
    private var moreJob: Job? = null
    private var subscribedJob: Job? = null

    init {
        load()
        // A new content language changes which chapters are listed.
        viewModelScope.launch(LogFailures) {
            repository.contentVersion.drop(1).collect {
                load()
                if (_subscribedOnly.value) loadSubscribed()
            }
        }
    }

    fun setSubscribedOnly(on: Boolean) {
        _subscribedOnly.value = on
        if (on && _subscribedState.value !is Load.Ready) loadSubscribed()
    }

    /** Refreshes whichever list is showing. */
    fun refresh() = if (_subscribedOnly.value) loadSubscribed() else load()

    fun loadSubscribed() {
        subscribedJob?.cancel()
        _subscribedState.value = Load.Loading
        subscribedJob = viewModelScope.launch(LogFailures) {
            _subscribedState.value = try {
                Load.Ready(repository.subscribedUpdates(libraryStore.current().subscribed))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Load.Error(friendlyError(e, "Could not load your subscriptions"))
            }
        }
    }

    private val _schedule = MutableStateFlow<Load<List<SeriesSchedule>>?>(null)

    /** Followed series grouped by their usual update weekday. Null until first opened. */
    val schedule: StateFlow<Load<List<SeriesSchedule>>?> = _schedule

    /** Derives each followed series' usual update weekday from its recent chapter upload dates. */
    fun loadSchedule() {
        // A failed build can be retried; a loaded or loading one is kept.
        if (_schedule.value != null && _schedule.value !is Load.Error) return
        _schedule.value = Load.Loading
        viewModelScope.launch(LogFailures) {
            _schedule.value = try {
                val followed = libraryStore.current().subscribed
                // A few series at a time, so a big library builds its schedule in parallel without flooding MangaDex.
                val permits = Semaphore(4)
                val schedules = coroutineScope {
                    followed.map { series ->
                        async {
                            permits.withPermit {
                                val chapters = catching { repository.allChapters(series.id) }.getOrNull().orEmpty()
                                val weekday = typicalWeekday(chapters.take(12).map { it.publishedAt })
                                weekday?.let { SeriesSchedule(series.id, series.title, series.coverUrl, it, chapters.size.coerceAtMost(12)) }
                            }
                        }
                    }.awaitAll().filterNotNull()
                }
                Load.Ready(schedules.sortedWith(compareBy({ it.weekday }, { it.title })))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Load.Error(friendlyError(e, "Could not build the schedule"))
            }
        }
    }

    /** Records [entry]'s chapter as the last one read of its series, so it and earlier chapters show as read. */
    fun markRead(entry: UpdateEntry) {
        if (entry.chapterId.isEmpty()) return
        viewModelScope.launch(LogFailures) {
            libraryStore.recordRecent(SavedSeries(entry.series.id, entry.series.title, entry.series.coverUrl, entry.chapterId, entry.chapterNumber))
            _toast.value = "Marked ${entry.series.title} Ep. ${entry.chapterNumber} as read"
        }
    }

    fun load() {
        // A refresh replaces the list, so an older refresh or a page still loading must not land on top of it.
        moreJob?.cancel()
        loadJob?.cancel()
        _loadingMore.value = false
        _state.value = Load.Loading
        page = 0
        loadJob = viewModelScope.launch(LogFailures) {
            try {
                val entries = repository.latestUpdates(0)
                _offlineSavedAt.value = null
                _state.value = Load.Ready(entries)
                catching { offline.saveUpdates(entries, repository.language) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Fall back to the last first page, if there is one.
                val saved = catching { offline.loadUpdates(repository.language) }.getOrNull()
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
        moreJob = viewModelScope.launch(LogFailures) {
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

/** True for a subscribed series whose listed chapter is newer than the one you read last. A series you never opened has no dot. */
fun isUnreadUpdate(entry: UpdateEntry, subscribed: Boolean, lastReadNumber: String?): Boolean =
    subscribed && lastReadNumber != null && entry.chapterNumber.toDoubleOrNull() != null && !isChapterRead(entry.chapterNumber, lastReadNumber)

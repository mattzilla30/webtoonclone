package com.dexter.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.SingletonImageLoader
import com.dexter.DexterApp
import com.dexter.data.Accounts
import com.dexter.data.Backup
import com.dexter.data.DuplicateGroup
import com.dexter.data.HttpStatusException
import com.dexter.data.LibraryData
import com.dexter.data.Release
import com.dexter.data.Settings
import com.dexter.data.decodeBackup
import com.dexter.data.isNewer
import com.dexter.data.latestRelease
import com.dexter.data.parseMihonBackup
import com.dexter.data.readCapped
import com.dexter.data.suggestedKeep
import com.dexter.notify.AutoBackupWorker
import com.dexter.notify.DownloadWorker
import com.dexter.notify.GoalReminderWorker
import com.dexter.notify.NewChaptersWorker
import com.dexter.ui.LogFailures
import com.dexter.ui.friendlyError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(private val app: DexterApp) : ViewModel() {
    val settings: StateFlow<Settings> = app.settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), app.settingsStore.latest)

    val library: StateFlow<LibraryData> = app.libraryStore.data
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), app.libraryStore.latest)

    private val _cacheBytes = MutableStateFlow<Long?>(null)

    /** Size of the API and image caches, or null while it is being measured. */
    val cacheBytes: StateFlow<Long?> = _cacheBytes

    fun update(change: (Settings) -> Settings) {
        viewModelScope.launch(LogFailures) { app.settingsStore.update(change) }
    }

    /** Saves the check interval and reschedules the background check to match. */
    fun setCheckInterval(minutes: Int) {
        viewModelScope.launch(LogFailures) {
            app.settingsStore.update { it.copy(checkIntervalMinutes = minutes) }
            NewChaptersWorker.schedule(app, minutes)
        }
    }

    /** Every account you signed in to. */
    val accounts: StateFlow<Accounts> = app.accountStore.accounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Accounts())

    private val _busy = MutableStateFlow(false)

    /** True while a sign-in or sync is running, so its button can wait. */
    val busy: StateFlow<Boolean> = _busy

    private fun accountJob(work: suspend () -> String) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch(LogFailures) {
            _message.value = try {
                work()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                friendlyError(e, "That did not work")
            } finally {
                _busy.value = false
            }
        }
    }

    /** Reads a Mihon or Tachiyomi backup file and adds its MangaDex series to the library. */
    fun importMihon(uri: Uri) = accountJob {
        // The read is capped: an unbounded readBytes on a crafted file exhausts memory first.
        val bytes: ByteArray? = try {
            withContext(Dispatchers.IO) { app.contentResolver.openInputStream(uri)?.use { it.readCapped() } }
        } catch (e: IllegalArgumentException) {
            return@accountJob "That file is not a Mihon or Tachiyomi backup."
        }
        if (bytes == null) return@accountJob "Couldn't open that file."
        val backup = try {
            withContext(Dispatchers.Default) { parseMihonBackup(bytes) }
        } catch (e: RuntimeException) {
            return@accountJob "That file is not a Mihon or Tachiyomi backup."
        }
        app.libraryStore.importSeries(backup.series)
        buildString {
            append("Imported ${backup.series.size} MangaDex series, ${backup.series.count { it.favorite }} of them as subscriptions.")
            if (backup.otherSources > 0) append(" ${backup.otherSources} from other sources were left out.")
        }
    }

    private val _release = MutableStateFlow<Release?>(null)

    /** A newer release found by [checkForUpdate], for the screen to offer. */
    val release: StateFlow<Release?> = _release

    fun dismissRelease() {
        _release.value = null
    }

    /** Looks for a newer release of Dexter on GitHub than [current]. */
    fun checkForUpdate(current: String) = accountJob {
        val latest = latestRelease(app.plainClient)
        when {
            latest == null -> "No releases are published yet."
            isNewer(latest.tag, current) -> {
                _release.value = latest
                "Version ${latest.tag.removePrefix("v")} is out."
            }
            else -> "You have the latest version, $current."
        }
    }

    fun signInMangaDex(clientId: String, clientSecret: String, username: String, password: String) = accountJob {
        val name = try {
            app.mangaDexAccount.signIn(clientId, clientSecret, username, password)
        } catch (e: HttpStatusException) {
            if (e.code in 400..401) return@accountJob "MangaDex refused the sign-in. Check the client id, client secret, username, and password."
            throw e
        }
        val (here, there) = app.mangaDexAccount.sync()
        "Signed in as $name. Subscribed to $here followed series here and followed $there of yours on MangaDex."
    }

    fun syncMangaDex() = accountJob {
        val (here, there) = app.mangaDexAccount.sync()
        "Synced. $here new subscriptions here, $there new follows on MangaDex."
    }

    /** Opens the AniList sign-in page through [open]. The browser sends you back to the app when it is done. */
    fun signInAniList(clientId: String, open: (String) -> Unit) {
        viewModelScope.launch(LogFailures) { open(app.trackers.aniListSignInUrl(clientId)) }
    }

    /** Opens the MyAnimeList sign-in page through [open]. */
    fun signInMal(clientId: String, open: (String) -> Unit) {
        viewModelScope.launch(LogFailures) { open(app.trackers.malSignInUrl(clientId)) }
    }

    fun signOutAniList() {
        viewModelScope.launch(LogFailures) { app.trackers.signOutAniList() }
    }

    fun signOutMal() {
        viewModelScope.launch(LogFailures) { app.trackers.signOutMal() }
    }

    fun signOutMangaDex() {
        viewModelScope.launch(LogFailures) { app.mangaDexAccount.signOut() }
    }

    fun setReadMarkers(on: Boolean) {
        viewModelScope.launch(LogFailures) { app.mangaDexAccount.setReadMarkers(on) }
    }

    /** Saves the hour of the goal reminder, or -1 for none, and schedules it. */
    fun setGoalReminder(hour: Int) {
        viewModelScope.launch(LogFailures) {
            app.settingsStore.update { it.copy(goalReminderHour = hour) }
            GoalReminderWorker.sync(app, hour)
        }
    }

    fun checkNow() {
        NewChaptersWorker.checkNow(app)
        _message.value = "Checking for new chapters now. Notifications arrive when it finishes."
    }

    fun setNotifications(enabled: Boolean) {
        viewModelScope.launch(LogFailures) { app.libraryStore.setNotifications(enabled) }
    }

    private val _message = MutableStateFlow<String?>(null)

    /** The result of the last backup or restore, for the screen to show once. */
    val message: StateFlow<String?> = _message

    /** A backup read from a file, waiting for you to confirm before it replaces your data. */
    private val _pending = MutableStateFlow<Backup?>(null)
    val pending: StateFlow<Backup?> = _pending

    /** Shows [text] in the screen's message dialog. */
    fun showMessage(text: String) {
        _message.value = text
    }

    fun clearMessage() {
        _message.value = null
    }

    fun exportTo(uri: Uri) {
        viewModelScope.launch(LogFailures) {
            _message.value = runCatching {
                app.backupService.writeTo(uri)
                "Backup saved"
            }.getOrElse { "Could not save the backup" }
        }
    }

    /** Picks the folder for the daily backup. Null turns it off. */
    fun setAutoBackupFolder(uri: Uri?) {
        viewModelScope.launch(LogFailures) {
            if (uri != null) {
                runCatching {
                    app.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                }
            }
            app.settingsStore.update { it.copy(autoBackupFolder = uri?.toString()) }
            AutoBackupWorker.sync(app, uri?.toString())
            if (uri != null) _message.value = runCatching { app.backupService.writeToFolder(uri); "Backup saved to the folder" }.getOrElse { "Could not write to that folder" }
        }
    }

    fun readBackup(uri: Uri) {
        viewModelScope.launch(LogFailures) {
            // Reading and parsing a whole backup is more than the main thread should do.
            val backup = runCatching {
                withContext(Dispatchers.IO) { app.contentResolver.openInputStream(uri)!!.use { String(it.readBytes()) } }
            }.getOrNull()?.let { text -> withContext(Dispatchers.Default) { decodeBackup(text) } }
            if (backup == null) _message.value = "That file is not a backup from this app" else _pending.value = backup
        }
    }

    fun cancelRestore() {
        _pending.value = null
    }

    fun confirmRestore() {
        val backup = _pending.value ?: return
        _pending.value = null
        viewModelScope.launch(LogFailures) {
            _message.value = runCatching {
                app.backupService.restore(backup)
                afterRestore()
                "Backup restored"
            }.getOrElse { "Could not restore the backup" }
        }
    }

    /**
     * Puts the restored settings to work now instead of at the next launch: the chapter check runs on
     * the restored interval, the downloader drains a restored queue (Wi-Fi-only still applies), and the
     * reading-goal reminder follows the restored goal.
     */
    private suspend fun afterRestore() {
        val restored = app.settingsStore.current()
        NewChaptersWorker.schedule(app, restored.checkIntervalMinutes)
        DownloadWorker.start(app, restored.downloadWifiOnly)
        GoalReminderWorker.sync(app, restored.goalReminderHour)
    }

    private val _pendingArchive = MutableStateFlow<Uri?>(null)
    val pendingArchive: StateFlow<Uri?> = _pendingArchive

    /** Writes the full zip archive (data, covers, downloaded pages) to [uri]. */
    fun exportArchive(uri: Uri) {
        viewModelScope.launch(LogFailures) {
            _message.value = runCatching {
                app.backupArchive.exportTo(uri)
                "Backup archive saved"
            }.getOrElse { "Could not save the backup archive" }
        }
    }

    /** Reads the backup out of a zip archive at [uri], then asks before replacing anything. */
    fun readArchive(uri: Uri) {
        viewModelScope.launch(LogFailures) {
            val backup = runCatching { app.backupArchive.readArchive(uri) }.getOrNull()
            if (backup == null) {
                _message.value = "That file is not a backup archive from this app"
            } else {
                _pendingArchive.value = uri
                _pending.value = backup
            }
        }
    }

    fun cancelArchiveRestore() {
        _pending.value = null
        _pendingArchive.value = null
    }

    fun confirmArchiveRestore() {
        val backup = _pending.value ?: return
        val uri = _pendingArchive.value ?: return
        _pending.value = null
        _pendingArchive.value = null
        viewModelScope.launch(LogFailures) {
            _message.value = runCatching {
                app.backupArchive.restoreArchive(uri, backup)
                afterRestore()
                "Backup archive restored"
            }.getOrElse { "Could not restore the backup archive" }
        }
    }

    /** Removes every series from the Recent list. Subscriptions, lists, and collections stay. */
    fun clearHistory() {
        viewModelScope.launch(LogFailures) {
            val ids = app.libraryStore.current().recent.mapTo(mutableSetOf()) { it.id }
            app.libraryStore.removeRecent(ids)
            _message.value = "Reading history cleared"
        }
    }

    /** Removes every copy of each duplicated series except the suggested one to keep. */
    fun removeDuplicateCopies(group: DuplicateGroup) {
        viewModelScope.launch(LogFailures) {
            val keep = suggestedKeep(group)
            val drop = group.series.map { it.id }.toSet() - keep.id
            app.libraryStore.removeRecent(drop)
            app.libraryStore.removeSubscribed(drop)
            app.libraryStore.removeLists(drop)
            _message.value = "Removed ${drop.size} duplicate ${if (drop.size == 1) "copy" else "copies"}"
        }
    }

    fun refreshCacheSize() {
        viewModelScope.launch(LogFailures) { _cacheBytes.value = withContext(Dispatchers.IO) { measureCache() } }
    }

    fun clearCache() {
        viewModelScope.launch(LogFailures) {
            withContext(Dispatchers.IO) {
                app.apiClient.cache?.evictAll()
                val loader = SingletonImageLoader.get(app)
                loader.diskCache?.clear()
                loader.memoryCache?.clear()
            }
            _cacheBytes.value = withContext(Dispatchers.IO) { measureCache() }
        }
    }

    private fun measureCache(): Long =
        (app.apiClient.cache?.size() ?: 0L) + (SingletonImageLoader.get(app).diskCache?.size ?: 0L)
}

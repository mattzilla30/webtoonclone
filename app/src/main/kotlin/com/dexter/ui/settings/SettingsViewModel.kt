package com.dexter.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.SingletonImageLoader
import com.dexter.DexterApp
import com.dexter.data.Accounts
import com.dexter.data.Backup
import com.dexter.data.HttpStatusException
import com.dexter.data.LibraryData
import com.dexter.data.Settings
import com.dexter.data.decodeBackup
import com.dexter.notify.AutoBackupWorker
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
                "Backup restored"
            }.getOrElse { "Could not restore the backup" }
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

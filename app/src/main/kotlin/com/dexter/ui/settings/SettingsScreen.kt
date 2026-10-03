package com.dexter.ui.settings

import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dexter.R
import com.dexter.data.A11yPrefs
import com.dexter.data.ChapterBlacklist
import com.dexter.data.ContentRatings
import com.dexter.data.Languages
import com.dexter.data.LibraryStore
import com.dexter.data.LockMode
import com.dexter.data.NasShareStore
import com.dexter.data.PowerPrefs
import com.dexter.data.QolPrefs
import com.dexter.data.ReadingStatus
import com.dexter.data.archiveFileName
import com.dexter.data.findDuplicateSeries
import com.dexter.data.formatBytes
import com.dexter.data.libraryText
import com.dexter.data.resetReaderSettings
import com.dexter.notify.CHANNEL_ID
import com.dexter.ui.AppLock
import com.dexter.ui.AppTopBar
import com.dexter.ui.ChoiceChip
import com.dexter.ui.ConfirmDialog
import com.dexter.ui.PinSetupScreen
import com.dexter.ui.timeAgo
import org.koin.compose.koinInject
import java.time.Instant

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onOpenDownloads: () -> Unit, onOpenStats: () -> Unit, onOpenErrors: () -> Unit, onOpenStorage: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    val cacheBytes by viewModel.cacheBytes.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refreshCacheSize() }
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val pendingArchive by viewModel.pendingArchive.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val release by viewModel.release.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.exportTo(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.readBackup(uri)
    }
    val mihonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importMihon(uri)
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.setAutoBackupFolder(uri)
    }
    val archiveExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) viewModel.exportArchive(uri)
    }
    val archiveImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.readArchive(uri)
    }
    var pickTag by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmResetReader by remember { mutableStateOf(false) }
    if (confirmResetReader) {
        ConfirmDialog(
            title = "Reset reader options?",
            text = "Background, dimming, auto-scroll, volume keys, orientation, screen on, page gap and prefetch go back to their defaults. Per-series looks and reading modes stay.",
            confirmLabel = "Reset",
            onConfirm = { viewModel.update(::resetReaderSettings) },
            onDismiss = { confirmResetReader = false },
        )
    }
    if (confirmClear) {
        ConfirmDialog(
            title = "Clear reading history?",
            text = "This empties the Recent list. Subscriptions, lists, and collections stay.",
            confirmLabel = "Clear",
            onConfirm = viewModel::clearHistory,
            onDismiss = { confirmClear = false },
        )
    }
    if (pickTag) {
        TagPickerDialog(
            blocked = settings.blockedTags,
            onToggle = { tag -> viewModel.update { it.copy(blockedTags = if (tag in it.blockedTags) it.blockedTags - tag else it.blockedTags + tag) } },
            onDismiss = { pickTag = false },
        )
    }
    pending?.let { backup ->
        AlertDialog(
            onDismissRequest = { if (pendingArchive != null) viewModel.cancelArchiveRestore() else viewModel.cancelRestore() },
            title = { Text(stringResource(R.string.restore_this_backup)) },
            text = {
                Text(
                    if (pendingArchive != null)
                        "It holds the library, settings, reading positions, history, downloads, and covers from the archive. Your current library and settings will be replaced."
                    else
                        "It has ${backup.library.subscribed.size} subscriptions, ${backup.library.lists.size} listed series, and ${backup.library.recent.size} recent reads. " +
                            "Your current library and settings will be replaced.",
                )
            },
            confirmButton = { TextButton(onClick = { if (pendingArchive != null) viewModel.confirmArchiveRestore() else viewModel.confirmRestore() }) { Text(stringResource(R.string.restore)) } },
            dismissButton = { TextButton(onClick = { if (pendingArchive != null) viewModel.cancelArchiveRestore() else viewModel.cancelRestore() }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    release?.let { latest ->
        AlertDialog(
            onDismissRequest = viewModel::dismissRelease,
            title = { Text("Dexter ${latest.tag.removePrefix("v")}") },
            text = { Text(latest.name ?: "A newer version is ready to download.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissRelease()
                    context.startActivity(Intent(Intent.ACTION_VIEW, (latest.apk ?: latest.page).toUri()))
                }) { Text(if (latest.apk != null) "Download" else "Open release") }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissRelease) { Text("Later") } },
        )
    }
    message?.let { text ->
        AlertDialog(
            onDismissRequest = viewModel::clearMessage,
            text = { Text(text) },
            confirmButton = { TextButton(onClick = viewModel::clearMessage) { Text(stringResource(R.string.ok)) } },
        )
    }

    var query by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        AppTopBar(stringResource(R.string.settings))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Search settings") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, contentDescription = "Clear search") }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )
        CompositionLocalProvider(LocalSettingsQuery provides query) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                AppearanceSection(settings, viewModel::update)

                ReadingSection(settings, viewModel::update)

                LibraryExtrasSection(settings, viewModel::update)

                ReaderExtrasSection(settings, viewModel::update)

                ReaderUiSection()

                run {
                    val qol = remember(context) { QolPrefs(context) }
                    QolSettingsSection(qol)
                    val nas = remember(context) { NasShareStore(context) }
                    NasSharesSection(nas)
                    val libraryStore: LibraryStore = koinInject()
                    LocalImportSection(qol, libraryStore)
                    WebDavSection(qol)
                    CloudOAuthSection(qol)
                }

                run {
                    val a11y = remember(context) { A11yPrefs(context) }
                    A11ySection(a11y)
                    val power = remember(context) { PowerPrefs(context) }
                    val blacklist = remember(context) { ChapterBlacklist(context) }
                    val duplicates = remember(library) {
                        findDuplicateSeries((library.recent + library.subscribed + library.lists).distinctBy { it.id })
                    }
                    PowerSection(
                        prefs = power,
                        blacklist = blacklist,
                        onOpenStorage = onOpenStorage,
                        duplicates = duplicates,
                        onRemoveDuplicateCopies = viewModel::removeDuplicateCopies,
                    )
                }

                SectionTitle("Privacy")
                SwitchRow("Incognito", "Read without saving history, reading positions, or stats.", settings.incognito) { on ->
                    viewModel.update { it.copy(incognito = on) }
                }
                SectionTitle("App lock")
                SwitchRow("App lock", "Ask for your fingerprint, face, or PIN when Dexter opens or returns to the foreground.", settings.appLock) { on ->
                    // Turning the lock on or off asks first, so it only changes in your hands.
                    AppLock.authenticateBiometric(context, if (on) "Turn on app lock" else "Turn off app lock") { passed ->
                        if (passed) {
                            AppLock.locked = false
                            viewModel.update { it.copy(appLock = on) }
                        } else {
                            viewModel.showMessage("App lock needs a fingerprint, face, or screen lock set up on this phone.")
                        }
                    }
                }
                if (settings.appLock) {
                    ChoiceRow(
                        "Unlock with",
                        listOf(
                            LockMode.BiometricOrDeviceCredential to "Phone unlock",
                            LockMode.AppPin to "App PIN",
                        ),
                        settings.lockMode,
                    ) { mode -> viewModel.update { it.copy(lockMode = mode) } }
                    if (settings.lockMode == LockMode.AppPin) {
                        var showPinSetup by remember { mutableStateOf(false) }
                        InfoRow("App PIN", subtitle = "Set the PIN Dexter asks for.", onClick = { showPinSetup = true })
                        if (showPinSetup) {
                            PinSetupScreen(onDone = { showPinSetup = false })
                        }
                    }
                    ChoiceRow(
                        "Lock again after",
                        listOf(15_000L to "15 seconds", 30_000L to "30 seconds", 60_000L to "1 minute", 300_000L to "5 minutes"),
                        settings.relockTimeoutMs,
                    ) { ms -> viewModel.update { it.copy(relockTimeoutMs = ms) } }
                }

                SectionTitle("Titles")
                SwitchRow("Original titles", "Show the romanized original title instead of the English one.", settings.originalTitles) { on ->
                    viewModel.update { it.copy(originalTitles = on) }
                }

                ChoiceRow(
                    "Language",
                    Languages.map { it.code to it.name },
                    settings.language,
                ) { code -> viewModel.update { it.copy(language = code) } }
                Searchable("Language") {
                    Text(
                        "Chapters, titles, and descriptions use this language when MangaDex has it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Searchable("Content ratings", "adult", "erotica", "pornographic", "suggestive", "safe") {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Text(stringResource(R.string.content_ratings), style = MaterialTheme.typography.bodyMedium)
                        FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ContentRatings.forEach { rating ->
                                val on = rating in settings.contentRatings
                                ChoiceChip(rating.replaceFirstChar { it.uppercase() }, on) {
                                    viewModel.update { current ->
                                        val next = if (on) current.contentRatings - rating else current.contentRatings + rating
                                        // Keep at least one, so the lists never go blank.
                                        if (next.isEmpty()) current else current.copy(contentRatings = next)
                                    }
                                }
                            }
                        }
                        Text(
                            "Choose which MangaDex ratings appear in lists and search.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }

                SectionTitle("Blocking")
                Searchable("Blocking", "blocked tags", "block a tag", "scanlation groups", "hidden series") {
                    Text(stringResource(R.string.blocked_tags_stay_out_of_lists_and_searc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        settings.blockedTags.sorted().forEach { tag ->
                            ChoiceChip("$tag  ×", true) { viewModel.update { it.copy(blockedTags = it.blockedTags - tag) } }
                        }
                        ChoiceChip("+ Block a tag", false) { pickTag = true }
                    }
                    if (settings.blockedGroups.isNotEmpty()) {
                        Text(stringResource(R.string.blocked_scanlation_groups_tap_to_unblock), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            settings.blockedGroups.sorted().forEach { group ->
                                ChoiceChip("$group  ×", true) { viewModel.update { it.copy(blockedGroups = it.blockedGroups - group) } }
                            }
                        }
                    }
                    if (settings.hiddenSeries.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${settings.hiddenSeries.size} hidden series", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            TextButton(onClick = { viewModel.update { it.copy(hiddenSeries = emptySet()) } }) { Text(stringResource(R.string.show_all_again)) }
                        }
                    }
                }

                SectionTitle("Notifications")
                SwitchRow(
                    "New chapter notifications",
                    "Check subscribed series in the background. You can also silence one series on its page.",
                    library.notificationsEnabled,
                ) { on -> viewModel.setNotifications(on) }
                ChoiceRow(
                    "Check every",
                    listOf(15 to "15 min", 30 to "30 min", 60 to "1 hour", 360 to "6 hours", 720 to "12 hours"),
                    settings.checkIntervalMinutes,
                ) { minutes -> viewModel.setCheckInterval(minutes) }
                InfoRow(
                    title = "Check now",
                    subtitle = if (library.lastCheckAt == 0L) "No check has finished yet." else "Last check: ${timeAgo(Instant.ofEpochMilli(library.lastCheckAt))}",
                    action = { TextButton(onClick = viewModel::checkNow) { Text("Check") } },
                )
                Searchable("Keep these quiet", "mute", "notifications", "collections", "dropped") {
                    Text("Keep these quiet", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                    FlowRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReadingStatus.entries.forEach { status ->
                            val muted = status in settings.mutedStatuses
                            ChoiceChip(status.label, muted) {
                                viewModel.update { it.copy(mutedStatuses = if (muted) it.mutedStatuses - status else it.mutedStatuses + status) }
                            }
                        }
                        library.collections.keys.sorted().forEach { name ->
                            val muted = name in settings.mutedCollections
                            ChoiceChip(name, muted) {
                                viewModel.update { it.copy(mutedCollections = if (muted) it.mutedCollections - name else it.mutedCollections + name) }
                            }
                        }
                    }
                    Text(
                        "Series with a selected status or in a selected collection never notify.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SwitchRow("Combine into one notification", "One summary for all new chapters found in a check.", settings.notificationDigest) { on ->
                    viewModel.update { it.copy(notificationDigest = on) }
                }
                InfoRow(
                    title = "Sound and alerts",
                    subtitle = "Open Android's settings for new chapter notifications.",
                    onClick = {
                        val intent = Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                            .putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, CHANNEL_ID)
                        runCatching { context.startActivity(intent) }
                    },
                )
                SwitchRow("Quiet hours", "Hold notifications during these hours. New chapters notify once quiet hours end.", settings.quietHours) { on ->
                    viewModel.update { it.copy(quietHours = on) }
                }
                if (settings.quietHours) {
                    HourStepper("From", settings.quietStartHour) { hour -> viewModel.update { it.copy(quietStartHour = hour) } }
                    HourStepper("Until", settings.quietEndHour) { hour -> viewModel.update { it.copy(quietEndHour = hour) } }
                }

                SectionTitle("Storage")
                InfoRow(
                    title = stringResource(R.string.cache),
                    subtitle = cacheBytes?.let(::formatBytes) ?: "Measuring…",
                    action = { TextButton(onClick = { viewModel.clearCache() }) { Text(stringResource(R.string.clear_cache)) } },
                )

                SwitchRow("Save the next chapter while reading", "Queues the next chapter in the background so it is ready offline.", settings.autoDownloadNext) { on ->
                    viewModel.update { it.copy(autoDownloadNext = on) }
                }
                SwitchRow("Save on Wi-Fi only", "Downloads wait for an unmetered connection.", settings.downloadWifiOnly) { on ->
                    viewModel.update { it.copy(downloadWifiOnly = on) }
                }
                InfoRow(title = stringResource(R.string.downloaded_chapters), onClick = onOpenDownloads)
                ChoiceRow(
                    "Daily reading goal",
                    listOf(0 to "Off", 1 to "1", 2 to "2", 3 to "3", 5 to "5", 10 to "10"),
                    settings.dailyGoal,
                ) { goal -> viewModel.update { it.copy(dailyGoal = goal) } }
                if (settings.dailyGoal > 0) {
                    ChoiceRow(
                        "Remind me when I am short of the goal",
                        listOf(-1 to "Off", 12 to "12:00", 18 to "18:00", 20 to "20:00", 22 to "22:00"),
                        settings.goalReminderHour,
                    ) { hour -> viewModel.setGoalReminder(hour) }
                }
                InfoRow(title = stringResource(R.string.reading_stats), onClick = onOpenStats)
                InfoRow(title = "Reset reader options", subtitle = "Background, dimming, auto-scroll and more.", onClick = { confirmResetReader = true })
                InfoRow(title = "Error log", subtitle = "What went wrong lately, to read or share.", onClick = onOpenErrors)
                InfoRow(title = "Clear reading history", subtitle = "Empties the Recent list.", onClick = { confirmClear = true })

                MangaDexAccountSection(
                    login = accounts.mangaDex,
                    busy = busy,
                    onSignIn = viewModel::signInMangaDex,
                    onSync = viewModel::syncMangaDex,
                    onSignOut = viewModel::signOutMangaDex,
                    onReadMarkers = viewModel::setReadMarkers,
                )

                TrackingSection(
                    accounts = accounts,
                    onAniList = { id -> viewModel.signInAniList(id) { url -> context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) } },
                    onMal = { id -> viewModel.signInMal(id) { url -> context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) } },
                    onSignOutAniList = viewModel::signOutAniList,
                    onSignOutMal = viewModel::signOutMal,
                )

                DownloadSyncSection(
                    settings = settings,
                    subscribed = library.subscribed,
                    update = viewModel::update,
                    onExportArchive = { archiveExportLauncher.launch(archiveFileName()) },
                    onImportArchive = { archiveImportLauncher.launch(arrayOf("application/zip", "*/*")) },
                )

                SectionTitle("Backup")
                Searchable("Backup", "restore", "save backup", "export", "import") {
                    Text(
                        "Save your library, lists, reading positions, and settings to a file. Restoring replaces what is on this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { exportLauncher.launch("dexter-backup.json") }) { Text(stringResource(R.string.save_backup)) }
                        OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text(stringResource(R.string.restore_backup)) }
                    }
                }
                InfoRow(
                    title = "Import from Mihon or Tachiyomi",
                    subtitle = "Adds the MangaDex series in a .tachibk backup, with categories and last read chapters.",
                    onClick = { mihonLauncher.launch(arrayOf("*/*")) },
                )
                InfoRow(
                    title = "Share my library as text",
                    subtitle = "A list of your titles to send to a friend or keep in a note.",
                    onClick = {
                        val text = libraryText(library)
                        if (text.isBlank()) {
                            viewModel.showMessage("Your library is empty. Read, subscribe to, or list a series first.")
                        } else {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            context.startActivity(Intent.createChooser(send, null))
                        }
                    },
                )
                InfoRow(
                    title = stringResource(R.string.daily_backup_folder),
                    subtitle = if (settings.autoBackupFolder == null) "Off" else "On. Writes dexter-backup.json once a day.",
                    action = {
                        TextButton(onClick = { folderLauncher.launch(null) }) { Text(stringResource(R.string.choose)) }
                        if (settings.autoBackupFolder != null) {
                            TextButton(onClick = { viewModel.setAutoBackupFolder(null) }) { Text(stringResource(R.string.turn_off)) }
                        }
                    },
                )
                val version = remember(context) {
                    runCatching { context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0)).versionName }.getOrNull()
                }
                InfoRow(
                    title = "Check for updates",
                    subtitle = "Looks for a newer release on GitHub.",
                    action = { TextButton(onClick = { viewModel.checkForUpdate(version ?: "0") }, enabled = !busy) { Text("Check") } },
                )
                Text(
                    "Dexter" + (version?.let { " $it" } ?: ""),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

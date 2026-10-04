package com.dexter.ui.settings

import android.Manifest
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
import androidx.compose.foundation.layout.widthIn
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
import com.dexter.platform.WatchLink
import com.dexter.ui.AppLock
import com.dexter.ui.ChoiceChip
import com.dexter.ui.ConfirmDialog
import com.dexter.ui.FilterField
import com.dexter.ui.PinSetupScreen
import com.dexter.ui.openLink
import com.dexter.ui.timeAgo
import org.koin.compose.koinInject
import java.time.Instant

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onClose: () -> Unit, onOpenDownloads: () -> Unit, onOpenStats: () -> Unit, onOpenErrors: () -> Unit, onOpenStorage: () -> Unit) {
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
    // Turning the watch link on asks for Bluetooth permission first, and stays off without it.
    val bluetoothLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            viewModel.update { it.copy(watchLink = true) }
        } else {
            viewModel.showMessage("The watch link needs Bluetooth permission to reach your watch.")
        }
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
                    context.openLink(latest.apk ?: latest.page)
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

    val version = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0)).versionName }.getOrNull()
    }
    var query by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        // No title bar: the bubble opens from the Settings button, so the search box and Close lead it.
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterField(query, { query = it }, "Search settings", Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close settings") }
        }
        // Every setting on its own card, A to Z by name. The search box narrows it to matching cards.
        CompositionLocalProvider(LocalSettingsQuery provides query) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SortedSettingsColumn(
                    Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                    empty = {
                        Text(
                            "No settings match \"${query.trim()}\".",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        )
                    },
                ) {
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
                        onAniList = { id -> viewModel.signInAniList(id) { url -> context.openLink(url) } },
                        onMal = { id -> viewModel.signInMal(id) { url -> context.openLink(url) } },
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

                    LicensesSection()

                    SettingsBlock("Watch") {
                        SwitchRow(
                            "Watch app",
                            "Turn pages and see your progress from Dexter on a paired Wear OS watch, over Bluetooth.",
                            settings.watchLink,
                            keywords = listOf("wear os", "bluetooth", "connect"),
                        ) { on ->
                            if (on && !WatchLink.hasPermission(context)) {
                                bluetoothLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                            } else {
                                viewModel.update { it.copy(watchLink = on) }
                            }
                        }
                    }

                    SettingsBlock("Privacy") {
                        SwitchRow("Incognito", "Read without saving history, reading positions, or stats.", settings.incognito, keywords = listOf("private")) { on ->
                            viewModel.update { it.copy(incognito = on) }
                        }
                        SwitchRow(
                            "App lock",
                            "Ask for your fingerprint, face, or PIN when Dexter opens or returns to the foreground.",
                            settings.appLock,
                            keywords = listOf("biometric", "unlock", "pin", "password", "lock again", "security"),
                            more = if (!settings.appLock) {
                                null
                            } else {
                                {
                                    SubChoice(
                                        "Unlock with",
                                        listOf(LockMode.BiometricOrDeviceCredential to "Phone unlock", LockMode.AppPin to "App PIN"),
                                        settings.lockMode,
                                    ) { mode -> viewModel.update { it.copy(lockMode = mode) } }
                                    if (settings.lockMode == LockMode.AppPin) {
                                        var showPinSetup by remember { mutableStateOf(false) }
                                        SubLink("App PIN", "Set the PIN Dexter asks for.") { showPinSetup = true }
                                        if (showPinSetup) PinSetupScreen(onDone = { showPinSetup = false })
                                    }
                                    SubChoice(
                                        "Lock again after",
                                        listOf(15_000L to "15 seconds", 30_000L to "30 seconds", 60_000L to "1 minute", 300_000L to "5 minutes"),
                                        settings.relockTimeoutMs,
                                    ) { ms -> viewModel.update { it.copy(relockTimeoutMs = ms) } }
                                }
                            },
                        ) { on ->
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
                    }

                    SettingsBlock("Content") {
                        SwitchRow(
                            "Original titles",
                            "Show the romanized original title instead of the English one.",
                            settings.originalTitles,
                            keywords = listOf("romaji", "japanese", "names"),
                        ) { on -> viewModel.update { it.copy(originalTitles = on) } }

                        ChoiceRow(
                            "Language",
                            Languages.map { it.code to it.name },
                            settings.language,
                            summary = "Chapters, titles, and descriptions use this language when MangaDex has it.",
                            keywords = listOf("translation"),
                        ) { code -> viewModel.update { it.copy(language = code) } }

                        Setting(
                            stringResource(R.string.content_ratings),
                            "Choose which MangaDex ratings appear in lists and search. At least one stays on.",
                            keywords = listOf("adult", "nsfw", "mature") + ContentRatings,
                        ) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        }

                        Setting("Blocked tags", stringResource(R.string.blocked_tags_stay_out_of_lists_and_searc), keywords = listOf("block", "hide", "genre") + settings.blockedTags) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                settings.blockedTags.sorted().forEach { tag ->
                                    ChoiceChip("$tag  ×", true) { viewModel.update { it.copy(blockedTags = it.blockedTags - tag) } }
                                }
                                ChoiceChip("+ Block a tag", false) { pickTag = true }
                            }
                        }
                        if (settings.blockedGroups.isNotEmpty()) {
                            Setting(
                                "Blocked scanlation groups",
                                stringResource(R.string.blocked_scanlation_groups_tap_to_unblock),
                                keywords = listOf("block", "scanlators") + settings.blockedGroups,
                            ) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    settings.blockedGroups.sorted().forEach { group ->
                                        ChoiceChip("$group  ×", true) { viewModel.update { it.copy(blockedGroups = it.blockedGroups - group) } }
                                    }
                                }
                            }
                        }
                        if (settings.hiddenSeries.isNotEmpty()) {
                            InfoRow(
                                "Hidden series",
                                "${settings.hiddenSeries.size} series stay out of lists and search.",
                                keywords = listOf("block", "unhide"),
                                action = { FilledTonalButton(onClick = { viewModel.update { it.copy(hiddenSeries = emptySet()) } }) { Text(stringResource(R.string.show_all_again)) } },
                            )
                        }
                    }

                    SettingsBlock("Notifications") {
                        SwitchRow(
                            "New chapter notifications",
                            "Check subscribed series in the background. You can also silence one series on its page.",
                            library.notificationsEnabled,
                            keywords = listOf("alerts", "updates"),
                        ) { on -> viewModel.setNotifications(on) }
                        ChoiceRow(
                            "New chapter check interval",
                            listOf(15 to "15 min", 30 to "30 min", 60 to "1 hour", 360 to "6 hours", 720 to "12 hours"),
                            settings.checkIntervalMinutes,
                            summary = "How often Dexter looks for new chapters of your subscriptions.",
                            keywords = listOf("check every", "frequency"),
                        ) { minutes -> viewModel.setCheckInterval(minutes) }
                        InfoRow(
                            "Check for new chapters now",
                            if (library.lastCheckAt == 0L) "No check has finished yet." else "Last check: ${timeAgo(Instant.ofEpochMilli(library.lastCheckAt))}",
                            keywords = listOf("refresh", "update"),
                            action = { FilledTonalButton(onClick = viewModel::checkNow) { Text("Check") } },
                        )
                        Setting(
                            "Muted statuses and collections",
                            "Series with a selected status or in a selected collection never notify.",
                            keywords = listOf("keep these quiet", "mute", "silence") + ReadingStatus.entries.map { it.label } + library.collections.keys,
                        ) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        }
                        SwitchRow(
                            "Combine notifications",
                            "One summary for all new chapters found in a check.",
                            settings.notificationDigest,
                            keywords = listOf("digest", "summary", "one notification"),
                        ) { on -> viewModel.update { it.copy(notificationDigest = on) } }
                        InfoRow(
                            "Notification sound and alerts",
                            "Open Android's settings for new chapter notifications.",
                            keywords = listOf("ringtone", "vibrate", "channel"),
                            onClick = {
                                val intent = Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    .putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, CHANNEL_ID)
                                runCatching { context.startActivity(intent) }
                            },
                        )
                        SwitchRow(
                            "Quiet hours",
                            "Hold notifications during these hours. New chapters notify once quiet hours end.",
                            settings.quietHours,
                            keywords = listOf("do not disturb", "night", "sleep", "from", "until"),
                            more = if (!settings.quietHours) {
                                null
                            } else {
                                {
                                    HourStepper("From", settings.quietStartHour) { hour -> viewModel.update { it.copy(quietStartHour = hour) } }
                                    HourStepper("Until", settings.quietEndHour) { hour -> viewModel.update { it.copy(quietEndHour = hour) } }
                                }
                            },
                        ) { on -> viewModel.update { it.copy(quietHours = on) } }
                    }

                    SettingsBlock("Storage") {
                        InfoRow(
                            stringResource(R.string.cache),
                            cacheBytes?.let(::formatBytes) ?: "Measuring…",
                            keywords = listOf("space", "clear", "images"),
                            action = { FilledTonalButton(onClick = { viewModel.clearCache() }) { Text(stringResource(R.string.clear_cache)) } },
                        )
                        SwitchRow(
                            "Save the next chapter while reading",
                            "Queues the next chapter in the background so it is ready offline.",
                            settings.autoDownloadNext,
                            keywords = listOf("download", "offline", "prefetch"),
                        ) { on -> viewModel.update { it.copy(autoDownloadNext = on) } }
                        SwitchRow(
                            "Download on Wi-Fi only",
                            "Downloads wait for an unmetered connection.",
                            settings.downloadWifiOnly,
                            keywords = listOf("save", "mobile data", "metered"),
                        ) { on -> viewModel.update { it.copy(downloadWifiOnly = on) } }
                        InfoRow(stringResource(R.string.downloaded_chapters), "See and delete saved chapters.", onClick = onOpenDownloads, keywords = listOf("offline", "saved"))
                        InfoRow("Clear reading history", "Empties the Recent list. Subscriptions and lists stay.", onClick = { confirmClear = true }, keywords = listOf("recent", "delete"))
                        InfoRow("Error log", "What went wrong lately, to read or share.", onClick = onOpenErrors, keywords = listOf("bug", "crash", "debug"))
                        InfoRow(
                            "Reset reader options",
                            "Background, dimming, auto-scroll and more go back to their defaults.",
                            onClick = { confirmResetReader = true },
                            keywords = listOf("defaults", "restore"),
                        )
                    }

                    SettingsBlock("Goals") {
                        ChoiceRow(
                            "Daily reading goal",
                            listOf(0 to "Off", 1 to "1", 2 to "2", 3 to "3", 5 to "5", 10 to "10"),
                            settings.dailyGoal,
                            summary = "Chapters to read each day.",
                            keywords = listOf("reminder", "streak", "remind me"),
                            more = if (settings.dailyGoal <= 0) {
                                null
                            } else {
                                {
                                    SubChoice(
                                        "Remind me when I am short of the goal",
                                        listOf(-1 to "Off", 12 to "12:00", 18 to "18:00", 20 to "20:00", 22 to "22:00"),
                                        settings.goalReminderHour,
                                    ) { hour -> viewModel.setGoalReminder(hour) }
                                }
                            },
                        ) { goal -> viewModel.update { it.copy(dailyGoal = goal) } }
                        InfoRow(stringResource(R.string.reading_stats), "Time read, chapters, and streaks.", onClick = onOpenStats, keywords = listOf("statistics", "history"))
                    }

                    SettingsBlock("Backup") {
                        Setting(
                            "Backup file",
                            "Save your library, lists, reading positions, and settings to a file. Restoring replaces what is on this device.",
                            keywords = listOf("restore", "export", "import", "json"),
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(onClick = { exportLauncher.launch("dexter-backup.json") }) { Text(stringResource(R.string.save_backup)) }
                                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text(stringResource(R.string.restore_backup)) }
                            }
                        }
                        Setting(
                            stringResource(R.string.daily_backup_folder),
                            if (settings.autoBackupFolder == null) "Off. Pick a folder to write dexter-backup.json there once a day." else "On. Writes dexter-backup.json once a day.",
                            keywords = listOf("automatic", "auto backup"),
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(onClick = { folderLauncher.launch(null) }) { Text(stringResource(R.string.choose)) }
                                if (settings.autoBackupFolder != null) {
                                    OutlinedButton(onClick = { viewModel.setAutoBackupFolder(null) }) { Text(stringResource(R.string.turn_off)) }
                                }
                            }
                        }
                        InfoRow(
                            "Import from Mihon or Tachiyomi",
                            "Adds the MangaDex series in a .tachibk backup, with categories and last read chapters.",
                            onClick = { mihonLauncher.launch(arrayOf("*/*")) },
                            keywords = listOf("tachibk", "migrate"),
                        )
                        InfoRow(
                            "Share my library as text",
                            "A list of your titles to send to a friend or keep in a note.",
                            keywords = listOf("export", "list"),
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
                    }

                    SettingsBlock("About") {
                        InfoRow(
                            "Check for updates",
                            "Dexter" + (version?.let { " $it" } ?: "") + ". Looks for a newer release on GitHub.",
                            keywords = listOf("version", "upgrade", "release"),
                            action = { FilledTonalButton(onClick = { viewModel.checkForUpdate(version ?: "0") }, enabled = !busy) { Text("Check") } },
                        )
                    }
                }
            }
        }
    }
}

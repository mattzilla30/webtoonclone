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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dexter.R
import com.dexter.data.ContentRatings
import com.dexter.data.Languages
import com.dexter.data.ReaderBackground
import com.dexter.data.ThemeMode
import com.dexter.data.formatBytes
import com.dexter.data.libraryText
import com.dexter.data.resetReaderSettings
import com.dexter.notify.CHANNEL_ID
import com.dexter.ui.AppTopBar
import com.dexter.ui.ChoiceChip
import com.dexter.ui.ConfirmDialog

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onOpenDownloads: () -> Unit, onOpenStats: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    val cacheBytes by viewModel.cacheBytes.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refreshCacheSize() }
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.exportTo(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.readBackup(uri)
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.setAutoBackupFolder(uri)
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
            onDismissRequest = viewModel::cancelRestore,
            title = { Text(stringResource(R.string.restore_this_backup)) },
            text = {
                Text(
                    "It has ${backup.library.subscribed.size} subscriptions, ${backup.library.lists.size} listed series, and ${backup.library.recent.size} recent reads. " +
                        "Your current library and settings will be replaced.",
                )
            },
            confirmButton = { TextButton(onClick = viewModel::confirmRestore) { Text(stringResource(R.string.restore)) } },
            dismissButton = { TextButton(onClick = viewModel::cancelRestore) { Text(stringResource(R.string.cancel)) } },
        )
    }
    message?.let { text ->
        AlertDialog(
            onDismissRequest = viewModel::clearMessage,
            text = { Text(text) },
            confirmButton = { TextButton(onClick = viewModel::clearMessage) { Text(stringResource(R.string.ok)) } },
        )
    }

    Column(Modifier.fillMaxSize()) {
        AppTopBar(stringResource(R.string.settings))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SectionTitle("Appearance")
            ChoiceRow(
                "Theme",
                listOf(
                    ThemeMode.Dark to "Dark",
                    ThemeMode.Black to "True black",
                    ThemeMode.Light to "Light",
                    ThemeMode.System to "System",
                ),
                settings.theme,
            ) { choice -> viewModel.update { it.copy(theme = choice) } }
            SwitchRow("Material You colors", "Use your wallpaper colors for backgrounds. Accents stay green.", settings.dynamicColor) { on ->
                viewModel.update { it.copy(dynamicColor = on) }
            }

            SectionTitle("Reading")
            SwitchRow("Data saver", "Load smaller page images. Applies to chapters you open next.", settings.dataSaver) { on ->
                viewModel.update { it.copy(dataSaver = on) }
            }
            ChoiceRow(
                "Reader background",
                listOf(ReaderBackground.Dark to "Dark", ReaderBackground.Black to "Black", ReaderBackground.White to "White"),
                settings.readerBackground,
            ) { choice -> viewModel.update { it.copy(readerBackground = choice) } }
            SwitchRow("Volume keys scroll", "Volume up and down move the reader by a page.", settings.volumeKeys) { on ->
                viewModel.update { it.copy(volumeKeys = on) }
            }
            SwitchRow(
                "Report image loads to MangaDex",
                "MangaDex asks apps to say whether page images loaded. A report holds the image address, its size, and how long it took.",
                settings.reportImageLoads,
            ) { on -> viewModel.update { it.copy(reportImageLoads = on) } }

            SectionTitle("Titles")
            SwitchRow("Original titles", "Show the romanized original title instead of the English one.", settings.originalTitles) { on ->
                viewModel.update { it.copy(originalTitles = on) }
            }

            ChoiceRow(
                "Language",
                Languages.map { it.code to it.name },
                settings.language,
            ) { code -> viewModel.update { it.copy(language = code) } }
            Text(
                "Chapters, titles, and descriptions use this language when MangaDex has it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

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
                    "Choose which MangaDex ratings appear in lists and search. Erotica and pornographic are on by default here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            SectionTitle("Blocking")
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

            SectionTitle("Notifications")
            SwitchRow(
                "New chapter notifications",
                "Check subscribed series about every 30 minutes. You can also silence one series on its page.",
                library.notificationsEnabled,
            ) { on -> viewModel.setNotifications(on) }
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
            InfoRow(title = stringResource(R.string.reading_stats), onClick = onOpenStats)
            InfoRow(title = "Reset reader options", subtitle = "Background, dimming, auto-scroll and more.", onClick = { confirmResetReader = true })
            InfoRow(title = "Clear reading history", subtitle = "Empties the Recent list.", onClick = { confirmClear = true })

            SectionTitle("Backup")
            Text(
                "Save your library, lists, reading positions, and settings to a file. Restoring replaces what is on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { exportLauncher.launch("dexter-backup.json") }) { Text(stringResource(R.string.save_backup)) }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text(stringResource(R.string.restore_backup)) }
            }
            InfoRow(
                title = "Share my library as text",
                subtitle = "A list of your titles to send to a friend or keep in a note.",
                onClick = {
                    val text = libraryText(library)
                    if (text.isNotBlank()) {
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

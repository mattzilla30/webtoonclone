package com.dexter.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dexter.R
import com.dexter.data.ContentRatings
import com.dexter.data.ContentTags
import com.dexter.data.Formats
import com.dexter.data.Genres
import com.dexter.data.Languages
import com.dexter.data.ReaderBackground
import com.dexter.data.ThemeMode
import com.dexter.data.Themes
import com.dexter.data.formatBytes
import com.dexter.ui.ChoiceChip
import com.dexter.ui.iconTap

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onOpenDownloads: () -> Unit, onOpenStats: () -> Unit) {
    val settings by viewModel.settings.collectAsState()
    val library by viewModel.library.collectAsState()
    val cacheBytes by viewModel.cacheBytes.collectAsState()
    LaunchedEffect(Unit) { viewModel.refreshCacheSize() }
    val message by viewModel.message.collectAsState()
    val pending by viewModel.pending.collectAsState()
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
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings), fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
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
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Column(Modifier.padding(vertical = 8.dp)) {
                Text(stringResource(R.string.content_ratings), fontSize = 14.sp)
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
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            SectionTitle("Blocking")
            Text(stringResource(R.string.blocked_tags_stay_out_of_lists_and_searc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                settings.blockedTags.sorted().forEach { tag ->
                    ChoiceChip("$tag  ×", true) { viewModel.update { it.copy(blockedTags = it.blockedTags - tag) } }
                }
                ChoiceChip("+ Block a tag", false) { pickTag = true }
            }
            if (settings.blockedGroups.isNotEmpty()) {
                Text(stringResource(R.string.blocked_scanlation_groups_tap_to_unblock), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    settings.blockedGroups.sorted().forEach { group ->
                        ChoiceChip("$group  ×", true) { viewModel.update { it.copy(blockedGroups = it.blockedGroups - group) } }
                    }
                }
            }
            if (settings.hiddenSeries.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${settings.hiddenSeries.size} hidden series", fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.show_all_again), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clickable { viewModel.update { it.copy(hiddenSeries = emptySet()) } })
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
            SwitchRow("Quiet hours", "Hold notifications during these hours. New chapters notify once quiet hours end.", settings.quietHours) { on ->
                viewModel.update { it.copy(quietHours = on) }
            }
            if (settings.quietHours) {
                HourStepper("From", settings.quietStartHour) { hour -> viewModel.update { it.copy(quietStartHour = hour) } }
                HourStepper("Until", settings.quietEndHour) { hour -> viewModel.update { it.copy(quietEndHour = hour) } }
            }

            SectionTitle("Storage")
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.cache), fontSize = 14.sp)
                    Text(
                        cacheBytes?.let(::formatBytes) ?: "Measuring...",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(stringResource(R.string.clear_cache), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clickable { viewModel.clearCache() })
            }

            SwitchRow("Save on Wi-Fi only", "Downloads wait for an unmetered connection.", settings.downloadWifiOnly) { on ->
                viewModel.update { it.copy(downloadWifiOnly = on) }
            }
            Row(Modifier.fillMaxWidth().clickable(onClick = onOpenDownloads).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.downloaded_chapters), fontSize = 14.sp, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }

            Row(Modifier.fillMaxWidth().clickable(onClick = onOpenStats).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.reading_stats), fontSize = 14.sp, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }

            SectionTitle("Backup")
            Text(
                "Save your library, lists, reading positions, and settings to a file. Restoring replaces what is on this device.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Text(stringResource(R.string.save_backup), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clickable { exportLauncher.launch("dexter-backup.json") })
                Text(stringResource(R.string.restore_backup), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clickable { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) })
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.daily_backup_folder), fontSize = 14.sp)
                    Text(
                        if (settings.autoBackupFolder == null) "Off" else "On. Writes dexter-backup.json once a day.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(stringResource(R.string.choose), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clickable { folderLauncher.launch(null) })
                if (settings.autoBackupFolder != null) {
                    Text(stringResource(R.string.turn_off), fontSize = 13.sp, modifier = Modifier.padding(start = 16.dp).clickable { viewModel.setAutoBackupFolder(null) })
                }
            }

            Text("", modifier = Modifier.padding(bottom = 24.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text.uppercase(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(top = 20.dp, bottom = 6.dp).semantics { heading() })
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontSize = 14.sp)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceRow(title: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(title, fontSize = 14.sp)
        FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, label) -> ChoiceChip(label, value == selected) { onSelect(value) } }
        }
    }
}

/** An hour of the day (0 to 23) with minus and plus buttons that wrap around midnight. */
@Composable
private fun HourStepper(label: String, hour: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text("-", fontSize = 20.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onChange((hour + 23) % 24) }.padding(horizontal = 16.dp))
        Text("%02d:00".format(hour), fontSize = 14.sp)
        Text("+", fontSize = 20.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onChange((hour + 1) % 24) }.padding(horizontal = 16.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagPickerDialog(blocked: Set<String>, onToggle: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.block_tags)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    (Genres.map { it.name } + Themes + Formats + ContentTags).distinct().sortedBy { it.lowercase() }.forEach { tag ->
                        ChoiceChip(tag, tag in blocked) { onToggle(tag) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
    )
}

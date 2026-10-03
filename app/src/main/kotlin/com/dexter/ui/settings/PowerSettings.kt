package com.dexter.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dexter.automation.DexterAutomation
import com.dexter.data.ChapterBlacklist
import com.dexter.data.DuplicateGroup
import com.dexter.data.PowerPrefs
import com.dexter.data.PowerState
import com.dexter.data.suggestedKeep
import com.dexter.ui.CardRow
import com.dexter.ui.ConfirmDialog
import kotlinx.coroutines.launch

/**
 * Power-user settings: gamepad page turning, Tasker automation intents, duplicate detection, the
 * chapter blacklist, and the storage analyzer. Wired into the Settings screen by calling
 * [PowerSection] next to the other sections; see the insertion snippet in the task report.
 *
 * @param onOpenStorage opens the storage analyzer screen.
 * @param duplicates duplicate library groups from [com.dexter.data.findDuplicateSeries].
 * @param onRemoveDuplicateCopies removes every copy of [DuplicateGroup] except its suggested keep.
 */
@Composable
internal fun PowerSection(
    prefs: PowerPrefs,
    blacklist: ChapterBlacklist,
    onOpenStorage: () -> Unit,
    duplicates: List<DuplicateGroup>,
    onRemoveDuplicateCopies: (DuplicateGroup) -> Unit,
) {
    val state by prefs.state.collectAsStateWithLifecycle(initialValue = PowerState())
    val blacklisted by blacklist.all().collectAsStateWithLifecycle(initialValue = emptyMap())
    val scope = rememberCoroutineScope()

    SettingsBlock("Gamepad and remote") {
        SwitchRow(
            "Gamepad page turning",
            "Turn pages in the reader with a Bluetooth clicker or gamepad: A / D-pad right goes forward, B / D-pad left goes back.",
            state.gamepadReader,
        ) { on -> scope.launch { prefs.setGamepadReader(on) } }
    }

    SettingsBlock("Automation") {
        SwitchRow(
            "Automation intents",
            "Let Tasker, MacroDroid, and friends drive Dexter with broadcast intents. Off by default.",
            state.taskerEnabled,
        ) { on -> scope.launch { prefs.setTaskerEnabled(on) } }
        if (state.taskerEnabled) {
            InfoRow(
                "Available intents",
                "${DexterAutomation.ACTION_LIBRARY_UPDATE} (extra ${DexterAutomation.EXTRA_FORCE}), " +
                    "${DexterAutomation.ACTION_OPEN_CONTINUE_READING}, " +
                    "${DexterAutomation.ACTION_OPEN_READER} (extras ${DexterAutomation.EXTRA_SERIES_ID}, ${DexterAutomation.EXTRA_CHAPTER_ID}).",
            )
        }
    }

    SettingsBlock("Library power tools") {
        SwitchRow(
            "Duplicate hints",
            "Flag the same series saved twice and repeated downloads, and suggest which copy to keep.",
            state.duplicateHints,
        ) { on -> scope.launch { prefs.setDuplicateHints(on) } }
        if (state.duplicateHints && duplicates.isNotEmpty()) {
            duplicates.forEach { group -> DuplicateGroupRow(group, onRemoveDuplicateCopies) }
        }
        InfoRow(
            "Storage analyzer",
            "Per-series breakdown, largest chapters, and cleanup suggestions.",
            onClick = onOpenStorage,
        )
    }

    SettingsBlock("Chapter blacklist") {
        val blacklistedCount = blacklisted.values.sumOf { it.size }
        var confirmClear by remember { mutableStateOf(false) }
        InfoRow(
            "Blacklisted chapters",
            if (blacklistedCount == 0) {
                "None. Long-press a chapter in its series page to never see it again: it stays out of downloads, update checks, and listings."
            } else {
                "$blacklistedCount ${if (blacklistedCount == 1) "chapter" else "chapters"} in ${blacklisted.size} ${if (blacklisted.size == 1) "series" else "series"} stay out of downloads, update checks, and listings."
            },
            action = {
                if (blacklistedCount > 0) {
                    TextButton(onClick = { confirmClear = true }) { Text("Clear") }
                }
            },
        )
        if (confirmClear) {
            ConfirmDialog(
                title = "Clear the blacklist?",
                text = "Blacklisted chapters become downloadable and visible again.",
                confirmLabel = "Clear",
                onConfirm = {
                    confirmClear = false
                    scope.launch {
                        blacklisted.forEach { (seriesId, ids) -> ids.forEach { blacklist.remove(seriesId, it) } }
                    }
                },
                onDismiss = { confirmClear = false },
            )
        }
    }
}

/** One duplicate group: the copies, the suggested keep, and a button to drop the rest. */
@Composable
private fun DuplicateGroupRow(group: DuplicateGroup, onRemove: (DuplicateGroup) -> Unit) {
    if (!matchesQuery(LocalSettingsQuery.current, "duplicate", *group.series.map { it.title }.toTypedArray())) return
    val keep = suggestedKeep(group)
    CardRow {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(keep.title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Saved ${group.series.size} times. Keep \"${keep.title}\"; the rest can go.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = { onRemove(group) }) { Text("Remove others") }
        }
    }
}

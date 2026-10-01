package com.dexter.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dexter.R
import com.dexter.data.ContentTags
import com.dexter.data.Formats
import com.dexter.data.Genres
import com.dexter.data.Themes
import com.dexter.ui.CardRow
import com.dexter.ui.ChoiceChip

/** What you typed in the Settings search box. Rows that do not mention it hide. */
internal val LocalSettingsQuery = compositionLocalOf { "" }

/** True when [query] is empty or one of [text] contains it. */
fun matchesQuery(query: String, vararg text: String?): Boolean =
    query.isBlank() || text.any { it?.contains(query.trim(), ignoreCase = true) == true }

/** Shows [content] only while the search box is empty or matches one of [words]. */
@Composable
internal fun Searchable(vararg words: String, content: @Composable () -> Unit) {
    if (matchesQuery(LocalSettingsQuery.current, *words)) content()
}

@Composable
internal fun SectionTitle(text: String) {
    // While searching, the matches show as one list without headings.
    if (LocalSettingsQuery.current.isNotBlank()) return
    Text(text, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMediumEmphasized, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp).semantics { heading() })
}

@Composable
internal fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    if (!matchesQuery(LocalSettingsQuery.current, title, subtitle)) return
    val haptics = LocalHapticFeedback.current
    CardRow {
        Row(
            Modifier.toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = {
                    haptics.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                    onChange(it)
                },
            ).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = checked,
                onCheckedChange = null,
                thumbContent = if (checked) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
                } else {
                    null
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> ChoiceRow(title: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    if (!matchesQuery(LocalSettingsQuery.current, title, *options.map { it.second }.toTypedArray())) return
    CardRow {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (value, label) -> ChoiceChip(label, value == selected) { onSelect(value) } }
            }
        }
    }
}

/** An hour of the day (0 to 23) with minus and plus buttons that wrap around midnight. */
@Composable
internal fun HourStepper(label: String, hour: Int, onChange: (Int) -> Unit) {
    if (!matchesQuery(LocalSettingsQuery.current, label, "quiet hours")) return
    CardRow {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            FilledTonalIconButton(onClick = { onChange((hour + 23) % 24) }) { Text("\u2212", style = MaterialTheme.typography.titleMediumEmphasized) }
            Text("%02d:00".format(hour), style = MaterialTheme.typography.titleMediumEmphasized, modifier = Modifier.padding(horizontal = 12.dp))
            FilledTonalIconButton(onClick = { onChange((hour + 1) % 24) }) { Text("+", style = MaterialTheme.typography.titleMediumEmphasized) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TagPickerDialog(blocked: Set<String>, onToggle: (String) -> Unit, onDismiss: () -> Unit) {
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

/** A card row with a title, an optional subtitle, and either a tap action (with an arrow) or buttons on the right. */
@Composable
internal fun InfoRow(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    action: @Composable RowScope.() -> Unit = {},
) {
    if (!matchesQuery(LocalSettingsQuery.current, title, subtitle)) return
    val content: @Composable () -> Unit = {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            action()
            if (onClick != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
    CardRow(onClick) { content() }
}

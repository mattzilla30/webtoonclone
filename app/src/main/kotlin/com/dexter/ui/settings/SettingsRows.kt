package com.dexter.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.dexter.R
import com.dexter.data.ContentTags
import com.dexter.data.Formats
import com.dexter.data.Genres
import com.dexter.data.Themes
import com.dexter.ui.ChoiceChip

/** What you typed in the Settings search box. Settings that do not match it hide. */
internal val LocalSettingsQuery = compositionLocalOf { "" }

/** The group a setting belongs to. Its name is one more word the search matches. */
private val LocalSettingsGroup = compositionLocalOf { "" }

/** Lowercase words of [text], with "colour" spelled "color" so either spelling finds a setting. */
internal fun searchWords(text: String): List<String> =
    text.lowercase().replace("colour", "color").split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }

/**
 * True when [query] is blank, or every word of it starts a word in [text]. A word of four letters
 * or more also matches across punctuation, so "wifi" finds "Wi-Fi" and "ebook" finds "e-book".
 */
fun matchesQuery(query: String, vararg text: String?): Boolean {
    val wanted = searchWords(query)
    if (wanted.isEmpty()) return true
    val words = text.filterNotNull().flatMap(::searchWords)
    val joined = words.joinToString("")
    return wanted.all { w -> words.any { it.startsWith(w) } || (w.length >= 4 && w in joined) }
}

/** A setting's sort name. The settings list orders its cards by this, A to Z. */
internal data class SettingKey(val title: String)

/** Marks the "nothing matches" note, which the list shows only when every setting hides. */
internal object EmptyNote

/**
 * The display order of the settings list's children, from each child's layout id. Named cards
 * sort A to Z. A child with no name keeps its place after the card before it. The empty note
 * is left out.
 */
internal fun settingsOrder(ids: List<Any?>): List<Int> {
    class Group(val key: String, val members: MutableList<Int>)
    val groups = ArrayList<Group>()
    ids.forEachIndexed { index, id ->
        when {
            id == EmptyNote -> Unit
            id is SettingKey -> groups += Group(id.title, mutableListOf(index))
            groups.isEmpty() -> groups += Group("", mutableListOf(index))
            else -> groups.last().members += index
        }
    }
    return groups.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.key }).flatMap { it.members }
}

/** Every setting as its own card, A to Z by name, 8dp apart. [empty] shows when no card matches the search. */
@Composable
internal fun SortedSettingsColumn(modifier: Modifier = Modifier, empty: @Composable () -> Unit, content: @Composable () -> Unit) {
    val gap = 8.dp
    Layout({
        content()
        Box(Modifier.layoutId(EmptyNote)) { empty() }
    }, modifier) { measurables, constraints ->
        val child = constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity)
        val order = settingsOrder(measurables.map { it.layoutId })
        val shown = order.ifEmpty { listOf(measurables.indexOfFirst { it.layoutId == EmptyNote }) }
        val placeables = shown.map { measurables[it].measure(child) }
        val spacing = gap.roundToPx()
        val height = placeables.sumOf { it.height } + spacing * (placeables.size - 1).coerceAtLeast(0)
        layout(constraints.maxWidth, height.coerceAtLeast(constraints.minHeight)) {
            var y = 0
            placeables.forEach {
                it.place(0, y)
                y += it.height + spacing
            }
        }
    }
}

/** A group of related settings. Its [name] never shows, but searching for it finds every setting inside. */
@Composable
internal fun SettingsBlock(name: String, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSettingsGroup provides name, content = content)
}

/**
 * One setting on its own card. The title, the summary and every control below them start on the
 * same 16dp edge. [header] makes the title area act, such as toggling a switch. [keywords] are
 * more words the search matches, such as the names of controls inside [content].
 */
@Composable
internal fun Setting(
    title: String,
    summary: String? = null,
    keywords: List<String> = emptyList(),
    header: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    if (!matchesQuery(LocalSettingsQuery.current, title, summary, LocalSettingsGroup.current, *keywords.toTypedArray())) return
    Box(Modifier.layoutId(SettingKey(title))) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(header.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.bodyLarge)
                        if (summary != null) {
                            Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                    if (trailing != null) Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically, content = trailing)
                }
                if (content != null) {
                    Column(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        content = content,
                    )
                }
            }
        }
    }
}

/** A setting that turns on and off. Tapping anywhere on its title flips it. [more] holds controls that belong to it. */
@Composable
internal fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    keywords: List<String> = emptyList(),
    more: (@Composable ColumnScope.() -> Unit)? = null,
    onChange: (Boolean) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Setting(
        title,
        subtitle,
        keywords,
        header = Modifier.toggleable(value = checked, role = Role.Switch) {
            haptics.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
            onChange(it)
        },
        trailing = { SettingSwitch(checked) },
        content = more,
    )
}

/** A setting with one choice out of [options], shown as chips under its title. */
@Composable
internal fun <T> ChoiceRow(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    summary: String? = null,
    keywords: List<String> = emptyList(),
    more: (@Composable ColumnScope.() -> Unit)? = null,
    onSelect: (T) -> Unit,
) {
    Setting(title, summary, keywords + options.map { it.second }) {
        Chips(options, selected, onSelect)
        more?.invoke(this)
    }
}

/** A setting that opens something, with an arrow, or that carries its own buttons in [action]. */
@Composable
internal fun InfoRow(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    keywords: List<String> = emptyList(),
    action: (@Composable RowScope.() -> Unit)? = null,
) {
    Setting(
        title,
        subtitle,
        keywords,
        header = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        trailing = action ?: if (onClick != null) {
            { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) }
        } else {
            null
        },
    )
}

/** The switch on a setting card, with a check mark while on. The card's title row does the toggling. */
@Composable
private fun SettingSwitch(checked: Boolean) {
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

/** Choice chips that wrap onto new lines, for use inside a setting card. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> Chips(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) -> ChoiceChip(label, value == selected) { onSelect(value) } }
    }
}

/** A labelled choice inside a setting card, such as how long the app lock waits. */
@Composable
internal fun <T> SubChoice(label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Chips(options, selected, onSelect)
    }
}

/** A switch inside a setting card, for an option that only applies while the card's own switch is on. */
@Composable
internal fun SubSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(end = 12.dp))
        SettingSwitch(checked)
    }
}

/** A line inside a setting card that opens something. */
@Composable
internal fun SubLink(title: String, subtitle: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}

/** Small grey text inside a setting card. */
@Composable
internal fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** A value with minus and plus buttons inside a setting card. */
@Composable
internal fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        FilledTonalIconButton(onClick = onMinus) { Text("−", style = MaterialTheme.typography.titleMediumEmphasized) }
        Text(value, style = MaterialTheme.typography.titleMediumEmphasized, modifier = Modifier.padding(horizontal = 12.dp))
        FilledTonalIconButton(onClick = onPlus) { Text("+", style = MaterialTheme.typography.titleMediumEmphasized) }
    }
}

/** An hour of the day (0 to 23) with minus and plus buttons that wrap around midnight. */
@Composable
internal fun HourStepper(label: String, hour: Int, onChange: (Int) -> Unit) {
    Stepper(label, "%02d:00".format(hour), onMinus = { onChange((hour + 23) % 24) }, onPlus = { onChange((hour + 1) % 24) })
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

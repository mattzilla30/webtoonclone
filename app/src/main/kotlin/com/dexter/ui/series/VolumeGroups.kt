package com.dexter.ui.series

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dexter.data.Chapter
import com.dexter.data.ChapterListItem

/** One volume's chapters together with the label [groupByVolume] gave them. */
data class VolumeGroup(
    val label: String,
    val chapters: List<Chapter>,
)

/**
 * Folds [ChapterListItem] output into volume groups. Chapters before the first heading (or the
 * whole list when there are no headings) land in a group with an empty label, which the UI renders
 * without a header.
 */
fun toVolumeGroups(items: List<ChapterListItem>): List<VolumeGroup> {
    val groups = mutableListOf<VolumeGroup>()
    var label = ""
    var current = mutableListOf<Chapter>()
    fun flush() {
        if (current.isNotEmpty() || groups.isEmpty()) {
            // Keep even an empty first group so the no-heading case still renders.
            groups += VolumeGroup(label, current.toList())
        }
        current = mutableListOf()
    }
    for (item in items) {
        when (item) {
            is ChapterListItem.VolumeHeader -> {
                flush()
                label = item.label
                current = mutableListOf()
            }
            is ChapterListItem.Entry -> current += item.chapter
        }
    }
    flush()
    return groups.filter { it.chapters.isNotEmpty() || it.label.isEmpty() }
}

/**
 * The set of collapsed volume labels, surviving rotation. Pass it to [visibleChapters] and
 * [VolumeGroupHeader] so long chapter lists collapse per volume.
 */
@Composable
fun rememberCollapsedVolumes(): MutableVolumeCollapse {
    val saver = Saver<Set<String>, String>({ it.joinToString("\u0001") }, { text -> if (text.isEmpty()) emptySet() else text.split("\u0001").toSet() })
    var collapsed by rememberSaveable(stateSaver = saver) { mutableStateOf(emptySet<String>()) }
    return MutableVolumeCollapse(collapsed) { collapsed = it }
}

/** Mutable view of the collapsed set, so headers can toggle without exposing the state holder. */
class MutableVolumeCollapse(
    val collapsed: Set<String>,
    private val set: (Set<String>) -> Unit,
) {
    fun toggle(label: String) {
        set(if (label in collapsed) collapsed - label else collapsed + label)
    }

    fun isCollapsed(label: String): Boolean = label in collapsed
}

/** The chapters of [groups] with collapsed volumes hidden, in order. */
fun visibleChapters(groups: List<VolumeGroup>, collapse: MutableVolumeCollapse): List<Chapter> =
    groups.flatMap { group ->
        if (group.label.isNotEmpty() && collapse.isCollapsed(group.label)) emptyList() else group.chapters
    }

/**
 * A volume heading that expands and collapses its chapters on tap. Drop-in replacement for the
 * plain `Text` header in the series chapter list; see the integration snippet in the task report.
 */
@Composable
fun VolumeGroupHeader(
    group: VolumeGroup,
    collapse: MutableVolumeCollapse,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clickable(role = Role.Button) { collapse.toggle(group.label) }
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            group.label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Text(
            "${group.chapters.size}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 4.dp),
        )
        Icon(
            if (collapse.isCollapsed(group.label)) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
            contentDescription = if (collapse.isCollapsed(group.label)) "Expand ${group.label}" else "Collapse ${group.label}",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

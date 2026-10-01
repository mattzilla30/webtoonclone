package com.dexter.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dexter.data.LibraryList

/** The three My Series lists as one connected row of toggle buttons. */
@Composable
internal fun LibraryTabs(selected: LibraryList, onSelect: (LibraryList) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        val options = listOf("Recent" to LibraryList.Recent, "Subscribed" to LibraryList.Subscribed, "Lists" to LibraryList.Lists)
        options.forEachIndexed { index, (label, list) ->
            ToggleButton(
                checked = selected == list,
                onCheckedChange = { onSelect(list) },
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                contentPadding = PaddingValues(horizontal = 8.dp),
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) {
                Text(label, maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** What an empty list says, and the one action that helps: clear the filters, or go find something to read. */
@Composable
internal fun LibraryEmpty(tab: LibraryList, filtering: Boolean, onClearFilters: () -> Unit, onOpenSearch: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                when {
                    filtering -> "Nothing matches your filters."
                    tab == LibraryList.Subscribed -> "Subscribe to a series to see it here."
                    tab == LibraryList.Lists -> "Add a series to a list from its page."
                    else -> "Series you read show up here."
                },
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
            if (filtering) {
                FilledTonalButton(onClick = onClearFilters, modifier = Modifier.padding(top = 16.dp)) { Text("Clear filters") }
            } else {
                FilledTonalButton(onClick = onOpenSearch, modifier = Modifier.padding(top = 16.dp)) { Text("Find something to read") }
            }
        }
    }
}

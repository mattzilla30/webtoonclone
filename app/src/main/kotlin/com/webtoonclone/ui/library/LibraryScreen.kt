package com.webtoonclone.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.webtoonclone.ui.Cover
import com.webtoonclone.ui.theme.Green

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onOpenSeries: (String) -> Unit,
    onOpenSearch: () -> Unit,
) {
    val library by viewModel.library.collectAsState()
    var subscribedTab by rememberSaveable { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<String>() }
    val items = if (subscribedTab) library.subscribed else library.recent

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("My Series", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.clickable(onClick = onOpenSearch))
        }
        Row(Modifier.fillMaxWidth()) {
            Tab("RECENT", !subscribedTab, Modifier.weight(1f)) { subscribedTab = false; selected.clear() }
            Tab("SUBSCRIBED", subscribedTab, Modifier.weight(1f)) { subscribedTab = true; selected.clear() }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${items.size} SERIES", fontSize = 12.sp, color = Green, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Delete", fontSize = 12.sp, modifier = Modifier.clickable(enabled = selected.isNotEmpty()) {
                    viewModel.delete(subscribedTab, selected.toSet())
                    selected.clear()
                })
                Text("Delete All", fontSize = 12.sp, modifier = Modifier.clickable(enabled = items.isNotEmpty()) {
                    viewModel.delete(subscribedTab, items.map { it.id }.toSet())
                    selected.clear()
                })
            }
        }
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (subscribedTab) "Subscribe to a series to see it here." else "Series you read show up here.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(items, key = { it.id }) { series ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpenSeries(series.id) }.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Cover(series.coverUrl, series.title, Modifier.width(40.dp).aspectRatio(2f / 3f))
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(series.title, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            series.chapterNumber?.let {
                                Text("Ep. $it", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Checkbox(
                            checked = series.id in selected,
                            onCheckedChange = { if (it) selected.add(series.id) else selected.remove(series.id) },
                            colors = CheckboxDefaults.colors(checkedColor = Green),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Tab(label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(40.dp).background(if (active) Green else MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

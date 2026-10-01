package com.dexter.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.dexter.R
import com.dexter.data.SeriesSummary
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

fun compact(n: Int): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000 -> "%.1fK".format(n / 1_000.0)
    else -> n.toString()
}

private val genreColors = mapOf(
    "action" to Color(0xFFFF7043),
    "adventure" to Color(0xFF26A69A),
    "boys' love" to Color(0xFF42A5F5),
    "comedy" to Color(0xFFF5A623),
    "crime" to Color(0xFF8D6E63),
    "drama" to Color(0xFF5C6BC0),
    "fantasy" to Color(0xFF8E44EC),
    "girls' love" to Color(0xFFEC407A),
    "historical" to Color(0xFFA1887F),
    "horror" to Color(0xFF7B1FA2),
    "isekai" to Color(0xFF00ACC1),
    "magical girls" to Color(0xFFF06292),
    "mecha" to Color(0xFF546E7A),
    "medical" to Color(0xFF26C6DA),
    "mystery" to Color(0xFF3F51B5),
    "philosophical" to Color(0xFF78909C),
    "psychological" to Color(0xFF6A1B9A),
    "romance" to Color(0xFFFF4F81),
    "sci-fi" to Color(0xFF1E88E5),
    "slice of life" to Color(0xFF66BB6A),
    "sports" to Color(0xFFEF6C00),
    "superhero" to Color(0xFFD32F2F),
    "thriller" to Color(0xFFE53935),
    "tragedy" to Color(0xFF616161),
    "wuxia" to Color(0xFFC0A060),
)

/** The colour for a genre label, or null for a genre with no colour of its own. */
fun genreColor(genre: String?): Color? = genreColors[genre?.lowercase()]

@Composable
fun Cover(url: String?, description: String?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Fit, thumb: Boolean = false) {
    AsyncImage(
        model = if (thumb) thumbnailUrl(url) else url,
        contentDescription = description,
        contentScale = contentScale,
        modifier = modifier,
    )
}

/** The 256 px version of a MangaDex cover address, for small tiles. Other addresses pass through. */
fun thumbnailUrl(url: String?): String? = url?.replace(".512.jpg", ".256.jpg")

@Composable
fun GenreLabel(genre: String?) {
    if (genre == null) return
    Text(genre, color = genreColor(genre) ?: MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
}

@Composable
fun HeartCount(count: Int?) {
    if (count == null) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(3.dp))
        Text(compact(count), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.titleLargeEmphasized)
        if (onClick != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}

/** A cover card with genre, title, and follower count underneath. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PickTile(
    series: SeriesSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subscribed: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.clip(MaterialTheme.shapes.medium).combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Column {
            Box {
                Cover(series.coverUrl, series.title, Modifier.fillMaxWidth().aspectRatio(2f / 3f), contentScale = ContentScale.Fit)
                if (subscribed) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = stringResource(R.string.subscribed),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(28.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                            .padding(5.dp),
                    )
                }
            }
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                GenreLabel(series.genre)
                Text(series.title, style = MaterialTheme.typography.titleSmallEmphasized)
                HeartCount(series.follows)
            }
        }
    }
}

/** "5 min ago", "3 h ago", "2 d ago", or a date for anything older than a month. */
fun timeAgo(iso: String, now: Instant = Instant.now()): String {
    val time = runCatching { OffsetDateTime.parse(iso).toInstant() }.getOrNull() ?: return ""
    return timeAgo(time, now)
}

fun timeAgo(time: Instant, now: Instant = Instant.now()): String {
    val minutes = Duration.between(time, now).toMinutes().coerceAtLeast(0)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 60 * 24 -> "${minutes / 60} h ago"
        minutes < 60 * 24 * 30 -> "${minutes / (60 * 24)} d ago"
        else -> DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .format(LocalDate.ofInstant(time, ZoneId.systemDefault()))
    }
}

/** A choice chip. The selected one is filled. */
@Composable
fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    FilterChip(
        selected = selected,
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            onClick()
        },
        label = { Text(label, maxLines = 1, softWrap = false) },
        modifier = Modifier.semantics { role = Role.RadioButton },
    )
}

/** A bar shown when a screen displays a saved copy because the network failed. Tap to retry. */
@Composable
fun OfflineBanner(savedAt: Long, what: String, onRetry: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        onClick = onRetry,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            "Offline. Showing $what saved ${timeAgo(Instant.ofEpochMilli(savedAt))}. Tap to retry.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

/** The top bar every screen shares: an optional back arrow, a title, and actions on the right. */
@Composable
fun AppTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            Column {
                Text(title, style = MaterialTheme.typography.headlineSmallEmphasized)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            }
        },
        actions = actions,
        colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

/** A slider that follows [value] from outside and reports changes, built on the state-based Slider. */
@Composable
fun SyncedSlider(value: Float, onValueChange: (Float) -> Unit, valueRange: ClosedFloatingPointRange<Float>, modifier: Modifier = Modifier) {
    val state = remember { SliderState(value, 0, valueRange) }
    LaunchedEffect(value) { if (state.value != value) state.value = value }
    Slider(state = state, onValueChange = onValueChange, modifier = modifier)
}

/** A small button that scrolls [listState] back to the top once the list is a few rows down. Place it in a Box over the list. */
@Composable
fun BackToTopButton(listState: LazyListState, modifier: Modifier = Modifier) {
    val visible by remember { derivedStateOf { listState.firstVisibleItemIndex > 4 } }
    if (!visible) return
    val scope = rememberCoroutineScope()
    SmallFloatingActionButton(
        onClick = { scope.launch { listState.animateScrollToItem(0) } },
        modifier = modifier.padding(16.dp),
    ) { Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Back to top") }
}

/** A yes or no question. [onConfirm] runs on the confirm button, and the dialog closes either way through [onDismiss]. */
@Composable
fun ConfirmDialog(title: String, text: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

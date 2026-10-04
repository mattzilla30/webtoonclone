package com.dexter.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.dexter.R
import com.dexter.data.SeriesSummary
import kotlinx.coroutines.launch
import java.time.Instant

fun compact(n: Int): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000 -> "%.1fK".format(n / 1_000.0)
    else -> n.toString()
}

/** The shared transition scope around the navigation, or null outside it. */
val LocalSharedScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** The current destination's animation scope, or null outside navigation. */
val LocalNavScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/** Marks a series cover so it moves between screens. Does nothing without a [key] or outside navigation. */
@Composable
fun Modifier.sharedCover(key: String?): Modifier {
    val shared = LocalSharedScope.current
    val nav = LocalNavScope.current
    if (key == null || shared == null || nav == null) return this
    return with(shared) { this@sharedCover.sharedElement(rememberSharedContentState("cover-$key"), nav) }
}

/** A series cover. With [sharedKey], usually the series id, it moves into the series page when that opens. */
@Composable
fun Cover(
    url: String?,
    description: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    thumb: Boolean = false,
    sharedKey: String? = null,
) {
    AsyncImage(
        model = if (thumb) thumbnailUrl(url) else url,
        contentDescription = description,
        contentScale = contentScale,
        modifier = Modifier.sharedCover(sharedKey).then(modifier),
    )
}

/** The 256 px version of a MangaDex cover address, for small tiles. Other addresses pass through. */
fun thumbnailUrl(url: String?): String? = url?.replace(".512.jpg", ".256.jpg")

/**
 * Text that shrinks, down to [minSize], until it fits its space in [maxLines] lines. Only text that
 * still does not fit at [minSize] is cut off with an ellipsis.
 */
@Composable
fun FitText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = 2,
    color: Color = Color.Unspecified,
    minSize: TextUnit = 10.sp,
) {
    Text(
        text,
        modifier = modifier,
        color = color,
        style = style,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(minFontSize = minSize, maxFontSize = style.fontSize, stepSize = 0.5.sp),
    )
}

/**
 * The width of one tile in a sideways row: as many whole tiles as fit across [widthDp], plus part of
 * the next, so the row shows that it scrolls. Tiles grow and shrink with the screen.
 */
fun rowTileWidth(widthDp: Float, spacingDp: Float = 8f, paddingDp: Float = 16f): Float {
    val whole = adaptiveColumns(widthDp)
    return ((widthDp - 2 * paddingDp - whole * spacingDp) / (whole + 0.35f)).coerceAtLeast(96f)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.titleMediumEmphasized, modifier = Modifier.semantics { heading() })
        if (onClick != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}

/** A cover with its title underneath. Long-press subscribes when [onLongClick] is given. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PickTile(
    series: SeriesSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subscribed: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    // No card fill: the cover and its title stand on the page background.
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = Color.Transparent,
        modifier = modifier.clip(MaterialTheme.shapes.medium).combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick,
            onLongClickLabel = if (subscribed) "Unsubscribe" else "Subscribe",
        ),
    ) {
        Column {
            Box {
                Cover(series.coverUrl, null, Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(MaterialTheme.shapes.small), contentScale = ContentScale.Fit, sharedKey = series.id)
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
            // Just the title under the cover: genre and follows are on the series page. Every tile's text area is
            // two lines tall, sized from the font scale, so tiles in a row line up; a longer title shrinks to fit.
            val type = MaterialTheme.typography
            val textHeight = with(LocalDensity.current) { type.titleSmallEmphasized.lineHeight.toDp() * 2 }
            FitText(series.title, type.titleSmallEmphasized, Modifier.padding(horizontal = 2.dp, vertical = 6.dp).height(textHeight), maxLines = 2)
        }
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
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            Column {
                Text(title, style = MaterialTheme.typography.headlineSmallEmphasized, modifier = Modifier.semantics { heading() })
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
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
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

/** A flat row, as used in lists of settings and suggestions. It is tappable when [onClick] is given. */
@Composable
fun CardRow(onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    // A flat row: no card fill and no gap, so lists read as lists instead of stacks of boxes.
    val modifier = Modifier.fillMaxWidth()
    if (onClick != null) {
        Surface(onClick = onClick, shape = MaterialTheme.shapes.medium, color = Color.Transparent, modifier = modifier, content = content)
    } else {
        Surface(shape = MaterialTheme.shapes.medium, color = Color.Transparent, modifier = modifier, content = content)
    }
}

/** Asks for a short piece of text, such as a name. [onConfirm] gets the text, and the dialog closes either way. */
@Composable
fun TextPromptDialog(title: String, initial: String, confirmLabel: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit, singleLine: Boolean = true) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = singleLine, modifier = Modifier.fillMaxWidth()) },
        confirmButton = {
            TextButton(
                enabled = singleLine.not() || text.isNotBlank(),
                onClick = {
                    onConfirm(text)
                    onDismiss()
                },
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * The app's one search-and-filter box: rounded, no outline, with a clear button once it holds text.
 * Settings and My Series use it, matching the search page's box.
 */
@Composable
fun FilterField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) { Icon(Icons.Default.Clear, contentDescription = "Clear") }
            }
        },
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

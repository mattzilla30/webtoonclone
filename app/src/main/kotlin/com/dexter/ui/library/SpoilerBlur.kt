package com.dexter.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dexter.data.ReadingProgress
import com.dexter.ui.series.isChapterRead

// Spoiler-safe blur: when the setting is on, covers stay blurred until the series is started, and
// chapter rows ahead of the current position blur their thumbnails until read past. Nothing is
// hidden forever; reading past a point unblurs it.

/** Blurs the content when [enabled]; a no-op otherwise, so call sites stay branch-free. */
fun Modifier.spoilerBlur(enabled: Boolean, radius: Dp = 18.dp): Modifier =
    if (enabled) blur(radius) else this

/**
 * True when a series cover should blur: the series was never opened, so even the cover art could
 * spoil. Pass the [ReadingProgress] from `ProgressStore.observe(seriesId)`.
 */
fun blurCoverForSeries(progress: ReadingProgress?): Boolean = progress == null

/**
 * True when a chapter row should blur: it sits ahead of the last-read chapter number. Uses the
 * same numbering comparison as the read marks, so fractional chapters behave consistently.
 */
fun blurChapterAhead(number: String, lastReadNumber: String?): Boolean =
    !isChapterRead(number, lastReadNumber) && number != lastReadNumber

/**
 * A blurred cover with a centered label, for library grids. [content] is the cover image; it
 * blurs when [blurred], and the label invites the tap that starts the series.
 */
@Composable
fun SpoilerSafeCover(
    blurred: Boolean,
    label: String = "Tap to reveal",
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier) {
        Box(Modifier.fillMaxSize().spoilerBlur(blurred)) { content() }
        if (blurred) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

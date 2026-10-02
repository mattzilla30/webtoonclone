package com.dexter.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dexter.data.ReaderBackground
import com.dexter.data.Settings

/** The strip's background beyond the reader's own background choice. */
enum class StripBackground {
    Follow,
    Dark,
    Black,
    White,
    Checker,
    Dots,
}

/** Parses a stored [StripBackground] name, falling back to [StripBackground.Follow]. */
fun parseStripBackground(name: String): StripBackground =
    runCatching { StripBackground.valueOf(name) }.getOrDefault(StripBackground.Follow)

/** True for the patterned choices, which draw over the reader background instead of replacing it. */
fun StripBackground.isPattern(): Boolean = this == StripBackground.Checker || this == StripBackground.Dots

/**
 * The reader background color for [choice], or null to keep the reader's own background (and draw
 * a pattern over it for the patterned choices).
 */
fun stripBackgroundColor(choice: StripBackground, settings: Settings): Color? = when (choice) {
    StripBackground.Follow -> null
    StripBackground.Dark -> readerBackgroundColor(ReaderBackground.Dark)
    StripBackground.Black -> Color.Black
    StripBackground.White -> Color.White
    StripBackground.Checker, StripBackground.Dots -> null
}

/**
 * A subtle checker or dot grid over the strip, drawn behind the pages. Nothing when [pattern] is a
 * plain color choice.
 */
@Composable
internal fun StripPattern(pattern: StripBackground, modifier: Modifier = Modifier) {
    if (!pattern.isPattern()) return
    Box(
        modifier.drawBehind {
            val step = 28.dp.toPx()
            val tone = Color.White.copy(alpha = 0.05f)
            if (pattern == StripBackground.Checker) {
                var row = 0
                var y = 0f
                while (y < size.height) {
                    var x = if (row % 2 == 0) 0f else step
                    var column = row % 2
                    while (x < size.width) {
                        if (column % 2 == 0) drawRect(tone, Offset(x, y), Size(step, step))
                        x += step
                        column++
                    }
                    y += step
                    row++
                }
            } else {
                var y = step / 2f
                while (y < size.height) {
                    var x = step / 2f
                    while (x < size.width) {
                        drawCircle(tone, 2.dp.toPx(), Offset(x, y))
                        x += step
                    }
                    y += step
                }
            }
        },
    )
}

package com.webtoonclone.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Green = Color(0xFF00DC64)
val GreenDark = Color(0xFF00B852)

private val Dark = darkColorScheme(
    primary = Green,
    onPrimary = Color.Black,
    background = Color(0xFF181818),
    onBackground = Color.White,
    surface = Color(0xFF181818),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFF9A9A9A),
    outlineVariant = Color(0xFF333333),
)

/** The whole app uses the dark scheme. */
@Composable
fun WebtoonTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Dark, content = content)
}

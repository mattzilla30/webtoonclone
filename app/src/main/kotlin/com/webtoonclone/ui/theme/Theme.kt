package com.webtoonclone.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Colors = darkColorScheme(
    primary = Color(0xFF00DC64),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
)

@Composable
fun WebtoonTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}

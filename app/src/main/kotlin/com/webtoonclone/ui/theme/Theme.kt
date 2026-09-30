package com.webtoonclone.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.webtoonclone.data.Settings
import com.webtoonclone.data.ThemeMode

val Green = Color(0xFF00DC64)
val GreenDark = Color(0xFF00B852)

private val Light = lightColorScheme(
    primary = Green,
    onPrimary = Color.Black,
    background = Color.White,
    onBackground = Color(0xFF111111),
    surface = Color.White,
    onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFF2F2F2),
    onSurfaceVariant = Color(0xFF6B6B6B),
    outlineVariant = Color(0xFFE0E0E0),
)

/** The default scheme. The reader and the series header always use it, whatever the app theme. */
val DarkScheme = darkColorScheme(
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

private val Black = DarkScheme.copy(background = Color.Black, surface = Color.Black, surfaceVariant = Color(0xFF1A1A1A))

/** Whether a theme choice is dark right now. System follows the phone. */
fun isDark(mode: ThemeMode, systemDark: Boolean): Boolean = when (mode) {
    ThemeMode.Dark, ThemeMode.Black -> true
    ThemeMode.Light -> false
    ThemeMode.System -> systemDark
}

@Composable
fun WebtoonTheme(settings: Settings = Settings(), content: @Composable () -> Unit) {
    val dark = isDark(settings.theme, isSystemInDarkTheme())
    val context = LocalContext.current
    val scheme: ColorScheme = when {
        settings.dynamicColor && dark -> dynamicDarkColorScheme(context).let {
            if (settings.theme == ThemeMode.Black) it.copy(background = Color.Black, surface = Color.Black) else it
        }
        settings.dynamicColor -> dynamicLightColorScheme(context)
        !dark -> Light
        settings.theme == ThemeMode.Black -> Black
        else -> DarkScheme
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** A dark theme for screens that stay dark in a light app, such as the reader. */
@Composable
fun DarkTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme, content = content)
}

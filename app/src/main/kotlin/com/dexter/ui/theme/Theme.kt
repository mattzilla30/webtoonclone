package com.dexter.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dexter.data.Settings
import com.dexter.data.ThemeMode

val Green = Color(0xFF00DC64)

private val Light = lightColorScheme(
    primary = Color(0xFF006D33),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF8FF7A8),
    onPrimaryContainer = Color(0xFF00210B),
    secondary = Color(0xFF4F6354),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1E8D5),
    onSecondaryContainer = Color(0xFF0C1F13),
    tertiary = Color(0xFF3A656F),
    tertiaryContainer = Color(0xFFBDEAF5),
    onTertiaryContainer = Color(0xFF001F26),
    background = Color(0xFFF6FBF4),
    onBackground = Color(0xFF181D18),
    surface = Color(0xFFF6FBF4),
    onSurface = Color(0xFF181D18),
    surfaceVariant = Color(0xFFDDE5DB),
    onSurfaceVariant = Color(0xFF414941),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF0F5EE),
    surfaceContainer = Color(0xFFEAEFE8),
    surfaceContainerHigh = Color(0xFFE4EAE3),
    surfaceContainerHighest = Color(0xFFDFE4DD),
    outline = Color(0xFF717971),
    outlineVariant = Color(0xFFC1C9BF),
)

/** The default scheme. The reader and the series header always use it, whatever the app theme. */
val DarkScheme = darkColorScheme(
    primary = Green,
    onPrimary = Color(0xFF003919),
    primaryContainer = Color(0xFF005229),
    onPrimaryContainer = Color(0xFF8FF7A8),
    secondary = Color(0xFFB5CCBA),
    onSecondary = Color(0xFF213527),
    secondaryContainer = Color(0xFF384B3D),
    onSecondaryContainer = Color(0xFFD1E8D5),
    tertiary = Color(0xFFA2CEDA),
    tertiaryContainer = Color(0xFF214D57),
    onTertiaryContainer = Color(0xFFBDEAF5),
    background = Color(0xFF131413),
    onBackground = Color(0xFFE2E3DE),
    surface = Color(0xFF131413),
    onSurface = Color(0xFFE2E3DE),
    surfaceVariant = Color(0xFF414941),
    onSurfaceVariant = Color(0xFFC1C9BF),
    surfaceContainerLowest = Color(0xFF0E0F0E),
    surfaceContainerLow = Color(0xFF1B1C1B),
    surfaceContainer = Color(0xFF1F201F),
    surfaceContainerHigh = Color(0xFF2A2B29),
    surfaceContainerHighest = Color(0xFF353634),
    outline = Color(0xFF8B938A),
    outlineVariant = Color(0xFF414941),
)

private val Black = DarkScheme.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0C0C0C),
    surfaceContainer = Color(0xFF141414),
    surfaceContainerHigh = Color(0xFF1C1C1C),
    surfaceContainerHighest = Color(0xFF262626),
)

/** Rounder corners than the standard scale, as Material 3 Expressive asks for. */
private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

/** Whether a theme choice is dark right now. System follows the phone. */
fun isDark(mode: ThemeMode, systemDark: Boolean): Boolean = when (mode) {
    ThemeMode.Dark, ThemeMode.Black -> true
    ThemeMode.Light -> false
    ThemeMode.System -> systemDark
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DexterTheme(settings: Settings = Settings(), content: @Composable () -> Unit) {
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
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        shapes = ExpressiveShapes,
        content = content,
    )
}

/** A dark theme for screens that stay dark in a light app, such as the reader. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DarkTheme(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = DarkScheme,
        motionScheme = MotionScheme.expressive(),
        shapes = ExpressiveShapes,
        content = content,
    )
}

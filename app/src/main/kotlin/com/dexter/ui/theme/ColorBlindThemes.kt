package com.dexter.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.dexter.data.CvdTheme

/**
 * Colour-blind-safe palettes. Deuteranopia and protanopia both confuse red and green, so these
 * themes never encode meaning in a red/green pair: primaries are blue, secondaries are orange or
 * yellow, and errors are a warm orange always paired with an icon or label. Charts and progress in
 * the app should follow the same pairs when a CVD theme is active.
 */

/** The scheme for [theme] in light or dark, or null when [CvdTheme.None] keeps the regular theme. */
fun cvdColorScheme(theme: CvdTheme, dark: Boolean): ColorScheme? = when (theme) {
    CvdTheme.None -> null
    CvdTheme.DeuteranopiaSafe -> if (dark) deuteranopiaDark else deuteranopiaLight
    CvdTheme.ProtanopiaSafe -> if (dark) protanopiaDark else protanopiaLight
    CvdTheme.HighContrast -> if (dark) highContrastDark else highContrastLight
}

// Blue primary, orange secondary: the classic deuteranopia-safe pair.
private val deuteranopiaLight = Light.copy(
    primary = Color(0xFF0B5FFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E6FF),
    onPrimaryContainer = Color(0xFF00287A),
    secondary = Color(0xFF9A5B00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDDB3),
    onSecondaryContainer = Color(0xFF2F1A00),
    tertiary = Color(0xFF006874),
    tertiaryContainer = Color(0xFF9DEFFD),
    onTertiaryContainer = Color(0xFF001F24),
    error = Color(0xFFB24500),
    onError = Color.White,
    errorContainer = Color(0xFFFFDCC8),
    onErrorContainer = Color(0xFF3A1A00),
)

private val deuteranopiaDark = DarkScheme.copy(
    primary = Color(0xFFABC7FF),
    onPrimary = Color(0xFF002F7A),
    primaryContainer = Color(0xFF0046B8),
    onPrimaryContainer = Color(0xFFD9E6FF),
    secondary = Color(0xFFFFB871),
    onSecondary = Color(0xFF4A2800),
    secondaryContainer = Color(0xFF6B3F00),
    onSecondaryContainer = Color(0xFFFFDDB3),
    tertiary = Color(0xFF4FD8EC),
    tertiaryContainer = Color(0xFF004E59),
    onTertiaryContainer = Color(0xFF9DEFFD),
    error = Color(0xFFFFB59D),
    onError = Color(0xFF5B1D00),
    errorContainer = Color(0xFF7E2D00),
    onErrorContainer = Color(0xFFFFDCC8),
)

// Blue primary, yellow secondary: red is darkened for protanopes, so yellow carries the contrast.
private val protanopiaLight = Light.copy(
    primary = Color(0xFF0B5FFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E6FF),
    onPrimaryContainer = Color(0xFF00287A),
    secondary = Color(0xFF7A5C00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE27A),
    onSecondaryContainer = Color(0xFF241A00),
    tertiary = Color(0xFF00639B),
    tertiaryContainer = Color(0xFFCDE5FF),
    onTertiaryContainer = Color(0xFF001D33),
    error = Color(0xFF8A3D00),
    onError = Color.White,
    errorContainer = Color(0xFFFFDCC8),
    onErrorContainer = Color(0xFF3A1A00),
)

private val protanopiaDark = DarkScheme.copy(
    primary = Color(0xFFABC7FF),
    onPrimary = Color(0xFF002F7A),
    primaryContainer = Color(0xFF0046B8),
    onPrimaryContainer = Color(0xFFD9E6FF),
    secondary = Color(0xFFFFDF3D),
    onSecondary = Color(0xFF3A2F00),
    secondaryContainer = Color(0xFF5C4D00),
    onSecondaryContainer = Color(0xFFFFE27A),
    tertiary = Color(0xFF9DCAFF),
    tertiaryContainer = Color(0xFF004A77),
    onTertiaryContainer = Color(0xFFCDE5FF),
    error = Color(0xFFFFB59D),
    onError = Color(0xFF5B1D00),
    errorContainer = Color(0xFF7E2D00),
    onErrorContainer = Color(0xFFFFDCC8),
)

// Maximum luminance separation: nothing meaningful lives in hue at all.
private val highContrastLight = Light.copy(
    primary = Color.Black,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD600),
    onPrimaryContainer = Color.Black,
    secondary = Color(0xFF1A1A1A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E0E0),
    onSecondaryContainer = Color.Black,
    tertiary = Color(0xFF0033AA),
    tertiaryContainer = Color(0xFFD9E6FF),
    onTertiaryContainer = Color.Black,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    error = Color(0xFF7A0000),
    onError = Color.White,
)

private val highContrastDark = DarkScheme.copy(
    primary = Color(0xFFFFD600),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFFFFD600),
    onPrimaryContainer = Color.Black,
    secondary = Color.White,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF3A3A3A),
    onSecondaryContainer = Color.White,
    tertiary = Color(0xFF9DCAFF),
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    error = Color(0xFFFF8A80),
    onError = Color.Black,
)

/**
 * What [color] looks like to someone with [theme]'s colour vision, using the Machado et al. (2009)
 * simulation matrices. Used by the settings preview so sighted users can check that a CVD theme
 * still reads well. [CvdTheme.None] and [CvdTheme.HighContrast] return the colour unchanged.
 */
fun simulateCvd(color: Color, theme: CvdTheme): Color {
    val matrix = when (theme) {
        // Machado 2009, protanopia and deuteranopia rows.
        CvdTheme.ProtanopiaSafe -> floatArrayOf(
            0.567f, 0.433f, 0f,
            0.558f, 0.442f, 0f,
            0f, 0.242f, 0.758f,
        )
        CvdTheme.DeuteranopiaSafe -> floatArrayOf(
            0.625f, 0.375f, 0f,
            0.7f, 0.3f, 0f,
            0f, 0.3f, 0.7f,
        )
        CvdTheme.None, CvdTheme.HighContrast -> return color
    }
    val r = color.red * matrix[0] + color.green * matrix[1] + color.blue * matrix[2]
    val g = color.red * matrix[3] + color.green * matrix[4] + color.blue * matrix[5]
    val b = color.red * matrix[6] + color.green * matrix[7] + color.blue * matrix[8]
    return Color(r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), b.coerceIn(0f, 1f), color.alpha)
}

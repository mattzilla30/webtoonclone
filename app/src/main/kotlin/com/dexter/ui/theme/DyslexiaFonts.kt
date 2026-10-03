package com.dexter.ui.theme

import android.content.Context
import android.net.Uri
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.dexter.data.A11yState
import com.dexter.data.AppFont
import com.dexter.data.writeAtomically
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The UI typeface chosen in Accessibility settings. Null means the system default; every other
 * value overrides Material's typeface below.
 */
val LocalUiFontFamily = compositionLocalOf<FontFamily?> { null }

/** Where a user-picked font file lives once installed. */
fun customFontFile(context: Context): File = File(File(context.filesDir, "fonts"), "custom_font.ttf")

/**
 * Copies a user-picked .ttf or .otf ([uri], from the system file picker) into the app's private
 * fonts directory. Returns the file, or null when the copy failed.
 */
suspend fun installCustomFont(context: Context, uri: Uri): File? = withContext(Dispatchers.IO) {
    try {
        val dir = File(context.filesDir, "fonts").also { it.mkdirs() }
        val target = File(dir, "custom_font.ttf")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.writeAtomically { input.copyTo(it) }
        } ?: return@withContext null
        target.takeIf { it.length() > 0 }
    } catch (e: Exception) {
        null
    }
}

/**
 * The [FontFamily] for [state], or null for the system default. OpenDyslexicStyle without an
 * installed file falls back to the default face; the legibility tweaks in [dyslexiaTypography]
 * still apply its wider spacing. A real OpenDyslexic install is just a custom file pick away.
 */
fun fontFamilyFor(context: Context, state: A11yState): FontFamily? = when (state.font) {
    AppFont.System -> null
    AppFont.OpenDyslexicStyle, AppFont.CustomFile -> {
        val file = customFontFile(context).takeIf { it.exists() }
        if (file != null) {
            runCatching { FontFamily(Font(file)) }.getOrNull()
        } else if (state.font == AppFont.OpenDyslexicStyle) {
            FontFamily.Default
        } else {
            null
        }
    }
}

/**
 * Applies the dyslexia-friendly tweaks to Material's typeface: a slightly wider letter spacing on
 * body text, which the OpenDyslexic design also relies on. Headings keep their tighter spacing.
 */
fun dyslexiaTypography(
    base: Typography,
    family: FontFamily?,
): Typography {
    if (family == null) return base
    fun spaced(style: TextStyle) =
        style.copy(fontFamily = family, letterSpacing = (style.letterSpacing.value + 0.5f).sp)
    return base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = family),
        displayMedium = base.displayMedium.copy(fontFamily = family),
        displaySmall = base.displaySmall.copy(fontFamily = family),
        headlineLarge = base.headlineLarge.copy(fontFamily = family),
        headlineMedium = base.headlineMedium.copy(fontFamily = family),
        headlineSmall = base.headlineSmall.copy(fontFamily = family),
        titleLarge = base.titleLarge.copy(fontFamily = family),
        titleMedium = base.titleMedium.copy(fontFamily = family),
        titleSmall = base.titleSmall.copy(fontFamily = family),
        bodyLarge = spaced(base.bodyLarge),
        bodyMedium = spaced(base.bodyMedium),
        bodySmall = spaced(base.bodySmall),
        labelLarge = spaced(base.labelLarge),
        labelMedium = spaced(base.labelMedium),
        labelSmall = spaced(base.labelSmall),
    )
}

/**
 * Provides [LocalUiFontFamily] for [content] from the current a11y state. The app theme reads it
 * and swaps its typography; see the integration snippet in the batch report.
 */
@Composable
fun UiFontProvider(state: A11yState, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val family = remember(state.font, state.customFontUri) { fontFamilyFor(context, state) }
    CompositionLocalProvider(LocalUiFontFamily provides family) {
        content()
    }
}

package com.tendril.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/** Exposes the raw palette to composables that need tokens Material3's ColorScheme has no slot for
 *  (accentSoft/accentSoftText, textFaint, border) — the mapped ColorScheme below covers the rest. */
val LocalTendrilPalette = staticCompositionLocalOf { paletteFor(TendrilColorTheme.INK, TendrilMode.LIGHT) }

private fun ColorScheme.applyPalette(p: TendrilPalette): ColorScheme = copy(
    primary = p.accent,
    onPrimary = p.onAccent,
    primaryContainer = p.accentSoft,
    onPrimaryContainer = p.accentSoftText,
    secondary = p.accentStrong,
    onSecondary = p.onAccent,
    background = p.bg,
    onBackground = p.text,
    surface = p.bg,
    onSurface = p.text,
    surfaceVariant = p.surface2,
    onSurfaceVariant = p.textDim,
    outline = p.border,
    outlineVariant = p.textFaint,
)

@Composable
fun TendrilTheme(
    colorTheme: TendrilColorTheme,
    mode: TendrilMode,
    typeface: TendrilTypeface,
    content: @Composable () -> Unit,
) {
    val palette = paletteFor(colorTheme, mode)
    val base = if (mode == TendrilMode.DARK) darkColorScheme() else lightColorScheme()
    val colorScheme = base.applyPalette(palette)

    CompositionLocalProvider(LocalTendrilPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typographyFor(typeface),
            content = content,
        )
    }
}

/** Default mode before the user has ever touched the picker: follow the system setting. */
@Composable
fun defaultTendrilMode(): TendrilMode =
    if (isSystemInDarkTheme()) TendrilMode.DARK else TendrilMode.LIGHT

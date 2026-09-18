package com.tendril.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

/** Exposes the raw palette to composables that need tokens Material3's ColorScheme has no slot for
 *  (accentSoftText, textFaint, border, the calendar's layers, the find mark) — the mapped ColorScheme
 *  below covers the rest. */
val LocalTendrilPalette = staticCompositionLocalOf { paletteFor(Register.INK, dark = false) }

private fun ColorScheme.applyPalette(p: TendrilPalette): ColorScheme = copy(
    primary = p.accent,
    onPrimary = p.onAccent,
    primaryContainer = p.accentSoft,
    onPrimaryContainer = p.accentSoftText,
    secondary = p.accent,
    onSecondary = p.onAccent,
    // 14g·2 — the calendar reads its layers from the palette, so Material's secondary container
    // is only ever a lifted ground here; the third hue and the error family are solved tokens.
    secondaryContainer = p.surface2,
    onSecondaryContainer = p.text,
    tertiary = p.third,
    onTertiary = p.onThird,
    tertiaryContainer = p.thirdSoft,
    onTertiaryContainer = p.text,
    error = p.error,
    onError = p.onError,
    errorContainer = p.errorSoft,
    onErrorContainer = p.error,
    background = p.bg,
    onBackground = p.text,
    surface = p.bg,
    onSurface = p.text,
    surfaceVariant = p.surface2,
    onSurfaceVariant = p.textDim,
    // Material's container family — menus, sheets, dialogs, the switcher — sits on the same two
    // grounds, so a dark register never shows Material's own purple-grey through the chrome.
    surfaceContainerLowest = p.bg,
    surfaceContainerLow = p.bg,
    surfaceContainer = p.surface2,
    surfaceContainerHigh = p.surface2,
    surfaceContainerHighest = p.surface2,
    surfaceBright = p.bg,
    surfaceDim = p.surface2,
    surfaceTint = p.accent,
    inverseSurface = p.text,
    inverseOnSurface = p.bg,
    inversePrimary = p.accentSoft,
    outline = p.border,
    outlineVariant = p.textFaint,
)

/**
 * 14g·1 — the theme is a [Register] in one resolved mode. [dark] is the *resolved* mode (the
 * stored [TendrilMode] may be SYSTEM — [TendrilMode.resolveDark] settles it against the OS);
 * [oled] is the phone's *Deeper blacks* device setting and means nothing on a light ground.
 */
@Composable
fun TendrilTheme(
    register: Register,
    dark: Boolean,
    typeface: TendrilTypeface,
    oled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val palette = remember(register, dark, oled) { paletteFor(register, dark, oled) }
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val colorScheme = base.applyPalette(palette)

    CompositionLocalProvider(LocalTendrilPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typographyFor(typeface),
            shapes = TendrilShapes,
            content = content,
        )
    }
}

/** The radius family — 4 · 6 · 8 · 10 · 12 (the design layer, 2026-09-18): Material's own
 *  28 dp `extraLarge` had leaked through every `AlertDialog`, the date picker and the bottom
 *  sheet. 6 is the workhorse (rows, fields, chips), 8 cards, 10 the switcher and the hover card,
 *  12 sheets and dialogs; audit rule 17 refuses any other literal radius in `ui/`. */
val TendrilShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(12.dp),
)

/** SYSTEM follows the OS — Android's night mode, Windows' app theme through Compose Desktop. */
@Composable
fun TendrilMode.resolveDark(): Boolean = when (this) {
    TendrilMode.SYSTEM -> isSystemInDarkTheme()
    TendrilMode.LIGHT -> false
    TendrilMode.DARK -> true
}

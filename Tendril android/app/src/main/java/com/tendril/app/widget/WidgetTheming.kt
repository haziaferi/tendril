package com.tendril.app.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.graphics.Color
import com.tendril.app.AppContainer
import com.tendril.app.ui.theme.TendrilColorTheme
import com.tendril.app.ui.theme.TendrilMode
import com.tendril.app.ui.theme.TendrilPalette
import com.tendril.app.ui.theme.paletteFor

/** The fully-resolved set of colors one widget instance renders with — theme/mode inherited
 * live from Settings (§8.7), opacity/shade/hue from this instance's own Glance state. */
data class WidgetTheme(
    val colorTheme: TendrilColorTheme,
    val mode: TendrilMode,
    val palette: TendrilPalette,
    val config: WidgetColorConfig,
    val backgroundWithOpacity: Color,
    val accent2: Color,
)

/** Non-Composable on purpose — [android.content.res.Configuration] is a plain data read, and
 * keeping this outside Composable scope means it works identically whether called from a real
 * Compose tree, Glance's composition, or [AppWidgetConfigureActivity]'s preview logic. */
fun resolveThemeMode(container: AppContainer, context: Context): Pair<TendrilColorTheme, TendrilMode> {
    val theme = container.themePreferences.colorTheme.value
    val mode = if (container.themePreferences.modeIsExplicit.value) {
        container.themePreferences.explicitMode.value ?: systemMode(context)
    } else {
        systemMode(context)
    }
    return theme to mode
}

private fun systemMode(context: Context): TendrilMode {
    val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    return if (night == Configuration.UI_MODE_NIGHT_YES) TendrilMode.DARK else TendrilMode.LIGHT
}

fun resolveWidgetTheme(container: AppContainer, context: Context, config: WidgetColorConfig): WidgetTheme {
    val (theme, mode) = resolveThemeMode(container, context)
    val palette = paletteFor(theme, mode)
    // §8.2 — opacity is real launcher-composited translucency, applied only to the
    // background; text/accent colors always render at full opacity over it.
    val bgWithOpacity = palette.bg.copy(alpha = config.opacity / 100f)
    val accent2Rgb = currentAccent2(theme, mode, config.shade, config.hueOffset)
    val accent2 = Color(accent2Rgb.r, accent2Rgb.g, accent2Rgb.b)
    return WidgetTheme(theme, mode, palette, config, bgWithOpacity, accent2)
}

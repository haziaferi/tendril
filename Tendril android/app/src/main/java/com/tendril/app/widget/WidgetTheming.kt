package com.tendril.app.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.graphics.Color
import com.tendril.app.AppContainer
import com.tendril.app.ui.theme.Register
import com.tendril.app.ui.theme.TendrilMode
import com.tendril.app.ui.theme.TendrilPalette
import com.tendril.app.ui.theme.paletteFor

/** The fully-resolved set of colors one widget instance renders with — theme/mode inherited
 * live from Settings (§8.7), opacity/shade/hue from this instance's own Glance state. */
data class WidgetTheme(
    val register: Register,
    val dark: Boolean,
    val palette: TendrilPalette,
    val config: WidgetColorConfig,
    val backgroundWithOpacity: Color,
    val accent2: Color,
)

/** Non-Composable on purpose — [android.content.res.Configuration] is a plain data read, and
 * keeping this outside Composable scope means it works identically whether called from a real
 * Compose tree, Glance's composition, or [AppWidgetConfigureActivity]'s preview logic. */
/** 14g·1 — the store's choice with System settled against the device's night mode; OLED as stored. */
data class ResolvedTheme(val register: Register, val dark: Boolean, val oled: Boolean) {
    val palette: TendrilPalette get() = paletteFor(register, dark, oled)
}

fun resolveThemeMode(container: AppContainer, context: Context): ResolvedTheme {
    val choice = container.themeSettings.current()
    val dark = when (choice.mode) {
        TendrilMode.SYSTEM -> systemDark(context)
        TendrilMode.LIGHT -> false
        TendrilMode.DARK -> true
    }
    return ResolvedTheme(choice.register, dark, choice.oled)
}

private fun systemDark(context: Context): Boolean {
    val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    return night == Configuration.UI_MODE_NIGHT_YES
}

fun resolveWidgetTheme(container: AppContainer, context: Context, config: WidgetColorConfig): WidgetTheme {
    val resolved = resolveThemeMode(container, context)
    val (theme, mode) = resolved.register to resolved.dark
    val palette = resolved.palette
    // §8.2 — opacity is real launcher-composited translucency, applied only to the
    // background; text/accent colors always render at full opacity over it.
    val bgWithOpacity = palette.bg.copy(alpha = config.opacity / 100f)
    val accent2Rgb = currentAccent2(theme, mode, config.shade, config.hueOffset)
    val accent2 = Color(accent2Rgb.r, accent2Rgb.g, accent2Rgb.b)
    return WidgetTheme(theme, mode, palette, config, bgWithOpacity, accent2)
}

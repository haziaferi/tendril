package com.tendril.app.widget

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition

/**
 * §8.7 — "opacity/Shade/Hue... per placed widget instance." Backed by Glance's own built-in
 * [PreferencesGlanceStateDefinition] (one Preferences DataStore per [GlanceId], managed by
 * Glance itself) rather than a hand-rolled store — no new persistence mechanism needed for
 * data this small and this tightly scoped to widget rendering.
 */
object WidgetPrefKeys {
    val OPACITY = intPreferencesKey("opacity_pct")
    val SHADE = intPreferencesKey("shade_pct")
    val HUE_OFFSET = intPreferencesKey("hue_offset_deg")

    /** §8.3 — "defaulting to whatever the currently-selected in-app theme's values are at
     * placement time" for opacity; Shade/Hue both start at 0 (§8.3's byte-for-byte-identical
     * default state). */
    const val DEFAULT_OPACITY = 88
    const val DEFAULT_SHADE = 0
    const val DEFAULT_HUE_OFFSET = 0
}

data class WidgetColorConfig(val opacity: Int, val shade: Int, val hueOffset: Int)

fun Preferences.toWidgetColorConfig(): WidgetColorConfig = WidgetColorConfig(
    opacity = this[WidgetPrefKeys.OPACITY] ?: WidgetPrefKeys.DEFAULT_OPACITY,
    shade = this[WidgetPrefKeys.SHADE] ?: WidgetPrefKeys.DEFAULT_SHADE,
    hueOffset = this[WidgetPrefKeys.HUE_OFFSET] ?: WidgetPrefKeys.DEFAULT_HUE_OFFSET,
)

/** Called once from `AppWidgetConfigureActivity` on Save (§8.7's config screen). */
suspend fun saveWidgetColorConfig(context: Context, glanceId: GlanceId, config: WidgetColorConfig) {
    updateAppWidgetState(context, glanceId) { prefs ->
        prefs[WidgetPrefKeys.OPACITY] = config.opacity
        prefs[WidgetPrefKeys.SHADE] = config.shade
        prefs[WidgetPrefKeys.HUE_OFFSET] = config.hueOffset
    }
}

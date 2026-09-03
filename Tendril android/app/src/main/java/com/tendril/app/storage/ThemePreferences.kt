package com.tendril.app.storage

import android.content.Context
import androidx.core.content.edit
import com.tendril.app.ui.theme.TendrilColorTheme
import com.tendril.app.ui.theme.TendrilMode
import com.tendril.app.ui.theme.TendrilTypeface
import com.tendril.app.widget.WidgetRefresh
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val PREFS_NAME = "tendril_theme_prefs"
private const val KEY_COLOR_THEME = "color_theme"
private const val KEY_MODE = "mode"
private const val KEY_MODE_IS_EXPLICIT = "mode_is_explicit"
private const val KEY_TYPEFACE = "typeface"

/**
 * Plain SharedPreferences, not the encrypted store §3.5 reserves for the Anthropic key /
 * Google OAuth tokens — a theme choice isn't a secret. Room + DataStore land in Phase 2
 * (§9.9 item 2); this is intentionally the minimal thing that survives process death for now.
 */
class ThemePreferences(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    // §9.6 — theme changes must push a widget refresh explicitly; a dedicated scope here
    // (rather than requiring every call site to remember to do it) keeps that a fire-and-forget
    // side effect of the setter itself, so no future caller can forget the hook.
    private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _colorTheme = MutableStateFlow(
        runCatching { TendrilColorTheme.valueOf(prefs.getString(KEY_COLOR_THEME, null) ?: "") }
            .getOrDefault(TendrilColorTheme.INK)
    )
    val colorTheme: StateFlow<TendrilColorTheme> = _colorTheme.asStateFlow()

    private val _modeIsExplicit = MutableStateFlow(prefs.getBoolean(KEY_MODE_IS_EXPLICIT, false))
    val modeIsExplicit: StateFlow<Boolean> = _modeIsExplicit.asStateFlow()

    private val _explicitMode = MutableStateFlow(
        runCatching { TendrilMode.valueOf(prefs.getString(KEY_MODE, null) ?: "") }.getOrNull()
    )
    val explicitMode: StateFlow<TendrilMode?> = _explicitMode.asStateFlow()

    private val _typeface = MutableStateFlow(
        runCatching { TendrilTypeface.valueOf(prefs.getString(KEY_TYPEFACE, null) ?: "") }
            .getOrDefault(TendrilTypeface.SANS)
    )
    val typeface: StateFlow<TendrilTypeface> = _typeface.asStateFlow()

    fun setColorTheme(theme: TendrilColorTheme) {
        _colorTheme.value = theme
        prefs.edit { putString(KEY_COLOR_THEME, theme.name) }
        pushWidgetRefresh()
    }

    fun setMode(mode: TendrilMode) {
        _explicitMode.value = mode
        _modeIsExplicit.value = true
        prefs.edit {
            putString(KEY_MODE, mode.name)
            putBoolean(KEY_MODE_IS_EXPLICIT, true)
        }
        pushWidgetRefresh()
    }

    private fun pushWidgetRefresh() {
        refreshScope.launch { WidgetRefresh.updateAll(appContext) }
    }

    fun setTypeface(typeface: TendrilTypeface) {
        _typeface.value = typeface
        prefs.edit { putString(KEY_TYPEFACE, typeface.name) }
    }
}

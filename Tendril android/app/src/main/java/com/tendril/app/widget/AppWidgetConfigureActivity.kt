@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tendril.app.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.tendril.app.AppContainer
import com.tendril.app.ui.theme.TendrilTheme
import kotlinx.coroutines.launch

/**
 * §8.7 — "AppWidgetConfigureActivity shows only opacity/Shade/Hue controls plus the live
 * contrast-audit readout (§8.4)... theme/mode fields are absent from that screen entirely."
 * One shared activity for all three widget types (§8.1) — the appWidgetId's own provider
 * determines which [androidx.glance.appwidget.GlanceAppWidget] gets `.update()`d on save.
 */
class AppWidgetConfigureActivity : ComponentActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val container = AppContainer.from(this)
        val resolved = resolveThemeMode(container, this)
        val theme = resolved.register
        val mode = resolved.dark
        val palette = resolved.palette

        setContent {
            var opacity by remember { mutableStateOf(WidgetPrefKeys.DEFAULT_OPACITY) }
            var shade by remember { mutableStateOf(WidgetPrefKeys.DEFAULT_SHADE) }
            var hueOffset by remember { mutableStateOf(WidgetPrefKeys.DEFAULT_HUE_OFFSET) }
            val scope = rememberCoroutineScope()

            LaunchedEffect(appWidgetId) {
                val glanceId = GlanceAppWidgetManager(this@AppWidgetConfigureActivity).getGlanceIdBy(appWidgetId)
                val saved = runCatching {
                    getAppWidgetState(this@AppWidgetConfigureActivity, PreferencesGlanceStateDefinition, glanceId).toWidgetColorConfig()
                }.getOrNull()
                if (saved != null) {
                    opacity = saved.opacity
                    shade = saved.shade
                    hueOffset = saved.hueOffset
                }
            }

            val contrastRows = remember(opacity, shade, hueOffset) {
                auditContrast(
                    register = theme,
                    dark = mode,
                    widgetBg = palette.bg.toRgb(),
                    opacityPct = opacity,
                    shade = shade,
                    hueOffsetDeg = hueOffset,
                    roleRgb = { role ->
                        when (role) {
                            ContrastRole.TEXT -> palette.text
                            ContrastRole.TEXT_DIM -> palette.textDim
                            ContrastRole.TEXT_FAINT -> palette.textFaint
                            ContrastRole.ACCENT -> palette.accent
                            ContrastRole.ACCENT_STRONG -> palette.accentStrong
                            // auditContrast() always overrides ACCENT2 with currentAccent2()
                            // internally — this branch exists only for when-exhaustiveness
                            // and is never actually read.
                            ContrastRole.ACCENT2 -> palette.accent
                        }.toRgb()
                    },
                )
            }

            TendrilTheme(register = theme, dark = mode, typeface = com.tendril.app.ui.theme.TendrilTypeface.SANS, oled = resolved.oled) {
                Scaffold(
                    topBar = { TopAppBar(title = { Text("Widget appearance") }) },
                ) { padding ->
                    Column(
                        modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
                    ) {
                        Text("Background opacity", style = MaterialTheme.typography.labelLarge)
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Slider(
                                value = opacity.toFloat(),
                                onValueChange = { opacity = it.toInt() },
                                valueRange = 0f..100f,
                                modifier = Modifier.weight(1f),
                            )
                            Text("$opacity%", modifier = Modifier.padding(start = 8.dp))
                        }

                        Spacer(Modifier.height(16.dp))
                        Text("Date / time color", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(8.dp))
                        ShadeHueWheel(
                            theme = theme,
                            mode = mode,
                            shade = shade,
                            hueOffset = hueOffset,
                            onChange = { newShade, newHue -> shade = newShade; hueOffset = newHue },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(Modifier.height(20.dp))
                        Text("Contrast audit", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(4.dp))
                        contrastRows.forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                            ) {
                                Text(row.role.name.lowercase().replace('_', ' '), style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "%.1f:1 %s".format(row.ratio, if (row.pass) "✓" else "✗ (need ${row.role.threshold}:1)"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (row.pass) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        Text(
                            "Tested against the worst-case pure-black/pure-white wallpaper bracket — a real wallpaper is one fixed luminance, not this adversarial extreme, so a fail here isn't necessarily a fail in practice.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )

                        Spacer(Modifier.height(24.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
                            TextButton(onClick = { finish() }) {
                                Icon(Icons.Filled.Close, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Cancel")
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = {
                                scope.launch {
                                    val glanceId = GlanceAppWidgetManager(this@AppWidgetConfigureActivity).getGlanceIdBy(appWidgetId)
                                    saveWidgetColorConfig(this@AppWidgetConfigureActivity, glanceId, WidgetColorConfig(opacity, shade, hueOffset))
                                    updateWidgetForAppWidgetId(this@AppWidgetConfigureActivity, appWidgetId)

                                    val resultValue = android.content.Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                                    setResult(Activity.RESULT_OK, resultValue)
                                    finish()
                                }
                            }) {
                                Icon(Icons.Filled.Check, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Save")
                            }
                        }
                    }
                }
            }
        }
    }
}

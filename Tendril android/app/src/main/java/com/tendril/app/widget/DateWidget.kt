package com.tendril.app.widget

import android.content.Context
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.currentState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.tendril.app.MainActivity
import com.tendril.app.TendrilApp
import java.time.LocalDate
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

/**
 * §8.1 — "resizable from 1×1 up to 2×2+; the day number renders at the same font size
 * regardless of footprint — a larger widget only adds tap area/breathing room, never distorts
 * the number." No size-dependent branching at all, unlike Monthly/Agenda's density tiers.
 */
class DateWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as TendrilApp).container
        provideContent {
            val prefs = currentState<Preferences>()
            val config = prefs.toWidgetColorConfig()
            val theme = resolveWidgetTheme(container, context, config)
            DateWidgetContent(theme)
        }
    }
}

class DateWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DateWidget()
}

@androidx.compose.runtime.Composable
private fun DateWidgetContent(theme: WidgetTheme) {
    val today = LocalDate.now()
    val month = today.month.getDisplayName(JavaTextStyle.SHORT, Locale.getDefault())

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(theme.backgroundWithOpacity)
            .clickable(actionStartActivity<MainActivity>()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = today.dayOfMonth.toString(),
            style = TextStyle(
                color = ColorProvider(theme.accent2),
                fontSize = 30.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            ),
        )
        Text(
            text = month,
            style = TextStyle(
                color = ColorProvider(theme.palette.textDim),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

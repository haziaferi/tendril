package com.tendril.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll

/**
 * §9.6 — "Glance widget colours resolve once, at placement time, and do not automatically
 * follow later theme changes unless the app explicitly triggers a widget update." Called from
 * [com.tendril.app.storage.ThemePreferences] whenever the color theme or mode actually changes.
 */
object WidgetRefresh {
    suspend fun updateAll(context: Context) {
        DateWidget().updateAll(context)
        MonthlyGridWidget().updateAll(context)
        AgendaWidget().updateAll(context)
        HabitsWidget().updateAll(context)
    }
}

/** [AppWidgetConfigureActivity]'s save step — one shared activity serves all four widget
 * types (§8.1's original three, plus the Habits quick-check widget), so it resolves which one
 * this specific `appWidgetId` belongs to via its provider component, rather than each widget
 * needing its own configure Activity subclass. */
suspend fun updateWidgetForAppWidgetId(context: Context, appWidgetId: Int) {
    val provider = AppWidgetManager.getInstance(context).getAppWidgetInfo(appWidgetId)?.provider?.className ?: return
    val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
    when (provider) {
        DateWidgetReceiver::class.java.name -> DateWidget().update(context, glanceId)
        MonthlyGridWidgetReceiver::class.java.name -> MonthlyGridWidget().update(context, glanceId)
        AgendaWidgetReceiver::class.java.name -> AgendaWidget().update(context, glanceId)
        HabitsWidgetReceiver::class.java.name -> HabitsWidget().update(context, glanceId)
    }
}

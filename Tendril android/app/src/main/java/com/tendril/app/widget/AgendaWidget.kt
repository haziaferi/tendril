package com.tendril.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.currentState
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.tendril.app.MainActivity
import com.tendril.app.TendrilApp
import com.tendril.app.domain.recurrence.EntryOccurrence
import com.tendril.app.domain.recurrence.EntryOccurrences
import java.time.LocalDate
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

/** §8.1 — "compact (4×2) and tall (4×4) variants, times and titles fully spelled out." A
 * single [LazyColumn] naturally covers both — the OS-placed widget height determines how many
 * rows are visible before scrolling, with no separate density-tier branch needed the way
 * Monthly's marker style does (§8.1's "written out, not just a dot" is already the only mode). */
class AgendaWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as TendrilApp).container
        val today = LocalDate.now()
        // getAllDated + expand, not getInRange: a recurring series (§4.1) is anchored at its
        // first occurrence, which for a long-running weekly meeting is nowhere near the next
        // fortnight — `startDate BETWEEN` returned nothing for it while the series genuinely
        // occurs inside the window. Expansion also puts a multi-day event on every day it
        // covers rather than only its first.
        val entries = EntryOccurrences
            .byDay(container.database.entryDao().getAllDated(), today, today.plusDays(13))
            .toSortedMap()

        provideContent {
            val prefs = currentState<Preferences>()
            val config = prefs.toWidgetColorConfig()
            val theme = resolveWidgetTheme(container, context, config)
            AgendaContent(theme, entries)
        }
    }
}

class AgendaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AgendaWidget()
}

@Composable
private fun AgendaContent(theme: WidgetTheme, entriesByDay: Map<LocalDate, List<EntryOccurrence>>) {
    Column(modifier = GlanceModifier.fillMaxSize().background(theme.backgroundWithOpacity)) {
        Text(
            text = "Agenda",
            style = TextStyle(color = ColorProvider(theme.palette.text), fontSize = 14.sp, fontWeight = FontWeight.Medium),
            modifier = GlanceModifier.fillMaxWidth().padding(8.dp).clickable(actionStartActivity<MainActivity>()),
        )
        if (entriesByDay.isEmpty()) {
            Text(
                text = "Nothing scheduled",
                style = TextStyle(color = ColorProvider(theme.palette.textDim), fontSize = 12.sp),
                modifier = GlanceModifier.padding(8.dp),
            )
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                entriesByDay.forEach { (date, dayEntries) ->
                    item {
                        Text(
                            text = date.dayLabel(),
                            style = TextStyle(color = ColorProvider(theme.accent2), fontSize = 11.sp, fontWeight = FontWeight.Medium),
                            modifier = GlanceModifier.fillMaxWidth().padding(start = 8.dp, top = 6.dp, bottom = 2.dp),
                        )
                    }
                    // Already ordered by EntryOccurrences.expand; re-sorting on a nullable
                    // startTime here put untimed entries first or last depending on the
                    // comparator, inconsistently with every other surface.
                    items(dayEntries) { occurrence ->
                        Row(modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)) {
                            Text(
                                // "→" on a span's continuation days: the same title on three consecutive
                                // days needs something saying it started earlier.
                                text = if (occurrence.isFirstDay) (occurrence.startTime?.toString() ?: "—") else "→",
                                style = TextStyle(color = ColorProvider(theme.accent2), fontSize = 11.sp),
                                modifier = GlanceModifier.width(48.dp),
                            )
                            Text(
                                text = occurrence.entry.title,
                                maxLines = 1,
                                style = TextStyle(color = ColorProvider(theme.palette.text), fontSize = 12.sp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun LocalDate.dayLabel(): String {
    val today = LocalDate.now()
    return when (this) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> "${dayOfWeek.getDisplayName(JavaTextStyle.SHORT, Locale.getDefault())} $dayOfMonth"
    }
}

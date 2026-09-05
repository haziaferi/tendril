package com.tendril.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.tendril.app.MainActivity
import com.tendril.app.TendrilApp
import com.tendril.app.domain.recurrence.EntryOccurrence
import com.tendril.app.domain.recurrence.EntryOccurrences
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

private enum class MonthDensity { DOTS, ONE, WRAP }

/** §8.1 — "four density tiers depending on widget height." Thresholds are dp approximations
 * of the spec's grid-cell notation (`dots` 4×2, `one` 4×3.5, `wrap` 4×5) — no exact dp figure
 * was specified, so these are reasonable breakpoints against a standard ~70dp launcher cell. */
private fun densityFor(heightDp: Int): MonthDensity = when {
    heightDp < 220 -> MonthDensity.DOTS
    heightDp < 340 -> MonthDensity.ONE
    else -> MonthDensity.WRAP
}

class MonthlyGridWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as TendrilApp).container
        val today = LocalDate.now()
        val gridStart = today.withDayOfMonth(1).let { it.minusDays(((it.dayOfWeek.value - DayOfWeek.MONDAY.value) + 7).toLong() % 7) }
        val gridEnd = gridStart.plusDays(41)
        // See AgendaWidget for why this is getAllDated + expansion rather than getInRange:
        // a recurring series' stored row sits at its first occurrence, not inside this grid.
        val entriesByDay = EntryOccurrences
            .byDay(container.database.entryDao().getAllDated(), gridStart, gridEnd)

        provideContent {
            val prefs = currentState<Preferences>()
            val config = prefs.toWidgetColorConfig()
            val theme = resolveWidgetTheme(container, context, config)
            MonthlyGridContent(theme, today, gridStart, entriesByDay)
        }
    }
}

class MonthlyGridWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MonthlyGridWidget()
}

@Composable
private fun MonthlyGridContent(theme: WidgetTheme, today: LocalDate, gridStart: LocalDate, entriesByDay: Map<LocalDate, List<EntryOccurrence>>) {
    val size = LocalSize.current
    val density = densityFor(size.height.value.toInt())
    val monthLabel = today.month.getDisplayName(JavaTextStyle.FULL, Locale.getDefault())

    Column(
        modifier = GlanceModifier.fillMaxSize().background(theme.backgroundWithOpacity).padding(8.dp),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Text(
                text = monthLabel,
                style = TextStyle(color = ColorProvider(theme.palette.text), fontSize = 14.sp, fontWeight = FontWeight.Medium),
                modifier = GlanceModifier.defaultWeight(),
            )
        }
        Row(modifier = GlanceModifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp)) {
            for (dow in MONDAY_FIRST) {
                Text(
                    text = dow.getDisplayName(JavaTextStyle.NARROW, Locale.getDefault()),
                    style = TextStyle(color = ColorProvider(theme.palette.textDim), fontSize = 10.sp, textAlign = TextAlign.Center),
                    modifier = GlanceModifier.defaultWeight(),
                )
            }
        }
        for (week in 0 until 6) {
            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                for (dow in 0 until 7) {
                    val date = gridStart.plusDays((week * 7 + dow).toLong())
                    MonthDayCell(
                        date = date,
                        inMonth = date.month == today.month,
                        isToday = date == today,
                        events = entriesByDay[date].orEmpty(),
                        density = density,
                        theme = theme,
                        modifier = GlanceModifier.defaultWeight(),
                    )
                }
            }
        }
    }
}

private val MONDAY_FIRST = listOf(
    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY,
)

@Composable
private fun MonthDayCell(
    date: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    events: List<EntryOccurrence>,
    density: MonthDensity,
    theme: WidgetTheme,
    modifier: GlanceModifier,
) {
    val dayNumColor = when {
        isToday -> theme.accent2
        inMonth -> theme.palette.text
        else -> theme.palette.textFaint
    }
    Column(modifier = modifier.padding(1.dp), horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
        Text(
            text = date.dayOfMonth.toString(),
            style = TextStyle(color = ColorProvider(dayNumColor), fontSize = 11.sp, textAlign = TextAlign.Center),
        )
        if (events.isNotEmpty()) {
            when (density) {
                MonthDensity.DOTS -> Text(
                    text = "•",
                    style = TextStyle(color = ColorProvider(theme.accent2), fontSize = 10.sp, textAlign = TextAlign.Center),
                )
                MonthDensity.ONE, MonthDensity.WRAP -> {
                    Text(
                        text = events.first().entry.title,
                        maxLines = if (density == MonthDensity.WRAP) 2 else 1,
                        style = TextStyle(color = ColorProvider(theme.palette.textDim), fontSize = 8.sp, textAlign = TextAlign.Center),
                    )
                    if (events.size > 1) {
                        Text(
                            text = "+${events.size - 1}",
                            style = TextStyle(color = ColorProvider(theme.palette.textDim), fontSize = 8.sp, textAlign = TextAlign.Center),
                        )
                    }
                }
            }
        }
    }
}

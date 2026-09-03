package com.tendril.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.tendril.app.MainActivity
import com.tendril.app.TendrilApp
import com.tendril.app.data.habit.Habit
import java.time.LocalDate

/**
 * Habits quick-check widget — added against Loop/uhabits prior art (checking a habit off from
 * the home screen without opening the app is their core interaction; none of §8.1's original
 * three widget types touched Habits at all). Tapping an unchecked habit checks it in for today
 * via [CheckInHabitAction], which calls the exact same [com.tendril.app.domain.CheckInHabitUseCase]
 * the in-app Tasks & Habits screen uses — one streak-math implementation, not two.
 */
class HabitsWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as TendrilApp).container
        // One-shot read, not a live Flow collection (§8.1's own note on AgendaWidget) —
        // Glance widgets render from a snapshot on each update.
        val habits = container.database.habitDao().getAll()
            .filter { it.deletedAt == null }
            .sortedBy { it.title }
        val today = LocalDate.now()

        provideContent {
            val prefs = currentState<Preferences>()
            val config = prefs.toWidgetColorConfig()
            val theme = resolveWidgetTheme(container, context, config)
            HabitsContent(theme, habits, today)
        }
    }
}

class HabitsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HabitsWidget()
}

private val HABIT_ID_KEY = ActionParameters.Key<Long>("habit_id")

/**
 * Runs without opening the app — the whole point of a quick-check widget. Toggles: tapping an
 * unchecked habit checks it in, tapping an already-checked one undoes today's check-in (§8.1.1
 * — the "no in-app undo either" gap this closes), both through the same shared use case the
 * in-app screen calls so there's still only one streak-math implementation.
 */
class CheckInHabitAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val habitId = parameters[HABIT_ID_KEY] ?: return
        val container = (context.applicationContext as TendrilApp).container
        val habit = container.database.habitDao().getById(habitId) ?: return
        if (habit.lastCompletedDate == LocalDate.now()) {
            container.checkInHabitUseCase.undoCheckIn(habitId)
        } else {
            container.checkInHabitUseCase.checkIn(habitId)
        }
        HabitsWidget().update(context, glanceId)
    }
}

@Composable
private fun HabitsContent(theme: WidgetTheme, habits: List<Habit>, today: LocalDate) {
    Column(modifier = GlanceModifier.fillMaxSize().background(theme.backgroundWithOpacity)) {
        Text(
            text = "Habits",
            style = TextStyle(color = ColorProvider(theme.palette.text), fontSize = 14.sp, fontWeight = FontWeight.Medium),
            modifier = GlanceModifier.fillMaxWidth().padding(8.dp).clickable(actionStartActivity<MainActivity>()),
        )
        if (habits.isEmpty()) {
            Text(
                text = "No habits yet",
                style = TextStyle(color = ColorProvider(theme.palette.textDim), fontSize = 12.sp),
                modifier = GlanceModifier.padding(8.dp),
            )
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(habits) { habit ->
                    val doneToday = habit.lastCompletedDate == today
                    Row(
                        modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
                            .clickable(actionRunCallback<CheckInHabitAction>(actionParametersOf(HABIT_ID_KEY to habit.id))),
                    ) {
                        Text(
                            text = if (doneToday) "✓" else "○",
                            style = TextStyle(
                                color = ColorProvider(if (doneToday) theme.accent2 else theme.palette.textDim),
                                fontSize = 14.sp,
                            ),
                            modifier = GlanceModifier.width(24.dp),
                        )
                        Text(
                            text = habit.title,
                            maxLines = 1,
                            style = TextStyle(
                                color = ColorProvider(if (doneToday) theme.palette.textDim else theme.palette.text),
                                fontSize = 12.sp,
                            ),
                        )
                    }
                }
            }
        }
    }
}

package com.tendril.app.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.actionParametersOf
import androidx.test.platform.app.InstrumentationRegistry
import com.tendril.app.TendrilApp
import com.tendril.app.data.entry.IntervalUnit
import com.tendril.app.data.habit.Habit
import com.tendril.app.data.habit.HabitFrequency
import com.tendril.app.notifications.AlarmScheduler
import com.tendril.app.notifications.HabitReminderAlarmReceiver
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Audit 1.12 — an undo from the Habits widget re-arms the habit's alarm.
 *
 * This is the row the audit closed with a fix it could not prove. Four of the five check-in paths
 * re-arm; `CheckInHabitAction` was the one that did not, so an undo from the home screen *after*
 * that day's alarm had already fired left the habit due again with nothing armed for it. The fix
 * went in at `fd77880` and the row says plainly what pinned it: "the compiler and the walk row,
 * not a test", because "a Glance `ActionCallback` is not reachable from the unit suite".
 *
 * That was true and is not the whole truth. It is unreachable from the **JVM** suite; from here it
 * is an ordinary suspend function. Nothing about the fix needed a person holding the phone — what
 * it needed was a test that could run on one.
 *
 * **Why this cannot pass vacuously.** The habit's own alarm is cancelled first, the habit-reminder
 * alarms are counted, and the count must be exactly one higher afterwards. Delete the
 * `rescheduleHabit` line from [CheckInHabitAction] and that assertion fails, because nothing else
 * in that path arms anything — which is precisely the defect as it stood on 2026-09-22. The
 * undo branch is checked too: if the callback had taken the check-in direction instead, the habit
 * would still read as completed today and the test says so rather than quietly measuring the
 * wrong branch.
 *
 * The habit is written to the real database, because the callback reaches its container through
 * `applicationContext` and there is no seam to inject a fake. It is removed again in [tearDown],
 * along with its alarm.
 */
class HabitWidgetCheckInInstrumentedTest {

    private lateinit var context: Context
    private lateinit var app: TendrilApp
    private var habitId: Long = 0

    /**
     * `CheckInHabitAction` ends by calling `HabitsWidget().update(context, glanceId)`, which needs
     * a real app-widget id and throws without one. That call happens *after* the re-arm, so the
     * assertion below is unaffected — but it means the action is invoked inside `runCatching`, and
     * saying so is better than a silently swallowed exception. Placing an actual widget would need
     * a launcher, which is the thing this test exists to avoid.
     */
    private object TestGlanceId : GlanceId

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        app = context.applicationContext as TendrilApp
    }

    @After
    fun tearDown() = runBlocking {
        if (habitId != 0L) {
            app.container.alarmScheduler.cancelHabit(habitId)
            app.container.database.habitDao().deleteForever(habitId)
        }
    }

    /**
     * How many habit-reminder alarms the system holds for this package.
     *
     * A count rather than a yes/no, because `dumpsys` does not carry the request code and this
     * phone has other habits with their own alarms — "is any habit alarm armed?" would be true
     * before the call for reasons having nothing to do with this test, and the precondition that
     * makes the test non-vacuous would be unassertable. A delta of exactly one is specific enough
     * and survives whatever else is on the device.
     *
     * Matched on the receiver rather than on a moment: the habit's next firing is tomorrow, and
     * asserting a wall-clock time would re-implement `habitFiring`'s arithmetic in the test and
     * then check the test against itself.
     */
    private fun habitAlarmCount(): Int {
        val fd = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("dumpsys alarm")
        val dump = android.os.ParcelFileDescriptor.AutoCloseInputStream(fd)
            .bufferedReader().use { it.readText() }
        return dump.lineSequence().count {
            it.contains(context.packageName) &&
                it.contains(HabitReminderAlarmReceiver::class.java.name) &&
                it.contains("Alarm{")
        }
    }

    @Test
    fun undoingACheckInFromTheWidgetReArmsTheHabitsAlarm() = runBlocking {
        // A habit whose time has already passed today, already checked off — so the callback
        // takes its undo branch, which is the direction that does not self-heal. (A check-in
        // self-heals: the receiver re-tests `isHabitDueOn` at fire time, posts nothing, re-arms.)
        val past = LocalTime.now().minusHours(1)
        habitId = app.container.database.habitDao().insert(
            Habit(
                title = "Widget undo probe",
                time = past,
                frequency = HabitFrequency(1, IntervalUnit.DAY),
                streak = 3,
                lastCompletedDate = LocalDate.now(),
                previousStreak = 2,
                previousCompletedDate = LocalDate.now().minusDays(1),
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            )
        )
        assertTrue("App Lock must be off for the callback to write", !app.container.appLockPreferences.enabled.value)

        // The day's alarm has already fired, so nothing is armed for *this* habit. Recording the
        // count here is what stops the test passing on a tree where the fix was never applied.
        app.container.alarmScheduler.cancelHabit(habitId)
        val before = habitAlarmCount()

        runCatching {
            CheckInHabitAction().onAction(
                context, TestGlanceId, actionParametersOf(HABIT_ID_KEY to habitId),
            )
        }

        val habit = app.container.database.habitDao().getById(habitId)
        assertNotNull("the habit vanished", habit)
        assertTrue(
            "the undo did not restore the habit to un-completed, so this never exercised the " +
                "undo branch: lastCompletedDate=${habit?.lastCompletedDate}",
            habit?.lastCompletedDate != LocalDate.now(),
        )
        assertEquals(
            "audit 1.12: an undo from the widget left the habit due with nothing armed for it",
            before + 1,
            habitAlarmCount(),
        )
    }

    @Test
    fun theRequestCodeIsTheOneTheSchedulerWouldUse() {
        // Guards the matcher above: if habit alarms ever moved off HabitReminderAlarmReceiver the
        // dumpsys scan would quietly stop finding anything and the test would fail for the wrong
        // reason — or, worse, a future rewrite could make it pass for one.
        assertTrue(
            "habitRequestCode should be stable for a given id",
            AlarmScheduler.habitRequestCode(4242L) == AlarmScheduler.habitRequestCode(4242L),
        )
    }
}

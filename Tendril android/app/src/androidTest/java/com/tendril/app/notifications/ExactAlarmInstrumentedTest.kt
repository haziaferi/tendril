package com.tendril.app.notifications

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import com.tendril.app.data.reminder.Reminder
import com.tendril.app.data.reminder.ReminderDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * Audit 2.12 — does this app *ask* for an exact alarm when it is allowed one?
 *
 * That question, and deliberately not "is the alarm exact". The two came apart on 2026-09-23 and
 * the distinction is the whole point of this file. On the OnePlus LE2123 (API 34,
 * `USE_EXACT_ALARM` reported `granted=true`) [AlarmScheduler.canScheduleExact] returns **true**,
 * so `setExactAndAllowWhileIdle` is the call that runs — and the system still hands back a
 * windowed alarm: 3600000 ms for a task two days out, and 75% of the interval for the roughly
 * twenty-minute alarms the 1.13 walk armed, which is how a task due at 14:05 came to fire at
 * 14:16:04.
 *
 * **So exactness is not one property but two**, and only the first belongs in a test:
 *
 * | | under this app's control | asserted here |
 * |---|---|---|
 * | the request — permission held, exact branch taken | yes | **yes** |
 * | the outcome — `windowLength` the system records | no | no, it is an audit row |
 *
 * An assertion on the outcome would be red on this phone and green on a Pixel with no commit
 * able to move it either way, which is the kind of red that gets ignored and then deleted — and
 * it would take the half that *can* regress with it. The outcome is recorded instead as a dated
 * measurement in `docs/audit-2026-09-22.md`, because a test suite is the wrong place to assert a
 * fact about a handset.
 *
 * `Assume.assumeTrue` was considered for the outcome and rejected: detecting "this device
 * relaxes" means scheduling an alarm and reading its window, so the test would skip in exactly
 * the cases where it would have failed — a dead check wearing a skip's clothes.
 */
class ExactAlarmInstrumentedTest {

    private lateinit var context: Context
    private lateinit var manager: AlarmManager

    private class EmptyReminderDao : ReminderDao {
        override suspend fun insert(reminder: Reminder): Long = 1L
        override suspend fun getForEntry(entryId: Long): List<Reminder> = emptyList()
        override suspend fun getAllForEntry(entryId: Long): List<Reminder> = emptyList()
        override fun observeForEntry(entryId: Long): Flow<List<Reminder>> = flowOf(emptyList())
        override suspend fun softDelete(id: Long, deletedAt: Instant) = Unit
        override suspend fun getAll(): List<Reminder> = emptyList()
        override suspend fun getByUid(uid: String): Reminder? = null
        override suspend fun deleteAll() = Unit
    }

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    }

    @Test
    fun theSystemSaysThisAppMayScheduleExactAlarms() {
        // The manifest's half. `USE_EXACT_ALARM` is auto-granted from API 33; on 31-32 the
        // stand-in `SCHEDULE_EXACT_ALARM` is revocable in Settings, so this can legitimately be
        // false there — and below 31 the call does not exist at all.
        assertTrue(
            "canScheduleExactAlarms() is false on API ${Build.VERSION.SDK_INT} " +
                "(${Build.MANUFACTURER} ${Build.MODEL}): either the manifest lost " +
                "USE_EXACT_ALARM, or SCHEDULE_EXACT_ALARM was revoked on a 12/12L device",
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms(),
        )
    }

    @Test
    fun theSchedulerAsksForAnExactAlarmWhenItMay() {
        // The app's half, read from the scheduler's own decision rather than from a second copy
        // of the expression — a test that re-derives the logic it is checking agrees with itself
        // no matter what the code does. This is what goes red if the guard is inverted, the
        // permission is dropped from the manifest, or the branch is "simplified" away.
        val scheduler = AlarmScheduler(context, EmptyReminderDao())

        assertTrue(
            "AlarmScheduler.canScheduleExact() is false while the system reports " +
                "canScheduleExactAlarms()=${
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
                } — every alarm would take the inexact fallback and every reminder would drift",
            scheduler.canScheduleExact(manager),
        )
    }
}

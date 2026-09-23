package com.tendril.desktopapp

import com.tendril.app.data.TendrilDatabase
import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryDao
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.data.entry.EntryStatus
import com.tendril.app.data.habit.HabitDao
import com.tendril.app.data.reminder.ReminderDao
import com.tendril.app.domain.reminders.Firing
import com.tendril.app.domain.reminders.FiringKind
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

/**
 * The first tests this module has ever had.
 *
 * `Tendril windows` had no test source set, and the price of that is written down: audit rows 1.5,
 * 1.6 and 1.8 are all desktop-only defects that sat as hypotheses through five audits because
 * nothing here could execute a check on them. Two were eventually settled by walking the dev build
 * by hand, and their fixes are pinned by that walk rather than by anything a gate runs.
 *
 * [DesktopReminderScheduler] is the part of this module worth reaching first: it decides whether a
 * reminder is shown, and showing one twice — or not at all — is a failure the person notices. The
 * arithmetic underneath it (`domain/reminders/ReminderFirings.kt`) is shared and already tested on
 * the phone; what was untested is *this* class's own job, which is when to look again and showing
 * a firing once.
 *
 * **Real waits, not virtual time.** `loop()` sleeps with `delay` but then re-reads `Instant.now()`
 * before notifying, so a `TestDispatcher` can satisfy the delay without the wall clock having
 * moved and the firing never passes its own `!Instant.now().isBefore(next.at)` check. Virtual time
 * cannot drive this class at all. The waits below are therefore real — and, since 2026-09-23, they
 * wait *until* the expected firing arrives rather than for a fixed span (see [shownDuring]), because
 * the fixed version flaked once under load. Each fixture also asserts that something fired *at all*:
 * a fixture that quietly stops producing firings would otherwise turn every one of these into a
 * green test of nothing. The first draft did exactly that, and only that guard showed it.
 *
 * `TendrilDatabase` is a Room class with no desktop-constructible form, so the three DAOs the
 * scheduler reads are stubbed rather than a database being stood up.
 */
class DesktopReminderSchedulerTest {

    /**
     * A TASK whose firing is [millis] from now.
     *
     * `entryFirings` emits an OVERDUE firing only when the moment is still ahead (`at.isAfter(now)`)
     * — "overdue" names the firing scheduled *at* the due moment, not a task already past it. A
     * time in the past produces nothing at all, which is how the first draft of these tests came to
     * assert against an empty list five times over.
     */
    private fun taskDueIn(id: Long, title: String, millis: Long) = Entry(
        id = id,
        uid = "e$id",
        title = title,
        kind = EntryKind.TASK,
        status = EntryStatus.PENDING,
        startDate = LocalDate.now(),
        startTime = LocalTime.now().plusNanos(millis * 1_000_000),
        endDate = null,
        endTime = null,
        recurrenceRule = null,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    /** A database whose only content is [entries] — no reminders, no habits, no exceptions. */
    private fun databaseOf(entries: List<Entry>): TendrilDatabase {
        val entryDao = mockk<EntryDao>()
        coEvery { entryDao.getAllSchedulable() } returns entries
        coEvery { entryDao.getAllExceptions() } returns emptyList()
        val reminderDao = mockk<ReminderDao>()
        coEvery { reminderDao.getAll() } returns emptyList()
        val habitDao = mockk<HabitDao>()
        coEvery { habitDao.getAll() } returns emptyList()

        val db = mockk<TendrilDatabase>()
        every { db.entryDao() } returns entryDao
        every { db.reminderDao() } returns reminderDao
        every { db.habitDao() } returns habitDao
        return db
    }

    /**
     * Runs a scheduler over [entries], driving it with [drive], and returns what it showed.
     *
     * [awaitCount] is the fix for a flake this file shipped with on 2026-09-23: the first version
     * slept a fixed 900 ms for a firing 200 ms out, which is ample on an idle JVM and not ample on
     * one that has just class-loaded mockk and is running a Gradle daemon beside it. It failed
     * exactly once, in the run that compiled a new test class first, and passed on three
     * consecutive re-runs — which is the worst failure rate a test can have, since it is high
     * enough to be noticed and rare enough to be dismissed.
     *
     * So: wait *until* the expected number has been shown, up to [waitMs], instead of sleeping the
     * whole budget. Fast when the machine is idle, patient when it is not. A test asserting that
     * nothing is shown passes `awaitCount = 0` and still sleeps the full time, because absence
     * cannot be waited for — there is nothing to wait until.
     */
    private fun shownDuring(
        entries: List<Entry>,
        waitMs: Long,
        awaitCount: Int = 0,
        settleMs: Long = 0,
        drive: (DesktopReminderScheduler) -> Unit = { it.replan() },
    ): List<Firing> {
        val shown = Collections.synchronizedList(mutableListOf<Firing>())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val scheduler = DesktopReminderScheduler(databaseOf(entries), scope) { shown += it }
            drive(scheduler)
            val deadline = System.nanoTime() + waitMs * 1_000_000
            if (awaitCount <= 0) {
                Thread.sleep(waitMs)
            } else {
                while (shown.size < awaitCount && System.nanoTime() < deadline) Thread.sleep(20)
                // Then keep watching a little longer, so "exactly one" is a real assertion rather
                // than a race the poll happened to win on its first look.
                if (settleMs > 0) Thread.sleep(settleMs)
            }
        } finally {
            scope.cancel()
        }
        return shown.toList()
    }

    @Test
    fun `a task reaching its moment is shown once`() {
        val shown = shownDuring(
            listOf(taskDueIn(1, "Call the plumber", 400)),
            waitMs = 5_000, awaitCount = 1, settleMs = 400,
        )

        assertEquals("one due task, one toast: $shown", 1, shown.size)
        assertEquals(FiringKind.OVERDUE, shown.single().kind)
        assertEquals("Call the plumber", shown.single().title)
    }

    @Test
    fun `re-planning after a firing has been shown does not show it again`() {
        // The invariant audit 1.6 threatens, in the form that does not need a race. Every write in
        // the app calls a coordinator callback and every callback is `replan()`, so re-planning
        // after something has fired is the ordinary case rather than an edge one — a second toast
        // for the same reminder is exactly what the `fired` keys exist to prevent.
        val shown = shownDuring(
            listOf(taskDueIn(1, "Call the plumber", 400)),
            waitMs = 5_000, awaitCount = 1, settleMs = 600,
        ) { scheduler ->
            scheduler.replan()
            Thread.sleep(500)          // let it fire
            repeat(5) { scheduler.replan(); Thread.sleep(60) }
        }

        assertEquals("a re-plan showed a firing that had already been shown: $shown", 1, shown.size)
    }

    @Test
    fun `nothing is shown before its moment`() {
        val shown = shownDuring(listOf(taskDueIn(1, "Later", 60_000)), waitMs = 700)  // absence: no awaitCount

        assertEquals("a firing a minute away was shown early", emptyList<Firing>(), shown)
    }

    @Test
    fun `two firings due together are both shown`() {
        // The contrast that makes the two tests above mean anything: the scheduler is not simply
        // notifying once and stopping. Two entries owe two toasts.
        val shown = shownDuring(
            listOf(taskDueIn(1, "First", 400), taskDueIn(2, "Second", 400)),
            waitMs = 5_000, awaitCount = 2, settleMs = 400,
        )

        assertEquals("expected two toasts, got $shown", 2, shown.size)
        assertEquals(setOf("First", "Second"), shown.map { it.title }.toSet())
    }

    @Test
    fun `concurrent re-plans do not show a firing twice`() {
        // Audit 1.6 proper. `replan()` is `job?.cancel(); job = scope.launch { loop() }` with no
        // lock, so two callbacks in flight together can both read the same `job`, both cancel it,
        // and both assign — leaving one loop running that nothing references. `pending()` filters
        // by `fired` *before* the delay, so both loops can resolve the same firing and both notify.
        //
        // Threads rather than a dispatcher: the window is between two machine instructions. It is
        // repeated because a race that fires sometimes is still a defect, and asserted on the worst
        // run rather than the average.
        //
        // The walk of 2026-09-22 did not reproduce this with two writes 2 ms apart. If this ever
        // goes red, that walk simply was not fast enough and row 1.6 can move to executed.
        val counts = mutableListOf<Int>()
        repeat(25) {
            val shown = AtomicInteger(0)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            try {
                val scheduler = DesktopReminderScheduler(
                    databaseOf(listOf(taskDueIn(1, "Contested", 250))), scope
                ) { shown.incrementAndGet() }
                val t1 = Thread { scheduler.replan() }
                val t2 = Thread { scheduler.replan() }
                t1.start(); t2.start(); t1.join(); t2.join()
                Thread.sleep(700)
            } finally {
                scope.cancel()
            }
            counts += shown.get()
        }

        assertTrue(
            "a firing was shown more than once by concurrent re-plans: $counts",
            counts.all { it <= 1 },
        )
        // Without this the assertion above passes on a fixture that fires nothing — which is what
        // the first draft of this file did, in five tests at once.
        assertTrue("nothing fired in any of the 25 runs; the fixture stopped exercising it", counts.any { it == 1 })
    }
}

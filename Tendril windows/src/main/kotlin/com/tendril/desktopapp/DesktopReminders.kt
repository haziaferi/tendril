package com.tendril.desktopapp

import com.tendril.app.data.TendrilDatabase
import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.data.entry.RecurrenceRule
import com.tendril.app.data.habit.Habit
import com.tendril.app.domain.EntryScheduleCoordinator
import com.tendril.app.domain.reminders.Firing
import com.tendril.app.domain.reminders.FiringKind
import com.tendril.app.domain.reminders.entryFirings
import com.tendril.app.domain.reminders.habitFiring
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime

/**
 * B§13.6 #7 — the desktop's reminders: the phone arms one alarm per firing with `AlarmManager`;
 * this keeps **one sleeping coroutine** for the earliest firing owed and re-plans whenever
 * anything that can move a firing changes (every [EntryScheduleCoordinator] callback — the same
 * seam `ResolveEntryUseCase`, the editors and the reminder sheet already call). The arithmetic
 * is `domain/reminders/ReminderFirings.kt`, shared with the phone and tested there; this class
 * only decides *when to look again*. A firing shows once (the [fired] keys); the fifteen-minute
 * ceiling re-plans through a laptop's sleep — a firing slept past shows on wake, once — and
 * nothing is queued for a moment that passed while the app was not running (the phone's boot
 * sweep has no twin here; recorded in the windows spec). [notify] is the tray's toast.
 */
class DesktopReminderScheduler(
    private val database: TendrilDatabase,
    private val scope: CoroutineScope,
    private val notify: (Firing) -> Unit,
) : EntryScheduleCoordinator {
    private var job: Job? = null
    private val fired = LinkedHashSet<String>()
    /** The last firing shown — a click on its toast opens the tab it belongs to. */
    @Volatile var lastFired: Firing? = null
        private set

    override suspend fun onEntryChanged(entry: Entry) = replan()
    override suspend fun onEntryRemoved(entry: Entry) = replan()
    override suspend fun onHabitChanged(habit: Habit) = replan()
    override suspend fun onHabitRemoved(habitId: Long) = replan()
    override suspend fun onReminderRemoved(entryId: Long, reminderId: Long) = replan()

    /** Drop the sleeping job and plan again from the database; called at start and on every change. */
    /**
     * Audit 1.6 — `@Synchronized` because this is called from every coordinator callback and two
     * of them can be in flight together. Unsynchronised, both read the same [job], both cancel it,
     * and both assign: one loop is left running that nothing references, and two loops resolve the
     * same firing. `DesktopReminderSchedulerTest` reproduced it in **9 of 25** runs; the hand walk
     * of 2026-09-22 could not, which is the argument for this module having tests at all.
     *
     * The lock alone is not enough, and [fire] is the other half: cancellation is cooperative, so a
     * loop that has already come back from its `delay` has no suspension point left before it
     * notifies and will show its firing whatever `cancel()` says.
     */
    @Synchronized
    fun replan() {
        job?.cancel()
        job = scope.launch { loop() }
    }

    private suspend fun loop() {
        while (true) {
            val now = Instant.now()
            // The whole batch, not just the earliest. `entryFirings` only ever emits a firing
            // still ahead of `now`, so anything whose moment passes while another toast is being
            // shown disappears from the next `pending()` **permanently** — two tasks due in the
            // same minute produced one toast and silently dropped the other. Holding the batch
            // across the delay is what keeps the second one.
            val due = pending(now)
            val next = due.firstOrNull()
            val wait = next?.let { Duration.between(now, it.at) } ?: CEILING
            delay(minOf(wait, CEILING).toMillis().coerceAtLeast(0))
            val woke = Instant.now()
            due.filter { !woke.isBefore(it.at) }.forEach { fire(it) }
        }
    }

    /**
     * Show a firing, unless it has already been shown.
     *
     * The check and the record are one atomic step. `pending()` also filters on [fired], but it
     * does so *before* the delay, which is too early to help: two loops racing past that filter
     * both hold the same firing and both arrive here. Deciding it at the moment of notifying is
     * what makes "a firing shows once" true rather than likely.
     */
    private fun fire(f: Firing) {
        val fresh = synchronized(fired) {
            if (!fired.add(key(f))) false
            else {
                if (fired.size > 500) fired.remove(fired.first())
                true
            }
        }
        if (!fresh) return
        lastFired = f
        notify(f)
    }

    /** Every firing owed after [now], soonest first, minus the ones already shown. */
    suspend fun pending(now: Instant): List<Firing> {
        val entryDao = database.entryDao()
        val reminders = database.reminderDao().getAll().filter { it.deletedAt == null }.groupBy { it.entryId }
        val exceptions = entryDao.getAllExceptions().groupBy { it.originalEntryId }
        val entries = entryDao.getAllSchedulable().flatMap { e ->
            val ex = if (e.kind == EntryKind.EVENT && e.recurrenceRule is RecurrenceRule.Fixed) exceptions[e.id].orEmpty() else emptyList()
            entryFirings(e, reminders[e.id].orEmpty(), ex, now)
        }
        val habits = database.habitDao().getAll().mapNotNull { habitFiring(it, ZonedDateTime.now()) }
        val shown = synchronized(fired) { fired.toSet() }
        return (entries + habits).filter { key(it) !in shown }.sortedBy { it.at }
    }

    private fun key(f: Firing) = "${f.kind}:${f.entryId ?: f.habitId}:${f.reminderId}:${f.at.epochSecond}"

    companion object {
        val CEILING: Duration = Duration.ofMinutes(15)
        /** The toast's title: the moment first, so a glance ranks it. */
        fun title(f: Firing): String = when (f.kind) {
            FiringKind.OVERDUE -> "Due now · ${f.title}"
            FiringKind.REMINDER -> "${f.detail.substringBefore(" ·").replaceFirstChar { it.uppercase() }} · ${f.title}"
            FiringKind.HABIT -> "Habit · ${f.title}"
        }
        fun message(f: Firing): String = when (f.kind) {
            FiringKind.OVERDUE -> if (f.detail == "today") "Today" else "Today ${f.detail}"
            FiringKind.REMINDER -> f.detail.substringAfter("· ", f.detail)
            FiringKind.HABIT -> f.detail
        }
    }
}

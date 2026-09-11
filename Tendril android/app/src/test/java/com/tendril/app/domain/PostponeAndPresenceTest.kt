package com.tendril.app.domain

import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.data.entry.EntryStatus
import com.tendril.app.data.habit.HabitCompletion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** §0.6.4's Postpone and sub-task grouping, and §0.6.6's presence summary. */
class PostponeAndPresenceTest {

    private val today = LocalDate.of(2026, 9, 11)
    private val now = LocalTime.of(14, 30)

    private fun task(
        id: Long = 1,
        date: LocalDate? = today,
        time: LocalTime? = null,
        due: LocalDate? = null,
        parent: Long? = null,
        status: EntryStatus = EntryStatus.PENDING,
    ) = Entry(
        id = id, title = "t$id", kind = EntryKind.TASK, status = status,
        startDate = date, startTime = time, endDate = null, endTime = null, recurrenceRule = null,
        dueDate = due, parentEntryId = parent, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
    )

    // ------------------------------------------------------------------ postpone

    @Test
    fun `a day moves the date and leaves a timeless task timeless`() {
        val moved = task().postponed(PostponeAmount(1, PostponeUnit.DAY), today, now)
        assertEquals(today.plusDays(1), moved.startDate)
        assertNull(moved.startTime)
    }

    @Test
    fun `an hour on a timeless task anchors at now, and rolls the date past midnight`() {
        val late = task().postponed(PostponeAmount(1, PostponeUnit.HOUR), today, LocalTime.of(23, 30))
        assertEquals(today.plusDays(1), late.startDate)
        assertEquals(LocalTime.of(0, 30), late.startTime)
    }

    @Test
    fun `a timed task keeps its time across a week`() {
        val moved = task(time = LocalTime.of(9, 0)).postponed(PostponeAmount(1, PostponeUnit.WEEK), today, now)
        assertEquals(today.plusWeeks(1), moved.startDate)
        assertEquals(LocalTime.of(9, 0), moved.startTime)
    }

    @Test
    fun `a Someday task is dated by postponing it`() {
        val moved = task(date = null).postponed(PostponeAmount(1, PostponeUnit.MONTH), today, now)
        assertEquals(today.plusMonths(1), moved.startDate)
        assertNull(moved.startTime)
    }

    @Test
    fun `the deadline never moves`() {
        val due = LocalDate.of(2026, 9, 30)
        val moved = task(due = due).postponed(PostponeAmount(1, PostponeUnit.MONTH), today, now)
        assertEquals(due, moved.dueDate)
    }

    // ------------------------------------------------------------------ sub-tasks

    @Test
    fun `children group under their parent in the parent's order, and count their done`() {
        val list = listOf(task(2), task(1), task(3, parent = 1, status = EntryStatus.DONE), task(4, parent = 1))
        val grouped = list.withSubtasks()
        assertEquals(listOf(2L, 1L), grouped.map { it.task.id })
        assertEquals(listOf(3L, 4L), grouped[1].subtasks.map { it.id })
        assertEquals(1, grouped[1].done)
    }

    @Test
    fun `a child whose parent is not in the list is shown at top level, not dropped`() {
        val grouped = listOf(task(4, parent = 99)).withSubtasks()
        assertEquals(listOf(4L), grouped.map { it.task.id })
    }

    // ------------------------------------------------------------------ presence

    private fun checkIn(day: LocalDate, hour: Int, deleted: Boolean = false) = HabitCompletion(
        habitId = 1, date = day, checkedAt = day.atTime(hour, 0).toInstant(ZoneOffset.UTC),
        deletedAt = if (deleted) Instant.EPOCH else null,
    )

    @Test
    fun `presence counts distinct days this month, names the last, and says nothing about misses`() {
        val p = habitPresenceOf(
            listOf(
                checkIn(LocalDate.of(2026, 9, 1), 8), checkIn(LocalDate.of(2026, 9, 1), 9), // one day, twice
                checkIn(LocalDate.of(2026, 9, 5), 8), checkIn(LocalDate.of(2026, 9, 9), 7),
                checkIn(LocalDate.of(2026, 8, 30), 8), // last month
                checkIn(LocalDate.of(2026, 9, 10), 8, deleted = true), // undone
            ),
            today, ZoneId.of("UTC"),
        )
        assertEquals(3, p.timesThisMonth)
        assertEquals(LocalDate.of(2026, 9, 9), p.lastDate)
        assertEquals(TimeOfDay.MORNING, p.usualTime)
        assertEquals(setOf(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 9)), p.daysThisMonth)
    }

    @Test
    fun `usually is withheld below three check-ins or without a clear majority`() {
        val two = habitPresenceOf(listOf(checkIn(today, 8), checkIn(today.minusDays(1), 8)), today, ZoneId.of("UTC"))
        assertNull(two.usualTime)
        val split = habitPresenceOf(
            listOf(checkIn(today, 8), checkIn(today.minusDays(1), 14), checkIn(today.minusDays(2), 20), checkIn(today.minusDays(3), 8)),
            today, ZoneId.of("UTC"),
        )
        assertNull(split.usualTime) // 2 of 4 is not "usually"
    }

    @Test
    fun `no memory yet reads as nothing, not as zero of something`() {
        val p = habitPresenceOf(emptyList(), today)
        assertEquals(0, p.timesThisMonth)
        assertNull(p.lastDate)
        assertNull(p.usualTime)
    }
}

package com.tendril.app.domain

import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.data.entry.EntryStatus
import com.tendril.app.data.entry.IntervalUnit
import com.tendril.app.data.entry.RecurrenceRule
import com.tendril.app.data.habit.Habit
import com.tendril.app.data.habit.HabitFrequency
import com.tendril.app.data.reminder.Reminder
import com.tendril.app.data.reminder.ReminderOffset
import com.tendril.app.domain.reminders.FiringKind
import com.tendril.app.domain.reminders.entryFirings
import com.tendril.app.domain.reminders.habitFiring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * B§13.6 #7 — the alarm arithmetic, pinned for the first time: it lived in Android's
 * `AlarmScheduler` behind `AlarmManager`, unreachable from a unit test, and now feeds both the
 * phone's alarms and the desktop's toasts, so the two must agree to the minute.
 */
class ReminderFiringsTest {
    private val zone: ZoneId = ZoneId.of("UTC")
    private val at = Instant.ofEpochMilli(1_000L)
    private val now: Instant = LocalDateTime.of(2026, 9, 16, 12, 0).atZone(zone).toInstant()

    private fun entry(
        date: LocalDate? = LocalDate.of(2026, 9, 18),
        time: LocalTime? = LocalTime.of(14, 30),
        kind: EntryKind = EntryKind.TASK,
        status: EntryStatus? = EntryStatus.PENDING,
        deleted: Boolean = false,
        rule: RecurrenceRule? = null,
    ) = Entry(
        id = 5, title = "Dentist", kind = kind, startDate = date, startTime = time, endDate = null, endTime = null,
        status = if (kind == EntryKind.TASK) status else null, recurrenceRule = rule, createdAt = at, updatedAt = at,
        deletedAt = if (deleted) at else null,
    )

    private fun reminder(offset: ReminderOffset, anchor: LocalTime? = null, id: Long = 1) =
        Reminder(id = id, entryId = 5, offset = offset, anchorTime = anchor)

    private fun instant(date: LocalDate, time: LocalTime) = LocalDateTime.of(date, time).atZone(zone).toInstant()

    @Test
    fun `a timed task owes its overdue firing and a reminder counted back from its time`() {
        val firings = entryFirings(entry(), listOf(reminder(ReminderOffset.FromPreset(ReminderOffset.Preset.ONE_HOUR))), now = now, zone = zone)
        assertEquals(listOf(FiringKind.REMINDER, FiringKind.OVERDUE), firings.map { it.kind })
        assertEquals(instant(LocalDate.of(2026, 9, 18), LocalTime.of(13, 30)), firings[0].at)
        assertEquals("in 1 h · 14:30", firings[0].detail)
        assertEquals(instant(LocalDate.of(2026, 9, 18), LocalTime.of(14, 30)), firings[1].at)
        assertEquals("14:30", firings[1].detail)
    }

    @Test
    fun `an undated time falls to midnight for the overdue and to the reminder's anchor for the reminder`() {
        val firings = entryFirings(entry(time = null), listOf(reminder(ReminderOffset.FromPreset(ReminderOffset.Preset.TWO_HOURS), anchor = LocalTime.of(10, 0))), now = now, zone = zone)
        assertEquals(instant(LocalDate.of(2026, 9, 18), LocalTime.MIDNIGHT), firings.first { it.kind == FiringKind.OVERDUE }.at)
        assertEquals(instant(LocalDate.of(2026, 9, 18), LocalTime.of(8, 0)), firings.first { it.kind == FiringKind.REMINDER }.at)
    }

    @Test
    fun `a resolved task, a trashed one and a dateless one fire nothing`() {
        val r = listOf(reminder(ReminderOffset.FromPreset(ReminderOffset.Preset.ONE_HOUR)))
        assertTrue(entryFirings(entry(status = EntryStatus.DONE), r, now = now, zone = zone).isEmpty())
        assertTrue(entryFirings(entry(deleted = true), r, now = now, zone = zone).isEmpty())
        assertTrue(entryFirings(entry(date = null), r, now = now, zone = zone).isEmpty())
    }

    @Test
    fun `a firing at or before now is dropped, a deleted reminder too`() {
        val e = entry(date = LocalDate.of(2026, 9, 16), time = LocalTime.of(12, 30))
        val firings = entryFirings(e, listOf(
            reminder(ReminderOffset.FromPreset(ReminderOffset.Preset.ONE_HOUR), id = 1), // 11:30 — past
            reminder(ReminderOffset.Custom(1, IntervalUnit.DAY), id = 2).copy(deletedAt = at), // tombstone
        ), now = now, zone = zone)
        assertEquals(listOf(FiringKind.OVERDUE), firings.map { it.kind })
    }

    @Test
    fun `an event keeps its pre-due reminder and owes no overdue`() {
        val firings = entryFirings(entry(kind = EntryKind.EVENT), listOf(reminder(ReminderOffset.FromPreset(ReminderOffset.Preset.ONE_DAY))), now = now, zone = zone)
        assertEquals(listOf(FiringKind.REMINDER), firings.map { it.kind })
        assertEquals("in 1 d · 14:30", firings[0].detail)
    }

    @Test
    fun `a weekly event anchors to next week once today's has started`() {
        // A Wednesday 09:00 weekly event; now is Wednesday 12:00 — today's occurrence is behind.
        val e = entry(date = LocalDate.of(2026, 9, 9), time = LocalTime.of(9, 0), kind = EntryKind.EVENT, rule = RecurrenceRule.Fixed("FREQ=WEEKLY;BYDAY=WE"))
        val firings = entryFirings(e, listOf(reminder(ReminderOffset.FromPreset(ReminderOffset.Preset.ONE_HOUR))), now = now, zone = zone)
        assertEquals(instant(LocalDate.of(2026, 9, 23), LocalTime.of(8, 0)), firings.single().at)
    }

    @Test
    fun `a habit with an hour fires at it, without one never`() {
        val habit = Habit(id = 3, title = "Stretch", time = LocalTime.of(18, 0), frequency = HabitFrequency(1, IntervalUnit.DAY), createdAt = at, updatedAt = at)
        val firing = habitFiring(habit, ZonedDateTime.ofInstant(now, zone))!!
        assertEquals(FiringKind.HABIT, firing.kind)
        assertEquals(instant(LocalDate.of(2026, 9, 16), LocalTime.of(18, 0)), firing.at)
        assertEquals("18:00", firing.detail)
        assertNull(habitFiring(habit.copy(time = null), ZonedDateTime.ofInstant(now, zone)))
    }
}

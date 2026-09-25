package com.tendril.app.domain

import com.tendril.app.calendarprovider.CalendarProviderSync
import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.data.entry.RecurrenceRule
import com.tendril.app.notifications.AlarmScheduler
import com.tendril.app.sync.FakeEntryDao
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * §9.7 on Android, the recurring half. A recurring EVENT's alarms anchor to its next occurrence,
 * and an exception row (§4.1 — "this one" moved or skipped) changes which occurrence that is. So
 * the series must be re-armed *with* its exceptions whenever one of them is written.
 *
 * Audit 2026-09-24: handed an exception row, the coordinator re-armed that row alone. Moving this
 * week's occurrence of a weekly meeting (`EntryEditor.move(THIS_ONE)`), importing an ICS
 * EXDATE/RECURRENCE-ID, or merging an exception from another device left the series' alarm armed
 * for the occurrence that no longer happens.
 */
class AndroidEntryScheduleCoordinatorTest {

    private val alarms = mockk<AlarmScheduler>(relaxed = true)
    private val calendar = mockk<CalendarProviderSync>(relaxed = true)
    private val entryDao = FakeEntryDao()
    private val coordinator = AndroidEntryScheduleCoordinator(alarms, calendar, entryDao)

    private val at = Instant.ofEpochMilli(1_000L)
    private val monday = LocalDate.of(2026, 9, 14)

    private fun stored(entry: Entry): Entry = runBlocking { entryDao.getById(entryDao.insert(entry))!! }

    private fun series() = stored(
        Entry(title = "Standup", kind = EntryKind.EVENT, startDate = monday, startTime = LocalTime.of(9, 0),
            endDate = monday, endTime = LocalTime.of(9, 15), recurrenceRule = RecurrenceRule.Fixed("FREQ=WEEKLY"),
            createdAt = at, updatedAt = at),
    )

    private fun movedOccurrence(series: Entry) = stored(
        series.copy(id = 0, uid = series.uid + "-x", recurrenceRule = null, startDate = monday.plusDays(1),
            endDate = monday.plusDays(1), originalEntryId = series.id, originalOccurrenceDate = monday),
    )

    @Test
    fun `a series is re-armed with its exceptions`() = runBlocking {
        val series = series()
        val moved = movedOccurrence(series)

        coordinator.onEntryChanged(series)

        coVerify { alarms.rescheduleFor(series, listOf(moved)) }
    }

    @Test
    fun `an exception written alone re-arms its series too, with the exception included`() = runBlocking {
        val series = series()
        val moved = movedOccurrence(series)

        coordinator.onEntryChanged(moved)

        coVerify { alarms.rescheduleFor(moved, emptyList()) }
        coVerify { alarms.rescheduleFor(series, listOf(moved)) }
    }
}

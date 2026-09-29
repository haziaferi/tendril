package com.tendril.app.domain

import com.tendril.app.data.entry.EntryKind
import com.tendril.app.sync.FakeEntryDao
import com.tendril.app.sync.RecordingEntryScheduleCoordinator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * §9.12 — a new event starts in the calendar last chosen, so quick add, which has no *Calendar*
 * field, puts it there too; a task never has one (CalDAV event calendars hold no tasks).
 */
class QuickAddCalendarTest {

    private val entryDao = FakeEntryDao()
    private val coordinator = RecordingEntryScheduleCoordinator()
    private val day = LocalDate.of(2026, 10, 1)

    @Test
    fun `a quick-added event starts in the calendar last chosen`() = runBlocking {
        val entry = quickAddEntry(entryDao, coordinator, ParsedEntry("Yoga", EntryKind.EVENT, time = LocalTime.of(9, 0)), day, calendarKey = "davx|me|3")!!
        assertEquals("davx|me|3", entry.calendarKey)
    }

    @Test
    fun `a quick-added task has no calendar, whatever was last chosen`() = runBlocking {
        val entry = quickAddEntry(entryDao, coordinator, ParsedEntry("Call bank", EntryKind.TASK), day, calendarKey = "davx|me|3")!!
        assertEquals(null, entry.calendarKey)
    }

    /** The desktop's popup passes nothing: phone only, as before §9.12. */
    @Test
    fun `with no calendar chosen an event stays on this phone`() = runBlocking {
        assertEquals(null, quickAddEntry(entryDao, coordinator, ParsedEntry("Yoga", EntryKind.EVENT), day)!!.calendarKey)
    }
}

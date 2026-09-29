package com.tendril.app.calendarprovider

import com.tendril.app.data.calendar.CalendarLink
import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.domain.ResolveEntryUseCase
import com.tendril.app.sync.FakeEntryCompletionDao
import com.tendril.app.sync.FakeEntryDao
import com.tendril.app.sync.RecordingEntryScheduleCoordinator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** §9.12 — the pass over every ticked calendar, and what un-ticking one does. */
class SystemCalendarSyncTest {

    private val t0: Instant = Instant.parse("2026-09-29T10:00:00Z")
    private val entryDao = FakeEntryDao()
    private val links = FakeCalendarLinkDao()
    private val coordinator = RecordingEntryScheduleCoordinator()
    private val stores = mapOf("cal-a" to FakeCalendarStore(), "cal-b" to FakeCalendarStore())
    private val ticked = mutableSetOf<String>()

    private fun sync(permitted: Boolean = true) = SystemCalendarSync(
        idForKey = { key -> if (key in stores) stores.keys.indexOf(key).toLong() else null },
        readBackFor = { key -> CalendarReadBack(entryDao, links, stores.getValue(key), coordinator, ResolveEntryUseCase(entryDao, FakeEntryCompletionDao(), coordinator)) },
        ticked = { ticked.toSet() },
        setTicked = { key, on -> if (on) ticked += key else ticked -= key },
        linkDao = links,
        hasPermission = { permitted },
    )

    private fun event(uid: String, key: String) = Entry(
        uid = uid, title = uid, kind = EntryKind.EVENT, startDate = LocalDate.of(2026, 10, 1), startTime = LocalTime.of(9, 0),
        endDate = LocalDate.of(2026, 10, 1), endTime = LocalTime.of(10, 0), recurrenceRule = null, calendarKey = key, createdAt = t0, updatedAt = t0,
    )

    @Test
    fun `a pass covers every ticked calendar and only those`() = runBlocking {
        entryDao.insert(event("in-a", "cal-a"))
        entryDao.insert(event("in-b", "cal-b"))
        ticked += "cal-a"

        val result = sync().syncNow(t0)

        assertEquals(1, result.written)
        assertEquals(listOf("in-a"), stores.getValue("cal-a").live().map { it.uid })
        assertTrue("an unticked calendar is not written", stores.getValue("cal-b").live().isEmpty())
    }

    @Test
    fun `a ticked calendar the phone no longer has is skipped, not failed`() = runBlocking {
        ticked += "gone"
        assertEquals(ReadBackResult(), sync().syncNow(t0))
    }

    @Test
    fun `nothing happens without calendar permission`() = runBlocking {
        entryDao.insert(event("in-a", "cal-a"))
        ticked += "cal-a"
        assertEquals(ReadBackResult(), sync(permitted = false).syncNow(t0))
        assertTrue(stores.getValue("cal-a").live().isEmpty())
    }

    /** Un-ticking keeps the entries (nothing in Tendril is destroyed) and forgets the links, so a
     * later re-tick relinks equal events rather than treating them as deleted. */
    @Test
    fun `un-ticking a calendar forgets its links and keeps its entries`() = runBlocking {
        links.upsert(CalendarLink("cal-a", "in-a", "", "f"))
        entryDao.insert(event("in-a", "cal-a"))
        ticked += "cal-a"

        sync().setTicked("cal-a", false)

        assertTrue(links.rows.isEmpty())
        assertEquals(1, entryDao.getAll().size)
        assertTrue("cal-a" !in ticked)
    }
}

package com.tendril.app.calendarprovider

import com.tendril.app.data.calendar.CalendarLink
import com.tendril.app.data.calendar.CalendarLinkDao
import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.data.entry.EntrySource
import com.tendril.app.data.entry.RecurrenceRule
import com.tendril.app.domain.ResolveEntryUseCase
import com.tendril.app.sync.FakeEntryCompletionDao
import com.tendril.app.sync.FakeEntryDao
import com.tendril.app.sync.RecordingEntryScheduleCoordinator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * §9.12's read-back, end to end over an in-memory system calendar that behaves like the one walked
 * on 2026-09-29: an exception inserted through the exception route takes its series' UID, and a
 * delete leaves nothing a read can see.
 */
class CalendarReadBackTest {

    private val zone = ZoneId.of("Europe/Rome")
    private val t0: Instant = Instant.parse("2026-09-29T10:00:00Z")
    private val key = "bitfire.at.davdroid|me|3"

    private val entryDao = FakeEntryDao()
    private val links = FakeCalendarLinkDao()
    private val store = FakeCalendarStore()
    private val coordinator = RecordingEntryScheduleCoordinator()
    private val readBack = CalendarReadBack(
        entryDao, links, store, coordinator, ResolveEntryUseCase(entryDao, FakeEntryCompletionDao(), coordinator), zone,
    )

    private fun sync(now: Instant = t0) = runBlocking { readBack.sync(key, CALENDAR_ID, now) }

    private fun event(uid: String, title: String = "Yoga", day: Int = 1, rrule: String? = null, calendarKey: String? = key) = Entry(
        uid = uid, title = title, kind = EventKind, startDate = LocalDate.of(2026, 10, day), startTime = LocalTime.of(9, 0),
        endDate = LocalDate.of(2026, 10, day), endTime = LocalTime.of(10, 0), recurrenceRule = rrule?.let { RecurrenceRule.Fixed(it) },
        calendarKey = calendarKey, createdAt = t0, updatedAt = t0,
    )

    private fun millis(day: Int, hour: Int) = LocalDateTime.of(2026, 10, day, hour, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `an event added in the calendar appears in Tendril, linked`() {
        store.add(SystemCalendarRow(0, "srv-1", null, null, "Dentist", millis(2, 10), millis(2, 11), null, false, "Europe/Rome", null, false))

        val result = sync()

        val entry = runBlocking { entryDao.getByUid("srv-1") }!!
        assertEquals("Dentist", entry.title)
        assertEquals(EntrySource.CALENDAR, entry.source)
        assertEquals(key, entry.calendarKey)
        assertEquals(1, result.created)
        assertEquals(1, links.rows.size)
        assertTrue("alarms and the rest follow the new entry", coordinator.changed.any { it.uid == "srv-1" })
    }

    @Test
    fun `an event made in Tendril is written to its calendar once, under its own uid`() {
        runBlocking { entryDao.insert(event("t-1")) }

        assertEquals(1, sync().written)
        assertEquals(listOf("t-1"), store.live().map { it.uid })
        assertEquals("a second pass has nothing to do", ReadBackResult(), sync())
        assertEquals(1, store.live().size)
    }

    @Test
    fun `an edit in Tendril reaches the calendar as an update, not a second event`() {
        val id = runBlocking { entryDao.insert(event("t-1")) }
        sync()

        runBlocking { entryDao.update(entryDao.getById(id)!!.copy(title = "Yoga, later")) }
        sync()

        assertEquals(listOf("Yoga, later"), store.live().map { it.title })
    }

    @Test
    fun `an edit made elsewhere is taken into Tendril`() {
        runBlocking { entryDao.insert(event("t-1")) }
        sync()

        store.edit("t-1") { it.copy(title = "Moved room") }
        val result = sync(t0.plusSeconds(60))

        val entry = runBlocking { entryDao.getByUid("t-1") }!!
        assertEquals("Moved room", entry.title)
        assertEquals("the take is an edit, stamped when it was read", t0.plusSeconds(60), entry.updatedAt)
        assertEquals(1, result.updated)
    }

    @Test
    fun `a moved occurrence made in Tendril is written as the calendar's own exception`() {
        val series = runBlocking { entryDao.insert(event("t-2", rrule = "FREQ=WEEKLY;COUNT=4")) }
        sync()
        runBlocking {
            entryDao.insert(event("t-2-moved", day = 9).copy(originalEntryId = series, originalOccurrenceDate = LocalDate.of(2026, 10, 8), isExceptionSkip = false))
        }

        sync()

        val exception = store.live().single { it.originalId != null }
        assertEquals(millis(8, 9), exception.originalInstanceTime)
        assertEquals(millis(9, 9), exception.dtStart)
    }

    @Test
    fun `an event deleted in the calendar moves to Tendril's Trash`() {
        runBlocking { entryDao.insert(event("t-1")) }
        sync()

        store.remove("t-1")
        assertEquals(1, sync().trashed)

        assertNotNull(runBlocking { entryDao.getByUid("t-1") }!!.deletedAt)
        assertTrue(links.rows.isEmpty())
    }

    @Test
    fun `an event trashed in Tendril is deleted from the calendar`() {
        val id = runBlocking { entryDao.insert(event("t-1")) }
        sync()

        runBlocking { entryDao.softDelete(id, t0.plusSeconds(5)) }
        assertEquals(1, sync().deleted)

        assertTrue(store.live().isEmpty())
    }

    @Test
    fun `a conflict keeps the calendar's version and names what was overridden`() {
        val id = runBlocking { entryDao.insert(event("t-1")) }
        sync()
        runBlocking { entryDao.update(entryDao.getById(id)!!.copy(title = "Ours")) }
        store.edit("t-1") { it.copy(title = "Theirs") }

        val result = sync()

        assertEquals("Theirs", runBlocking { entryDao.getById(id) }!!.title)
        assertEquals(listOf("Ours"), result.conflicts)
    }

    @Test
    fun `entries on this phone only, or in another calendar, are left alone`() {
        runBlocking {
            entryDao.insert(event("local", calendarKey = null))
            entryDao.insert(event("other", calendarKey = "com.google|me|cal"))
        }

        assertEquals(ReadBackResult(), sync())
        assertTrue(store.live().isEmpty())
    }

    /** The same server event reached through two sync apps, both ticked: one entry, not two. */
    @Test
    fun `an event already in Tendril from another calendar is not created twice`() {
        runBlocking { entryDao.insert(event("shared", calendarKey = "com.google|me|cal")) }
        store.add(SystemCalendarRow(0, "shared", null, null, "Yoga", millis(1, 9), millis(1, 10), null, false, "Europe/Rome", null, false))

        val result = sync()

        assertEquals(1, runBlocking { entryDao.getAll() }.size)
        assertEquals(listOf("Yoga"), result.duplicates)
        assertNull(links.rows.firstOrNull { it.calendarKey == key })
    }

    private companion object {
        const val CALENDAR_ID = 9L
        val EventKind = EntryKind.EVENT
    }
}

class FakeCalendarLinkDao : CalendarLinkDao {
    val rows = mutableListOf<CalendarLink>()
    override suspend fun getForCalendar(calendarKey: String) = rows.filter { it.calendarKey == calendarKey }
    override suspend fun upsert(link: CalendarLink) {
        rows.removeAll { it.calendarKey == link.calendarKey && it.entryUid == link.entryUid && it.occurrence == link.occurrence }
        rows += link
    }
    override suspend fun delete(calendarKey: String, entryUid: String, occurrence: String) {
        rows.removeAll { it.calendarKey == calendarKey && it.entryUid == entryUid && it.occurrence == occurrence }
    }
    override suspend fun deleteForCalendar(calendarKey: String) { rows.removeAll { it.calendarKey == calendarKey } }
}

/** The system calendar's behaviour as walked: exception rows take their series' UID; deletes vanish. */
class FakeCalendarStore : CalendarStore {
    private val rows = linkedMapOf<Long, SystemCalendarRow>()
    private var nextId = 100L

    fun live() = rows.values.toList()
    fun add(row: SystemCalendarRow) { val id = nextId++; rows[id] = row.copy(rowId = id) }
    fun edit(uid: String, change: (SystemCalendarRow) -> SystemCalendarRow) {
        val row = rows.values.single { it.uid == uid && it.originalId == null }
        rows[row.rowId] = change(row)
    }
    fun remove(uid: String) { rows.values.removeAll { it.uid == uid } }

    override fun rows(calendarId: Long) = rows.values.toList()
    override fun insert(calendarId: Long, values: Map<String, Any?>): Long? { val id = nextId++; rows[id] = rowOf(id, values, null); return id }
    override fun update(rowId: Long, values: Map<String, Any?>): Boolean {
        val old = rows[rowId] ?: return false
        rows[rowId] = rowOf(rowId, values, old)
        return true
    }
    override fun insertException(seriesRowId: Long, values: Map<String, Any?>): Long? {
        val series = rows[seriesRowId] ?: return null
        val id = nextId++
        rows[id] = rowOf(id, values, null).copy(uid = series.uid, originalId = seriesRowId, timeZone = series.timeZone)
        return id
    }
    override fun delete(rowId: Long): Boolean {
        val removed = rows.remove(rowId) ?: return false
        if (removed.originalId == null) rows.values.removeAll { it.originalId == rowId }
        return true
    }

    private fun rowOf(id: Long, v: Map<String, Any?>, old: SystemCalendarRow?) = SystemCalendarRow(
        rowId = id,
        uid = if ("uid2445" in v) v["uid2445"] as String? else old?.uid,
        originalId = old?.originalId,
        originalInstanceTime = (v["originalInstanceTime"] as Long?) ?: old?.originalInstanceTime,
        title = (v["title"] as String?) ?: old?.title,
        dtStart = (v["dtstart"] as Long?) ?: old!!.dtStart,
        dtEnd = if ("dtend" in v) v["dtend"] as Long? else old?.dtEnd,
        duration = if ("duration" in v) v["duration"] as String? else old?.duration,
        allDay = ((v["allDay"] as Int?) ?: if (old?.allDay == true) 1 else 0) == 1,
        timeZone = (v["eventTimezone"] as String?) ?: old?.timeZone,
        rrule = if ("rrule" in v) v["rrule"] as String? else old?.rrule,
        cancelled = ((v["eventStatus"] as Int?) ?: if (old?.cancelled == true) 2 else 1) == 2,
    )
}

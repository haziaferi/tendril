package com.tendril.app.domain

import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.data.entry.EntryStatus
import com.tendril.app.data.entry.RecurrenceRule
import com.tendril.app.domain.ics.IcsImporter
import com.tendril.app.domain.ics.IcsReader
import com.tendril.app.domain.ics.IcsWriter
import com.tendril.app.domain.recurrence.EntryOccurrences
import com.tendril.app.sync.FakeEntryDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.Period
import java.time.ZoneId

/** §0.8 step 6e — `.ics` out and back, and other people's files in. */
class IcsRoundTripTest {

    private val rome: ZoneId = ZoneId.of("Europe/Rome")
    private val at = Instant.parse("2026-09-12T10:00:00Z")
    private val monday = LocalDate.of(2026, 9, 14)

    private fun entry(
        title: String, kind: EntryKind, date: LocalDate?, time: LocalTime? = null, endTime: LocalTime? = null, endDate: LocalDate? = null,
        rule: RecurrenceRule? = null, due: LocalDate? = null, status: EntryStatus? = if (kind == EntryKind.TASK) EntryStatus.PENDING else null,
        importance: Int = 0, estimate: Duration? = null, id: Long = 0, uid: String = java.util.UUID.randomUUID().toString(),
    ) = Entry(
        id = id, uid = uid, title = title, kind = kind, startDate = date, startTime = time, endDate = endDate, endTime = endTime,
        recurrenceRule = rule, status = status, dueDate = due, importance = importance, estimate = estimate, createdAt = at, updatedAt = at,
    )

    private fun importInto(dao: FakeEntryDao, text: String, now: Instant = at) = runBlocking {
        IcsImporter(dao, object : EntryScheduleCoordinator {
            override suspend fun onEntryChanged(entry: Entry) = Unit
            override suspend fun onEntryRemoved(entry: Entry) = Unit
        }).import(text, rome, now)
    }

    /** A one-event file with an explicit LAST-MODIFIED (or none), for the version tests below. */
    private fun oneEvent(uid: String, title: String, lastModified: String?) = listOfNotNull(
        "BEGIN:VCALENDAR", "VERSION:2.0", "BEGIN:VEVENT", "UID:$uid", "SUMMARY:$title",
        "DTSTART;TZID=Europe/Rome:20260914T150000", "DTEND;TZID=Europe/Rome:20260914T154500",
        lastModified?.let { "LAST-MODIFIED:$it" }, "END:VEVENT", "END:VCALENDAR",
    ).joinToString("\r\n")

    // ---- audit 5a.4: an import is a write under §9.4's last-write-wins, and a claim of authorship

    @Test
    fun `re-importing an identical file moves no timestamp`() {
        val dao = FakeEntryDao()
        val text = IcsWriter.write(listOf(entry("Dentist", EntryKind.EVENT, monday, LocalTime.of(15, 0), LocalTime.of(15, 45))), rome, at)
        importInto(dao, text)

        val result = importInto(dao, text, now = at.plusSeconds(3600))

        assertEquals("nothing changed, so nothing claims an edit", at, runBlocking { dao.getAll() }.single().updatedAt)
        assertEquals(0, result.updated); assertEquals(1, result.kept)
    }

    @Test
    fun `a file older than this device's edit does not overwrite it`() {
        val dao = FakeEntryDao()
        val old = IcsWriter.write(listOf(entry("Dentist", EntryKind.EVENT, monday, LocalTime.of(15, 0), LocalTime.of(15, 45), uid = "d")), rome, at)
        importInto(dao, old)
        runBlocking { dao.update(dao.getAll().single().copy(title = "Dentist, rebooked", updatedAt = at.plusSeconds(600))) }

        val result = importInto(dao, old, now = at.plusSeconds(3600))

        assertEquals("Dentist, rebooked", runBlocking { dao.getAll() }.single().title)
        assertEquals(0, result.updated); assertEquals(1, result.kept)
    }

    /** Control for the one above: the same comparison lets a newer file through. */
    @Test
    fun `a file newer than this device's copy updates it`() {
        val dao = FakeEntryDao()
        importInto(dao, oneEvent("d", "Dentist", lastModified = "20260912T100000Z"))

        val result = importInto(dao, oneEvent("d", "Dentist, rebooked", lastModified = "20260912T101000Z"), now = at.plusSeconds(3600))

        assertEquals("Dentist, rebooked", runBlocking { dao.getAll() }.single().title)
        assertEquals(1, result.updated)
    }

    /** A file that says nothing about when it was written cannot lose on time: the person chose to
     * import it, so its differing content applies, as it did before. */
    @Test
    fun `an undated file with different content still updates`() {
        val dao = FakeEntryDao()
        importInto(dao, oneEvent("d", "Dentist", lastModified = null))

        val result = importInto(dao, oneEvent("d", "Dentist, rebooked", lastModified = null), now = at.plusSeconds(3600))

        assertEquals("Dentist, rebooked", runBlocking { dao.getAll() }.single().title)
        assertEquals(1, result.updated)
    }

    @Test
    fun `write then read gives the same entries, events and tasks alike`() {
        val entries = listOf(
            entry("Dentist, upstairs; bring card", EntryKind.EVENT, monday, LocalTime.of(15, 0), LocalTime.of(15, 45)),
            entry("Holiday", EntryKind.EVENT, monday, endDate = monday.plusDays(2)),
            entry("Yoga", EntryKind.EVENT, monday, LocalTime.of(7, 0), LocalTime.of(8, 0), rule = RecurrenceRule.Fixed("FREQ=WEEKLY;BYDAY=MO")),
            entry("Call bank", EntryKind.TASK, monday, due = monday.plusDays(4), importance = 4, estimate = Duration.ofMinutes(30)),
            entry("Bins", EntryKind.TASK, monday, rule = RecurrenceRule.Elastic(Period.ofWeeks(2)), status = EntryStatus.DONE),
            entry("Someday", EntryKind.TASK, null),
        )
        val text = IcsWriter.write(entries, rome, at)
        assertTrue(text.startsWith("BEGIN:VCALENDAR\r\n"))
        assertTrue("escaped", "SUMMARY:Dentist\\, upstairs\\; bring card" in text)
        assertTrue("all-day end is exclusive", "DTEND;VALUE=DATE:20260917" in text)
        assertTrue("a task's period as an RRULE", "RRULE:FREQ=WEEKLY;INTERVAL=2" in text)
        assertTrue("PRIORITY:1" in text && "DURATION:PT30M" in text && "STATUS:COMPLETED" in text)

        val dao = FakeEntryDao()
        val result = importInto(dao, text)
        assertEquals(6, result.created)
        val back = runBlocking { dao.getAll() }.associateBy { it.title }

        val dentist = back.getValue("Dentist, upstairs; bring card")
        assertEquals(EntryKind.EVENT, dentist.kind); assertEquals(LocalTime.of(15, 0), dentist.startTime); assertEquals(LocalTime.of(15, 45), dentist.endTime)
        val holiday = back.getValue("Holiday")
        assertEquals(monday.plusDays(2), holiday.endDate); assertNull(holiday.startTime)
        assertEquals(RecurrenceRule.Fixed("FREQ=WEEKLY;BYDAY=MO"), back.getValue("Yoga").recurrenceRule)
        val bank = back.getValue("Call bank")
        assertEquals(EntryKind.TASK, bank.kind); assertEquals(monday.plusDays(4), bank.dueDate); assertEquals(EntryStatus.PENDING, bank.status)
        val bins = back.getValue("Bins")
        assertEquals(RecurrenceRule.Elastic(Period.ofWeeks(2)), bins.recurrenceRule); assertEquals(EntryStatus.DONE, bins.status)
        assertNull(back.getValue("Someday").startDate)
        assertEquals("uids survive", entries.map { it.uid }.toSet(), back.values.map { it.uid }.toSet())
    }

    @Test
    fun `re-importing the same file updates rather than duplicates`() {
        val dao = FakeEntryDao()
        val e = entry("Dentist", EntryKind.EVENT, monday, LocalTime.of(15, 0), LocalTime.of(15, 45))
        importInto(dao, IcsWriter.write(listOf(e), rome, at))
        val moved = e.copy(startTime = LocalTime.of(16, 0), endTime = LocalTime.of(16, 45), title = "Dentist (moved)")
        val result = importInto(dao, IcsWriter.write(listOf(moved), rome, at))
        assertEquals(0, result.created); assertEquals(1, result.updated)
        val rows = runBlocking { dao.getAll() }
        assertEquals(1, rows.size)
        assertEquals(LocalTime.of(16, 0), rows[0].startTime); assertEquals("Dentist (moved)", rows[0].title)
    }

    @Test
    fun `a moved occurrence and a skipped one travel as RECURRENCE-ID and EXDATE`() {
        val base = entry("Yoga", EntryKind.EVENT, monday, LocalTime.of(7, 0), LocalTime.of(8, 0), rule = RecurrenceRule.Fixed("FREQ=WEEKLY"), id = 1)
        val moved = entry("Yoga", EntryKind.EVENT, monday.plusWeeks(1).plusDays(1), LocalTime.of(7, 0), LocalTime.of(8, 0), id = 2)
            .copy(originalEntryId = 1, originalOccurrenceDate = monday.plusWeeks(1), isExceptionSkip = false)
        val skip = entry("Yoga", EntryKind.EVENT, monday.plusWeeks(2), id = 3)
            .copy(originalEntryId = 1, originalOccurrenceDate = monday.plusWeeks(2), isExceptionSkip = true)
        val text = IcsWriter.write(listOf(base, moved, skip), rome, at)
        assertTrue("RECURRENCE-ID;TZID=Europe/Rome:20260921T070000" in text)
        assertTrue("EXDATE;TZID=Europe/Rome:20260928T070000" in text)
        assertEquals("a skip is not a component", 2, text.split("BEGIN:VEVENT").size - 1)

        val dao = FakeEntryDao()
        importInto(dao, text)
        val rows = runBlocking { dao.getAll() }
        val dates = EntryOccurrences.expand(rows, monday, monday.plusWeeks(3)).map { it.startDate }
        assertEquals(listOf(monday, monday.plusWeeks(1).plusDays(1), monday.plusWeeks(3)), dates)
    }

    /** §3.2 "every row through the coordinator", audit 2026-09-24: the base was reported before its
     * EXDATE skip rows existed, and the skips were never reported — so the series was armed for the
     * occurrence the file had just skipped. */
    @Test
    fun `a series is reported after its skips exist, so the scheduler sees them`() {
        val base = entry("Yoga", EntryKind.EVENT, monday, LocalTime.of(7, 0), LocalTime.of(8, 0), rule = RecurrenceRule.Fixed("FREQ=WEEKLY"), id = 1)
        val skip = entry("Yoga", EntryKind.EVENT, monday.plusWeeks(2), id = 3)
            .copy(originalEntryId = 1, originalOccurrenceDate = monday.plusWeeks(2), isExceptionSkip = true)
        val text = IcsWriter.write(listOf(base, skip), rome, at)

        val dao = FakeEntryDao()
        val skipsSeenAtLastBaseReport = mutableListOf<Int>()
        runBlocking {
            IcsImporter(dao, object : EntryScheduleCoordinator {
                override suspend fun onEntryChanged(entry: Entry) {
                    if (entry.originalEntryId == null && entry.recurrenceRule != null)
                        skipsSeenAtLastBaseReport += dao.getExceptionsOf(entry.id).count { it.isExceptionSkip == true }
                }
                override suspend fun onEntryRemoved(entry: Entry) = Unit
            }).import(text, rome, at)
        }

        assertEquals("the last report of the series must see its one skip", 1, skipsSeenAtLastBaseReport.last())
    }

    @Test
    fun `a Google-style file reads with UTC, TZID and all-day dates converted to the device zone`() {
        val text = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Google Inc//Google Calendar 70.9054//EN
            BEGIN:VTIMEZONE
            TZID:America/New_York
            BEGIN:STANDARD
            DTSTART:19701101T020000
            END:STANDARD
            END:VTIMEZONE
            BEGIN:VEVENT
            DTSTART:20260914T130000Z
            DTEND:20260914T140000Z
            UID:abc123@google.com
            SUMMARY:Call with New York\, the long one that certainly needs folding at seve
             nty-five octets to be sure
            BEGIN:VALARM
            TRIGGER:-PT10M
            END:VALARM
            END:VEVENT
            BEGIN:VEVENT
            DTSTART;TZID=America/New_York:20260915T090000
            DTEND;TZID=America/New_York:20260915T100000
            UID:def456@google.com
            SUMMARY:Standup
            RRULE:FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR
            END:VEVENT
            BEGIN:VEVENT
            DTSTART;VALUE=DATE:20260920
            DTEND;VALUE=DATE:20260921
            UID:ghi789@google.com
            SUMMARY:Birthday
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val components = IcsReader.read(text, rome)
        assertEquals(3, components.size)
        val call = components[0]
        assertEquals("Call with New York, the long one that certainly needs folding at seventy-five octets to be sure", call.summary)
        assertEquals(LocalTime.of(15, 0), call.startTime) // 13:00Z is 15:00 in Rome in September
        val standup = components[1]
        assertEquals(LocalTime.of(15, 0), standup.startTime) // 09:00 New York
        assertEquals("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR", standup.rrule)
        val birthday = components[2]
        assertNull(birthday.startTime)
        assertEquals(LocalDate.of(2026, 9, 20), birthday.startDate)
        assertNull("a one-day all-day event has no end of its own", birthday.endDate)

        val dao = FakeEntryDao()
        assertEquals(3, importInto(dao, text).created)
        assertTrue(runBlocking { dao.getAll() }.all { it.kind == EntryKind.EVENT })
    }
}

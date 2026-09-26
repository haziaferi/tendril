package com.tendril.app.calendarprovider

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract
import androidx.test.platform.app.InstrumentationRegistry
import com.tendril.app.AppContainer
import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.data.entry.RecurrenceRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters
import java.util.UUID

/**
 * Audit 2026-09-24 5.4, as the 2026-09-26 walk found it — against the phone's real system calendar.
 *
 * A weekly all-day series with one occurrence moved (§4.1's override row) and one skipped (a skip
 * row). The system calendar must show what Tendril shows: the series on its own weekday, except the
 * moved week — which shows on the new day instead — and nothing on the skipped week. It showed the
 * skipped occurrence twice over (the series' RRULE carried no EXDATE, and the skip row was mirrored
 * as an event of its own), and the moved one on both days.
 */
class SeriesMirrorInstrumentedTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    /** All-day instances of [title] the system calendar holds, as the days they fall on. */
    private fun daysShown(title: String, from: LocalDate, until: LocalDate): List<LocalDate> {
        val begin = from.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val end = until.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .also { ContentUris.appendId(it, begin); ContentUris.appendId(it, end) }.build()
        val cursor = context.contentResolver.query(
            uri, arrayOf(CalendarContract.Instances.BEGIN), "${CalendarContract.Instances.TITLE} = ?", arrayOf(title), null,
        ) ?: return emptyList()
        return cursor.use { c ->
            generateSequence { if (c.moveToNext()) c.getLong(0) else null }
                .map { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                .sorted().toList()
        }
    }

    /**
     * Rows written before the fix still carry their series' `providerEventId` — the walk's own
     * "Walk 5.4" did. The mirror repairs them rather than updating the series with them again.
     */
    @Test
    fun anOccurrenceStillCarryingItsSeriesRowDoesNotMoveTheSeries() = runBlocking {
        val container = AppContainer.from(context)
        assumeTrue("needs calendar permission granted to the app", container.calendarProviderSync.hasPermission())
        val entryDao = container.database.entryDao()
        val coordinator = container.entryScheduleCoordinator
        val title = "Mirror repair " + UUID.randomUUID().toString().take(8)
        val monday = LocalDate.now().plusWeeks(2).with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val now = Instant.now()
        val baseId = entryDao.insert(
            Entry(title = title, kind = EntryKind.EVENT, startDate = monday, startTime = null, endDate = null, endTime = null,
                recurrenceRule = RecurrenceRule.Fixed("FREQ=WEEKLY"), createdAt = now, updatedAt = now),
        )
        coordinator.onEntryChanged(entryDao.getById(baseId)!!)
        val base = entryDao.getById(baseId)!!
        // The pre-fix shape: a plain copy, the series' provider row id included.
        val movedId = entryDao.insert(
            base.copy(id = 0, uid = UUID.randomUUID().toString(), startDate = monday.plusWeeks(1).plusDays(1), recurrenceRule = null,
                originalEntryId = baseId, originalOccurrenceDate = monday.plusWeeks(1), isExceptionSkip = false),
        )
        coordinator.onEntryChanged(entryDao.getById(movedId)!!)

        try {
            assertEquals(
                listOf(monday, monday.plusWeeks(1).plusDays(1), monday.plusWeeks(2)),
                daysShown(title, monday, monday.plusWeeks(3).minusDays(1)),
            )
        } finally {
            for (id in listOf(movedId, baseId)) {
                entryDao.getById(id)?.let { coordinator.onEntryRemoved(it) }
                entryDao.deleteForever(id)
            }
        }
    }

    @Test
    fun aMovedAndASkippedOccurrenceShowWhereTendrilShowsThem() = runBlocking {
        val container = AppContainer.from(context)
        assumeTrue("needs calendar permission granted to the app", container.calendarProviderSync.hasPermission())
        val entryDao = container.database.entryDao()
        val coordinator = container.entryScheduleCoordinator
        val title = "Mirror test " + UUID.randomUUID().toString().take(8)
        val monday = LocalDate.now().plusWeeks(2).with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val now = Instant.now()

        val baseId = entryDao.insert(
            Entry(title = title, kind = EntryKind.EVENT, startDate = monday, startTime = null, endDate = null, endTime = null,
                recurrenceRule = RecurrenceRule.Fixed("FREQ=WEEKLY"), createdAt = now, updatedAt = now),
        )
        coordinator.onEntryChanged(entryDao.getById(baseId)!!)
        val base = entryDao.getById(baseId)!!
        // Built as `EntryEditor.move` builds them now — without the series' own external ids.
        val movedId = entryDao.insert(
            base.copy(id = 0, uid = UUID.randomUUID().toString(), startDate = monday.plusWeeks(1).plusDays(1), recurrenceRule = null,
                originalEntryId = baseId, originalOccurrenceDate = monday.plusWeeks(1), isExceptionSkip = false,
                providerEventId = null, googleEventId = null),
        )
        coordinator.onEntryChanged(entryDao.getById(movedId)!!)
        val skipId = entryDao.insert(
            base.copy(id = 0, uid = UUID.randomUUID().toString(), startDate = monday.plusWeeks(2), recurrenceRule = null,
                originalEntryId = baseId, originalOccurrenceDate = monday.plusWeeks(2), isExceptionSkip = true,
                providerEventId = null, googleEventId = null),
        )
        coordinator.onEntryChanged(entryDao.getById(skipId)!!)

        try {
            assertEquals(
                "week 1 on its Monday, week 2 moved to Tuesday, week 3 skipped, week 4 on its Monday",
                listOf(monday, monday.plusWeeks(1).plusDays(1), monday.plusWeeks(3)),
                // Ends the day before week 5: an all-day instance starting exactly at the window's end counts as inside it.
                daysShown(title, monday, monday.plusWeeks(4).minusDays(1)),
            )
        } finally {
            for (id in listOf(skipId, movedId, baseId)) {
                entryDao.getById(id)?.let { coordinator.onEntryRemoved(it) }
                entryDao.deleteForever(id)
            }
        }
    }
}

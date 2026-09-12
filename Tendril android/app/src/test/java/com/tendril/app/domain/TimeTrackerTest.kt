package com.tendril.app.domain

import com.tendril.app.data.track.TimeLog
import com.tendril.app.domain.track.TimeTracker
import com.tendril.app.domain.track.TrackTarget
import com.tendril.app.domain.track.formatMinutes
import com.tendril.app.domain.track.loggedInMonth
import com.tendril.app.domain.track.loggedMinutes
import com.tendril.app.domain.track.target
import com.tendril.app.sync.FakeTimeLogDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset

/** §0.6.5 / step 7c — one timer at a time, and the totals the screens read. */
class TimeTrackerTest {

    private var now = Instant.ofEpochSecond(1_000)
    private val dao = FakeTimeLogDao()
    private val tracker = TimeTracker(dao) { now }

    private val task = TrackTarget.Entry(1)
    private val habit = TrackTarget.Habit(2)

    @Test
    fun `start opens a log on the target and running shows it`() = runBlocking {
        val log = tracker.start(task)
        assertTrue(log.isRunning)
        assertEquals(task, log.target())
        assertEquals(log.uid, tracker.running.first()!!.uid)
    }

    @Test
    fun `starting another target closes the first - one timer at a time`() = runBlocking {
        tracker.start(task)
        now = now.plusSeconds(60)
        tracker.start(habit)
        val all = dao.getAll().sortedBy { it.id }
        assertEquals(2, all.size)
        assertEquals(now, all[0].endedAt)
        assertEquals(habit, tracker.running.first()!!.target())
    }

    @Test
    fun `stop closes and is harmless when nothing runs`() = runBlocking {
        tracker.stop()
        assertNull(tracker.running.first())
        tracker.start(task)
        now = now.plusSeconds(90)
        tracker.stop()
        assertNull(tracker.running.first())
        assertEquals(now, dao.getAll().single().endedAt)
    }

    @Test
    fun `toggle stops the running target and switches to another`() = runBlocking {
        tracker.toggle(task)
        assertEquals(task, tracker.running.first()!!.target())
        tracker.toggle(habit)
        assertEquals(habit, tracker.running.first()!!.target())
        tracker.toggle(habit)
        assertNull(tracker.running.first())
        assertEquals(2, dao.getAll().size)
    }

    @Test
    fun `stop closes every open log, the ones a merge left open included`() = runBlocking {
        dao.insert(TimeLog(uid = "peer", entryId = 7, startedAt = now.minusSeconds(500), updatedAt = now))
        tracker.start(task)
        assertEquals(1, dao.getRunning().size)
        assertEquals(task, dao.getRunning().single().target())
    }

    @Test
    fun `logged minutes clip to the window, count an open log to now, and skip tombstones`() {
        val t = Instant.ofEpochSecond(0)
        val logs = listOf(
            TimeLog(uid = "a", entryId = 1, startedAt = t.minusSeconds(600), endedAt = t.plusSeconds(600), updatedAt = t), // 10 min inside
            TimeLog(uid = "b", entryId = 1, startedAt = t.plusSeconds(1200), endedAt = null, updatedAt = t),               // open: 20 min to now
            TimeLog(uid = "c", entryId = 1, startedAt = t.plusSeconds(60), endedAt = t.plusSeconds(3660), deletedAt = t, updatedAt = t),
        )
        assertEquals(30, loggedMinutes(logs, from = t, to = t.plusSeconds(7200), now = t.plusSeconds(2400)))
        assertEquals("1h 05m", formatMinutes(65)); assertEquals("45m", formatMinutes(45))
    }

    @Test
    fun `the month total is the calendar month in the given zone`() {
        val sep1 = YearMonth.of(2026, 9).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant()
        val logs = listOf(
            TimeLog(uid = "a", habitId = 1, startedAt = sep1.minusSeconds(1800), endedAt = sep1.plusSeconds(1800), updatedAt = sep1), // 30 min in Sept
            TimeLog(uid = "b", habitId = 1, startedAt = sep1.plusSeconds(86400), endedAt = sep1.plusSeconds(86400 + 900), updatedAt = sep1),
        )
        assertEquals(45, loggedInMonth(logs, YearMonth.of(2026, 9), now = sep1.plusSeconds(200_000), zone = ZoneOffset.UTC))
        assertEquals(30, loggedInMonth(logs, YearMonth.of(2026, 8), now = sep1.plusSeconds(200_000), zone = ZoneOffset.UTC))
    }
}

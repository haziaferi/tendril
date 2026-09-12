package com.tendril.app.domain.track

import com.tendril.app.data.track.TimeLog
import com.tendril.app.data.track.TimeLogDao
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A `now` every minute (and once at once) — what a total with an open log combines with, so
 * the number on screen moves without anything in the database changing. */
fun minuteTicker(): Flow<Instant> = flow {
    while (true) {
        emit(Instant.now())
        delay(60_000)
    }
}

/** What a timer runs on. The only way a [TimeLog] gets its owner, which is what keeps
 * "exactly one of entryId/habitId" true without a CHECK Room cannot write. */
sealed interface TrackTarget {
    data class Entry(val id: Long) : TrackTarget
    data class Habit(val id: Long) : TrackTarget
}

fun TimeLog.target(): TrackTarget = when {
    entryId != null -> TrackTarget.Entry(entryId)
    habitId != null -> TrackTarget.Habit(habitId)
    else -> error("time log ${uid} has no owner")
}

/**
 * §0.6.5 / §0.8 step 7c — the one funnel for starting and stopping a timer. Llama Life's
 * shape: *one* thing runs at a time, so starting another stops the first. The row is the timer
 * (see [TimeLog]); this class holds no state of its own, which is what lets a notification
 * action on the phone, the running strip and a row button all be the same operation.
 */
class TimeTracker(
    private val timeLogDao: TimeLogDao,
    private val clock: () -> Instant = Instant::now,
) {
    /** The running timer, or null. After a §9.4 merge two devices may each have left one
     * open; the newest is the one shown, and the next [start] or [stop] closes them all. */
    val running: Flow<TimeLog?> = timeLogDao.observeRunning().map { it.firstOrNull() }

    /** A habit's live logs, newest first — what the presence sheet totals. */
    fun logsForHabit(habitId: Long): Flow<List<TimeLog>> = timeLogDao.observeForHabit(habitId)

    /** The live logs that started in `[from, to)` — a day's, for planned-vs-actual (step 7d). */
    fun logsBetween(from: Instant, to: Instant): Flow<List<TimeLog>> = timeLogDao.observeBetween(from, to)

    /** [logsBetween] for one calendar day in [zone]. */
    fun logsOn(day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Flow<List<TimeLog>> =
        logsBetween(day.atStartOfDay(zone).toInstant(), day.plusDays(1).atStartOfDay(zone).toInstant())

    /** Starts a timer on [target], closing whatever ran before. Returns the new log. */
    suspend fun start(target: TrackTarget): TimeLog {
        val now = clock()
        stop(now)
        val log = TimeLog(
            entryId = (target as? TrackTarget.Entry)?.id,
            habitId = (target as? TrackTarget.Habit)?.id,
            startedAt = now,
            updatedAt = now,
        )
        return log.copy(id = timeLogDao.insert(log))
    }

    /** Closes every open log. Idempotent: nothing running is not an error. */
    suspend fun stop(now: Instant = clock()) {
        for (open in timeLogDao.getRunning()) {
            timeLogDao.update(open.copy(endedAt = now, updatedAt = now))
        }
    }

    /** The row button's gesture: stop if this is what runs, otherwise switch to it. */
    suspend fun toggle(target: TrackTarget) {
        val current = timeLogDao.getRunning().firstOrNull()
        if (current != null && current.target() == target) stop() else start(target)
    }
}

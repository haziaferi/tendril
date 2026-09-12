package com.tendril.app.domain.review

import com.tendril.app.data.completion.EntryCompletion
import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.data.entry.EntryStatus
import com.tendril.app.data.habit.HabitCompletion
import com.tendril.app.data.page.Page
import com.tendril.app.data.pagedatabase.PageDatabase
import com.tendril.app.data.track.TimeLog
import com.tendril.app.domain.track.loggedMinutes
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * §0.6.10 — how long a database may go unreviewed before Review offers it again. One global
 * number, fixed for now: a setting waits for the cross-platform preference store (§0.10 item
 * 12), and a per-database interval is one nullable column later if a week ever fits nothing.
 */
val REVIEW_CADENCE: Duration = Duration.ofDays(7)

/** What Review knows about one database — enough for a card, nothing that needs the rows. */
data class DatabaseReview(
    val database: PageDatabase,
    val page: Page,
    val rowCount: Int,
    /** Rows not touched since the last review — or every row, when there has never been one. */
    val staleRows: Int,
    /** Pending tasks synced from this database's rows (§5.2), by `sourceRowId`. */
    val openTasks: Int,
)

/** One card of the walk, in walk order. */
sealed interface ReviewItem {
    data class DatabaseDue(val review: DatabaseReview) : ReviewItem
    /** A task kept without a date (§0.6.4 Someday): still wanted? */
    data class SomedayTask(val entry: Entry) : ReviewItem
    /** A pending task whose When passed more than a cadence ago and was never resolved. */
    data class PastTask(val entry: Entry) : ReviewItem
}

/** Due when never reviewed, or reviewed longer than [cadence] ago. */
fun isDueForReview(database: PageDatabase, now: Instant, cadence: Duration = REVIEW_CADENCE): Boolean =
    database.lastReviewedAt?.let { Duration.between(it, now) >= cadence } ?: true

/**
 * The walk: databases due first (never reviewed before longest-ago), then the Someday tasks,
 * then the tasks whose When is more than a cadence in the past. Steps and series overrides are
 * left out — a step is reviewed with its parent, an override is one occurrence of a series.
 * Pure: the callers load rows and tasks, this decides.
 */
fun reviewItems(
    databases: List<Pair<PageDatabase, Page>>,
    rowsOf: (PageDatabase) -> List<Page>,
    tasks: List<Entry>,
    now: Instant,
    today: LocalDate,
    cadence: Duration = REVIEW_CADENCE,
): List<ReviewItem> {
    val pending = tasks.filter { it.kind == EntryKind.TASK && it.status == EntryStatus.PENDING && it.deletedAt == null && it.parentEntryId == null && it.originalEntryId == null }
    val due = databases
        .filter { (db, page) -> page.deletedAt == null && isDueForReview(db, now, cadence) }
        .sortedWith(compareBy(nullsFirst()) { (db, _) -> db.lastReviewedAt })
        .map { (db, page) ->
            val rows = rowsOf(db).filter { it.deletedAt == null }
            val rowIds = rows.mapTo(mutableSetOf()) { it.id }
            val since = db.lastReviewedAt
            ReviewItem.DatabaseDue(
                DatabaseReview(
                    database = db, page = page, rowCount = rows.size,
                    staleRows = if (since == null) rows.size else rows.count { it.updatedAt <= since },
                    openTasks = pending.count { it.sourceRowId in rowIds },
                )
            )
        }
    val someday = pending.filter { it.startDate == null }.sortedBy { it.createdAt }.map { ReviewItem.SomedayTask(it) }
    val cutoff = today.minusDays(cadence.toDays())
    val past = pending.filter { it.startDate != null && it.startDate < cutoff }.sortedBy { it.startDate }.map { ReviewItem.PastTask(it) }
    return due + someday + past
}

/** Sunsama's weekly summary, as three numbers and nothing else: the trailing seven days. */
data class WeekSummary(val loggedMinutes: Int, val tasksDone: Int, val habitCheckIns: Int, val from: LocalDate) {
    val isEmpty: Boolean get() = loggedMinutes == 0 && tasksDone == 0 && habitCheckIns == 0
}

fun weekSummary(
    logs: List<TimeLog>,
    completions: List<EntryCompletion>,
    habitCompletions: List<HabitCompletion>,
    today: LocalDate,
    now: Instant,
    zone: ZoneId = ZoneId.systemDefault(),
): WeekSummary {
    val from = today.minusDays(6)
    val fromInstant = from.atStartOfDay(zone).toInstant()
    val toInstant = today.plusDays(1).atStartOfDay(zone).toInstant()
    return WeekSummary(
        loggedMinutes = loggedMinutes(logs, fromInstant, toInstant, now),
        tasksDone = completions.count { it.status == EntryStatus.DONE && it.resolvedAt >= fromInstant && it.resolvedAt < toInstant },
        habitCheckIns = habitCompletions.count { it.deletedAt == null && it.date >= from && it.date <= today },
        from = from,
    )
}

package com.tendril.app.domain.review

import com.tendril.app.data.completion.EntryCompletionDao
import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryDao
import com.tendril.app.data.entry.EntryStatus
import com.tendril.app.data.habit.HabitCompletionDao
import com.tendril.app.data.page.PageDao
import com.tendril.app.data.page.PageKind
import com.tendril.app.data.pagedatabase.PageDatabase
import com.tendril.app.data.pagedatabase.PageDatabaseDao
import com.tendril.app.data.track.TimeLogDao
import com.tendril.app.domain.EntryEditor
import com.tendril.app.domain.MoveScope
import com.tendril.app.domain.ResolveEntryUseCase
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * §0.6.10 — the Review's reads and writes, over DAOs that already exist: a walk is a one-shot
 * load re-taken after each answer, not a live query, so nothing new is asked of Room. The
 * decisions are [reviewItems] and [weekSummary]; this class only fetches and applies.
 */
class Review(
    private val pageDao: PageDao,
    private val pageDatabaseDao: PageDatabaseDao,
    private val entryDao: EntryDao,
    private val entryCompletionDao: EntryCompletionDao,
    private val habitCompletionDao: HabitCompletionDao,
    private val timeLogDao: TimeLogDao,
    private val entryEditor: EntryEditor,
    private val resolveEntryUseCase: ResolveEntryUseCase,
) {
    suspend fun items(now: Instant = Instant.now(), today: LocalDate = LocalDate.now()): List<ReviewItem> {
        val databases = pageDao.getByKind(PageKind.DATABASE).mapNotNull { page -> pageDatabaseDao.getByPageId(page.id)?.let { it to page } }
        val rows = databases.associate { (db, _) -> db.id to pageDao.getMembersOf(db.id, db.labelId) }
        return reviewItems(databases, { rows[it.id].orEmpty() }, entryDao.observeTasks().first(), now, today)
    }

    /** True when [items] would be non-empty — the dot on Tasks, and nothing more (§0.5.2). */
    suspend fun isDue(now: Instant = Instant.now(), today: LocalDate = LocalDate.now()): Boolean = items(now, today).isNotEmpty()

    suspend fun week(today: LocalDate = LocalDate.now(), now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): WeekSummary {
        val from = today.minusDays(6).atStartOfDay(zone).toInstant()
        val to = today.plusDays(1).atStartOfDay(zone).toInstant()
        return weekSummary(
            logs = timeLogDao.observeBetween(from, to).first(),
            completions = entryCompletionDao.getAll(),
            habitCompletions = habitCompletionDao.getAll(),
            today = today, now = now, zone = zone,
        )
    }

    /** Follows `LabelMembership.confirm`: the database row *and* the page's `updatedAt`, because
     * the page merge is whole-record LWW on the page and nothing bumps it for a child change. */
    suspend fun markReviewed(database: PageDatabase, now: Instant = Instant.now()) {
        pageDatabaseDao.update(database.copy(lastReviewedAt = now, updatedAt = now))
        pageDao.touch(database.pageId, now)
    }

    /** Someday or past → today, keeping any time it had (Plan mode's `place`, without a time). */
    suspend fun today(entry: Entry, today: LocalDate = LocalDate.now()) {
        entryEditor.move(entry, null, today, null, MoveScope.ALL)
    }

    /** Past → Someday: the When is dropped, the deadline (if any) stays (§0.6.4). */
    suspend fun someday(entry: Entry) {
        entryEditor.save(entry.copy(startDate = null, startTime = null, endDate = null, endTime = null))
    }

    suspend fun done(entry: Entry) = resolveEntryUseCase.resolve(entry.id, EntryStatus.DONE)

    suspend fun trash(entry: Entry) = resolveEntryUseCase.trash(entry.id)
}

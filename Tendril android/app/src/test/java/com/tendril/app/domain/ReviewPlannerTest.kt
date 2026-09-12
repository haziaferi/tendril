package com.tendril.app.domain

import com.tendril.app.data.completion.EntryCompletion
import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.data.entry.EntryStatus
import com.tendril.app.data.habit.HabitCompletion
import com.tendril.app.data.page.Page
import com.tendril.app.data.page.PageKind
import com.tendril.app.data.pagedatabase.PageDatabase
import com.tendril.app.data.track.TimeLog
import com.tendril.app.domain.review.REVIEW_CADENCE
import com.tendril.app.domain.review.ReviewItem
import com.tendril.app.domain.review.isDueForReview
import com.tendril.app.domain.review.reviewItems
import com.tendril.app.domain.review.weekSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** §0.6.10 — what the weekly walk offers, in what order, and the week's three numbers. */
class ReviewPlannerTest {

    private val today = LocalDate.of(2026, 9, 12)
    private val zone = ZoneOffset.UTC
    private val now: Instant = today.atTime(12, 0).toInstant(zone)
    private var nextId = 1L

    private fun page(title: String, kind: PageKind = PageKind.PAGE, databaseId: Long? = null, updatedAt: Instant = now) =
        Page(id = nextId++, title = title, kind = kind, databaseId = databaseId, createdAt = updatedAt, updatedAt = updatedAt)

    private fun database(page: Page, lastReviewedAt: Instant?) =
        PageDatabase(id = nextId++, pageId = page.id, lastReviewedAt = lastReviewedAt, createdAt = now, updatedAt = now)

    private fun task(title: String, startDate: LocalDate?, status: EntryStatus = EntryStatus.PENDING, parent: Long? = null, original: Long? = null, sourceRowId: Long? = null, createdAt: Instant = now) = Entry(
        id = nextId++, title = title, kind = EntryKind.TASK, startDate = startDate, startTime = null, endDate = null, endTime = null,
        recurrenceRule = null, status = status, parentEntryId = parent, originalEntryId = original, sourceRowId = sourceRowId, createdAt = createdAt, updatedAt = now,
    )

    @Test
    fun `never reviewed is due, eight days ago is due, six days ago is not`() {
        val p = page("Books", PageKind.DATABASE)
        assertTrue(isDueForReview(database(p, null), now))
        assertTrue(isDueForReview(database(p, now.minus(Duration.ofDays(8))), now))
        assertFalse(isDueForReview(database(p, now.minus(Duration.ofDays(6))), now))
    }

    @Test
    fun `a due database counts its rows, the stale ones and its open tasks`() {
        val booksPage = page("Books", PageKind.DATABASE)
        val books = database(booksPage, now.minus(Duration.ofDays(10)))
        val old = page("Dune", databaseId = books.id, updatedAt = now.minus(Duration.ofDays(20)))
        val fresh = page("Emma", databaseId = books.id, updatedAt = now.minus(Duration.ofDays(1)))
        val gone = page("Gone", databaseId = books.id).copy(deletedAt = now)
        val tasks = listOf(task("Read Dune", today, sourceRowId = old.id), task("Read Emma", today, status = EntryStatus.DONE, sourceRowId = fresh.id))
        val items = reviewItems(listOf(books to booksPage), { listOf(old, fresh, gone) }, tasks, now, today)
        val due = items.filterIsInstance<ReviewItem.DatabaseDue>().single().review
        assertEquals(2, due.rowCount); assertEquals(1, due.staleRows); assertEquals(1, due.openTasks)
        assertEquals("never reviewed: every row is stale", 2, reviewItems(listOf(database(booksPage, null) to booksPage), { listOf(old, fresh) }, emptyList(), now, today).filterIsInstance<ReviewItem.DatabaseDue>().single().review.staleRows)
    }

    @Test
    fun `someday and past tasks are offered, steps, overrides, resolved and recent ones are not`() {
        val parent = task("Parent", null)
        val items = reviewItems(
            emptyList(), { emptyList() },
            listOf(
                parent,
                task("Step", null, parent = parent.id),
                task("Override", today.minusDays(30), original = 99),
                task("Done long ago", today.minusDays(30), status = EntryStatus.DONE),
                task("Six days ago", today.minusDays(6)),
                task("Eight days ago", today.minusDays(8)),
                task("Today", today),
            ),
            now, today,
        )
        assertEquals(listOf("Parent"), items.filterIsInstance<ReviewItem.SomedayTask>().map { it.entry.title })
        assertEquals(listOf("Eight days ago"), items.filterIsInstance<ReviewItem.PastTask>().map { it.entry.title })
    }

    @Test
    fun `order - never-reviewed databases, then longest-ago, then someday, then past`() {
        val a = page("A", PageKind.DATABASE); val b = page("B", PageKind.DATABASE); val c = page("C", PageKind.DATABASE)
        val items = reviewItems(
            listOf(database(a, now.minus(Duration.ofDays(9))) to a, database(b, null) to b, database(c, now.minus(Duration.ofDays(30))) to c),
            { emptyList() },
            listOf(task("Past", today.minusDays(20)), task("Someday", null)),
            now, today,
        )
        assertEquals(listOf("B", "C", "A", "Someday", "Past"), items.map {
            when (it) { is ReviewItem.DatabaseDue -> it.review.page.title; is ReviewItem.SomedayTask -> it.entry.title; is ReviewItem.PastTask -> it.entry.title }
        })
    }

    @Test
    fun `the week is the trailing seven days`() {
        val eightDaysAgo = today.minusDays(8).atStartOfDay(zone).toInstant()
        val threeDaysAgo = today.minusDays(3).atStartOfDay(zone).toInstant()
        val summary = weekSummary(
            logs = listOf(
                TimeLog(uid = "a", entryId = 1, startedAt = threeDaysAgo, endedAt = threeDaysAgo.plusSeconds(1800), updatedAt = now),
                TimeLog(uid = "b", entryId = 1, startedAt = eightDaysAgo, endedAt = eightDaysAgo.plusSeconds(1800), updatedAt = now),
            ),
            completions = listOf(
                EntryCompletion(uid = "c", entryId = 1, occurrenceDate = today, resolvedAt = threeDaysAgo, status = EntryStatus.DONE),
                EntryCompletion(uid = "d", entryId = 1, occurrenceDate = today, resolvedAt = threeDaysAgo, status = EntryStatus.SKIPPED),
                EntryCompletion(uid = "e", entryId = 1, occurrenceDate = today, resolvedAt = eightDaysAgo, status = EntryStatus.DONE),
            ),
            habitCompletions = listOf(
                HabitCompletion(uid = "f", habitId = 1, date = today.minusDays(6), checkedAt = now),
                HabitCompletion(uid = "g", habitId = 1, date = today.minusDays(7), checkedAt = now),
                HabitCompletion(uid = "h", habitId = 1, date = today, checkedAt = now, deletedAt = now),
            ),
            today = today, now = now, zone = zone,
        )
        assertEquals(30, summary.loggedMinutes); assertEquals(1, summary.tasksDone); assertEquals(1, summary.habitCheckIns)
        assertEquals(today.minusDays(6), summary.from)
        assertEquals(7, REVIEW_CADENCE.toDays())
    }
}

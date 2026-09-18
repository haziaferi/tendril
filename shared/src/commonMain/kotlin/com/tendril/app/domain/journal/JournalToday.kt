package com.tendril.app.domain.journal

import com.tendril.app.data.entry.Entry
import com.tendril.app.data.habit.Habit
import com.tendril.app.data.page.Page
import com.tendril.app.domain.isHabitDueOn
import com.tendril.app.domain.recurrence.EntryOccurrence
import com.tendril.app.domain.recurrence.EntryOccurrences
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * §3.1.4 (amended, §0.8 step 8b / B§6 #15) — what today's Journal page shows above its blocks.
 * Pure: the page's ViewModel feeds it the live task and habit lists and asks nothing else.
 */
data class JournalToday(val tasks: List<EntryOccurrence>, val habits: List<Habit>)

/** The prefix `PagesViewModel.openJournal` titles a day's page with — `journal/2026-09-13`. */
const val JOURNAL_TITLE_PREFIX = "journal/"

/**
 * The day [page] is the Journal for, or null: a child of the "Journal" root whose title is
 * `journal/YYYY-MM-DD`. The root is passed in rather than looked up here so the check costs one
 * string parse on every page open, and a page that merely *mentions* a date is not a Journal.
 */
/**
 * L12 (small things III, 2026-09-18): what a row, a header, a hover card or a switcher row
 * shows for a page — a Journal day's stored title is `journal/2026-09-13` (the storage name;
 * `PagesViewModel.openJournal` writes it and [journalDayOf] parses it), which read as a
 * repeated prefix under a parent already called Journal. The title string is untouched; the
 * date is the display: *13 Sep 2026*. Any other title is returned as it is.
 */
fun displayTitle(title: String): String {
    val m = JOURNAL_TITLE.matchEntire(title.trim()) ?: return title
    val day = runCatching { LocalDate.parse(m.groupValues[1]) }.getOrNull() ?: return title
    return day.format(JOURNAL_DISPLAY)
}

private val JOURNAL_TITLE = Regex("journal/(\\d{4}-\\d{2}-\\d{2})")
private val JOURNAL_DISPLAY: java.time.format.DateTimeFormatter = java.time.format.DateTimeFormatter.ofPattern("d MMM uuuu", java.util.Locale.ENGLISH)

fun journalDayOf(page: Page, journalRoot: Page?): LocalDate? {
    if (journalRoot == null || page.parentId != journalRoot.id) return null
    if (!page.title.startsWith(JOURNAL_TITLE_PREFIX)) return null
    return try {
        LocalDate.parse(page.title.removePrefix(JOURNAL_TITLE_PREFIX))
    } catch (e: DateTimeParseException) {
        null
    }
}

/**
 * The tasks that fall on [today], through [EntryOccurrences.onDay] so the Calendar and the
 * Journal never disagree about a day: what starts today — a recurring task's one live row when
 * its next date is today (§6.2 resolve-and-advance) — with done ones kept so the box can be
 * unticked as on the Tasks tab. Not overdue ones — the Tasks tab's *Today* filter is `date == today` too, and the two
 * surfaces should agree: the strip is the day, Tasks is the backlog.
 */
fun todayTasks(tasks: List<Entry>, today: LocalDate): List<EntryOccurrence> =
    EntryOccurrences.onDay(tasks.filter { it.deletedAt == null }, today)

/**
 * The habits that belong on today's page: due today, **or** already checked in today —
 * [isHabitDueOn] is false the moment a habit is done, and the one just ticked must stay on the
 * strip with its box checked. Timed ones first by hour, then by title.
 */
fun todayHabits(habits: List<Habit>, today: LocalDate): List<Habit> =
    habits
        .filter { it.deletedAt == null && (isHabitDueOn(it, today) || it.lastCompletedDate == today) }
        .sortedWith(compareBy<Habit, java.time.LocalTime?>(nullsLast()) { it.time }.thenBy { it.title.lowercase() })

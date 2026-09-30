package com.tendril.app.domain

import com.tendril.app.domain.plan.CalendarRule
import com.tendril.app.domain.plan.EditChanges
import com.tendril.app.domain.plan.EditScope
import com.tendril.app.domain.plan.EditTarget
import com.tendril.app.domain.plan.Patch
import com.tendril.app.domain.plan.PlanBlock
import com.tendril.app.domain.plan.PlanHabit
import com.tendril.app.domain.plan.PlanModel
import com.tendril.app.domain.plan.PlanOccurrence
import com.tendril.app.domain.plan.RulePatch
import com.tendril.app.domain.plan.ScheduleEdit
import com.tendril.app.domain.plan.ScopeChoice
import com.tendril.app.domain.plan.Slot
import com.tendril.app.domain.plan.changesFor
import com.tendril.app.domain.plan.placementChange
import com.tendril.app.domain.plan.planWeek
import com.tendril.app.domain.plan.scopeChoices
import com.tendril.app.domain.plan.scopeFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

/** §6.3 (2026-09-29) — the calendar schedule's rules that Tendril adds to the planner, or that the golden cases do not pin alone. */
class CalendarScheduleTest {
    private val monday = LocalDate.of(2026, 9, 28)
    private val wednesday = monday.plusDays(2)
    private val blocks = listOf(
        PlanBlock("morning", 6 * 60 + 30, 9 * 60), PlanBlock("midday", 9 * 60, 13 * 60), PlanBlock("afternoon", 13 * 60, 18 * 60),
        PlanBlock("evening", 18 * 60, 21 * 60 + 30), PlanBlock("night", 21 * 60 + 30, 23 * 60 + 30),
    )
    private var clock = 0L
    private fun edit(ref: String, scope: EditScope, changes: EditChanges, target: EditTarget = EditTarget.HABIT) =
        ScheduleEdit("e${++clock}", target, ref, scope, changes, Instant.ofEpochSecond(clock))

    private fun week(vararg habits: PlanHabit, edits: List<ScheduleEdit> = emptyList()) = planWeek(PlanModel(blocks, habits = habits.toList(), edits = edits), monday)
    private fun occurrences(w: com.tendril.app.domain.plan.PlanWeek, date: LocalDate) =
        w.days.single { it.date == date }.let { d -> d.blocks.flatMap { b -> (b.timed + b.flexible).map { b.uid to it } } }

    // ---------- block boundaries: start inclusive, end exclusive ----------

    @Test
    fun `a set time at a block's start belongs to that block, at its end to the next`() {
        val w = week(PlanHabit("a", CalendarRule.Daily, 5, time = 9 * 60), PlanHabit("b", CalendarRule.Daily, 5, time = 9 * 60 - 1))
        val placed = occurrences(w, monday).associate { (block, o) -> o.habitUid to block }
        assertEquals("midday", placed["a"])
        assertEquals("morning", placed["b"])
    }

    @Test
    fun `a set time after the last block ends is kept and reported, not dropped`() {
        val w = week(PlanHabit("late", CalendarRule.Daily, 5, time = 23 * 60 + 30))
        val day = w.days.first()
        assertTrue(occurrences(w, monday).isEmpty())
        assertEquals(listOf("late"), day.outside.map { it.habitUid })
    }

    @Test
    fun `a flexible habit with no block, or a block that is gone, is Any time`() {
        val w = week(PlanHabit("free", CalendarRule.Daily, 5), PlanHabit("orphan", CalendarRule.Daily, 5, blockUid = "deleted-block"))
        assertEquals(setOf("free", "orphan"), w.days.first().anyTime.map { it.habitUid }.toSet())
    }

    @Test
    fun `overlapping blocks are reported once per overlapping pair`() {
        val overlap = edit("morning", EditScope.Day(wednesday), EditChanges(blockEnd = 10 * 60), EditTarget.BLOCK)
        val w = week(PlanHabit("a", CalendarRule.Daily, 5, blockUid = "morning"), edits = listOf(overlap))
        assertEquals(1, w.days.single { it.date == wednesday }.overlaps.size)
        assertTrue(w.days.filter { it.date != wednesday }.all { it.overlaps.isEmpty() })
    }

    // ---------- scope coverage ----------

    @Test
    fun `a from-date edit with weekdays covers only those weekdays from that date`() {
        val move = edit("s", EditScope.From(wednesday, setOf(DayOfWeek.FRIDAY)), EditChanges(blockUid = Patch("evening")))
        val w = week(PlanHabit("s", CalendarRule.Daily, 5, blockUid = "morning"), edits = listOf(move))
        val where = w.days.associate { d -> d.date.dayOfWeek to d.blocks.single { b -> b.flexible.isNotEmpty() }.uid }
        assertEquals("evening", where[DayOfWeek.FRIDAY])
        assertTrue(where.filterKeys { it != DayOfWeek.FRIDAY }.values.all { it == "morning" })
    }

    @Test
    fun `a week edit covers only its ISO week`() {
        val nextMonday = monday.plusWeeks(1)
        val move = edit("s", EditScope.Week(nextMonday), EditChanges(blockUid = Patch("night")))
        val model = PlanModel(blocks, habits = listOf(PlanHabit("s", CalendarRule.Daily, 5, blockUid = "morning")), edits = listOf(move))
        assertTrue(planWeek(model, monday).days.all { d -> d.blocks.single { it.flexible.isNotEmpty() }.uid == "morning" })
        assertTrue(planWeek(model, nextMonday).days.all { d -> d.blocks.single { it.flexible.isNotEmpty() }.uid == "night" })
    }

    @Test
    fun `later edits win over earlier ones on the days both cover`() {
        val first = edit("s", EditScope.From(monday), EditChanges(blockUid = Patch("evening")))
        val second = edit("s", EditScope.Day(wednesday), EditChanges(blockUid = Patch("night")))
        val w = week(PlanHabit("s", CalendarRule.Daily, 5, blockUid = "morning"), edits = listOf(second, first).sortedByDescending { it.uid })
        assertEquals("night", occurrences(w, wednesday).single().first)
        assertEquals("evening", occurrences(w, monday).single().first)
    }

    @Test
    fun `a skip for a day takes the habit off that day — the planner's engine ignored it`() {
        val skip = edit("s", EditScope.Day(wednesday), EditChanges(skip = true))
        val w = week(PlanHabit("s", CalendarRule.Daily, 5, blockUid = "morning"), edits = listOf(skip))
        assertTrue(occurrences(w, wednesday).isEmpty())
        assertEquals(1, occurrences(w, monday).size)
    }

    @Test
    fun `a pause holds both its ends, and an open end runs on`() {
        val w = week(
            PlanHabit("p", CalendarRule.Daily, 5, blockUid = "morning", pause = com.tendril.app.domain.plan.PauseSpan(monday.plusDays(1), wednesday)),
            PlanHabit("q", CalendarRule.Daily, 5, blockUid = "morning", pause = com.tendril.app.domain.plan.PauseSpan(wednesday, null)),
        )
        val p = w.days.filter { d -> occurrences(w, d.date).any { it.second.habitUid == "p" } }.map { it.date.dayOfWeek }
        val q = w.days.filter { d -> occurrences(w, d.date).any { it.second.habitUid == "q" } }.map { it.date.dayOfWeek }
        assertEquals(listOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), p)
        assertEquals(listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY), q)
    }

    // ---------- X times a week: suggested, then confirmed (D5, Q1) ----------

    @Test
    fun `an unconfirmed times-a-week habit is suggested and placed nowhere`() {
        val w = week(PlanHabit("run", CalendarRule.TimesPerWeek(3), 30, blockUid = "afternoon"))
        assertTrue(w.days.all { d -> occurrences(w, d.date).isEmpty() })
        assertEquals(3, w.suggestions.single().days.size)
    }

    @Test
    fun `confirmed days are placed as confirmed, whatever the suggestion was`() {
        val confirm = edit("run", EditScope.Week(monday), EditChanges(weekDays = setOf(DayOfWeek.TUESDAY, DayOfWeek.SUNDAY)))
        val w = week(PlanHabit("run", CalendarRule.TimesPerWeek(3), 30, blockUid = "afternoon"), edits = listOf(confirm))
        assertTrue(w.suggestions.isEmpty())
        assertEquals(listOf(DayOfWeek.TUESDAY, DayOfWeek.SUNDAY), w.days.filter { occurrences(w, it.date).isNotEmpty() }.map { it.date.dayOfWeek })
    }

    @Test
    fun `a zero or negative step places nothing rather than looping`() {
        val w = week(PlanHabit("h", CalendarRule.EveryNHours(0, 8 * 60, 20 * 60), 5), PlanHabit("d", CalendarRule.EveryNDays(0, monday), 5, blockUid = "morning"))
        assertTrue(w.days.all { d -> occurrences(w, d.date).isEmpty() && d.outside.isEmpty() })
    }

    // ---------- "Apply this change to…" (D10) ----------

    private fun occ(w: com.tendril.app.domain.plan.PlanWeek, uid: String, date: LocalDate): PlanOccurrence =
        occurrences(w, date).map { it.second }.first { it.habitUid == uid }

    @Test
    fun `a habit once a day does not offer This day beside Only this entry`() {
        val w = week(PlanHabit("s", CalendarRule.Daily, 5, blockUid = "morning"))
        assertEquals(
            listOf(ScopeChoice.ONLY_THIS_ENTRY, ScopeChoice.THIS_WEEK, ScopeChoice.CHOSEN_WEEKDAYS_THIS_WEEK, ScopeChoice.FROM_NOW_ON, ScopeChoice.CHOSEN_WEEKDAYS_FROM_NOW_ON),
            scopeChoices(w, occ(w, "s", wednesday)),
        )
    }

    @Test
    fun `a habit twice a day keeps This day, and one only this week drops This week only`() {
        val twice = week(PlanHabit("v", CalendarRule.TimesPerDay(2, listOf(Slot.InBlock("morning"), Slot.InBlock("evening"))), 1))
        assertTrue(ScopeChoice.THIS_DAY in scopeChoices(twice, occ(twice, "v", wednesday)))
        val once = week(PlanHabit("w", CalendarRule.Weekdays(setOf(DayOfWeek.WEDNESDAY)), 5, blockUid = "midday"))
        val choices = scopeChoices(once, occ(once, "w", wednesday))
        assertTrue(ScopeChoice.THIS_DAY !in choices && ScopeChoice.THIS_WEEK !in choices)
    }

    @Test
    fun `a habit that happens once, and an added entry, are addressed alone`() {
        val w = week(PlanHabit("dentist", CalendarRule.Once(wednesday), 30, time = 15 * 60 + 30))
        assertEquals(listOf(ScopeChoice.ONLY_THIS_ENTRY), scopeChoices(w, occ(w, "dentist", wednesday)))
        val extra = edit("s", EditScope.Extra(wednesday), EditChanges(blockUid = Patch("night")))
        val moved = week(PlanHabit("s", CalendarRule.Weekdays(setOf(DayOfWeek.MONDAY)), 5, blockUid = "morning"), edits = listOf(extra))
        val added = occurrences(moved, wednesday).single().second
        assertTrue(added.moved && added.key.startsWith("x:"))
        assertEquals(listOf(ScopeChoice.ONLY_THIS_ENTRY), scopeChoices(moved, added))
        assertEquals(EditScope.Occurrence(wednesday, added.key), scopeFor(ScopeChoice.FROM_NOW_ON, added))
    }

    // ---------- from the review of the port (2026-09-29) ----------

    @Test
    fun `an entry outside every block still counts when hiding This day`() {
        val w = week(PlanHabit("m", CalendarRule.TimesPerDay(2, listOf(Slot.At(23 * 60 + 40), Slot.InBlock("morning"))), 5))
        val inBlock = occurrences(w, wednesday).single().second
        assertEquals(1, w.days.single { it.date == wednesday }.outside.size)
        assertTrue(ScopeChoice.THIS_DAY in scopeChoices(w, inBlock))
    }

    @Test
    fun `entries in a block keep their number order past nine`() {
        val w = week(PlanHabit("t", CalendarRule.TimesPerDay(12, List(12) { Slot.InBlock("morning") }), 1))
        val keys = w.days.first().blocks.single { it.uid == "morning" }.flexible.map { it.key }
        assertEquals((1..12).map { it.toString() }, keys)
    }

    @Test
    fun `a times-a-week habit asking for none is not suggested at all`() {
        val w = week(PlanHabit("z", CalendarRule.TimesPerWeek(0), 5, blockUid = "morning"))
        assertTrue(w.suggestions.isEmpty())
    }

    @Test
    fun `week days confirm only the week they were written for`() {
        val leak = edit("run", EditScope.From(monday), EditChanges(weekDays = setOf(DayOfWeek.MONDAY)))
        val w = week(PlanHabit("run", CalendarRule.TimesPerWeek(2), 30, blockUid = "afternoon"), edits = listOf(leak))
        assertTrue(w.days.all { d -> occurrences(w, d.date).isEmpty() })
        assertEquals(1, w.suggestions.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a weekday choice with no weekdays is refused, not widened to every day`() {
        val w = week(PlanHabit("s", CalendarRule.Daily, 5, blockUid = "morning"))
        scopeFor(ScopeChoice.CHOSEN_WEEKDAYS_FROM_NOW_ON, occ(w, "s", wednesday), days = emptySet())
    }

    // ---------- series moves (P5) ----------

    @Test
    fun `an every-N-hours step moved to another block changes that entry only`() {
        val water = PlanHabit("water", CalendarRule.EveryNHours(3, 7 * 60, 22 * 60), 2)
        val w = week(water)
        val step = occurrences(w, wednesday).map { it.second }.first { it.time == 10 * 60 }
        val change = placementChange(water, step, blocks, blockUid = "afternoon")
        assertEquals(setOf(ScopeChoice.ONLY_THIS_ENTRY), change.allowed)
        assertEquals(null, change.series)
    }

    @Test
    fun `an every-N-hours step given a new time shifts the whole series for a wider scope`() {
        val water = PlanHabit("water", CalendarRule.EveryNHours(3, 7 * 60, 22 * 60), 2)
        val w = week(water)
        val step = occurrences(w, wednesday).map { it.second }.first { it.time == 10 * 60 }
        val change = placementChange(water, step, blocks, time = Patch(10 * 60 + 30))
        assertEquals(Patch(10 * 60 + 30), change.entry.time)
        assertEquals(RulePatch(from = 7 * 60 + 30, until = 22 * 60 + 30), changesFor(change, ScopeChoice.FROM_NOW_ON).rulePatch)
        assertEquals(Patch(10 * 60 + 30), changesFor(change, ScopeChoice.ONLY_THIS_ENTRY).time)
    }

    @Test
    fun `a several-a-day slot moved rewrites that slot in the rule for a wider scope`() {
        val tidy = PlanHabit("tidy", CalendarRule.TimesPerDay(3), 10)
        val w = week(tidy)
        val second = occurrences(w, wednesday).map { it.second }.single { it.key == "2" }
        val change = placementChange(tidy, second, blocks, time = Patch(14 * 60))
        assertEquals(
            CalendarRule.TimesPerDay(3, listOf(Slot.InBlock("morning"), Slot.At(14 * 60), Slot.InBlock("night"))),
            changesFor(change, ScopeChoice.FROM_NOW_ON).rule,
        )
        assertEquals(ScopeChoice.entries.toSet(), change.allowed)
    }
}

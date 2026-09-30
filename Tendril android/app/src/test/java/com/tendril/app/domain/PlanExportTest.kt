package com.tendril.app.domain

import com.tendril.app.data.entry.IntervalUnit
import com.tendril.app.data.habit.DEFAULT_HABIT_BLOCKS
import com.tendril.app.data.habit.Habit
import com.tendril.app.data.habit.HabitCompletion
import com.tendril.app.data.habit.HabitFrequency
import com.tendril.app.data.habit.HabitScheduleKind
import com.tendril.app.domain.plan.CalendarRule
import com.tendril.app.domain.plan.Slot
import com.tendril.app.domain.plan.encodeRule
import com.tendril.app.domain.plan.export.PlanNames
import com.tendril.app.domain.plan.export.PlanWords
import com.tendril.app.domain.plan.export.planDailyPdf
import com.tendril.app.domain.plan.export.planDuration
import com.tendril.app.domain.plan.export.planMarkdown
import com.tendril.app.domain.plan.export.planWeeklyPdf
import com.tendril.app.domain.plan.habitWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

/** §6.3 (plan Phase 6) — the week's plan as Markdown and as A4 PDF, from the view the tab draws. Written before the bodies. */
class PlanExportTest {

    private val at = Instant.ofEpochMilli(1_000)
    private val today = LocalDate.of(2026, 9, 30) // Wednesday
    private val monday = LocalDate.of(2026, 9, 28)

    private val words = PlanWords(
        title = "Habit plan", weekLabel = "Week %1\$d · %2\$s – %3\$s", byTime = "By time", byArea = "By area",
        anyTimeToday = "Any time today", outsideBlocks = "Outside the blocks", noArea = "No area",
        nothingPlanned = "Nothing planned", total = "Total", expected = "expected",
        overlapNote = "%1\$s (%2\$s) and %3\$s (%4\$s) overlap by %5\$s.",
        outsideBeforeNote = "%1\$s, %2\$s, is before %3\$s starts (%4\$s).",
        outsideAfterNote = "%1\$s, %2\$s, is after %3\$s ends (%4\$s).",
        locale = Locale.ENGLISH,
    )
    private val names = PlanNames(
        blocks = mapOf("block-morning" to "Morning", "block-midday" to "Midday", "block-afternoon" to "Afternoon", "block-evening" to "Evening", "block-night" to "Night"),
        labels = listOf(9L to "Health", 7L to "Home"),
    )

    private fun cal(id: Long, title: String, rule: CalendarRule, time: LocalTime? = null, block: String? = null, minutes: Long? = null, label: Long? = null, note: String? = null) = Habit(
        id = id, uid = "c$id", title = title, time = time, duration = minutes?.let { Duration.ofMinutes(it) },
        frequency = HabitFrequency(1, IntervalUnit.DAY), createdAt = at, updatedAt = at,
        scheduleKind = HabitScheduleKind.CALENDAR, calendarRule = encodeRule(rule), blockUid = block, labelId = label, note = note,
    )

    private val stretch = cal(1, "Stretch", CalendarRule.Daily, time = LocalTime.of(7, 30), minutes = 10, label = 9)
    private val yoga = cal(2, "Yoga", CalendarRule.Daily, block = "block-morning", minutes = 30, label = 9, note = "mat by the door")
    private val water = cal(3, "Water", CalendarRule.TimesPerDay(3, listOf(Slot.At(480), Slot.At(720), Slot.At(1200))))
    private val plants = cal(4, "Plants", CalendarRule.Daily)
    private val walk = Habit(
        id = 5, uid = "i5", title = "Walk", frequency = HabitFrequency(1, IntervalUnit.DAY), duration = Duration.ofMinutes(20),
        lastCompletedDate = today.minusDays(1), createdAt = at, updatedAt = at, blockUid = "block-evening", labelId = 7,
    )

    private fun week(habits: List<Habit>, completions: List<HabitCompletion> = emptyList()) =
        habitWeek(monday, today, habits, DEFAULT_HABIT_BLOCKS, emptyList(), completions)

    private fun md(habits: List<Habit> = listOf(stretch, yoga, water, plants, walk), completions: List<HabitCompletion> = emptyList()) =
        planMarkdown(week(habits, completions), words, names)

    private val pageObject = Regex("/Type /Page\\b")
    private fun pages(pdf: String) = pageObject.findAll(pdf).count()

    private fun section(md: String, heading: String) = md.substringAfter("## $heading\n").substringBefore("\n## ")

    @Test
    fun `durations read as the tab reads them`() {
        assertEquals("10 min", planDuration(10))
        assertEquals("1 h", planDuration(60))
        assertEquals("1 h 10 min", planDuration(70))
    }

    @Test
    fun `the file opens with its title, the week, and the week by time`() {
        val m = md()
        assertTrue(m.startsWith("# Habit plan\n"))
        assertTrue(m.contains("**Week 40 · 28 Sep – 4 Oct**"))
        val table = section(m, "By time")
        assertTrue(table.contains("| | Mon 28 | Tue 29 | Wed 30 | Thu 1 | Fri 2 | Sat 3 | Sun 4 |"))
        val morning = table.lines().single { it.startsWith("| **Morning**") }
        assertTrue("set time first, then the rest", morning.contains("07:30 Stretch<br>08:00 Water (1/3)<br>Yoga"))
        assertTrue(table.lines().any { it.startsWith("| **Any time today** |") && it.contains("Plants") })
        assertTrue(table.lines().single { it.startsWith("| **Total** |") }.contains("40 min"))
    }

    @Test
    fun `each day is written by time, set times first, with durations, notes and its total`() {
        val wed = section(md(), "Wednesday 30 September")
        val byTime = wed.substringAfter("### By time\n").substringBefore("### By area")
        val morning = byTime.substringAfter("**Morning** · 06:30–09:00\n").substringBefore("\n**")
        val lines = morning.lines().filter { it.startsWith("- ") }
        assertEquals(listOf("- 07:30 · Stretch · 10 min", "- 08:00 · Water (1/3)", "- Yoga · 30 min — _mat by the door_"), lines)
        assertTrue(byTime.contains("**Night** · 21:30–23:30\n\n- _Nothing planned_"))
        assertTrue(byTime.contains("**Any time today**\n\n- Plants"))
        assertTrue("walk, due today, 20 min", wed.contains("Total: 1 h"))
    }

    @Test
    fun `by area follows the Labels' order, No area last, each entry naming its block`() {
        val wed = section(md(), "Wednesday 30 September").substringAfter("### By area\n")
        val headings = wed.lines().filter { it.startsWith("**") && it.endsWith("**") }
        assertEquals(listOf("**Health**", "**Home**", "**No area**"), headings)
        assertTrue(wed.contains("- 07:30 · Stretch · 10 min (Morning)"))
        assertTrue(wed.contains("- Walk · 20 min (Evening)"))
    }

    @Test
    fun `an interval habit on a later day is marked expected, and a past day without its check-in leaves it out`() {
        val m = md()
        assertTrue(section(m, "Thursday 1 October").contains("- Walk · 20 min · _expected_ (Evening)"))
        assertFalse(section(m, "Monday 28 September").contains("Walk"))
    }

    @Test
    fun `a plan sheet — a check-in changes nothing in it`() {
        val done = listOf(HabitCompletion(habitId = 1, date = today, checkedAt = at, occurrenceKey = "1"))
        assertEquals(md(listOf(stretch, yoga)), md(listOf(stretch, yoga), done))
    }

    @Test
    fun `a set time outside every block is kept, under its own heading, with the neutral note`() {
        val late = cal(6, "Late tea", CalendarRule.Daily, time = LocalTime.of(23, 45))
        val wed = section(md(listOf(late)), "Wednesday 30 September")
        assertTrue(wed.contains("**Outside the blocks**\n\n- 23:45 · Late tea"))
        assertTrue(wed.contains("> Late tea, 23:45, is after Night ends (23:30)."))
    }

    @Test
    fun `the daily sheets are seven A4 portrait pages and the weekly sheet one landscape page`() {
        val w = week(listOf(stretch, yoga, water, plants, walk))
        val daily = String(planDailyPdf(w, words, names), Charsets.ISO_8859_1)
        assertEquals(7, pages(daily))
        assertTrue(daily.contains("/MediaBox [0 0 595.28 841.89]"))
        assertTrue(daily.contains("(Stretch) Tj"))
        assertTrue(daily.contains("(Wednesday 30 September) Tj"))
        val weekly = String(planWeeklyPdf(w, words, names), Charsets.ISO_8859_1)
        assertEquals(1, pages(weekly))
        assertTrue(weekly.contains("/MediaBox [0 0 841.89 595.28]"))
        assertTrue(weekly.contains("(Morning) Tj"))
    }

    /** The phone walk (Phase 6): the weekly row read Monday's times while the weekend's block ran later. */
    @Test
    fun `a day whose block runs at other times says so in its own cell, and the row takes the times most days have`() {
        val weekendMorning = DEFAULT_HABIT_BLOCKS.map { if (it.uid == "block-morning") it.copy(overrides = "SAT,SUN@480-630") else it }
        val w = habitWeek(monday, today, listOf(yoga), weekendMorning, emptyList(), emptyList())
        val weekly = String(planWeeklyPdf(w, words, names), Charsets.ISO_8859_1)
        // WinAnsi writes the en dash as 0x96, which reads back through ISO-8859-1 as U+0096.
        fun drawn(span: String) = weekly.split("(" + span.replace('–', '\u0096') + ") Tj").size - 1
        assertEquals("the row's times: five days of seven", 1, drawn("06:30–09:00"))
        assertEquals("Saturday's and Sunday's cells", 2, drawn("08:00–10:30"))
    }

    @Test
    fun `with no durations anywhere, no Total row is written`() {
        val noMinutes = listOf(water, plants)
        assertFalse(section(md(noMinutes), "By time").contains("**Total**"))
        assertFalse(String(planWeeklyPdf(week(noMinutes), words, names), Charsets.ISO_8859_1).contains("(Total) Tj"))
    }

    @Test
    fun `a day too long for one page carries on onto the next`() {
        val many = (1L..60L).map { cal(100 + it, "Habit $it", CalendarRule.Daily, block = "block-midday", minutes = 5) }
        val daily = String(planDailyPdf(week(many), words, names), Charsets.ISO_8859_1)
        assertTrue(pages(daily) > 7)
        assertTrue(daily.contains("(Habit 60) Tj"))
    }
}

package com.tendril.app.domain

import com.tendril.app.data.entry.IntervalUnit
import com.tendril.app.data.habit.DEFAULT_HABIT_BLOCKS
import com.tendril.app.data.habit.Habit
import com.tendril.app.data.habit.HabitFrequency
import com.tendril.app.data.habit.HabitScheduleKind
import com.tendril.app.domain.plan.CalendarRule
import com.tendril.app.domain.plan.EditPos
import com.tendril.app.domain.plan.HabitDayView
import com.tendril.app.domain.plan.displayedGroups
import com.tendril.app.domain.plan.editGroups
import com.tendril.app.domain.plan.encodeRule
import com.tendril.app.domain.plan.habitDay
import com.tendril.app.domain.plan.moveDown
import com.tendril.app.domain.plan.moveUp
import com.tendril.app.domain.plan.originOf
import com.tendril.app.domain.plan.planMove
import com.tendril.app.domain.plan.timeFitsDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * §6.3 (plan Phase 5b) — edit mode's moves, as the planner's `app.js` makes them: ↑ and ↓ across the
 * set times and the blocks, the pending place drawn, and what *Done* must ask and write. Written
 * before `EditMode.kt` had bodies.
 */
class EditModeTest {

    private val at = Instant.ofEpochMilli(1_000)
    private val today = LocalDate.of(2026, 9, 30)

    private fun cal(id: Long, time: LocalTime? = null, block: String? = null, order: Double = 0.0) = Habit(
        id = id, uid = "c$id", title = "H$id", time = time, frequency = HabitFrequency(1, IntervalUnit.DAY), createdAt = at, updatedAt = at,
        scheduleKind = HabitScheduleKind.CALENDAR, calendarRule = encodeRule(CalendarRule.Daily), blockUid = block, sortOrder = order,
    )

    /** Morning: 07:00 (1), 08:00 (2), then 3, 4; Midday: nothing timed, 5; Afternoon: 14:00 (6); Any time: 7. */
    private val habits = listOf(
        cal(1, time = LocalTime.of(7, 0)), cal(2, time = LocalTime.of(8, 0)),
        cal(3, block = "block-morning", order = 1.0), cal(4, block = "block-morning", order = 2.0),
        cal(5, block = "block-midday"), cal(6, time = LocalTime.of(14, 0)), cal(7),
    )
    private val view: HabitDayView = habitDay(today, today, habits, DEFAULT_HABIT_BLOCKS, emptyList(), emptyList())
    private fun row(id: Long) = (view.blocks.flatMap { it.timed + it.flexible } + view.anyTime + view.outside).single { it.habit.id == id }

    private fun ids(view: HabitDayView, id: Long, pos: EditPos) = displayedGroups(view, row(id), pos).map { g -> g.timed.map { it.habit.id } to g.flexible.map { it.habit.id } }

    @Test
    fun `an entry starts where it is`() {
        assertEquals(EditPos(0, timed = true, index = 1), originOf(view, row(2)))
        assertEquals(EditPos(0, timed = false, index = 0), originOf(view, row(3)))
        assertEquals(EditPos(1, timed = false, index = 0), originOf(view, row(5)))
        assertEquals("Any time is the last group", EditPos(5, timed = false, index = 0), originOf(view, row(7)))
    }

    @Test
    fun `up moves one place, then from the rest into the set times, then to the end of the block above`() {
        val g = editGroups(view, row(4))
        var p = originOf(view, row(4))
        p = moveUp(g, p); assertEquals(EditPos(0, false, 0), p)
        p = moveUp(g, p); assertEquals("into the end of the set times", EditPos(0, true, 2), p)
        val g5 = editGroups(view, row(5))
        assertEquals("from the top of a block with no set times, to the end of the block above's rest", EditPos(0, false, 2), moveUp(g5, originOf(view, row(5))))
        val g1 = editGroups(view, row(1))
        assertEquals("the very top stays", EditPos(0, true, 0), moveUp(g1, originOf(view, row(1))))
    }

    @Test
    fun `down moves one place, from the set times into the rest, then to the top of the block below`() {
        val g2 = editGroups(view, row(2))
        assertEquals("from the end of the set times into the top of the rest", EditPos(0, false, 0), moveDown(g2, EditPos(0, true, 1)))
        val g4 = editGroups(view, row(4))
        assertEquals("from the end of a block to the next one's rest when it has no set times", EditPos(1, false, 0), moveDown(g4, originOf(view, row(4))))
        val g5 = editGroups(view, row(5))
        assertEquals("to the next block's set times first when it has them", EditPos(2, true, 0), moveDown(g5, originOf(view, row(5))))
    }

    @Test
    fun `an entry in Any time can move out and back, and nothing else moves in`() {
        val g7 = editGroups(view, row(7))
        assertEquals(6, g7.size)
        assertEquals("up from Any time: the end of the last block", EditPos(4, false, 0), moveUp(g7, originOf(view, row(7))))
        val g4 = editGroups(view, row(4))
        assertEquals("the day's own blocks only", 5, g4.size)
        assertEquals("down from the last block stays", EditPos(4, false, 0), moveDown(g4, EditPos(4, false, 0)))
    }

    @Test
    fun `the pending place is drawn, and the entry nowhere else`() {
        assertEquals(listOf(1L, 2L) to listOf(4L, 3L), ids(view, 4, EditPos(0, false, 0))[0])
        assertEquals(listOf(1L, 2L, 4L) to listOf(3L), ids(view, 4, EditPos(0, true, 2))[0])
    }

    @Test
    fun `back where it started is no move`() {
        assertNull(planMove(view, row(4), originOf(view, row(4)), originOf(view, row(4))))
    }

    @Test
    fun `into the set times asks for a time, out of them asks to remove it`() {
        val into = planMove(view, row(4), originOf(view, row(4)), EditPos(0, true, 2))!!
        assertTrue(into.askTime); assertFalse(into.askRemoveTime)
        val out = planMove(view, row(2), originOf(view, row(2)), EditPos(1, false, 1))!!
        assertTrue(out.askRemoveTime); assertFalse(out.askTime)
        assertEquals("block-midday", out.blockUid)
    }

    @Test
    fun `among set times, passing an entry at another time asks for a time`() {
        val p = planMove(view, row(2), originOf(view, row(2)), EditPos(0, true, 0))!!
        assertTrue(p.askTime)
    }

    @Test
    fun `within the rest, the order is renumbered in tens and only what changes is written`() {
        val p = planMove(view, row(4), originOf(view, row(4)), EditPos(0, false, 0))!!
        assertFalse(p.askTime); assertFalse(p.askRemoveTime); assertNull("same block", p.blockUid)
        assertEquals(listOf(4L to 10.0, 3L to 20.0), p.orders.map { it.first.habit.id to it.second })
        val q = planMove(view, row(5), originOf(view, row(5)), EditPos(0, false, 2))!!
        assertEquals("block-morning", q.blockUid)
        assertEquals("the block's rest renumbered in tens, the moved one at the end", listOf(3L to 10.0, 4L to 20.0, 5L to 30.0), q.orders.map { it.first.habit.id to it.second })
    }

    /** The planner's `askTime`: a time outside every block is refused, the block's end not being in it. */
    @Test
    fun `a typed time must fall in one of the day's blocks`() {
        assertFalse(timeFitsDay(view, 5 * 60 + 30))
        assertTrue(timeFitsDay(view, 7 * 60))
        assertTrue(timeFitsDay(view, 23 * 60 + 29))
        assertFalse(timeFitsDay(view, 23 * 60 + 30))
    }
}

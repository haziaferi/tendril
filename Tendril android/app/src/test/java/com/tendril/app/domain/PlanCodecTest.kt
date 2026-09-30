package com.tendril.app.domain

import com.tendril.app.domain.plan.BlockOverride
import com.tendril.app.domain.plan.CalendarRule
import com.tendril.app.domain.plan.EditChanges
import com.tendril.app.domain.plan.EditScope
import com.tendril.app.domain.plan.Patch
import com.tendril.app.domain.plan.PauseSpan
import com.tendril.app.domain.plan.RulePatch
import com.tendril.app.domain.plan.Slot
import com.tendril.app.domain.plan.decodeChanges
import com.tendril.app.domain.plan.decodeOverrides
import com.tendril.app.domain.plan.decodeRule
import com.tendril.app.domain.plan.decodeScope
import com.tendril.app.domain.plan.encodeChanges
import com.tendril.app.domain.plan.encodeOverrides
import com.tendril.app.domain.plan.encodeRule
import com.tendril.app.domain.plan.encodeScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate

/**
 * §6.3 (v27) — the stored text of a calendar habit's rule and of a scoped edit. Two properties
 * matter: everything written reads back as itself, and text this build cannot read reads as
 * *nothing* — never as a wider or partial change, which would move entries the person did not move.
 */
class PlanCodecTest {

    private val day = LocalDate.of(2026, 10, 1)

    @Test
    fun `every rule reads back as itself`() {
        val rules = listOf(
            CalendarRule.Daily,
            CalendarRule.Once(day),
            CalendarRule.Weekdays(setOf(MONDAY, WEDNESDAY)),
            CalendarRule.Weekdays(emptySet()),
            CalendarRule.EveryNDays(2, day),
            CalendarRule.EveryNWeeks(3, day, setOf(FRIDAY)),
            CalendarRule.TimesPerDay(3),
            CalendarRule.TimesPerDay(3, listOf(Slot.At(480), Slot.InBlock("block-evening"), Slot.At(1230))),
            CalendarRule.TimesPerWeek(3, setOf(MONDAY, WEDNESDAY, FRIDAY, SATURDAY)),
            CalendarRule.EveryNHours(2, 480, 1320),
        )
        for (r in rules) assertEquals(encodeRule(r), r, decodeRule(encodeRule(r)))
        assertEquals("TIMES_PER_DAY:3:08:00,block-evening,20:30", encodeRule(rules[7]))
        assertEquals("EVERY_N_HOURS:2:08:00:22:00", encodeRule(rules[9]))
    }

    @Test
    fun `a rule this build cannot read is null, not a guess`() {
        for (t in listOf(null, "", "HOURLY", "DAILY:1", "WEEKDAYS:MON,XYZ", "EVERY_N_DAYS:two:2026-10-01", "ONCE:2026-13-01", "EVERY_N_HOURS:2:25:00:22:00", "TIMES_PER_WEEK:3")) {
            assertNull(t, decodeRule(t))
        }
    }

    @Test
    fun `every scope reads back as itself, an added entry's key with its colon included`() {
        val scopes = listOf(
            EditScope.Occurrence(day, "2"),
            EditScope.Occurrence(day, "x:4f1c"),
            EditScope.Day(day),
            EditScope.Week(LocalDate.of(2026, 9, 28)),
            EditScope.Week(LocalDate.of(2026, 9, 28), setOf(SATURDAY, SUNDAY)),
            EditScope.From(day),
            EditScope.From(day, setOf(MONDAY)),
            EditScope.Extra(day),
        )
        for (s in scopes) assertEquals(encodeScope(s), s, decodeScope(encodeScope(s)))
    }

    /** The widening case: an unreadable weekday list must not read as "no restriction", which is every day. */
    @Test
    fun `a scope whose weekdays cannot be read is unreadable, not every day`() {
        assertNull(decodeScope("FROM:2026-10-01:MON,XYZ"))
        assertNull(decodeScope("WEEK:2026-09-28:SOMEDAY"))
        assertNull(decodeScope("DAY:2026-10-01:MON"))
        assertNull(decodeScope("OCC:2026-10-01:"))
        assertNull(decodeScope("LATER:2026-10-01"))
    }

    @Test
    fun `changes read back field for field, a null patch distinct from an absent one`() {
        val all = EditChanges(
            blockUid = Patch("block-midday"), time = Patch(null), minutes = 20, rule = CalendarRule.Daily,
            rulePatch = RulePatch(from = 540, until = 1260), skip = true, deleted = false,
            pause = Patch(PauseSpan(day, null)), sortOrder = 1.5, weekDays = setOf(MONDAY, FRIDAY), blockStart = 480, blockEnd = 630,
        )
        assertEquals(all, decodeChanges(encodeChanges(all)))
        assertEquals(EditChanges(), decodeChanges(encodeChanges(EditChanges())))
        assertEquals("remove the time is a patch to null", Patch(null), decodeChanges("""{"time":null}""")!!.time)
        assertNull("an absent time is no change", decodeChanges("""{"minutes":5}""")!!.time)
        assertEquals("a pause lifted", Patch(null), decodeChanges("""{"pause":null}""")!!.pause)
    }

    @Test
    fun `a key a newer build added is ignored, the rest kept`() {
        assertEquals(EditChanges(skip = true), decodeChanges("""{"skip":true,"colour":"sage"}"""))
    }

    /** All or nothing: a partial edit would apply some of a change and silently drop the rest. */
    @Test
    fun `a known key of the wrong shape, at any depth, makes the whole edit unreadable`() {
        for (t in listOf(
            """{"skip":"yes"}""", """{"time":"08:00"}""", """{"minutes":1.5}""", """{"block":5}""",
            """{"rulePatch":{"from":"early"}}""", """{"pause":{"from":"soon"}}""", """{"weekDays":"MON,XYZ"}""",
            """{"rule":"HOURLY"}""", """[1,2]""", """not json""",
        )) assertNull(t, decodeChanges(t))
    }

    @Test
    fun `weekday block times read back, and an unreadable part is dropped alone`() {
        val o = listOf(BlockOverride("b", setOf(SATURDAY, SUNDAY), 480, 630), BlockOverride("b", setOf(MONDAY), 420, 540))
        assertEquals("SAT,SUN@480-630;MON@420-540", encodeOverrides(o))
        assertEquals(o, decodeOverrides("b", encodeOverrides(o)))
        assertEquals(listOf(o[1]), decodeOverrides("b", "XYZ@480-630;MON@420-540"))
        assertEquals(emptyList<BlockOverride>(), decodeOverrides("b", null))
    }

    /** Review of Phase 3: a time off the day, or a negative length, would draw a stroke of negative height. */
    @Test
    fun `a time, length or block edge off the day is unreadable`() {
        for (t in listOf("""{"time":1500}""", """{"time":-5}""", """{"minutes":-30}""", """{"start":-1}""", """{"end":1441}""",
            """{"rulePatch":{"from":-60}}""", """{"rulePatch":{"until":1440}}""", """{"rulePatch":{"n":0}}""")) assertNull(t, decodeChanges(t))
        assertEquals(EditChanges(time = Patch(1439), minutes = 0, blockStart = 0, blockEnd = 1440), decodeChanges("""{"time":1439,"minutes":0,"start":0,"end":1440}"""))
    }

    /** Review of Phase 3: a malformed time slot read as a block's uid is a different rule, not an unreadable one. */
    @Test
    fun `a slot that looks like a time but is not one makes the rule unreadable`() {
        assertNull(decodeRule("TIMES_PER_DAY:2:25:00,block-evening"))
        assertNull(decodeRule("TIMES_PER_DAY:2:9:30,block-evening"))
    }

    /** Review of Phase 3: a sign read as a separator turned `-60-540` into 60–540. */
    @Test
    fun `weekday block times with a negative or doubled sign are dropped`() {
        assertEquals(emptyList<BlockOverride>(), decodeOverrides("b", "MON@-60-540;TUE@480--630;WED@600-500;THU@0-1441"))
    }
}

package com.tendril.app.domain

import com.tendril.app.domain.plan.BlockOverride
import com.tendril.app.domain.plan.CalendarRule
import com.tendril.app.domain.plan.EditChanges
import com.tendril.app.domain.plan.EditScope
import com.tendril.app.domain.plan.EditTarget
import com.tendril.app.domain.plan.OccurrenceTag
import com.tendril.app.domain.plan.Patch
import com.tendril.app.domain.plan.PauseSpan
import com.tendril.app.domain.plan.PlanBlock
import com.tendril.app.domain.plan.PlanHabit
import com.tendril.app.domain.plan.PlanModel
import com.tendril.app.domain.plan.RulePatch
import com.tendril.app.domain.plan.ScheduleEdit
import com.tendril.app.domain.plan.Slot
import com.tendril.app.domain.plan.planWeek
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.IsoFields

/**
 * §6.3 (2026-09-29) — the port reproduces the standalone planner. The ten cases in
 * `test/resources/habit-planner-golden/` were produced by the planner's own `engine.js`
 * (`tests/make_golden.py`): an input model, two weeks, and per day the blocks, every occurrence and
 * the two structural flags Tendril keeps. Priority, the daily limit and the capacity flag are not
 * in them (D3, D4).
 *
 * The planner places "X times a week" by itself; Tendril only suggests (D5). So each week is
 * computed twice: once to read the suggestions, then again with those days confirmed, as the person
 * would — which is what the golden case shows. Priorities in the models are ignored: the two
 * weekly habits in them share one, so the planner broke the tie by id, which is the uid here.
 */
class CalendarScheduleGoldenTest {

    private val cases = listOf(
        "base-rules", "edit-occurrence-block", "edit-day-series-shift", "edit-week-weekdays-time", "edit-from-minutes",
        "edit-skip-delete-pause", "edit-move-to-other-day", "edit-series-slot-to-time", "block-overlap-flag", "block-boundary-moved",
    )

    @Test
    fun `every golden case reproduces, day by day`() {
        val failures = cases.mapNotNull { name -> runCatching { check(name) }.exceptionOrNull()?.let { "$name: ${it.message}" } }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `the cases are all there and all non-trivial`() {
        cases.forEach { name ->
            val days = golden(name)["expected"]!!.jsonArray
            assertEquals("$name: two weeks", 14, days.size)
            assertTrue("$name: something is placed", days.any { it.jsonObject["occurrences"]!!.jsonArray.isNotEmpty() })
        }
    }

    private fun check(name: String) {
        val json = golden(name)
        val model = model(json["model"]!!.jsonObject)
        val expected = json["expected"]!!.jsonArray.associateBy { it.jsonObject["date"]!!.jsonPrimitive.content }
        for (monday in json["mondays"]!!.jsonArray.map { LocalDate.parse(it.jsonPrimitive.content) }) {
            val suggested = planWeek(model, monday)
            val confirmations = suggested.suggestions.mapIndexed { i, s ->
                ScheduleEdit("confirm-$i", EditTarget.HABIT, s.habitUid, EditScope.Week(monday), EditChanges(weekDays = s.days.map { it.dayOfWeek }.toSet()), LocalDateTime.of(2100, 1, 1, 0, 0).toInstant(ZoneOffset.UTC))
            }
            val week = planWeek(model.copy(edits = model.edits + confirmations), monday)
            assertTrue("$monday: every suggestion confirmed", week.suggestions.isEmpty())
            for (day in week.days) {
                val want = expected[day.date.toString()]?.jsonObject ?: error("no expected day ${day.date}")
                val blocks = day.blocks.map { "${it.uid} ${hhmm(it.start)}-${hhmm(it.end)}" }
                val wantBlocks = want["blocks"]!!.jsonArray.map { it.jsonObject.let { b -> "${b.str("id")} ${b.str("start")}-${b.str("end")}" } }
                assertEquals("${day.date} blocks", wantBlocks, blocks)
                val occ = day.blocks.flatMap { b -> (b.timed + b.flexible).map { o -> Row(o.habitUid, o.key, b.uid, o.time?.let(::hhmm), o.minutes, tag(o.tag)) } }
                    .sortedWith(compareBy({ it.block }, { it.time ?: "" }, { it.id }, { it.n }))
                val wantOcc = want["occurrences"]!!.jsonArray.map { e ->
                    val o = e.jsonObject
                    Row(o.str("id"), o["n"]!!.jsonPrimitive.content, o.str("block"), o["time"]!!.let { if (it is JsonNull) null else it.jsonPrimitive.content }, o["minutes"]!!.jsonPrimitive.int, tag(o["tag"]!!))
                }
                assertEquals("${day.date} occurrences", wantOcc, occ)
                assertTrue("${day.date}: nothing in Any time — every golden habit has a block", day.anyTime.isEmpty())
                val flags = (List(day.outside.size) { "flag_outside" } + List(day.overlaps.size) { "flag_overlap" }).sorted()
                assertEquals("${day.date} flags", want["flags"]!!.jsonArray.map { it.jsonPrimitive.content }, flags)
            }
        }
    }

    private data class Row(val id: String, val n: String, val block: String, val time: String?, val minutes: Int, val tag: String?)

    private fun tag(t: OccurrenceTag?): String? = when (t) {
        null -> null
        is OccurrenceTag.Count -> "count ${t.i}/${t.n}"
        OccurrenceTag.TimeStep -> "time"
        is OccurrenceTag.Week -> "week ${t.i}/${t.n}"
    }

    private fun tag(j: JsonElement): String? {
        if (j is JsonNull) return null
        val o = j.jsonObject
        return when (val k = o.str("kind")) {
            "time" -> "time"
            else -> "$k ${o["i"]!!.jsonPrimitive.int}/${o["n"]!!.jsonPrimitive.int}"
        }
    }

    // ---------- the planner's model, read into the engine's ----------

    private fun golden(name: String): JsonObject {
        val text = javaClass.classLoader!!.getResource("habit-planner-golden/$name.json")!!.readText()
        return Json.parseToJsonElement(text).jsonObject
    }

    private fun model(m: JsonObject) = PlanModel(
        blocks = m["blocks"]!!.jsonArray.map { it.jsonObject.let { b -> PlanBlock(b.str("id"), minute(b["start"]!!)!!, minute(b["end"]!!)!!) } },
        overrides = m["overrides"]!!.jsonArray.map { it.jsonObject.let { o -> BlockOverride(o.str("block"), days(o["days"]!!), minute(o["start"]!!)!!, minute(o["end"]!!)!!) } },
        habits = m["items"]!!.jsonArray.map { habit(it.jsonObject) },
        edits = m["edits"]!!.jsonArray.map { edit(it.jsonObject) },
    )

    private fun habit(o: JsonObject) = PlanHabit(
        uid = o.str("id"),
        rule = rule(o["repeat"]!!.jsonObject),
        minutes = o["minutes"]!!.jsonPrimitive.int,
        blockUid = o["block"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content,
        time = o["time"]?.let(::minute),
        activeFrom = o.date("start"),
        activeUntil = o.date("end"),
        pause = if (o["paused"]?.jsonPrimitive?.booleanOrNull == true) PauseSpan(null, null) else o["pause"]?.takeUnless { it is JsonNull }?.jsonObject?.let(::pause),
    )

    private fun rule(r: JsonObject): CalendarRule = when (val k = r.str("rule")) {
        "daily" -> CalendarRule.Daily
        "once" -> CalendarRule.Once(LocalDate.parse(r.str("date")))
        "weekly", "weekdays" -> CalendarRule.Weekdays(days(r["days"] ?: JsonArray(listOf(r["day"]!!))))
        "every_n_days", "alternate_days" -> CalendarRule.EveryNDays(if (k == "alternate_days") 2 else r["n"]!!.jsonPrimitive.int, LocalDate.parse(r.str("anchor")))
        "every_n_weeks", "alternate_weeks" -> CalendarRule.EveryNWeeks(if (k == "alternate_weeks") 2 else r["n"]!!.jsonPrimitive.int, LocalDate.parse(r.str("anchor")), days(r["days"]!!))
        "times_per_day" -> CalendarRule.TimesPerDay(r["n"]!!.jsonPrimitive.int, slots(r))
        "times_per_week" -> CalendarRule.TimesPerWeek(r["n"]!!.jsonPrimitive.int, r["days"]?.let(::days) ?: DayOfWeek.entries.toSet())
        "every_n_hours" -> CalendarRule.EveryNHours(r["n"]!!.jsonPrimitive.int, minute(r["from"]!!)!!, minute(r["until"]!!)!!)
        else -> error("unknown rule $k")
    }

    /** A slot written as a set time rather than a block id — the planner's own test in `expandWeek`. */
    private val clock = Regex("""^\d{1,2}:\d{2}$""")

    private fun slots(r: JsonObject): List<Slot> {
        (r["times"] as? JsonArray)?.takeIf { it.isNotEmpty() }?.let { ts -> return ts.map { Slot.At(minute(it)!!) } }
        return (r["blocks"] as? JsonArray).orEmpty().map { b ->
            val s = b.jsonPrimitive.content
            if (clock.matches(s)) Slot.At(minute(b)!!) else Slot.InBlock(s)
        }
    }

    private fun edit(e: JsonObject): ScheduleEdit {
        val s = e["scope"]!!.jsonObject
        val date = s.date("date")
        val days = s["days"]?.let(::days)
        val scope = when (s.str("kind")) {
            "occurrence" -> EditScope.Occurrence(date!!, s["occ"]!!.jsonPrimitive.content)
            "day" -> EditScope.Day(date!!)
            "week" -> EditScope.Week(mondayOf(s.str("week")), days)
            "from" -> EditScope.From(date!!, days)
            "extra" -> EditScope.Extra(date!!)
            else -> error("scope ${s.str("kind")}")
        }
        val target = if (e.str("target") == "block") EditTarget.BLOCK else EditTarget.HABIT
        val set = e["set"]!!.jsonObject
        val repeat = set["repeat"]?.jsonObject
        val changes = if (target == EditTarget.BLOCK) {
            EditChanges(blockStart = set["start"]?.let(::minute), blockEnd = set["end"]?.let(::minute))
        } else {
            EditChanges(
                blockUid = set["block"]?.let { Patch(if (it is JsonNull) null else it.jsonPrimitive.content) },
                time = set["time"]?.let { Patch(minute(it)) },
                minutes = set["minutes"]?.jsonPrimitive?.intOrNull,
                rule = repeat?.takeIf { "rule" in it }?.let(::rule),
                rulePatch = repeat?.takeUnless { "rule" in it }?.let { RulePatch(n = it["n"]?.jsonPrimitive?.intOrNull, from = it["from"]?.let(::minute), until = it["until"]?.let(::minute)) },
                skip = set["skip"]?.jsonPrimitive?.booleanOrNull,
                deleted = set["deleted"]?.jsonPrimitive?.booleanOrNull,
                pause = set["pause"]?.let { Patch(if (it is JsonNull) null else pause(it.jsonObject)) },
            )
        }
        // "2026-09-29T10:00#0001": the planner's stamp; the counter orders edits made in the same minute.
        val (stamp, counter) = e.str("created").split("#")
        val created = LocalDateTime.parse(stamp).toInstant(ZoneOffset.UTC).plusMillis(counter.toLong())
        return ScheduleEdit(e.str("id"), target, e.str("ref"), scope, changes, created)
    }

    private fun pause(p: JsonObject) = PauseSpan(p.date("from"), p.date("until"))

    private fun JsonObject.str(k: String) = this[k]!!.jsonPrimitive.content
    private fun JsonObject.date(k: String) = this[k]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content?.let(LocalDate::parse)

    /** The planner writes weekdays 0–6 from Monday. */
    private fun days(j: JsonElement) = j.jsonArray.map { DayOfWeek.of(it.jsonPrimitive.int + 1) }.toSet()

    /** "HH:MM" or minutes, as the planner's `tmin` accepts either; null for null. */
    private fun minute(j: JsonElement): Int? = when {
        j is JsonNull -> null
        j is JsonPrimitive && j.isString -> j.content.split(":").let { (h, m) -> h.toInt() * 60 + m.toInt() }
        else -> j.jsonPrimitive.int
    }

    private fun mondayOf(isoWeek: String): LocalDate {
        val (y, w) = isoWeek.split("-W")
        return LocalDate.of(y.toInt(), 1, 4).with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, w.toLong()).with(DayOfWeek.MONDAY)
    }

    private fun hhmm(m: Int) = "%02d:%02d".format(m / 60, m % 60)
}

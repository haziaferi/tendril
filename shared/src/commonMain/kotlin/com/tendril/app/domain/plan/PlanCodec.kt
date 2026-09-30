package com.tendril.app.domain.plan

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.time.DayOfWeek
import java.time.LocalDate

/*
 * §6.3 (v27) — how the engine's rule, scopes, changes and weekday block times are stored: as text,
 * so the row keeps exactly what was written and a value a newer build writes survives this one.
 * Every decoder answers null for text it cannot read, and the caller treats that as "this does
 * nothing" — a habit with no occurrences, an edit that is skipped — never as a crash (the house
 * rule for a newer peer's data, §9.4).
 */

private val DAY_CODES = mapOf(
    DayOfWeek.MONDAY to "MON", DayOfWeek.TUESDAY to "TUE", DayOfWeek.WEDNESDAY to "WED", DayOfWeek.THURSDAY to "THU",
    DayOfWeek.FRIDAY to "FRI", DayOfWeek.SATURDAY to "SAT", DayOfWeek.SUNDAY to "SUN",
)
private val CODE_DAYS = DAY_CODES.entries.associate { (k, v) -> v to k }

/** `MON,WED`, Monday first; the empty set is the empty string. */
fun encodeDays(days: Set<DayOfWeek>): String = days.sorted().joinToString(",") { DAY_CODES.getValue(it) }

fun decodeDays(text: String): Set<DayOfWeek>? =
    if (text.isEmpty()) emptySet() else text.split(",").map { CODE_DAYS[it] ?: return null }.toSet()

private fun hhmm(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)

private val HHMM = Regex("(\\d{2}):(\\d{2})")

private fun minuteOf(text: String): Int? {
    val m = HHMM.matchEntire(text) ?: return null
    val (h, min) = m.destructured
    return (h.toInt() * 60 + min.toInt()).takeIf { h.toInt() < 24 && min.toInt() < 60 }
}

private fun dateOf(text: String): LocalDate? = runCatching { LocalDate.parse(text) }.getOrNull()

/**
 * §6.3 — a rule as one tagged string, as `RecurrenceRule` is stored (`Converters`): `DAILY`,
 * `ONCE:<date>`, `WEEKDAYS:<days>`, `EVERY_N_DAYS:<n>:<anchor>`, `EVERY_N_WEEKS:<n>:<anchor>:<days>`,
 * `TIMES_PER_DAY:<n>[:<slots>]` (a slot is a block uid or `HH:MM`), `TIMES_PER_WEEK:<n>:<days>`,
 * `EVERY_N_HOURS:<n>:<HH:MM>:<HH:MM>`.
 */
fun encodeRule(rule: CalendarRule): String = when (rule) {
    CalendarRule.Daily -> "DAILY"
    is CalendarRule.Once -> "ONCE:${rule.date}"
    is CalendarRule.Weekdays -> "WEEKDAYS:${encodeDays(rule.days)}"
    is CalendarRule.EveryNDays -> "EVERY_N_DAYS:${rule.n}:${rule.anchor}"
    is CalendarRule.EveryNWeeks -> "EVERY_N_WEEKS:${rule.n}:${rule.anchor}:${encodeDays(rule.days)}"
    is CalendarRule.TimesPerDay -> if (rule.slots.isEmpty()) "TIMES_PER_DAY:${rule.n}" else
        "TIMES_PER_DAY:${rule.n}:" + rule.slots.joinToString(",") { s -> when (s) { is Slot.At -> hhmm(s.minute); is Slot.InBlock -> s.blockUid } }
    is CalendarRule.TimesPerWeek -> "TIMES_PER_WEEK:${rule.n}:${encodeDays(rule.days)}"
    is CalendarRule.EveryNHours -> "EVERY_N_HOURS:${rule.n}:${hhmm(rule.from)}:${hhmm(rule.until)}"
}

private val TIME_LIKE = Regex("\\d{1,2}:\\d{2}")

private val EVERY_N_HOURS = Regex("EVERY_N_HOURS:(\\d+):(\\d{2}:\\d{2}):(\\d{2}:\\d{2})")

/** [encodeRule]'s inverse; null for anything else, including a rule a newer build added. */
fun decodeRule(text: String?): CalendarRule? {
    if (text == null) return null
    EVERY_N_HOURS.matchEntire(text)?.let { m ->
        val (n, from, until) = m.destructured
        return CalendarRule.EveryNHours(n.toIntOrNull() ?: return null, minuteOf(from) ?: return null, minuteOf(until) ?: return null)
    }
    val p = text.split(":", limit = 3)
    fun n() = p.getOrNull(1)?.toIntOrNull()
    return when (p[0]) {
        "DAILY" -> CalendarRule.Daily.takeIf { p.size == 1 }
        "ONCE" -> dateOf(text.removePrefix("ONCE:"))?.let(CalendarRule::Once)
        "WEEKDAYS" -> decodeDays(text.removePrefix("WEEKDAYS:"))?.let(CalendarRule::Weekdays)
        "EVERY_N_DAYS" -> CalendarRule.EveryNDays(n() ?: return null, dateOf(p.getOrNull(2) ?: return null) ?: return null)
        "EVERY_N_WEEKS" -> {
            val rest = p.getOrNull(2)?.split(":", limit = 2) ?: return null
            CalendarRule.EveryNWeeks(n() ?: return null, dateOf(rest[0]) ?: return null, decodeDays(rest.getOrNull(1) ?: return null) ?: return null)
        }
        "TIMES_PER_DAY" -> {
            // a slot shaped like a time but not one (25:00, 9:30) is unreadable, not a block of that name
            val slots = p.getOrNull(2)?.split(",")?.map { s ->
                minuteOf(s)?.let(Slot::At) ?: if (TIME_LIKE.matches(s) || s.isEmpty()) return null else Slot.InBlock(s)
            } ?: emptyList()
            CalendarRule.TimesPerDay(n() ?: return null, slots)
        }
        "TIMES_PER_WEEK" -> CalendarRule.TimesPerWeek(n() ?: return null, decodeDays(p.getOrNull(2) ?: return null) ?: return null)
        else -> null
    }
}

/** §6.3 — a scope as one tagged string: `OCC:<date>:<key>`, `DAY:<date>`, `WEEK:<monday>[:<days>]`, `FROM:<date>[:<days>]`, `EXTRA:<date>`. */
fun encodeScope(scope: EditScope): String = when (scope) {
    is EditScope.Occurrence -> "OCC:${scope.date}:${scope.key}"
    is EditScope.Day -> "DAY:${scope.date}"
    is EditScope.Week -> "WEEK:${scope.monday}" + (scope.days?.let { ":" + encodeDays(it) } ?: "")
    is EditScope.From -> "FROM:${scope.date}" + (scope.days?.let { ":" + encodeDays(it) } ?: "")
    is EditScope.Extra -> "EXTRA:${scope.date}"
}

fun decodeScope(text: String): EditScope? {
    // the key may itself hold a colon (`x:<edit uid>`), so only the first two split
    val p = text.split(":", limit = 3)
    val date = dateOf(p.getOrNull(1) ?: return null) ?: return null
    // the third part is a key for OCC and a day list for WEEK and FROM; an unreadable day list is
    // an unreadable scope, never "every day", which would widen the edit
    fun days(): Set<DayOfWeek>? = p.getOrNull(2)?.let { decodeDays(it) ?: throw IllegalArgumentException() }
    return runCatching {
        when (p[0]) {
            "OCC" -> EditScope.Occurrence(date, p.getOrNull(2)?.takeIf { it.isNotEmpty() } ?: return null)
            "DAY" -> EditScope.Day(date).takeIf { p.size == 2 }
            "WEEK" -> EditScope.Week(date, days())
            "FROM" -> EditScope.From(date, days())
            "EXTRA" -> EditScope.Extra(date).takeIf { p.size == 2 }
            else -> null
        }
    }.getOrNull()
}

private val json = Json { ignoreUnknownKeys = true }

/**
 * §6.3 — an edit's changes as a JSON object of the fields it sets. A key present with `null` is
 * "set to nothing" (remove the time); a key absent is "not changed" — the engine's [Patch].
 */
fun encodeChanges(c: EditChanges): String = buildJsonObject {
    c.blockUid?.let { put("block", it.value) }
    c.time?.let { put("time", it.value) }
    c.minutes?.let { put("minutes", it) }
    c.rule?.let { put("rule", encodeRule(it)) }
    c.rulePatch?.let { r -> put("rulePatch", buildJsonObject { r.n?.let { put("n", it) }; r.from?.let { put("from", it) }; r.until?.let { put("until", it) } }) }
    c.skip?.let { put("skip", it) }
    c.deleted?.let { put("deleted", it) }
    c.pause?.let { p -> put("pause", p.value?.let { s -> buildJsonObject { put("from", s.from?.toString()); put("until", s.until?.toString()) } } ?: JsonNull) }
    c.sortOrder?.let { put("order", it) }
    c.weekDays?.let { put("weekDays", encodeDays(it)) }
    c.blockStart?.let { put("start", it) }
    c.blockEnd?.let { put("end", it) }
}.toString()

/** Thrown inside [decodeChanges] only: any part of the wrong shape makes the whole edit unreadable. */
private object Unreadable : RuntimeException()

private fun unreadable(): Nothing = throw Unreadable

/**
 * [encodeChanges]'s inverse. Unknown keys are ignored (a newer build's); a known key of the wrong
 * shape, at any depth, makes the whole edit unreadable (null) — never a partial edit, which would
 * apply some of a change and silently drop the rest.
 */
fun decodeChanges(text: String): EditChanges? = try {
    decodeChangesOrThrow(runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: unreadable())
} catch (_: Unreadable) {
    null
}

private fun decodeChangesOrThrow(o: JsonObject): EditChanges {
    fun prim(e: JsonElement): JsonPrimitive = e as? JsonPrimitive ?: unreadable()
    fun int(e: JsonElement): Int = prim(e).takeIf { !it.isString }?.intOrNull ?: unreadable()
    fun bool(e: JsonElement): Boolean = prim(e).takeIf { !it.isString }?.booleanOrNull ?: unreadable()
    fun text(e: JsonElement): String = prim(e).takeIf { it.isString }?.content ?: unreadable()
    fun obj(e: JsonElement): JsonObject = e as? JsonObject ?: unreadable()
    fun date(e: JsonElement): LocalDate? = if (e is JsonNull) null else dateOf(text(e)) ?: unreadable()
    // a time is a minute of the day, a length at least none, a block edge the day's own bounds —
    // off the day a stroke would draw with a negative height (review of Phase 3)
    fun within(e: JsonElement, range: IntRange): Int = int(e).takeIf { it in range } ?: unreadable()
    return EditChanges(
        blockUid = o["block"]?.let { Patch(if (it is JsonNull) null else text(it)) },
        time = o["time"]?.let { Patch(if (it is JsonNull) null else within(it, 0..1439)) },
        minutes = o["minutes"]?.let { within(it, 0..1440) },
        rule = o["rule"]?.let { decodeRule(text(it)) ?: unreadable() },
        rulePatch = o["rulePatch"]?.let(::obj)?.let { r -> RulePatch(r["n"]?.let { within(it, 1..Int.MAX_VALUE) }, r["from"]?.let { within(it, 0..1439) }, r["until"]?.let { within(it, 0..1439) }) },
        skip = o["skip"]?.let(::bool),
        deleted = o["deleted"]?.let(::bool),
        pause = o["pause"]?.let { Patch(if (it is JsonNull) null else obj(it).let { r -> PauseSpan(r["from"]?.let(::date), r["until"]?.let(::date)) }) },
        sortOrder = o["order"]?.let { prim(it).takeIf { p -> !p.isString }?.doubleOrNull ?: unreadable() },
        weekDays = o["weekDays"]?.let { decodeDays(text(it)) ?: unreadable() },
        blockStart = o["start"]?.let { within(it, 0..1440) },
        blockEnd = o["end"]?.let { within(it, 0..1440) },
    )
}

/** H2 — a block's weekday times: `SAT,SUN@480-630;MON@420-540`. */
fun encodeOverrides(overrides: List<BlockOverride>): String =
    overrides.joinToString(";") { "${encodeDays(it.days)}@${it.start}-${it.end}" }

private val OVERRIDE = Regex("([A-Z,]+)@(\\d{1,4})-(\\d{1,4})")

/**
 * [encodeOverrides]'s inverse for [blockUid]. Weekday times travel with their block (H2), so an
 * unreadable part — a sign, an end before its start, an edge off the day — is dropped and the
 * rest kept: one bad weekday must not cost the others.
 */
fun decodeOverrides(blockUid: String, text: String?): List<BlockOverride> =
    text.orEmpty().split(";").filter { it.isNotEmpty() }.mapNotNull { part ->
        val (days, s, e) = OVERRIDE.matchEntire(part)?.destructured ?: return@mapNotNull null
        val start = s.toInt()
        val end = e.toInt().takeIf { it in (start + 1)..1440 } ?: return@mapNotNull null
        BlockOverride(blockUid, decodeDays(days)?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null, start, end)
    }

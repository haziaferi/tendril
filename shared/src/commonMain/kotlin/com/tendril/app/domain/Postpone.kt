package com.tendril.app.domain

import com.tendril.app.data.entry.Entry
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** §0.6.4 — the units a task can be pushed by. Named by the author: minutes, hours, days, months;
 * weeks because a week is what "next week" means and a month is not seven of anything. */
enum class PostponeUnit { MINUTE, HOUR, DAY, WEEK, MONTH }

/** One tap's worth of postponing — the presets a sheet offers before "custom". */
data class PostponeAmount(val amount: Int, val unit: PostponeUnit) {
    init { require(amount > 0) { "Postpone moves forward; a non-positive amount is a different feature." } }

    companion object {
        val PRESETS: List<PostponeAmount> = listOf(
            PostponeAmount(15, PostponeUnit.MINUTE),
            PostponeAmount(1, PostponeUnit.HOUR),
            PostponeAmount(3, PostponeUnit.HOUR),
            PostponeAmount(1, PostponeUnit.DAY),
            PostponeAmount(1, PostponeUnit.WEEK),
            PostponeAmount(1, PostponeUnit.MONTH),
        )
    }
}

/**
 * §0.6.4's Postpone, applied to the *When* — `startDate`/`startTime` — and never to the
 * Deadline. §0.10 item 1 asked which date the control should move by default; this is the
 * answer taken: a plan moves without guilt (Things' "move to tomorrow"), a deadline is moved
 * only by someone editing the deadline. `dueDate` is untouched here by construction, not by a
 * branch.
 *
 * The rules, chosen so that every task has *some* sensible answer and none has a surprising one:
 * - A dated task with a time moves from that instant.
 * - A dated task without a time moves from that date; minutes and hours give it a time, anchored
 *   at [now] on that date, because "+1 hour" on a task with no hour has nothing else to mean.
 *   Days, weeks and months leave it timeless.
 * - An undated task ("Someday") moves from [today]: postponing it is what dates it.
 *
 * Pure, so the Tasks screen and any later Plan mode share one arithmetic. The caller stamps
 * `updatedAt` and re-arms alarms.
 */
fun Entry.postponed(by: PostponeAmount, today: LocalDate, now: LocalTime): Entry {
    val hadTime = startTime != null
    val base = LocalDateTime.of(startDate ?: today, startTime ?: now)
    val moved = when (by.unit) {
        PostponeUnit.MINUTE -> base.plusMinutes(by.amount.toLong())
        PostponeUnit.HOUR -> base.plusHours(by.amount.toLong())
        PostponeUnit.DAY -> base.plusDays(by.amount.toLong())
        PostponeUnit.WEEK -> base.plusWeeks(by.amount.toLong())
        PostponeUnit.MONTH -> base.plusMonths(by.amount.toLong())
    }
    val keepsTime = hadTime || by.unit == PostponeUnit.MINUTE || by.unit == PostponeUnit.HOUR
    return copy(
        startDate = moved.toLocalDate(),
        startTime = if (keepsTime) moved.toLocalTime() else null,
        updatedAt = Instant.now(),
    )
}

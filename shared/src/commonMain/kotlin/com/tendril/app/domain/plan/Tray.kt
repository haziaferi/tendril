package com.tendril.app.domain.plan

import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.data.entry.EntryStatus
import java.time.LocalDate

/**
 * B§13.6 #5 — what the Calendar's task tray offers (decided 2026-09-16: Unscheduled + Overdue —
 * Sunsama's and Akiflow's backlog beside the week). The Plan rail's rule without its "today"
 * half, plus the overdue. Never a step, never a done task, never a series (a series' past
 * occurrences are the rule's business, not a backlog's); an override row of a series is not a
 * backlog item either. Nothing is placed for the person (§0.5.2): the tray offers, the drag decides.
 */
data class TrayTasks(val unscheduled: List<Entry>, val overdue: List<Entry>) {
    val isEmpty: Boolean get() = unscheduled.isEmpty() && overdue.isEmpty()
}

fun trayTasks(tasks: List<Entry>, today: LocalDate): TrayTasks {
    val candidates = tasks.filter {
        it.kind == EntryKind.TASK && it.deletedAt == null && it.status == EntryStatus.PENDING &&
            it.parentEntryId == null && it.originalEntryId == null && it.recurrenceRule == null
    }
    val unscheduled = candidates.filter { it.startDate == null }.sortedBy { it.title.lowercase() }
    val overdue = candidates.filter { it.startDate != null && it.startDate!! < today }.sortedWith(compareBy({ it.startDate }, { it.title.lowercase() }))
    return TrayTasks(unscheduled, overdue)
}

/** L8 — the tray's drawn width: the remembered width, never more than [maxShare] of the pane (the shelf's rule at 45 %). The share wins even under the handle's 240 dp floor: the week's lanes come first. */
fun trayDrawnWidthDp(rememberedDp: Int, paneWidthDp: Float, maxShare: Float = TRAY_MAX_SHARE): Int =
    minOf(rememberedDp, (paneWidthDp * maxShare).toInt())

const val TRAY_MAX_SHARE = 0.30f

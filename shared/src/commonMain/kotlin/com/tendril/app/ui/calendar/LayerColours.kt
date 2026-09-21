package com.tendril.app.ui.calendar

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.tendril.app.domain.plan.BlockKind
import com.tendril.app.domain.plan.DotKind
import com.tendril.app.ui.theme.LocalTendrilPalette

/**
 * B§13.8.3 (14g·2) — the calendar's layers by token, not by Material role: an event block is the
 * register's event hue at its tint (a habit is a stroke, `HabitStrokeLayer.kt`); a task block keeps the
 * selection tint until 14g·3 gives it its category's fill and the urgency stripe; anything else
 * (a database date, until the slider PR gives a database its own hue) the third hue's tint.
 * The block's text is the register's own text on every one of them (≥ 4.6:1, tested).
 */
@Composable
fun layerTint(kind: BlockKind): Color {
    val p = LocalTendrilPalette.current
    return when (kind) {
        BlockKind.EVENT -> p.eventSoft
        BlockKind.TASK -> p.accentSoft
        BlockKind.OTHER -> p.thirdSoft
    }
}

/**
 * The phone's Month dot per layer (the dots PR folded into L4, 2026-09-17; the token map's note in
 * `docs/benchmarks.md`): a task's dot is the ladder's *none* — `textDim`, never the accent, which
 * rule 1 reserves — an event's the event hue, a habit's the habit hue, a database date's the third.
 */
@Composable
fun dotColour(kind: DotKind): Color {
    val p = LocalTendrilPalette.current
    return when (kind) {
        DotKind.TASK -> p.textDim
        DotKind.EVENT -> p.event
        DotKind.HABIT -> p.habit
        DotKind.DATABASE -> p.third
    }
}

/** 14g·3 — a task block's left edge is its urgency (B§13.8.1: *category fill + urgency stripe*);
 *  null when the block is not a task, the level is none, or the switch is off. */
@Composable
fun blockStripe(occurrenceEntry: com.tendril.app.data.entry.Entry?, showUrgency: Boolean, today: java.time.LocalDate): Color? {
    if (!showUrgency || occurrenceEntry == null || occurrenceEntry.kind != com.tendril.app.data.entry.EntryKind.TASK) return null
    return LocalTendrilPalette.current.urgencyColour(com.tendril.app.domain.urgency.urgencyOf(occurrenceEntry, today).level)
}

package com.tendril.app.ui.calendar

/**
 * L5 (2026-09-17) — the time grid's proportions, one home for the Day's Plan lanes and the
 * Week grid so the two cannot drift: Notion Calendar's, measured natively at 125 % on
 * 2026-09-17 — 48 px per hour and a 60 px gutter — as dp under the Compact scale
 * (`docs/critiques/calendar-chrome-mock.md`). 56 / 44 until this PR: nineteen hours now fit the
 * user's 1040 px window where eleven did.
 */
object CalendarGeometry {
    const val HOUR_DP = 45
    const val GUTTER_DP = 56
}

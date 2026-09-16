package com.tendril.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * One register's full token set in one mode, per spec §2.3 (rewritten 2026-09-16, B§13.7).
 * Nothing here is typed by hand any more: [paletteFor] derives every field from a register's
 * ground and hue, solving each to its contrast floor (`ColorSolve.kt`) — so the AA claim is a
 * property of the code, walked by `RegisterSolveTest` for every register × mode × OLED.
 */
data class TendrilPalette(
    /** The resolved mode — a stored hue (a label, a callout) is lightened on a dark ground, darkened on a light one. */
    val dark: Boolean,
    val bg: Color,
    /** The lifted ground — hover, the selected row, a sheet: text mixed 4 % (light) / 6 % (dark). */
    val surface2: Color,
    /** Solved into the 9.5–13:1 band (B§13.7.3 rule 2), never the ground's raw near-black/white. */
    val text: Color,
    /** ≥ 4.6:1 on the ground *and* on [surface2]. */
    val textDim: Color,
    /** ≥ 3.05:1 on both grounds — a hairline that means something takes this, not [border]. */
    val textFaint: Color,
    /** The hue, solved to ≥ 8.0:1 light / 7.6:1 dark on the ground by lightness alone. */
    val accent: Color,
    val accentStrong: Color,
    /** ≥ 4.6:1 on [accent]: white on a light ground, the ground itself on a dark one. */
    val onAccent: Color,
    /** The selection tint — [accent] at 12 % / 22 % over the ground. */
    val accentSoft: Color,
    /** ≥ 4.6:1 on [accentSoft]. */
    val accentSoftText: Color,
    /** Chrome hairlines: text at 11 % / 14 % — Notion's weight, ~1.3:1, meaningless by design. */
    val border: Color,
    /** The fan's third hue as a *mark* (≥ 3.0:1): the register's second channel where it has one,
     *  else the accent fanned 180°. Block-reference bars, related edges, canvas outlines, the
     *  current find match (14g·2). */
    val third: Color,
    /** [third] solved to ≥ 4.6:1 — the third hue where it is read or carries text. */
    val thirdStrong: Color,
    /** ≥ 4.6:1 on [thirdStrong]. */
    val onThird: Color,
    /** [third] at 14 % / 24 % into the ground — a database's fill until it has its own hue. */
    val thirdSoft: Color,
    /** The find mark: [third] at up to 30 % / 36 %, backed off until [text] reads on it at 4.6. */
    val findSoft: Color,
    /** The calendar's event layer: the accent fanned −120°, ≥ 4.6:1. */
    val event: Color,
    val eventSoft: Color,
    /** The calendar's habit layer: the accent fanned +60°, ≥ 4.6:1. */
    val habit: Color,
    val habitSoft: Color,
    /** The error family — blocked, overdue, delete: a red at 5° solved to ≥ 4.6:1 on the ground
     *  *and* on [errorSoft]. */
    val error: Color,
    val errorSoft: Color,
    val onError: Color,
    /** The urgency ladder's four marks, low → urgent ([Ladder]); *none* draws nothing (14g·3). */
    val ladder: List<Color>,
) {
    /** The mark for a level, or null for none. */
    fun urgencyColour(level: Int): Color? = if (level <= 0) null else ladder[(level - 1).coerceIn(0, 3)]
}

/** The stored preference; [SYSTEM] follows the OS and is the default (§2.3's tri-state, closed). */
enum class TendrilMode(val key: String, val label: String) {
    SYSTEM("system", "System"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark");

    companion object {
        /** Accepts the new keys and the old `ThemePreferences` enum names (`LIGHT`, `DARK`). */
        fun fromKey(key: String?): TendrilMode = entries.firstOrNull { it.key == key?.lowercase() } ?: SYSTEM
    }
}

enum class TendrilTypeface(val key: String, val label: String) {
    SANS("sans", "Sans"),
    SERIF("serif", "Serif");

    companion object {
        fun fromKey(key: String?): TendrilTypeface = entries.firstOrNull { it.key == key?.lowercase() } ?: SANS
    }
}

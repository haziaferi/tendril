package com.tendril.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * One register's full token set in one mode, per spec §2.3 (rewritten 2026-09-16, B§13.7).
 * Nothing here is typed by hand any more: [paletteFor] derives every field from a register's
 * ground and hue, solving each to its contrast floor (`ColorSolve.kt`) — so the AA claim is a
 * property of the code, walked by `RegisterSolveTest` for every register × mode × OLED.
 */
data class TendrilPalette(
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
    /** The fan's third hue: the register's second channel solved to ≥ 3.0:1 as a mark, else the
     *  accent mixed 55 % with the text. Block references, related edges, canvas nodes (14g·2). */
    val third: Color,
    val thirdSoft: Color,
)

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

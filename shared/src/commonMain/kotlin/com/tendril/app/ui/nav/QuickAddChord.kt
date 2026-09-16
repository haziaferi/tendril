package com.tendril.app.ui.nav

/** `prefs.properties` — which [QuickAddChord] the desktop registers; absent means the default. */
const val QUICK_ADD_CHORD_KEY = "quick_add_chord"
/** `prefs.properties` — the main window's × hides it to the notification area (true, the default) or quits. */
const val CLOSE_TO_TRAY_KEY = "close_to_tray"

/**
 * B§13.6 #7 — the global quick-add chords the desktop offers, a pick list rather than a recorder
 * (decided 2026-09-16): a recorder needs validation against the system's own chords, and four
 * known-free combinations cover the machines this app runs on. Win+Alt+letter is Todoist's
 * pattern on Windows (Win+Alt+Q, offered second); the default is **Win+Alt+N** — the walk found
 * Win+Alt+T, the planned default, held by Windows' Game Bar (its recording timer; Game Bar also
 * holds Win+Alt+R/G/B/M/W). Ctrl+Alt+letter is avoided because it is AltGr on Icelandic and most
 * European layouts; Ctrl+Space (Todoist's other) would steal an IDE's completion; Shift+Alt+A is
 * TickTick's. The Win32 modifier bits are spelled here as plain ints so this module never sees
 * JNA — the desktop passes [modifiersMask] and [vk] straight to `RegisterHotKey`.
 */
enum class QuickAddChord(val key: String, val label: String, val win: Boolean, val ctrl: Boolean, val shift: Boolean, val alt: Boolean, val vk: Int) {
    WIN_ALT_N("win_alt_n", "Win+Alt+N", win = true, ctrl = false, shift = false, alt = true, vk = 0x4E),
    WIN_ALT_Q("win_alt_q", "Win+Alt+Q", win = true, ctrl = false, shift = false, alt = true, vk = 0x51),
    CTRL_SHIFT_SPACE("ctrl_shift_space", "Ctrl+Shift+Space", win = false, ctrl = true, shift = true, alt = false, vk = 0x20),
    SHIFT_ALT_A("shift_alt_a", "Shift+Alt+A", win = false, ctrl = false, shift = true, alt = true, vk = 0x41),
    CTRL_ALT_SPACE("ctrl_alt_space", "Ctrl+Alt+Space", win = false, ctrl = true, shift = false, alt = true, vk = 0x20);

    /** `MOD_ALT | MOD_CONTROL | MOD_SHIFT | MOD_WIN` as Win32 spells them, plus `MOD_NOREPEAT`. */
    fun modifiersMask(): Int =
        (if (alt) MOD_ALT else 0) or (if (ctrl) MOD_CONTROL else 0) or (if (shift) MOD_SHIFT else 0) or (if (win) MOD_WIN else 0) or MOD_NOREPEAT

    companion object {
        const val MOD_ALT = 0x0001
        const val MOD_CONTROL = 0x0002
        const val MOD_SHIFT = 0x0004
        const val MOD_WIN = 0x0008
        const val MOD_NOREPEAT = 0x4000
        val DEFAULT = WIN_ALT_N
        fun fromKey(key: String?): QuickAddChord = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

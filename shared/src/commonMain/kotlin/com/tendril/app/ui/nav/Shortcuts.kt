package com.tendril.app.ui.nav

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key

/**
 * B§13.4 14e — the desktop's fixed shortcut set, as **one table**. `Main.kt` resolves a key event
 * through [shortcutFor] and runs the action through [ShortcutActions]; the overlay
 * ([ShortcutsOverlay]) is generated from the same table, so it can never list a chord that is
 * not bound (the mock's list had drifted — `docs/critiques/keyboard-desktop.md` #2). No menu bar
 * and no Alt-mnemonics (decided 2026-09-13): the actions live in each screen's `···` and in the
 * palette's `>` commands, and a bar would be a third home for the same list.
 *
 * The list keys (↑ ↓ ↵, type-ahead, Esc) are the list's own (`ListKeyboard.kt`) and Esc = back
 * is the window's; the overlay shows them as static rows under *Lists* and *Navigate*.
 */
enum class ShortcutAction(val group: ShortcutGroup, val label: String) {
    TAB_PAGES(ShortcutGroup.NAVIGATE, "Pages"),
    TAB_CALENDAR(ShortcutGroup.NAVIGATE, "Calendar"),
    TAB_TASKS(ShortcutGroup.NAVIGATE, "Tasks"),
    TAB_ROAD_MAP(ShortcutGroup.NAVIGATE, "Road Map"),
    TAB_SETTINGS(ShortcutGroup.NAVIGATE, "Settings"),
    BACK(ShortcutGroup.NAVIGATE, "Back"),
    FORWARD(ShortcutGroup.NAVIGATE, "Forward"),
    TOGGLE_TREE(ShortcutGroup.NAVIGATE, "Hide or show the tree"),
    TOGGLE_SHELF(ShortcutGroup.NAVIGATE, "Close or reopen the shelf"),
    NEW_PAGE(ShortcutGroup.CREATE, "New page"),
    NEW_TASK(ShortcutGroup.CREATE, "New task"),
    JOURNAL_TODAY(ShortcutGroup.CREATE, "Today's Journal"),
    SWITCHER(ShortcutGroup.FIND, "Quick switcher"),
    FIND_IN_PAGE(ShortcutGroup.FIND, "Find in page"),
    SHORTCUTS(ShortcutGroup.FIND, "This list"),
}

enum class ShortcutGroup(val label: String) { NAVIGATE("Navigate"), CREATE("Create"), FIND("Find"), LISTS("Lists") }

/**
 * A chord. Every key here is one a person can press **on any layout without AltGr or Shift**:
 * the plan's Ctrl+[ ] and Ctrl+/ (Notion's, Slack's) are AltGr+è and Shift+7 on an Italian
 * keyboard — the one this app is written on — so back/forward are the browsers' Alt+← / Alt+→
 * and the overlay is F1, Windows' own help key. Shift only where the action is a variant.
 */
data class Chord(val key: Key, val ctrl: Boolean = true, val shift: Boolean = false, val alt: Boolean = false) {
    /** What the overlay prints for this chord. */
    fun label(): String = buildString {
        if (ctrl) append("Ctrl+")
        if (alt) append("Alt+")
        if (shift) append("Shift+")
        append(KEY_NAMES[key] ?: key.toString())
    }
}

private val KEY_NAMES: Map<Key, String> = mapOf(
    Key.K to "K", Key.N to "N", Key.T to "T", Key.F to "F", Key.Backslash to "\\", Key.F1 to "F1",
    Key.DirectionLeft to "←", Key.DirectionRight to "→",
    Key.One to "1", Key.Two to "2", Key.Three to "3", Key.Four to "4", Key.Five to "5",
)

/** The table. Order is the overlay's order within a group. */
val SHORTCUTS: List<Pair<ShortcutAction, Chord>> = listOf(
    ShortcutAction.TAB_PAGES to Chord(Key.One),
    ShortcutAction.TAB_CALENDAR to Chord(Key.Two),
    ShortcutAction.TAB_TASKS to Chord(Key.Three),
    ShortcutAction.TAB_ROAD_MAP to Chord(Key.Four),
    ShortcutAction.TAB_SETTINGS to Chord(Key.Five),
    ShortcutAction.BACK to Chord(Key.DirectionLeft, ctrl = false, alt = true),
    ShortcutAction.FORWARD to Chord(Key.DirectionRight, ctrl = false, alt = true),
    ShortcutAction.TOGGLE_TREE to Chord(Key.Backslash),
    // 14h·1 — rhymes with the tree's: the shelf is the other side pane.
    ShortcutAction.TOGGLE_SHELF to Chord(Key.Backslash, shift = true),
    ShortcutAction.NEW_PAGE to Chord(Key.N),
    ShortcutAction.NEW_TASK to Chord(Key.N, shift = true),
    ShortcutAction.JOURNAL_TODAY to Chord(Key.T),
    ShortcutAction.SWITCHER to Chord(Key.K),
    ShortcutAction.FIND_IN_PAGE to Chord(Key.F),
    ShortcutAction.SHORTCUTS to Chord(Key.F1, ctrl = false),
)

/**
 * The action a KeyDown resolves to, or null. Every modifier must match exactly — a plain
 * letter is text or type-ahead, and Ctrl+Shift+N is never Ctrl+N with a bonus.
 */
fun shortcutFor(key: Key, ctrl: Boolean, shift: Boolean, alt: Boolean = false): ShortcutAction? =
    SHORTCUTS.firstOrNull { (_, chord) -> chord.key == key && chord.ctrl == ctrl && chord.shift == shift && chord.alt == alt }?.first

/** The chord's label for an action — the switcher's command rows read it (L6), so the card and the F1 card agree. */
fun chordLabelOf(action: ShortcutAction): String? = SHORTCUTS.firstOrNull { it.first == action }?.second?.label()

/** The tab an action selects, for the five `TAB_*` rows the overlay prints as one line. */
fun ShortcutAction.tab(): WorkbenchDestination? = when (this) {
    ShortcutAction.TAB_PAGES -> WorkbenchDestination.PAGES
    ShortcutAction.TAB_CALENDAR -> WorkbenchDestination.CALENDAR
    ShortcutAction.TAB_TASKS -> WorkbenchDestination.TASKS_HABITS
    ShortcutAction.TAB_ROAD_MAP -> WorkbenchDestination.ROAD_MAP
    ShortcutAction.TAB_SETTINGS -> WorkbenchDestination.SETTINGS
    else -> null
}

/**
 * The `EscapeBackInput` pattern: built in `Main.kt`, where the key event arrives, filled by the
 * scaffold, which owns the things the actions move (`navState`, the switcher, the tree, the
 * pages ViewModel). Nothing runs until the scaffold has composed once.
 */
class ShortcutActions {
    var run: (ShortcutAction) -> Unit = {}
}

/** The overlay's open flag — the switcher's pattern, so Settings and F1 share one door. */
class ShortcutsState {
    var open: Boolean by mutableStateOf(false)
    /** B§13.6 #7 — the global quick-add chord the desktop registered, for the card's static row. */
    var quickAddChordLabel: String by mutableStateOf(QuickAddChord.DEFAULT.label)
}

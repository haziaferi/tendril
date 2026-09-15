package com.tendril.app.ui.components

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import kotlinx.coroutines.delay
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

/**
 * B§13.6 #8 (14e) — ↑ ↓ ↵ and type-ahead in a list, shared: the desktop's keyboard and a
 * hardware keyboard on the phone run the same code. The list's container takes
 * [listKeyboard]; each row draws a ring when `state.focused == its index`, and a click on a row
 * sets the cursor there and gives the list focus, so "click the list, then ↓" works.
 *
 * Esc clears the cursor and the typed prefix through a `BackHandler` the site arms only while
 * there is something to clear — so the first Esc clears, the next goes back, on both platforms
 * (the desktop's Escape is the same back dispatch, `Main.kt`).
 */
class ListKeyState {
    var focused: Int? by mutableStateOf(null)
    /** The type-ahead buffer, as typed; the row's title underlines it. Empties 1 s after the last key. */
    var typed: String by mutableStateOf("")
    val focusRequester = FocusRequester()

    fun clear() { focused = null; typed = "" }
    val hasSomethingToClear: Boolean get() = focused != null || typed.isNotEmpty()

    /** A row was clicked: the cursor moves there and the list takes the keyboard. */
    fun clickedRow(index: Int) {
        focused = index
        typed = ""
        runCatching { focusRequester.requestFocus() }
    }
}

/**
 * The first title at or after [from] whose start matches [typed] (case-insensitive), wrapping to
 * the top; null when nothing matches — the cursor stays where it is. Pure, so tested.
 */
fun typeAheadTarget(titles: List<String>, typed: String, from: Int?): Int? {
    if (typed.isEmpty() || titles.isEmpty()) return null
    val needle = typed.lowercase()
    val start = from ?: 0
    for (i in titles.indices) {
        val idx = (start + i) % titles.size
        if (titles[idx].lowercase().startsWith(needle)) return idx
    }
    return null
}

/**
 * Whether the cursor ring is drawn: only while the input mode is the keyboard's. A finger on
 * the phone sets the cursor too (a tap is a click) but sees no ring — Android draws none for
 * touch — and a hardware keyboard's first key flips the mode and the ring appears.
 */
@Composable
fun keyboardCursorShown(): Boolean = LocalInputModeManager.current.inputMode == InputMode.Keyboard

/** Resets the typed prefix a second after the last keystroke, the Finder's rhythm. */
@Composable
fun TypeAheadReset(state: ListKeyState) {
    LaunchedEffect(state.typed) {
        if (state.typed.isNotEmpty()) { delay(1_000); state.typed = "" }
    }
}

/**
 * ↑ ↓ move the cursor (clamped — a list has ends), Home/End jump, ↵ opens, → and ← reach
 * [onExpand] where a site gives one (a tree: expand and collapse), a letter or digit appends to
 * the type-ahead buffer and jumps to the first matching title. Consumed only when handled, so
 * anything else (Tab, the window's chords) passes through.
 */
@Composable
fun Modifier.listKeyboard(
    state: ListKeyState,
    count: () -> Int,
    titles: () -> List<String>,
    onOpen: (Int) -> Unit,
    onExpand: ((index: Int, expand: Boolean) -> Unit)? = null,
): Modifier {
    // Remembered per site so the lambdas below read the latest values without re-keying the node.
    val latest = remember { mutableStateOf<() -> Int>({ 0 }) }.also { it.value = count }
    val latestTitles = remember { mutableStateOf<() -> List<String>>({ emptyList() }) }.also { it.value = titles }
    return this
        .focusRequester(state.focusRequester)
        .focusable()
        .onKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
            if (event.isCtrlPressed || event.isMetaPressed) return@onKeyEvent false
            val n = latest.value()
            if (n == 0) return@onKeyEvent false
            val cur = state.focused
            when (event.key) {
                Key.DirectionDown -> { state.focused = if (cur == null) 0 else minOf(cur + 1, n - 1); state.typed = ""; true }
                Key.DirectionUp -> { state.focused = if (cur == null) 0 else maxOf(cur - 1, 0); state.typed = ""; true }
                Key.MoveHome -> { state.focused = 0; state.typed = ""; true }
                Key.MoveEnd -> { state.focused = n - 1; state.typed = ""; true }
                Key.Enter, Key.NumPadEnter -> { if (cur != null) { onOpen(cur); true } else false }
                Key.DirectionRight -> { if (cur != null && onExpand != null) { onExpand(cur, true); true } else false }
                Key.DirectionLeft -> { if (cur != null && onExpand != null) { onExpand(cur, false); true } else false }
                else -> {
                    val cp = event.utf16CodePoint
                    if (cp <= 0 || cp > 0xFFFF || !cp.toChar().isLetterOrDigit()) return@onKeyEvent false
                    val typed = state.typed + cp.toChar()
                    state.typed = typed
                    typeAheadTarget(latestTitles.value(), typed, cur)?.let { state.focused = it }
                    true
                }
            }
        }
}

/**
 * The title with the typed prefix underlined when this row is the cursor — the type-ahead's
 * feedback in the row itself, not a caption elsewhere (`docs/critiques/keyboard-desktop.md` #3).
 */
fun keyedTitle(title: String, typed: String, focused: Boolean): AnnotatedString =
    if (focused && typed.isNotEmpty() && title.lowercase().startsWith(typed.lowercase())) buildAnnotatedString {
        withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) { append(title.substring(0, typed.length)) }
        append(title.substring(typed.length))
    } else AnnotatedString(title)

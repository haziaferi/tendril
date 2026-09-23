package com.tendril.desktopapp

import com.tendril.app.data.entry.Entry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Audit 1.8 — one write per open of the quick-add popup, lifted out of [QuickAddWindow]'s Enter
 * lambda so a test can reach it.
 *
 * The defect this exists to keep fixed: the popup used `added` — the *Added* flash's state — as its
 * one-write guard, and `added` is assigned only once `quickAddEntry` has returned from the
 * database. Both Enters of a double tap are delivered inside that round trip, so both saw it null
 * and both launched. Walked on the dev build 2026-09-22: five attempts, five pairs of duplicate
 * entries, 0–3 ms apart. The same five through `SendKeys.SendWait`, which waits for each keystroke
 * to be processed, wrote one apiece — the guard only ever held when the second Enter happened to
 * land after the write had finished.
 *
 * **What a write **completed** is not what a guard needs to know; what a write **started** is.**
 * That is the whole fix, and it is why the check below is outside `launch` rather than inside it.
 *
 * **Why this is a file and not a lambda.** The row said for five audits that it could not be
 * tested, because `Tendril windows` had no test source set; that stopped being true on 2026-09-23
 * and the row was swept up with three others whose reasons had quietly expired. Unlike row 1.5,
 * which is a duplicated `Text` and needs the `ui-test-junit4` artifact the offline cache does not
 * hold, none of this draws — it is a flag and a coroutine, and a single-threaded test reproduces
 * the defect exactly, with no race window to hit and nothing to flake. The extraction is the move
 * `SyncPass.kt` already made for row 1.17.
 */
internal class QuickAddWriteGate {

    private var started = false

    /**
     * True exactly once per instance; false for every later caller.
     *
     * Not Compose state: nothing draws from it, so backing it with `mutableStateOf` bought a
     * recomposition per keystroke-that-writes and no correctness. The popup is composed only while
     * it is open, so `remember { QuickAddWriteGate() }` starts each open closed with nothing having
     * to reset it — which is the same property the old `var writing` had, and the reason neither
     * needs an `open()`.
     */
    fun start(): Boolean {
        if (started) return false
        started = true
        return true
    }
}

/** How long *Added* stays up before the popup closes itself (B§13.6 #7). */
internal const val QUICK_ADD_FLASH_MS = 700L

/**
 * Writes one entry, shows the flash, closes the popup — or does nothing at all, if [gate] has
 * already been claimed for this open.
 *
 * [write] is passed rather than the DAO and the coordinator, so a test does not need a database to
 * ask the only question here: how many times did the write run?
 *
 * A [write] returning null still claims the gate, still flashes nothing and still closes the
 * popup. `quickAddEntry` returns null for a blank title, so that case is Enter on an empty
 * line, and closing is what it should do. This is the behaviour the lambda already had — the
 * extraction changed none of it — and it is pinned, so a later edit cannot quietly turn a
 * blank Enter into a retryable one.
 */
internal fun CoroutineScope.launchQuickAdd(
    gate: QuickAddWriteGate,
    write: suspend () -> Entry?,
    onAdded: (Entry?) -> Unit,
    onClose: () -> Unit,
    flashMs: Long = QUICK_ADD_FLASH_MS,
) {
    // Claimed here — on the caller's thread, before the coroutine starts — and deliberately not
    // inside `launch`. Inside, the window between the two Enters would be open again.
    if (!gate.start()) return
    launch {
        onAdded(write())
        delay(flashMs)
        onClose()
    }
}

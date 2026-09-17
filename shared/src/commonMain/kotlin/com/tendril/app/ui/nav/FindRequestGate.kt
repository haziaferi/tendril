package com.tendril.app.ui.nav

/**
 * The audit's fixes (2026-09-17, F1 of `docs/critiques/desktop-function-full.md`): Ctrl+F bumps
 * `WorkbenchNavState.findRequested`, a counter that is never reset; a page composed *after* the
 * bump saw the old count and opened its find bar unasked — every page after one Ctrl+F. A screen
 * keeps one gate, born with the count it first sees, and opens only on a change from there.
 */
class FindRequestGate(initial: Int) {
    private var seen = initial

    /** True once per new count; the count a screen was born with is not an event. */
    fun accept(n: Int): Boolean {
        if (n == seen) return false
        seen = n
        return true
    }
}

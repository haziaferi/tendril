package com.tendril.app.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * §3.1.2 — Checkbox-only mode: per-page, session-scoped opt-in, orthogonal to [ViewLockState].
 * At most one page at a time, since the lock-screen-bypass window flags it drives
 * (`Activity.setShowWhenLocked`/`setTurnScreenOn`, wired in
 * [com.tendril.app.ui.nav.WorkbenchScaffold]) are window-level, not per-screen — activating a
 * new page implicitly clears whatever page had it before. Not persisted: "it reverts
 * automatically the moment the person navigates away from that page or closes the app," a
 * natural consequence of the Activity lifecycle rather than data worth storing.
 */
class CheckboxOnlyState(private val appLockEnabled: () -> Boolean = { false }) {
    private val _activePageId = MutableStateFlow<Long?>(null)
    val activePageId: StateFlow<Long?> = _activePageId.asStateFlow()

    /**
     * @return false when refused because App Lock is on.
     *
     * The two features are in direct contradiction: App Lock exists to put an authentication
     * step in front of the app, and this mode's whole purpose is to draw the app over the
     * keyguard without one. Whichever order the person enabled them in, honouring both is
     * impossible, and honouring this one silently defeats the other. The caller explains the
     * refusal; deciding it here keeps the rule in one place rather than at each call site.
     *
     * Defaults to "no App Lock" because there is none on desktop (§1) — the predicate is
     * injected rather than read directly since AppLockPreferences is Android-only.
     */
    fun activate(pageId: Long): Boolean {
        if (appLockEnabled()) return false
        _activePageId.value = pageId
        return true
    }

    fun deactivate() { _activePageId.value = null }
}

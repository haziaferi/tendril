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
class CheckboxOnlyState {
    private val _activePageId = MutableStateFlow<Long?>(null)
    val activePageId: StateFlow<Long?> = _activePageId.asStateFlow()
    fun activate(pageId: Long) { _activePageId.value = pageId }
    fun deactivate() { _activePageId.value = null }
}

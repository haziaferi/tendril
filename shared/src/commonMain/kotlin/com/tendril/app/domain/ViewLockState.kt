package com.tendril.app.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * §3.1.2 — View-Only lock: one global toggle across every page under Pages, not per-page.
 * In-memory only, a session-scoped interaction guard rather than data worth persisting across
 * app restarts. Held here (not inside [com.tendril.app.ui.pages.PagesViewModel]) because the
 * eye toggle lives in the Pages hub's own topbar but has to gate editing on every other Pages
 * screen — page detail, database — each of which gets its own separate ViewModel instance
 * per page opened, so a per-ViewModel flag could never be the single shared switch the spec
 * calls for.
 */
class ViewLockState {
    private val _viewOnly = MutableStateFlow(false)
    val viewOnly: StateFlow<Boolean> = _viewOnly.asStateFlow()
    fun setViewOnly(value: Boolean) { _viewOnly.value = value }
}

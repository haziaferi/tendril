package com.tendril.app.domain

import com.tendril.app.data.entry.Entry

/**
 * §9.8 R1's centralization principle, applied to a second fan-out. Every write path that
 * changes a schedulable Entry needs its alarms and (Android's) Calendar Provider mirror kept
 * in sync — [ResolveEntryUseCase] calls through here rather than touching either directly.
 *
 * §12.5 — lives in `:shared` as an interface, not a concrete class: alarms and Calendar
 * Provider are Android-only platform APIs with no desktop equivalent (§12.1), so each platform
 * supplies its own implementation. Android's is `AndroidEntryScheduleCoordinator` (`:app`).
 */
interface EntryScheduleCoordinator {
    suspend fun onEntryChanged(entry: Entry)
    suspend fun onEntryRemoved(entry: Entry)
}

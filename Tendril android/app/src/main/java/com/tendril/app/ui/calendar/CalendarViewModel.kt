package com.tendril.app.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryDao
import com.tendril.app.data.entry.EntryKind
import com.tendril.app.domain.EntryScheduleCoordinator
import com.tendril.app.domain.ResolveEntryUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

class CalendarViewModel(
    private val entryDao: EntryDao,
    private val resolveEntryUseCase: ResolveEntryUseCase,
    private val entryScheduleCoordinator: EntryScheduleCoordinator,
) : ViewModel() {
    val entries: StateFlow<List<Entry>> =
        entryDao.observeDated().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Quick Add (§3.2) — deliberately minimal, single-line capture; no reminders list here. */
    fun quickAdd(title: String, date: LocalDate) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val now = Instant.now()
            val id = entryDao.insert(
                Entry(
                    title = title.trim(),
                    kind = EntryKind.EVENT,
                    startDate = date,
                    startTime = null,
                    endDate = null,
                    endTime = null,
                    recurrenceRule = null,
                    createdAt = now,
                    updatedAt = now,
                )
            )
            entryDao.getById(id)?.let { entryScheduleCoordinator.onEntryChanged(it) }
        }
    }

    /** §9.8 R1 — the checked/unchecked decision lives in [ResolveEntryUseCase.setDone], not in
     * each surface's own ViewModel. */
    fun setDone(entryId: Long, done: Boolean) {
        viewModelScope.launch { resolveEntryUseCase.setDone(entryId, done) }
    }

    /** Unchecking is an undo, not a second resolution. [ResolveEntryUseCase.resolve] only
     * takes a terminal status, so routing "unchecked" to `SKIPPED` logged a *second*
     * [com.tendril.app.data.completion.EntryCompletion] for one real-world act and, on a
     * recurring task, advanced `startDate` by another period — the opposite of undoing it.
     * §5.2 exposes Skipped as its own state, not as the meaning of clearing the box. */
    fun unresolve(entryId: Long) {
        viewModelScope.launch { resolveEntryUseCase.unresolve(entryId) }
    }
}

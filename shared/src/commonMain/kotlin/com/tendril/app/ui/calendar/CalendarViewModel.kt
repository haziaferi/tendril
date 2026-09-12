package com.tendril.app.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryDao
import com.tendril.app.domain.EntryEditor
import com.tendril.app.domain.EntryScheduleCoordinator
import com.tendril.app.domain.MoveScope
import com.tendril.app.domain.ParsedEntry
import com.tendril.app.domain.toEntry
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
    private val entryEditor: EntryEditor,
) : ViewModel() {
    /** §0.8 step 6b — the edit sheet's save; kind invariants are the editor's. */
    fun save(entry: Entry) {
        viewModelScope.launch { entryEditor.save(entry) }
    }

    /** §0.8 step 6b — a drag's end: this occurrence, or the whole series, to [toDate]. */
    fun move(entry: Entry, occurrenceDate: LocalDate, toDate: LocalDate, scope: MoveScope) {
        viewModelScope.launch { entryEditor.move(entry, occurrenceDate, toDate, scope = scope) }
    }

    fun trash(entryId: Long) {
        viewModelScope.launch { resolveEntryUseCase.trash(entryId) }
    }

    val entries: StateFlow<List<Entry>> =
        entryDao.observeDated().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Quick Add (§3.2) — deliberately minimal, single-line capture; no reminders list here.
     * §0.8 step 5: the line is read by [com.tendril.app.domain.QuickAddParser] on the screen,
     * previewed, and arrives here already a Task or an Event; [date] is the day on view, used
     * when the line named none. */
    fun quickAdd(parsed: ParsedEntry, date: LocalDate) {
        if (parsed.title.isBlank()) return
        viewModelScope.launch {
            val id = entryDao.insert(parsed.toEntry(fallbackDate = date, now = Instant.now()))
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

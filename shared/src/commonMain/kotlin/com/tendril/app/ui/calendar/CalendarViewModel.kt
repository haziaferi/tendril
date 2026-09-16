package com.tendril.app.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryDao
import com.tendril.app.data.habit.Habit
import com.tendril.app.data.habit.HabitDao
import com.tendril.app.data.pagedatabase.PropertyValueDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import com.tendril.app.domain.EntryEditor
import com.tendril.app.domain.EntryScheduleCoordinator
import com.tendril.app.domain.MoveScope
import com.tendril.app.domain.ParsedEntry
import com.tendril.app.domain.toEntry
import com.tendril.app.domain.ResolveEntryUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import com.tendril.app.data.track.TimeLog
import com.tendril.app.domain.track.minuteTicker
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import com.tendril.app.domain.track.TimeTracker
import com.tendril.app.data.prefs.KeyValueStore
import com.tendril.app.domain.track.TrackTarget
import java.time.LocalDate
import java.time.LocalTime

/**
 * §0.8 step 6d — what the Calendar draws. Tasks and events are the Entries; *habits* are §3.2's
 * "Show Habits", the ones with a time, drawn on every day; *database dates* are every stored
 * DATE cell in every database, drawn on its day and opening its page. Persisted through
 * [KeyValueStore] since §0.10 item 12 (2026-09-12) as four letters — the same on both platforms.
 */
data class CalendarLayers(
    val tasks: Boolean = true,
    val events: Boolean = true,
    val habits: Boolean = false,
    val databaseDates: Boolean = false,
) {
    fun encode(): String = listOfNotNull("t".takeIf { tasks }, "e".takeIf { events }, "h".takeIf { habits }, "d".takeIf { databaseDates }).joinToString("")

    companion object {
        const val KEY = "calendar_layers"
        fun decode(value: String?): CalendarLayers =
            if (value == null) CalendarLayers() else CalendarLayers(tasks = 't' in value, events = 'e' in value, habits = 'h' in value, databaseDates = 'd' in value)
    }
}

class CalendarViewModel(
    private val entryDao: EntryDao,
    private val resolveEntryUseCase: ResolveEntryUseCase,
    private val entryScheduleCoordinator: EntryScheduleCoordinator,
    private val entryEditor: EntryEditor,
    habitDao: HabitDao,
    propertyValueDao: PropertyValueDao,
    private val timeTracker: TimeTracker,
    private val keyValueStore: KeyValueStore,
) : ViewModel() {
    private val _layers = MutableStateFlow(CalendarLayers.decode(keyValueStore.get(CalendarLayers.KEY)))
    val layers: StateFlow<CalendarLayers> = _layers.asStateFlow()
    fun setLayers(layers: CalendarLayers) {
        _layers.value = layers
        keyValueStore.put(CalendarLayers.KEY, layers.encode())
    }

    /** §0.8 step 7b — every live task, for Plan mode's rail (`unplannedTasks` picks the day's). */
    val tasks: StateFlow<List<Entry>> = entryDao.observeTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** §0.8 step 7b — a rail task dropped on the grid: When = the day, time = the drop. */
    fun place(entry: Entry, day: LocalDate, time: LocalTime) {
        viewModelScope.launch { entryEditor.move(entry, null, day, time, MoveScope.ALL) }
    }

    /** §0.8 step 7b — a block dragged to another hour; a series asks the screen first. */
    fun moveTo(entry: Entry, occurrenceDate: LocalDate, time: LocalTime, scope: MoveScope) {
        viewModelScope.launch { entryEditor.move(entry, occurrenceDate, occurrenceDate, time, scope) }
    }

    /** §0.6.5 / step 7c — ▶/■ on a Day row. */
    fun toggleTracking(target: TrackTarget) {
        viewModelScope.launch { timeTracker.toggle(target) }
    }

    /** §0.8 step 7d — the day's logs, re-emitted every minute so an open log's minutes move. */
    fun logsOn(day: LocalDate): Flow<Pair<List<TimeLog>, Instant>> =
        combine(timeTracker.logsOn(day), minuteTicker()) { logs, now -> logs to now }

    /** Habits with a time — the only ones a calendar can place. */
    val timedHabits: StateFlow<List<Habit>> = habitDao.observeActive()
        .map { habits -> habits.filter { it.time != null } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Every stored DATE cell, parsed; a cell that does not parse as a date is not a day. */
    val dateCells: StateFlow<List<DatedCell>> = propertyValueDao.observeDateCells()
        .map { cells -> cells.mapNotNull { c -> runCatching { LocalDate.parse(c.value) }.getOrNull()?.let { DatedCell(c.pageId, c.title, c.propertyName, it) } } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** §0.8 step 6b — the edit sheet's save; kind invariants are the editor's. */
    fun save(entry: Entry) {
        viewModelScope.launch { entryEditor.save(entry) }
    }

    /** 14f·2 — a block dragged in the week grid: the column is the date, the quarter-hour the
     * time, one editor call for both axes; a series asks the screen first, as the others do. */
    fun moveBlock(entry: Entry, occurrenceDate: LocalDate, toDate: LocalDate, time: LocalTime, scope: MoveScope) {
        viewModelScope.launch { entryEditor.move(entry, occurrenceDate, toDate, time, scope) }
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

/** A database row's DATE cell, on the Calendar (§0.8 step 6d). */
data class DatedCell(val pageId: Long, val title: String, val propertyName: String, val date: LocalDate)

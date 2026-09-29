package com.tendril.app.calendarprovider

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import com.tendril.app.data.calendar.CalendarLinkDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

/**
 * §9.12 — read-back over every calendar the person ticked, one at a time, never two passes at once.
 *
 * A pass runs when the app starts, when a calendar event is edited in Tendril (the coordinator's
 * hook), when the system calendar changes under it (a sync app downloaded something — observed),
 * and from *Sync now*. Requests within [DEBOUNCE_MS] collapse into one pass. A pass that finds
 * nothing to do writes nothing, so the observer seeing Tendril's own writes settles after one
 * quiet pass rather than looping.
 *
 * Takes its collaborators as functions so the pass itself is testable without a device;
 * `AppContainer` wires them to [SystemCalendars] and `SystemCalendarPreferences`.
 */
class SystemCalendarSync(
    private val idForKey: (String) -> Long?,
    private val readBackFor: (String) -> CalendarReadBack,
    private val ticked: () -> Set<String>,
    private val setTicked: (String, Boolean) -> Unit,
    private val linkDao: CalendarLinkDao,
    private val hasPermission: () -> Boolean,
) {
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pending: Job? = null

    private val _lastResult = MutableStateFlow<ReadBackResult?>(null)
    /** The last pass, for Calendar settings to report — conflicts and duplicates included. */
    val lastResult: StateFlow<ReadBackResult?> = _lastResult.asStateFlow()

    suspend fun syncNow(now: Instant = Instant.now()): ReadBackResult = mutex.withLock {
        if (!hasPermission()) return@withLock ReadBackResult()
        var total = ReadBackResult()
        for (key in ticked().sorted()) {
            // A calendar ticked once and since removed from the phone — its sync app uninstalled,
            // its account signed out. Skipped, not failed: its entries stay in Tendril as they are.
            val id = idForKey(key) ?: continue
            total += readBackFor(key).sync(key, id, now)
        }
        _lastResult.value = total
        total
    }

    /** Un-ticked, a calendar's links mean nothing; its entries stay (nothing in Tendril is
     * destroyed by a setting), and a later re-tick relinks equal events without writing. */
    suspend fun setTicked(key: String, on: Boolean) {
        setTicked.invoke(key, on)
        if (!on) linkDao.deleteForCalendar(key)
        requestSync()
    }

    fun requestSync() {
        pending?.cancel()
        pending = scope.launch {
            delay(DEBOUNCE_MS)
            runCatching { syncNow() }
        }
    }

    private var observer: ContentObserver? = null

    /** Watches the system calendar for changes a sync app makes, from launch on. */
    fun start(context: Context) {
        if (observer == null) {
            val watcher = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) = requestSync()
            }
            runCatching { context.contentResolver.registerContentObserver(CalendarContract.Events.CONTENT_URI, true, watcher) }
                .onSuccess { observer = watcher }
        }
        requestSync()
    }

    private companion object {
        const val DEBOUNCE_MS = 1_500L
    }
}

private operator fun ReadBackResult.plus(other: ReadBackResult) = ReadBackResult(
    created + other.created, updated + other.updated, written + other.written, trashed + other.trashed, deleted + other.deleted,
    conflicts + other.conflicts, duplicates + other.duplicates,
)

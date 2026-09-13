package com.tendril.app.sync

import com.tendril.app.data.completion.EntryCompletion
import com.tendril.app.data.completion.EntryCompletionDao
import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryDao
import com.tendril.app.data.habit.Habit
import com.tendril.app.data.habit.HabitCompletion
import com.tendril.app.data.habit.HabitCompletionDao
import com.tendril.app.data.track.TimeLog
import com.tendril.app.data.track.TimeLogDao
import com.tendril.app.data.habit.HabitDao
import com.tendril.app.data.reminder.Reminder
import com.tendril.app.data.reminder.ReminderDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import java.time.Instant
import java.time.LocalDate

/**
 * In-memory doubles for the sync tests. Deliberately hand-written rather than mocked: these
 * three are what the assertions are actually about, so their behaviour should be readable in
 * one place instead of scattered across `every { } returns` stanzas.
 */

/** Backing store for [SyncFileStore], with a record of what was deleted so a test can assert
 * that an unreadable conflict file was *left alone* — the whole point of the SYNC-01 fix. */
class InMemorySyncFileStore(
    private val root: MutableMap<String, ByteArray> = linkedMapOf(),
    private val pages: MutableMap<String, ByteArray> = linkedMapOf(),
    private val images: MutableMap<String, ByteArray> = linkedMapOf(),
) : SyncFileStore {

    /** Names handed to `writePage`, so a test can assert that a pass wrote *nothing* — which
     * byte comparison cannot show once §9.4.2 is on, since every encryption uses a fresh IV. */
    val writtenPageNames = mutableListOf<String>()

    val deletedRootNames = mutableListOf<String>()
    val deletedPageNames = mutableListOf<String>()
    val deletedImageNames = mutableListOf<String>()

    fun putRoot(name: String, content: String) { root[name] = content.toByteArray(Charsets.UTF_8) }
    fun putRootBytes(name: String, bytes: ByteArray) { root[name] = bytes }
    fun putPage(name: String, content: String) { pages[name] = content.toByteArray(Charsets.UTF_8) }

    fun putImage(name: String, bytes: ByteArray) { images[name] = bytes }

    fun rootNames(): Set<String> = root.keys.toSet()
    fun pageNames(): Set<String> = pages.keys.toSet()
    fun imageNames(): Set<String> = images.keys.toSet()
    fun imageBytes(name: String): ByteArray? = images[name]

    override suspend fun readRoot(name: String): ByteArray? = root[name]
    override suspend fun writeRoot(name: String, bytes: ByteArray) { root[name] = bytes }
    override suspend fun listRoot(): List<String> = root.keys.toList()
    override suspend fun deleteRoot(name: String) {
        if (root.remove(name) != null) deletedRootNames += name
    }

    override suspend fun readPage(name: String): ByteArray? = pages[name]
    override suspend fun writePage(name: String, bytes: ByteArray) {
        pages[name] = bytes
        writtenPageNames += name
    }
    override suspend fun listPages(): List<String> = pages.keys.toList()
    override suspend fun deletePage(name: String) {
        if (pages.remove(name) != null) deletedPageNames += name
    }

    override suspend fun readImage(name: String): ByteArray? = images[name]
    override suspend fun writeImage(name: String, bytes: ByteArray) { images[name] = bytes }
    override suspend fun listImages(): List<String> = images.keys.toList()
    override suspend fun deleteImage(name: String) {
        if (images.remove(name) != null) deletedImageNames += name
    }
}

/** In-memory [LocalImageStore]. Paths are `local:<name>` so a test can tell at a glance that a
 * block's `imagePath` came from a fetch rather than from an importer. */
class InMemoryLocalImageStore : LocalImageStore {
    val written = linkedMapOf<String, ByteArray>()

    override suspend fun read(path: String): ByteArray? = written[path.removePrefix("local:")]

    override suspend fun write(name: String, bytes: ByteArray): String {
        written[name] = bytes
        return "local:$name"
    }

    override suspend fun list(): List<String> = written.keys.map { "local:$it" }

    override suspend fun delete(path: String) { written.remove(path.removePrefix("local:")) }
}

/** Autoincrementing in-memory [EntryDao]. Only the members the sync paths touch have real
 * behaviour; the observe/query members the orchestrator never calls return empties. */
class FakeEntryDao(seed: List<Entry> = emptyList()) : EntryDao {
    private val rows = linkedMapOf<Long, Entry>()
    private var nextId = 1L

    init { seed.forEach { rows[it.id] = it; nextId = maxOf(nextId, it.id + 1) } }

    override suspend fun insert(entry: Entry): Long {
        val id = nextId++
        rows[id] = entry.copy(id = id)
        return id
    }

    override suspend fun update(entry: Entry) { rows[entry.id] = entry }
    override suspend fun getAll(): List<Entry> = rows.values.toList()
    override suspend fun getById(id: Long): Entry? = rows[id]
    override suspend fun getByUid(uid: String): Entry? = rows.values.firstOrNull { it.uid == uid }
    override suspend fun getByGoogleEventId(googleEventId: String): Entry? =
        rows.values.firstOrNull { it.googleEventId == googleEventId }

    /** Column-scoped like the real query — touches `providerEventId` and nothing else, so a
     * test can still catch a whole-row write clobbering a concurrent change. */
    override suspend fun setProviderEventId(id: Long, providerEventId: Long?) {
        rows[id]?.let { rows[id] = it.copy(providerEventId = providerEventId) }
    }

    override suspend fun setGoogleEventId(id: Long, googleEventId: String?) {
        rows[id]?.let { rows[id] = it.copy(googleEventId = googleEventId) }
    }

    override suspend fun getBySourceRowId(rowPageId: Long): Entry? =
        rows.values.firstOrNull { it.sourceRowId == rowPageId && it.deletedAt == null }

    override suspend fun getTrashedBySourceRowId(rowPageId: Long): Entry? =
        rows.values.filter { it.sourceRowId == rowPageId && it.deletedAt != null }.maxByOrNull { it.deletedAt!! }

    override suspend fun getAllDated(): List<Entry> = rows.values.filter { it.startDate != null }

    override suspend fun getExceptionsOf(baseEntryId: Long): List<Entry> =
        rows.values.filter { it.originalEntryId == baseEntryId && it.deletedAt == null }

    override suspend fun getAllExceptions(): List<Entry> =
        rows.values.filter { it.originalEntryId != null && it.deletedAt == null }

    override suspend fun getAllSchedulable(): List<Entry> = emptyList()

    override suspend fun softDelete(id: Long, deletedAt: Instant) {
        rows[id]?.let { rows[id] = it.copy(deletedAt = deletedAt, updatedAt = deletedAt) }
    }

    override suspend fun restore(id: Long, restoredAt: Instant) {
        rows[id]?.let { rows[id] = it.copy(deletedAt = null, updatedAt = restoredAt) }
    }

    override suspend fun deleteForever(id: Long) { rows.remove(id) }
    override suspend fun deleteAll() { rows.clear() }

    override fun observeById(id: Long): Flow<Entry?> = flowOf(rows[id])
    override fun observeBySourceRowIds(rowPageIds: List<Long>): Flow<List<Entry>> = flowOf(emptyList())
    override fun observeTasks(): Flow<List<Entry>> = flowOf(rows.values.toList())
    override fun observeDated(): Flow<List<Entry>> = flowOf(rows.values.toList())
    override fun observeTrash(): Flow<List<Entry>> = flowOf(emptyList())
}

/**
 * Autoincrementing in-memory [ReminderDao], same shape as [FakeHabitDao].
 *
 * The tombstone filter is reproduced faithfully rather than simplified away, because the
 * distinction is exactly what the S2 tests are about: `getForEntry`/`observeForEntry` hide a
 * soft-deleted reminder (so no alarm is ever scheduled for one), while `getAll` deliberately
 * does not (so the tombstone still travels). A fake that filtered both would make the
 * resurrection bug untestable and pass regardless.
 */
class FakeReminderDao(seed: List<Reminder> = emptyList()) : ReminderDao {
    private val rows = linkedMapOf<Long, Reminder>()
    private var nextId = 1L

    init { seed.forEach { rows[it.id] = it; nextId = maxOf(nextId, it.id + 1) } }

    override suspend fun insert(reminder: Reminder): Long {
        val id = nextId++
        rows[id] = reminder.copy(id = id)
        return id
    }

    override suspend fun getForEntry(entryId: Long): List<Reminder> =
        rows.values.filter { it.entryId == entryId && it.deletedAt == null }

    override fun observeForEntry(entryId: Long): Flow<List<Reminder>> =
        flowOf(rows.values.filter { it.entryId == entryId && it.deletedAt == null })

    override suspend fun getAll(): List<Reminder> = rows.values.toList()

    override suspend fun getByUid(uid: String): Reminder? = rows.values.firstOrNull { it.uid == uid }

    override suspend fun softDelete(id: Long, deletedAt: Instant) {
        rows[id]?.let { rows[id] = it.copy(deletedAt = deletedAt) }
    }


    override suspend fun deleteAll() { rows.clear() }
}

/** Autoincrementing in-memory [EntryCompletionDao]. Append-only, like the real one: there is no
 * update or delete to model, which is the whole reason its merge is a plain union. */
class FakeEntryCompletionDao(seed: List<EntryCompletion> = emptyList()) : EntryCompletionDao {
    private val rows = linkedMapOf<Long, EntryCompletion>()
    private var nextId = 1L

    init { seed.forEach { rows[it.id] = it; nextId = maxOf(nextId, it.id + 1) } }

    override suspend fun insert(completion: EntryCompletion): Long {
        val id = nextId++
        rows[id] = completion.copy(id = id)
        return id
    }

    override fun observeForEntry(entryId: Long): Flow<List<EntryCompletion>> =
        flowOf(rows.values.filter { it.entryId == entryId }.sortedByDescending { it.resolvedAt })

    override suspend fun getAll(): List<EntryCompletion> = rows.values.toList()

    override suspend fun getByUid(uid: String): EntryCompletion? =
        rows.values.firstOrNull { it.uid == uid }


    override suspend fun deleteAll() { rows.clear() }
}

/** In-memory [HabitCompletionDao] — tombstoned like [FakeReminderDao], not append-only. */
class FakeHabitCompletionDao(seed: List<HabitCompletion> = emptyList()) : HabitCompletionDao {
    private val rows = linkedMapOf<Long, HabitCompletion>()
    private var nextId = 1L

    init { seed.forEach { rows[it.id] = it; nextId = maxOf(nextId, it.id + 1) } }

    override suspend fun insert(completion: HabitCompletion): Long {
        val id = nextId++
        rows[id] = completion.copy(id = id)
        return id
    }

    override fun observeForHabit(habitId: Long): Flow<List<HabitCompletion>> =
        flowOf(rows.values.filter { it.habitId == habitId && it.deletedAt == null }.sortedByDescending { it.date })

    override suspend fun getLiveForDay(habitId: Long, date: LocalDate): List<HabitCompletion> =
        rows.values.filter { it.habitId == habitId && it.date == date && it.deletedAt == null }

    override suspend fun getAll(): List<HabitCompletion> = rows.values.toList()

    override suspend fun getByUid(uid: String): HabitCompletion? = rows.values.firstOrNull { it.uid == uid }

    override suspend fun softDelete(id: Long, deletedAt: Instant) {
        rows[id]?.let { rows[id] = it.copy(deletedAt = deletedAt) }
    }

    override suspend fun deleteAll() { rows.clear() }
}

/** In-memory [TimeLogDao] — the running list is a [MutableStateFlow] so a test can observe a stop. */
class FakeTimeLogDao(seed: List<TimeLog> = emptyList()) : TimeLogDao {
    private val rows = linkedMapOf<Long, TimeLog>()
    private var nextId = 1L
    private val running = MutableStateFlow<List<TimeLog>>(emptyList())

    init { seed.forEach { rows[it.id] = it; nextId = maxOf(nextId, it.id + 1) }; publish() }

    private fun publish() { running.value = rows.values.filter { it.isRunning }.sortedByDescending { it.startedAt } }

    override suspend fun insert(log: TimeLog): Long {
        val id = nextId++
        rows[id] = log.copy(id = id)
        publish()
        return id
    }

    override suspend fun update(log: TimeLog) { rows[log.id] = log; publish() }

    override fun observeRunning(): Flow<List<TimeLog>> = running

    override suspend fun getRunning(): List<TimeLog> = running.value

    override fun observeForEntry(entryId: Long): Flow<List<TimeLog>> =
        flowOf(rows.values.filter { it.entryId == entryId && it.deletedAt == null }.sortedByDescending { it.startedAt })

    override fun observeForHabit(habitId: Long): Flow<List<TimeLog>> =
        flowOf(rows.values.filter { it.habitId == habitId && it.deletedAt == null }.sortedByDescending { it.startedAt })

    override fun observeBetween(from: Instant, to: Instant): Flow<List<TimeLog>> =
        flowOf(rows.values.filter { it.deletedAt == null && it.startedAt >= from && it.startedAt < to }.sortedBy { it.startedAt })

    override suspend fun getAll(): List<TimeLog> = rows.values.toList()

    override suspend fun getByUid(uid: String): TimeLog? = rows.values.firstOrNull { it.uid == uid }

    override suspend fun softDelete(id: Long, deletedAt: Instant) {
        rows[id]?.let { rows[id] = it.copy(deletedAt = deletedAt, updatedAt = deletedAt) }
        publish()
    }

    override suspend fun deleteAll() { rows.clear(); publish() }
}

/** Autoincrementing in-memory [HabitDao], same shape as [FakeEntryDao]. */
class FakeHabitDao(seed: List<Habit> = emptyList()) : HabitDao {
    private val rows = linkedMapOf<Long, Habit>()
    private var nextId = 1L

    init { seed.forEach { rows[it.id] = it; nextId = maxOf(nextId, it.id + 1) } }

    override suspend fun insert(habit: Habit): Long {
        val id = nextId++
        rows[id] = habit.copy(id = id)
        return id
    }

    override suspend fun update(habit: Habit) { rows[habit.id] = habit }
    override suspend fun getById(id: Long): Habit? = rows[id]
    override suspend fun getByUid(uid: String): Habit? = rows.values.firstOrNull { it.uid == uid }
    override suspend fun getAll(): List<Habit> = rows.values.toList()

    override suspend fun softDelete(id: Long, deletedAt: Instant) {
        rows[id]?.let { rows[id] = it.copy(deletedAt = deletedAt, updatedAt = deletedAt) }
    }

    override suspend fun restore(id: Long, restoredAt: Instant) {
        rows[id]?.let { rows[id] = it.copy(deletedAt = null, updatedAt = restoredAt) }
    }

    override suspend fun deleteForever(id: Long) { rows.remove(id) }
    override suspend fun deleteAll() { rows.clear() }

    override fun observeActive(): Flow<List<Habit>> = flowOf(rows.values.filter { it.deletedAt == null })
    override fun observeTrash(): Flow<List<Habit>> = flowOf(rows.values.filter { it.deletedAt != null })
}

/** §0.6.15 — an in-memory key; the tests never touch the network. */
class FakeAiKeyStore(initial: String? = null) : com.tendril.app.data.prefs.AiKeyStore {
    private val _key = kotlinx.coroutines.flow.MutableStateFlow(initial)
    override val key: kotlinx.coroutines.flow.StateFlow<String?> = _key
    override fun set(value: String?) { _key.value = value?.trim()?.takeIf { it.isNotEmpty() } }
}

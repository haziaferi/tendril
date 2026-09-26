package com.tendril.app.sync

import android.content.Context
import android.net.Uri
import com.tendril.app.data.completion.EntryCompletionDao
import com.tendril.app.data.entry.EntryDao
import com.tendril.app.data.habit.HabitCompletionDao
import com.tendril.app.data.checkin.CheckInDao
import com.tendril.app.data.track.TimeLogDao
import com.tendril.app.data.habit.HabitDao
import com.tendril.app.data.page.PageDao
import com.tendril.app.data.purge.PurgedKind
import com.tendril.app.data.reminder.ReminderDao
import com.tendril.app.data.purge.PurgedRecord
import com.tendril.app.domain.PurgeRegistry
import com.tendril.app.domain.ViewLockState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.spec.SecretKeySpec

private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
private const val MANIFEST_NAME = "manifest.json"
private const val FILE_ENTRIES_ACTIVE = "entries_active.json"
private const val FILE_ENTRIES_ARCHIVED = "entries_archived.json"
private const val FILE_HABITS = "habits.json"
private const val FILE_REMINDERS = "reminders.json"
private const val FILE_ENTRY_COMPLETIONS = "entry_completions.json"
private const val FILE_HABIT_COMPLETIONS = "habit_completions.json"
private const val FILE_CHECK_INS = "check_ins.json"
private const val FILE_TIME_LOGS = "time_logs.json"
private const val FILE_RELATIONS = "page_relations.json"
private const val FILE_PURGED = "purged_records.json"
private const val PAGES_DIR_PREFIX = "pages/"

/** §9.4 / S4 — the archive's image channel. Deliberately the same directory name the sync
 * folder uses: it is the same channel carrying the same `<block uid>.<extension>` names, and a
 * second name for it would suggest the two were different things. */
private const val IMAGES_DIR_PREFIX = "images/"

/** Ceiling on total inflated bytes from an imported archive — see [PortableArchive.readZipEntries]. */
private const val MAX_ARCHIVE_BYTES = 128L * 1024 * 1024

/**
 * §3.1.2 — the refusal text for a write blocked by View-Only. Names the toggle *and* where it
 * lives, because Settings is the one place the eye in the Pages toolbar isn't on screen: someone
 * who forgot it was on would otherwise read "nothing happened" as "the file is broken" and go
 * hunting for a second backup. Both messages end the same way as this class's other refusals so
 * the reassurance is identical wherever it comes from.
 */
private const val VIEW_ONLY_IMPORT_REFUSAL =
    "View-Only is on, so nothing can be imported into this device. Turn it off with the eye in " +
        "the Pages toolbar, then import again — nothing has been changed."
private const val VIEW_ONLY_RESTORE_REFUSAL =
    "View-Only is on, so this backup can't be restored — restoring erases and replaces everything " +
        "on this device. Turn off View-Only with the eye in the Pages toolbar to restore — nothing " +
        "has been changed."

/**
 * §9.4.1 — a one-off, user-initiated, shareable `.tendril` package. Distinct from
 * [com.tendril.app.sync.SnapshotSyncOrchestrator]'s continuous background sync feed: same per-domain JSON shapes,
 * packaged as a zip instead of loose files in a Syncthing-watched folder.
 *
 * Full-app export/import only for now — selective per-Page/Database export (§9.4.1's
 * "Save as template"-adjacent picker) is a separate, not-yet-built feature; the per-Page
 * snapshot files it would pick from already exist (`pages/<uid>.json`, one per
 * [PageSnapshotRecord], via [PagesSyncEngine]) even though this class still always packages
 * every one of them.
 */
class PortableArchive(
    private val context: Context,
    private val entryDao: EntryDao,
    private val habitDao: HabitDao,
    private val pageDao: PageDao,
    private val reminderDao: ReminderDao,
    private val entryCompletionDao: EntryCompletionDao,
    private val habitCompletionDao: HabitCompletionDao,
    private val checkInDao: CheckInDao,
    private val timeLogDao: TimeLogDao,
    private val purgeRegistry: PurgeRegistry,
    private val pagesSyncEngine: PagesSyncEngine,
    /**
     * §9.4 / S4 — this device's own image storage, both the source of what an export packages
     * and the destination of what an import unpacks.
     *
     * Required rather than defaulted, unlike [viewLockState] below. That one is a guard a fixture
     * can reasonably not care about; this one is where half the content goes, and a construction
     * site that forgot it would export pages whose pictures were simply absent — a silent,
     * per-image data loss that no test asserting on entries or habits would notice. Making the
     * compiler name every call site is the cheaper way to be sure.
     */
    private val localImages: LocalImageStore,
    /**
     * §9.4.2's passphrase, read fresh at each export/import rather than passed in per call. A
     * constructor dependency on purpose: this whole gap existed because encryption was
     * something a call site had to remember, and no call site did — there is nothing to forget
     * if the class fetches it itself. A supplier rather than the [SecretStore] itself so this
     * doesn't reach for Android Keystore, which a JVM test has no access to.
     */
    private val passphrase: () -> String?,
    /**
     * §9.7's invariant, for the two write paths that reach Room without a ViewModel: **every
     * write that moves when something is next due re-arms it.** Both entry points below rewrite
     * entries, habits and reminders wholesale and neither armed anything, so a Restore left every
     * restored reminder unarmed until the next `MainActivity.onCreate` — and `configChanges` on
     * that Activity swallows a rotation, so the app could sit open on the restored screen for
     * hours with nothing scheduled. The person had just lost their data; that is the worst
     * available moment for a reminder to silently not fire.
     *
     * Required rather than defaulted, for [passphrase]'s reason one floor down: that gap existed
     * because encryption was something a call site had to remember and no call site did. A
     * defaulted no-op here would be the same bug wearing the same disguise — a construction site
     * that forgot it would restore perfectly and arm nothing, and no test asserting on rows would
     * notice. Making the compiler name every call site is the cheaper way to be sure.
     *
     * A supplier rather than the scheduler itself because the production implementation is
     * `reconcileAlarms`, which reaches for [AppContainer] and the Provider sweep — neither of
     * which a JVM test can have. It is idempotent by construction (it already runs on every app
     * open as well as on boot), so calling it once more at the end of an import costs a sweep and
     * risks nothing.
     */
    private val rearmAlarms: suspend (changedEntryIds: Collection<Long>) -> Unit,
    /**
     * §3.1.2's View-Only toggle, as the user decided it: the lock is absolute and it covers
     * Settings. [importAdditive] and [restoreFromBackup] are the largest create and destroy
     * operations in the app, so the refusal lives down here beside the work rather than only on
     * the two buttons — any future caller inherits it. [export] is deliberately *not* gated: it
     * reads and writes nothing local, and a lock that stopped someone taking a backup would be
     * the toggle working against the data it exists to protect.
     *
     * Nullable for the test harness only. `PortableArchiveTest` and `UnknownEnumQuarantineTest`
     * construct this class to exercise decode and merge behaviour that has nothing to do with
     * View-Only, and a lock they would never toggle is noise in those fixtures. The running app
     * always supplies the real one: `AppContainer` declares the shared [ViewLockState] just above
     * `portableArchive` and passes it in, so both layers are live — the affordance guard in
     * `SettingsScreen`'s `PortableBackupSection` and its Notion sibling (both reading that same
     * single global flag through `LocalViewOnly`), and this one behind them. Read the default as
     * "a fixture didn't need a lock", never as "production is unprotected".
     */
    private val viewLockState: ViewLockState? = null,
) {
    /**
     * The established one-gate-per-write-surface idiom (see `PageDetailViewModel.viewOnlyLocked`
     * and `PageDatabaseViewModel.locked`), called at the top of every mutating entry point here.
     */
    private fun locked(): Boolean = viewLockState?.viewOnly?.value == true

    /** Null when §9.4.2's toggle is off — "off" is simply "no passphrase set". */
    private fun keyOrNull(): SecretKeySpec? = passphrase()?.let(SnapshotEncryption::deriveKey)

    suspend fun export(destination: Uri): ExportResult = withContext(Dispatchers.IO) {
        val allEntries = entryDao.getAll()
        val idToUid = allEntries.associate { it.id to it.uid }
        val rowIdToUid = pageDao.getAll().associate { it.id to it.uid }
        val allHabits = habitDao.getAll()
        val allReminders = reminderDao.getAll()
        val allCompletions = entryCompletionDao.getAll()
        val allHabitCompletions = habitCompletionDao.getAll()
        val allCheckIns = checkInDao.getAll()
        val allTimeLogs = timeLogDao.getAll()
        val habitIdToUid = allHabits.associate { it.id to it.uid }
        val (active, archived) = allEntries.partition { it.isActive() }
        val pageRecords = pagesSyncEngine.exportPages()
        val relations = pagesSyncEngine.exportRelations()
        val purged = purgeRegistry.all()
        // §9.4 / S4 — names only. The bytes are read one at a time during the write below, so a
        // photo library is never assembled in memory on the way out; an export's size limit is the
        // person's storage, not this process's heap. The cost is that `includedFiles` can name a
        // picture whose local file vanished between here and the write — acceptable because that
        // list is documentation of what the package holds and nothing branches on it.
        val imagesToPublish = pagesSyncEngine.localImagesToPublish()

        // §9.4.2 — "a `.tendril` export carries the same at-rest protection as continuous
        // sync, not a separate case to design." It didn't: this class never referenced
        // SnapshotEncryption at all, so with the toggle on, an export was the plaintext way
        // around it, carrying exactly the medical and financial data §9.4.2 names as its
        // reason for existing.
        val key = keyOrNull()

        val manifest = TendrilManifest(
            appVersion = "0.1.0",
            exportedAtEpochMillis = Instant.now().toEpochMilli(),
            kind = "full",
            includedFiles = listOf(
                FILE_ENTRIES_ACTIVE, FILE_ENTRIES_ARCHIVED, FILE_HABITS,
                FILE_REMINDERS, FILE_ENTRY_COMPLETIONS, FILE_HABIT_COMPLETIONS, FILE_CHECK_INS, FILE_TIME_LOGS, FILE_RELATIONS, FILE_PURGED,
            ) +
                pageRecords.map { "$PAGES_DIR_PREFIX${it.uid}.json" } +
                imagesToPublish.map { (name, _) -> "$IMAGES_DIR_PREFIX$name" },
            encrypted = key != null,
        )

        // Throw rather than no-op if the provider won't give us a stream: an export that
        // silently writes nothing and reports success is how someone discovers their backup
        // is empty at the worst possible moment.
        val out = context.contentResolver.openOutputStream(destination)
            ?: error("Couldn't open the chosen file for writing — nothing was exported.")
        out.use { stream ->
            ZipOutputStream(stream).use { zip ->
                // The manifest alone stays plaintext — see TendrilManifest.encrypted for why.
                zip.writeEntry(MANIFEST_NAME, json.encodeToString(manifest), key = null)
                zip.writeEntry(FILE_ENTRIES_ACTIVE, json.encodeToString(active.map { it.toSnapshot(idToUid, rowIdToUid) }), key)
                zip.writeEntry(FILE_ENTRIES_ARCHIVED, json.encodeToString(archived.map { it.toSnapshot(idToUid, rowIdToUid) }), key)
                zip.writeEntry(FILE_HABITS, json.encodeToString(allHabits.map { it.toSnapshot() }), key)
                // S2. Both name their Entry by uid, so both are dropped rather than exported
                // if that Entry is gone — an archive is a snapshot of consistent state, and a
                // reminder pointing at nothing would import as an alarm with no task.
                zip.writeEntry(
                    FILE_REMINDERS,
                    json.encodeToString(allReminders.mapNotNull { r -> idToUid[r.entryId]?.let { r.toSnapshot(it) } }),
                    key,
                )
                zip.writeEntry(
                    FILE_ENTRY_COMPLETIONS,
                    json.encodeToString(allCompletions.mapNotNull { c -> idToUid[c.entryId]?.let { c.toSnapshot(it) } }),
                    key,
                )
                // §0.6.6 — tombstones included, for the reason the sync folder includes them.
                zip.writeEntry(
                    FILE_HABIT_COMPLETIONS,
                    json.encodeToString(allHabitCompletions.mapNotNull { c -> habitIdToUid[c.habitId]?.let { c.toSnapshot(it) } }),
                    key,
                )
                zip.writeEntry(FILE_CHECK_INS, json.encodeToString(allCheckIns.map { it.toSnapshot() }), key)
                // §0.6.5 — every log, closed or deleted or not, on the sync folder's terms.
                zip.writeEntry(
                    FILE_TIME_LOGS,
                    json.encodeToString(
                        allTimeLogs.mapNotNull { log ->
                            val entryUid = log.entryId?.let(idToUid::get)
                            val habitUid = log.habitId?.let(habitIdToUid::get)
                            if (entryUid == null && habitUid == null) null else log.toSnapshot(entryUid, habitUid)
                        },
                    ),
                    key,
                )
                zip.writeEntry(FILE_RELATIONS, json.encodeToString(relations), key)
                // §5.5.1.1 — without these an export would quietly resurrect everything the
                // person had deleted forever the moment it was imported anywhere. Encrypted
                // with the rest: a tombstone names a uid the person chose to destroy.
                zip.writeEntry(
                    FILE_PURGED,
                    json.encodeToString(purged.map { PurgedRecordSnapshot(it.kind.name, it.uid, it.purgedAt.toEpochMilli()) }),
                    key,
                )
                for (record in pageRecords) {
                    zip.writeEntry("$PAGES_DIR_PREFIX${record.uid}.json", json.encodeToString(record), key)
                }
                // §9.4 / S4 — the image bytes, after the records that name them, and encrypted with
                // everything else. Worth stating plainly because the two halves of S4 now differ:
                // an exported picture *does* get §9.4.2's protection, while its copy in the sync
                // folder does not yet. That asymmetry is recorded in `docs/scope-decisions.md`,
                // and it falls out of this class encrypting per entry rather than per file type.
                //
                // A picture whose local file has gone is skipped, not fatal: `Block.imagePath`
                // outlives the file it names (see [LocalImageStore.read]), and one unreadable
                // image is no reason to fail a backup of everything else.
                for ((name, localPath) in imagesToPublish) {
                    val bytes = localImages.read(localPath) ?: continue
                    zip.writeEntry("$IMAGES_DIR_PREFIX$name", bytes, key)
                }
            }
        }
        ExportResult(encrypted = key != null)
    }

    /** Always additive — reuses the exact per-record last-write-wins merge rule §9.4
     * already defines, never a wholesale replace (§9.4.1's fix for the "one import
     * silently overwrites the other person's data" risk).
     *
     * A record this build can't decode is quarantined rather than fatal (see [applyEntries]):
     * import writes nothing over the local copy of anything, so one unreadable record has no
     * reason to cost the person the rest of the file. The count comes back in [ImportResult] —
     * quarantine is reported, never silent, and that includes the pages [PagesSyncEngine.mergePages]
     * quarantined on its own account. Dropping its outcome on the floor was the same silence in a
     * different place: the import reported "everything landed" while a page from a newer build had
     * not. */
    suspend fun importAdditive(source: Uri): ImportResult = withContext(Dispatchers.IO) {
        check(!locked()) { VIEW_ONLY_IMPORT_REFUSAL }
        val archive = readZipEntries(source).readable()
        val contents = archive.texts
        var entriesFound = 0
        var habitsFound = 0
        var quarantined = 0
        val pageRecords = decodePages(contents)
        // Tombstones first and applied before any record, exactly as the folder sync does: an
        // archive is just another source of the same two facts, and the later of "edited at" and
        // "purged at" wins either way. This replaces an earlier special case that lifted every
        // tombstone an import touched — that existed only because tombstones were local and
        // untimestamped, with no principled way to compare them against a record. Now there is.
        purgeRegistry.adopt(decodePurged(contents))
        purgeRegistry.applyToLocalRecords()
        quarantined += pagesSyncEngine.mergePages(pageRecords).quarantined.size
        // After the pages, necessarily — see [restoreImages].
        val imagesRestored = restoreImages(archive.images)
        decodeRelations(contents).takeIf { it.isNotEmpty() }?.let { pagesSyncEngine.mergeRelations(it) }
        // Audit 5.2 — every entry this import inserts or overwrites, for the re-arm below.
        val changedEntryIds = mutableSetOf<Long>()
        contents[FILE_ENTRIES_ACTIVE]?.let { quarantined += applyEntries(decodeEntries(it), changedEntryIds); entriesFound++ }
        contents[FILE_ENTRIES_ARCHIVED]?.let { quarantined += applyEntries(decodeEntries(it), changedEntryIds); entriesFound++ }
        contents[FILE_HABITS]?.let { quarantined += applyHabits(decodeHabits(it)); habitsFound++ }
        // After the entries, necessarily: both resolve `entryUid` against rows applyEntries
        // may only just have inserted.
        quarantined += applyReminders(decodeReminders(contents[FILE_REMINDERS]))
        quarantined += applyCompletions(decodeCompletions(contents[FILE_ENTRY_COMPLETIONS]))
        quarantined += applyHabitCompletions(decodeHabitCompletions(contents[FILE_HABIT_COMPLETIONS]))
        quarantined += applyCheckIns(decodeCheckIns(contents[FILE_CHECK_INS]))
        quarantined += applyTimeLogs(decodeTimeLogs(contents[FILE_TIME_LOGS]))
        // §9.7 — see [rearmAlarms]. Last, after every apply* above: the sweep arms from the rows
        // as they now stand, so anything earlier would arm the state this import is replacing.
        rearmAlarms(changedEntryIds)
        ImportResult(
            hadManifest = contents.containsKey(MANIFEST_NAME),
            imagesRestored = imagesRestored,
            entryFilesFound = entriesFound,
            habitFilesFound = habitsFound,
            quarantinedRecords = quarantined,
        )
    }

    /**
     * Deliberately harder-to-reach, full wipe-and-replace (§9.4.1) — never called from the
     * everyday Import path. Caller is responsible for the explicit "this erases current
     * data" confirmation before invoking this.
     *
     * The whole archive is decoded *before* anything local is deleted. Wiping first and
     * discovering afterwards that nothing parsed — a truncated download, another app's zip,
     * a future schema this build can't read — used to leave an empty database and no way
     * back, because every decode below is best-effort and simply yields nothing on failure.
     * An archive carrying no readable records now throws and changes nothing.
     *
     * "Decoded" means all the way to entities, not merely to JSON. Every enum in these shapes
     * travels as a plain String (that is what makes adding an enum member a zero-schema-change
     * edit, and what lets a newer build write values this one has never heard of), so an archive
     * from a newer build parses perfectly, passes the readability check below, and only throws
     * later inside `toEntity` — by which point `entryDao.deleteAll()`, `habitDao.deleteAll()` and
     * `purgeRegistry.clearAll()` have all already run, with no transaction to roll them back. The
     * device would be left empty *and* unrestored, which is the exact outcome the decode-first
     * ordering exists to prevent. [requireEveryRecordDecodes] closes that by mapping every record
     * to its entity while the local data is still there to lose.
     */
    // `: Unit` spelled out because the apply* helpers now return a quarantine count that only
    // [importAdditive] has any use for; without it this function's inferred type would follow
    // whatever the last one happens to return.
    suspend fun restoreFromBackup(source: Uri): Unit = withContext(Dispatchers.IO) {
        check(!locked()) { VIEW_ONLY_RESTORE_REFUSAL }
        // Before the decode-then-wipe sequence below, and so before anything is deleted.
        val archive = readZipEntries(source).readable()
        val contents = archive.texts

        val pageRecords = decodePages(contents)
        val relations = decodeRelations(contents)
        val activeEntries = decodeEntries(contents[FILE_ENTRIES_ACTIVE])
        val archivedEntries = decodeEntries(contents[FILE_ENTRIES_ARCHIVED])
        val habits = decodeHabits(contents[FILE_HABITS])
        val reminders = decodeReminders(contents[FILE_REMINDERS])
        val completions = decodeCompletions(contents[FILE_ENTRY_COMPLETIONS])
        val habitCompletions = decodeHabitCompletions(contents[FILE_HABIT_COMPLETIONS])
        val checkIns = decodeCheckIns(contents[FILE_CHECK_INS])
        val timeLogs = decodeTimeLogs(contents[FILE_TIME_LOGS])
        val purged = decodePurged(contents)

        require(
            pageRecords.isNotEmpty() || relations.isNotEmpty() || activeEntries.isNotEmpty() ||
                archivedEntries.isNotEmpty() || habits.isNotEmpty()
        ) { "This file doesn't contain any readable Tendril data — nothing was changed." }

        // The last thing that happens before the first delete: prove every record maps to an
        // entity, not just to JSON. Pages are asked the same question here rather than left to
        // mergePages' own quarantine — see the note in [requireEveryRecordDecodes] for why
        // quarantine is the wrong answer on this one path.
        requireEveryRecordDecodes(
            activeEntries + archivedEntries,
            habits,
            pagesSyncEngine.undecodablePages(pageRecords),
        )

        entryDao.deleteAll()
        habitDao.deleteAll()
        // Explicitly, not by cascade — see [ReminderDao.deleteAll]. `entry_completions` has
        // no foreign key at all, so nothing would clear it otherwise.
        reminderDao.deleteAll()
        entryCompletionDao.deleteAll()
        habitCompletionDao.deleteAll()
        checkInDao.deleteAll()
        timeLogDao.deleteAll()
        // §5.5.1.1 — Restore is "become exactly what this archive says", so this device's own
        // purge history is discarded and the archive's adopted in its place. Keeping the local
        // tombstones would silently drop records the archive still holds.
        purgeRegistry.clearAll()
        purgeRegistry.adopt(purged)
        // Pages are not bulk-wiped above, so an adopted tombstone still has local rows to act on.
        purgeRegistry.applyToLocalRecords()
        // Pages have no bulk wipe here (matching Entry/Habit's own restore step, one DAO
        // call each) — Restore only ever runs against a fresh/emptied install in practice,
        // so mergePages' whole-record LWW already behaves like a replace in that case.
        pagesSyncEngine.mergePages(pageRecords)
        restoreImages(archive.images)
        if (relations.isNotEmpty()) pagesSyncEngine.mergeRelations(relations)
        applyEntries(activeEntries, mutableSetOf())
        applyEntries(archivedEntries, mutableSetOf())
        applyHabits(habits)
        applyReminders(reminders)
        applyCompletions(completions)
        applyHabitCompletions(habitCompletions)
        applyCheckIns(checkIns)
        applyTimeLogs(timeLogs)
        // §9.7 — see [rearmAlarms]. The wipe above cancelled nothing and the applies armed
        // nothing, so without this the device holds a full set of restored reminders and no
        // alarm for any of them until the next cold start. No changed ids: after the wipe every
        // restored row is new, so the sweep reaches each one that can ring, and a wiped row's
        // leftover alarm finds no entry when it fires (AUTOINCREMENT never reuses an id).
        rearmAlarms(emptyList())
    }

    // Decode and apply are split so [restoreFromBackup] can prove an archive is readable
    // before it deletes anything. Decoding stays best-effort per file — a corrupt habits.json
    // shouldn't block an otherwise-good entries import — with the all-empty case caught above.

    /** Pages are applied first — Entry's `sourceRowId` resolution needs every Row's local id
     * to already exist. */
    private fun decodePages(contents: Map<String, String>): List<PageSnapshotRecord> =
        contents.entries
            .filter { it.key.startsWith(PAGES_DIR_PREFIX) && it.key.endsWith(".json") }
            .mapNotNull { runCatching { json.decodeFromString<PageSnapshotRecord>(it.value) }.getOrNull() }

    private fun decodeRelations(contents: Map<String, String>): List<PageRelationSnapshotRecord> {
        val content = contents[FILE_RELATIONS]?.takeIf { it.isNotBlank() } ?: return emptyList()
        return runCatching { json.decodeFromString<List<PageRelationSnapshotRecord>>(content) }.getOrNull() ?: emptyList()
    }

    /** An unrecognised kind is skipped rather than failing the file — a newer build may purge
     * things this one has no concept of. */
    private fun decodePurged(contents: Map<String, String>): List<PurgedRecord> {
        val content = contents[FILE_PURGED]?.takeIf { it.isNotBlank() } ?: return emptyList()
        val records = runCatching { json.decodeFromString<List<PurgedRecordSnapshot>>(content) }.getOrNull() ?: return emptyList()
        return records.mapNotNull { record ->
            runCatching { PurgedKind.valueOf(record.kind) }.getOrNull()
                ?.let { PurgedRecord(it, record.uid, Instant.ofEpochMilli(record.purgedAt)) }
        }
    }

    private fun decodeReminders(content: String?): List<ReminderSnapshotRecord> {
        if (content.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<ReminderSnapshotRecord>>(content) }.getOrNull()
            ?: emptyList()
    }

    private fun decodeCompletions(content: String?): List<EntryCompletionSnapshotRecord> {
        if (content.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<EntryCompletionSnapshotRecord>>(content) }.getOrNull()
            ?: emptyList()
    }

    private fun decodeTimeLogs(content: String?): List<TimeLogSnapshotRecord> {
        if (content.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<TimeLogSnapshotRecord>>(content) }.getOrNull() ?: emptyList()
    }

    private fun decodeCheckIns(content: String?): List<CheckInSnapshotRecord> {
        if (content.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<CheckInSnapshotRecord>>(content) }.getOrNull() ?: emptyList()
    }

    private fun decodeHabitCompletions(content: String?): List<HabitCompletionSnapshotRecord> {
        if (content.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<HabitCompletionSnapshotRecord>>(content) }.getOrNull()
            ?: emptyList()
    }

    private fun decodeEntries(content: String?): List<EntrySnapshotRecord> {
        if (content.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<EntrySnapshotRecord>>(content) }.getOrNull() ?: emptyList()
    }

    private fun decodeHabits(content: String?): List<HabitSnapshotRecord> {
        if (content.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<HabitSnapshotRecord>>(content) }.getOrNull() ?: emptyList()
    }

    /**
     * §9.4 / S4 — writes the archive's images into this device's own storage and points the
     * matching blocks at them. Returns how many landed.
     *
     * Runs *after* the pages are merged, on both paths and necessarily:
     * [PagesSyncEngine.imagesToFetch] answers from the blocks that exist here, so a block that
     * arrived in this same archive has to be in the database before it can be asked about.
     *
     * That is the same call the sync folder's fetch uses, handed the archive's names instead of
     * the folder's. An archive is another source of the same two facts, and nothing about which
     * image belongs to which block changes with the container it travelled in — so the rule for
     * deciding lives in one place and this supplies the listing.
     *
     * Two kinds of skip, both deliberate. A name for a block this device does not have belongs to
     * a page the import quarantined or never carried, and storing it would leave bytes nothing can
     * reach. A block that already has a local copy keeps it: an import is additive (§9.4.1), and
     * overwriting this device's picture with an older one from a file is not additive.
     */
    private suspend fun restoreImages(images: Map<String, ByteArray>): Int {
        if (images.isEmpty()) return 0
        var restored = 0
        for ((blockUid, name) in pagesSyncEngine.imagesToFetch(images.keys.toList())) {
            val bytes = images[name] ?: continue
            // Best-effort per image, matching the folder fetch: a name that sanitises away to
            // nothing, or a write that fails, costs that one picture and not the import.
            val localPath = runCatching { localImages.write(name, bytes) }.getOrNull() ?: continue
            pagesSyncEngine.attachLocalImage(blockUid, localPath)
            restored++
        }
        return restored
    }

    /**
     * Restore's pre-delete proof, and the reason the mapped entities here are thrown away: what is
     * kept is not the objects but the *fact* that every one of them can be built. [applyEntries]
     * re-maps against the uid→id maps as they stand after the wipe (a restored Entry's
     * `originalEntryUid`/`sourceRowUid` resolve against rows that only exist once this pass has
     * inserted them), so reusing entities mapped against the pre-wipe maps would silently rewire
     * those two links. Mapping twice costs one pass over records that are about to be inserted
     * anyway; getting the links wrong costs data.
     *
     * Refuse rather than quarantine, unlike [importAdditive]: restore's contract is "become
     * exactly what this archive says", so partially becoming it — some records applied, others
     * skipped, and the local copy of all of them already deleted — is worse than not starting.
     * Import can quarantine precisely because it deletes nothing.
     *
     * Pages are covered too, via [PagesSyncEngine.undecodablePages] — the check [PagesSyncEngine]
     * offers precisely for a caller that must decide before it destroys anything. Leaving them to
     * `mergePages`' own quarantine looked safe because restore bulk-deletes no pages, but the
     * arithmetic is different once the entries and habits either side of them *have* been wiped:
     * the archive is applied, the person is told it succeeded, and the pages this build couldn't
     * read are simply absent. Restore is the one operation that can empty the app, so it refuses
     * whole or does nothing — a half-restore reported as a success is the worst outcome available.
     *
     * That page check is very slightly wider than `mergePages`' own: it reads every record handed
     * to it, where `mergePages` first drops uids that aren't UUIDs and pages the tombstones already
     * cover. Nothing this app writes lands in that gap — a purged page is gone from the database
     * before an export can name it, and every uid generated here is a UUID — so in practice the
     * only file it turns away is one no version of Tendril produced, which is a file to refuse.
     */
    private fun requireEveryRecordDecodes(
        entries: List<EntrySnapshotRecord>,
        habits: List<HabitSnapshotRecord>,
        undecodablePages: List<QuarantinedRecord>,
    ) {
        // The mapper's own exception message names the offending field and value ("entry kind
        // \"MILESTONE\""), which is worth repeating verbatim: "some records can't be read" sends
        // someone looking for a corrupt file, "entry kind MILESTONE" tells them which build wrote
        // it. The empty FK maps are deliberate — an unresolved `originalEntryUid`/`sourceRowUid`
        // maps to null by design rather than throwing, so it cannot turn a readable record
        // unreadable here and then readable again in `applyEntries`.
        val failures = entries.mapNotNull { runCatching { it.toEntity(emptyMap(), emptyMap()) }.exceptionOrNull() } +
            habits.mapNotNull { runCatching { it.toEntity() }.exceptionOrNull() }
        // Page reasons join the same list because they read the same way — the engine's `detail`
        // is already the quoted `label "VALUE"` shape the entity mappers' messages use, so the
        // person sees one sentence naming the offending values whichever half they came from.
        val reasons = (
            failures.mapNotNull { it.message?.takeIf(String::isNotBlank) } + undecodablePages.map { it.detail }
            ).distinct().take(3)
        val unreadable = failures.size + undecodablePages.size
        val detail = if (reasons.isEmpty()) "" else " (${reasons.joinToString("; ")})"
        require(unreadable == 0) {
            "$unreadable record(s) in this backup can't be read by this version of Tendril$detail. " +
                "It was most likely written by a newer one. Nothing has been changed — update Tendril " +
                "and restore again, or use Import, which adds every record it can read and leaves the " +
                "rest alone."
        }
    }

    /**
     * Deliberately the same rules as `SnapshotSyncOrchestrator`'s entry merge, because an
     * archive is just another source of the same records — an Entry purged on this device must
     * not come back through Import when it cannot come back through the folder, and
     * `providerEventId` is per-device (§3.2) so it is kept from the local row rather than
     * adopted from an archive some other device wrote.
     *
     * Returns how many records were quarantined — decoded as JSON but not as an entity, the
     * newer-build case [requireEveryRecordDecodes] describes. Skipping one leaves this device's
     * copy of that record exactly as it was, which is the whole point: a value this build doesn't
     * recognise is a reason to leave a record alone, never a reason to overwrite or drop it. The
     * count is returned rather than logged so the caller can say so out loud (§9.4's rule that a
     * sync problem is surfaced, not swallowed).
     */
    /** @param changed receives the local id of every entry inserted or overwritten (audit 5.2). */
    private suspend fun applyEntries(records: List<EntrySnapshotRecord>, changed: MutableCollection<Long>): Int {
        if (records.isEmpty()) return 0
        val uidToId = entryDao.getAll().associate { it.uid to it.id }.toMutableMap()
        val rowUidToId = pageDao.getAll().associate { it.uid to it.id }
        val tombstones = purgeRegistry.tombstones(PurgedKind.ENTRY)
        var quarantined = 0
        for (record in records) {
            val remoteUpdatedAt = Instant.ofEpochMilli(record.updatedAt)
            if (purgeRegistry.isPurged(PurgedKind.ENTRY, record.uid, remoteUpdatedAt, tombstones)) continue
            // Decoded before anything is written, never after — the ordering that made Restore
            // dangerous is the same ordering that would make one bad record here overwrite a good
            // local row with half of itself. `toEntityOrNull` is the mapper's own quarantining
            // form, shared with the folder-sync merge so both boundaries skip on exactly the
            // same rule.
            val decoded = record.toEntityOrNull(uidToId, rowUidToId)
            if (decoded == null) {
                quarantined++
                continue
            }
            val local = entryDao.getByUid(record.uid)
            if (local == null) {
                uidToId[record.uid] = entryDao.insert(decoded).also { changed += it }
            } else if (remoteUpdatedAt.isAfter(local.updatedAt)) {
                // providerEventId is per-device only (§9.11) and isn't in the snapshot record,
                // so `toEntity` defaults it to null — a whole-row update then wrote that null
                // over this device's real CalendarContract row id, orphaning the mirror and
                // leaving the backfill sweep to insert a duplicate. The two sibling merge
                // paths (SnapshotSyncOrchestrator, GoogleCalendarSyncEngine) already preserve
                // it; this one was the outlier.
                entryDao.update(decoded.copy(id = local.id, providerEventId = local.providerEventId))
                changed += local.id
            }
        }
        return quarantined
    }

    /** Quarantines an undecodable habit for the same reason [applyEntries] does — one record from
     * a newer build costs that record and nothing else. */
    /**
     * S2. Merged on exactly the rule the folder sync uses (`SnapshotSyncOrchestrator`'s
     * `mergeReminderContent`): monotonic, delete wins, no timestamp to compare because a
     * reminder is never edited. The one difference is what happens to a record whose Entry is
     * not here — the folder sync *holds* it, because its next write would otherwise erase the
     * peer's copy, whereas an archive is read-only and never rewritten, so dropping is the only
     * option and costs nothing. It is not counted as quarantined: the record was perfectly
     * readable, it simply has no owner in the dataset being imported into.
     */
    private suspend fun applyReminders(records: List<ReminderSnapshotRecord>): Int {
        if (records.isEmpty()) return 0
        val entryUidToId = entryDao.getAll().associate { it.uid to it.id }
        var quarantined = 0
        for (record in records) {
            val entryId = entryUidToId[record.entryUid] ?: continue
            val decoded = record.toEntityOrNull(entryId)
            if (decoded == null) {
                quarantined++
                continue
            }
            val local = reminderDao.getByUid(record.uid)
            val remoteDeletedAt = decoded.deletedAt
            when {
                local == null -> reminderDao.insert(decoded)
                local.deletedAt == null && remoteDeletedAt != null ->
                    reminderDao.softDelete(local.id, remoteDeletedAt)
                else -> Unit
            }
        }
        return quarantined
    }

    /** S2 — a union by uid, the same as the folder sync's, because the table is append-only.
     * An archive imported twice therefore adds nothing the second time. */
    private suspend fun applyCompletions(records: List<EntryCompletionSnapshotRecord>): Int {
        if (records.isEmpty()) return 0
        val entryUidToId = entryDao.getAll().associate { it.uid to it.id }
        var quarantined = 0
        for (record in records) {
            if (entryCompletionDao.getByUid(record.uid) != null) continue
            val entryId = entryUidToId[record.entryUid] ?: continue
            val decoded = record.toEntityOrNull(entryId)
            if (decoded == null) {
                quarantined++
                continue
            }
            entryCompletionDao.insert(decoded)
        }
        return quarantined
    }

    /** §0.10 item 4 — [applyHabitCompletions]' rule with no owner to resolve. */
    private suspend fun applyCheckIns(records: List<CheckInSnapshotRecord>): Int {
        var quarantined = 0
        for (record in records) {
            val decoded = runCatching { record.toEntity() }.getOrNull()
            if (decoded == null) { quarantined++; continue }
            val local = checkInDao.getByUid(record.uid)
            val remoteDeletedAt = decoded.deletedAt
            when {
                local == null -> checkInDao.insert(decoded)
                local.deletedAt == null && remoteDeletedAt != null -> checkInDao.softDelete(local.id, remoteDeletedAt)
                else -> Unit
            }
        }
        return quarantined
    }

    /** §0.6.6 — [applyReminders]' rule, on a habit: insert once, and a tombstone from either side
     * wins. After [applyHabits], since it resolves `habitUid` against rows that may only just
     * have been inserted. */
    private suspend fun applyHabitCompletions(records: List<HabitCompletionSnapshotRecord>): Int {
        if (records.isEmpty()) return 0
        val habitUidToId = habitDao.getAll().associate { it.uid to it.id }
        var quarantined = 0
        for (record in records) {
            val habitId = habitUidToId[record.habitUid] ?: continue
            val decoded = runCatching { record.toEntity(habitId) }.getOrNull()
            if (decoded == null) {
                quarantined++
                continue
            }
            val local = habitCompletionDao.getByUid(record.uid)
            val remoteDeletedAt = decoded.deletedAt
            when {
                local == null -> habitCompletionDao.insert(decoded)
                local.deletedAt == null && remoteDeletedAt != null ->
                    habitCompletionDao.softDelete(local.id, remoteDeletedAt)
                else -> Unit
            }
        }
        return quarantined
    }

    /** §0.6.5 — the sync folder's rule (`SnapshotSyncOrchestrator.mergeTimeLogContent`): a
     * tombstone from either side wins, else the later write. After both [applyEntries] and
     * [applyHabits], since the owner is either. */
    private suspend fun applyTimeLogs(records: List<TimeLogSnapshotRecord>): Int {
        if (records.isEmpty()) return 0
        val entryUidToId = entryDao.getAll().associate { it.uid to it.id }
        val habitUidToId = habitDao.getAll().associate { it.uid to it.id }
        for (record in records) {
            val entryId = record.entryUid?.let(entryUidToId::get)
            val habitId = record.habitUid?.let(habitUidToId::get)
            if (entryId == null && habitId == null) continue
            val decoded = record.toEntity(entryId, habitId)
            val local = timeLogDao.getByUid(record.uid)
            val remoteDeletedAt = decoded.deletedAt
            when {
                local == null -> timeLogDao.insert(decoded)
                local.deletedAt == null && remoteDeletedAt != null -> timeLogDao.softDelete(local.id, remoteDeletedAt)
                local.deletedAt != null -> Unit
                decoded.updatedAt.isAfter(local.updatedAt) -> timeLogDao.update(decoded.copy(id = local.id))
                else -> Unit
            }
        }
        return 0
    }

    private suspend fun applyHabits(records: List<HabitSnapshotRecord>): Int {
        if (records.isEmpty()) return 0
        var quarantined = 0
        for (record in records) {
            val decoded = record.toEntityOrNull()
            if (decoded == null) {
                quarantined++
                continue
            }
            val local = habitDao.getByUid(record.uid)
            if (local == null) {
                habitDao.insert(decoded)
            } else if (Instant.ofEpochMilli(record.updatedAt).isAfter(local.updatedAt)) {
                habitDao.update(decoded.copy(id = local.id))
            }
        }
        return quarantined
    }

    /**
     * Reads every entry into memory: JSON as text, `images/` as bytes.
     *
     * Two maps rather than one because an archive stopped being all JSON in S4. Decoding image
     * bytes through a String would corrupt them outright, and carrying them as base64 inside the
     * JSON would inflate them by a third and then double that again in UTF-16 — on the one
     * channel here whose entire cost is its size.
     *
     * The source is a file the person picked, which may be corrupt, enormous, or not ours at all,
     * so the running total is capped: a malformed or deliberately-inflated zip fails with a
     * message instead of an OutOfMemoryError partway through.
     *
     * **The cap stays where it is now that images push archives towards it**, which is a real
     * decision and not an oversight. Everything read here is resident at once, so the cap is what
     * keeps that bounded. The obvious alternative — streaming image entries straight to
     * [LocalImageStore] as they are read — would write files to this device before either caller
     * has decided to proceed, and [restoreFromBackup] refuses with the words "nothing has been
     * changed". Making that sentence false to raise a limit nobody has hit is a bad trade; if the
     * limit does start biting, the honest fix is a staging area the refusal path can discard.
     */
    private fun readZipEntries(source: Uri): ArchiveContents {
        val key = keyOrNull()
        val result = mutableMapOf<String, String>()
        val images = mutableMapOf<String, ByteArray>()
        var total = 0L
        var undecryptable = 0
        context.contentResolver.openInputStream(source)?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val bytes = zip.readBytes()
                        total += bytes.size
                        require(total <= MAX_ARCHIVE_BYTES) {
                            "This archive expands to more than ${MAX_ARCHIVE_BYTES / (1024 * 1024)} MB — " +
                                "too large to import. Pictures count towards that, and are usually " +
                                "what takes an archive over it."
                        }
                        // Per-entry, and driven by the magic prefix rather than the manifest's
                        // own flag: a plaintext archive still imports with a passphrase set,
                        // and an encrypted one is recognised even if its manifest is missing.
                        val plain = if (SnapshotEncryption.isEncrypted(bytes)) {
                            key?.let { SnapshotEncryption.decrypt(SnapshotEncryption.stripMagic(bytes), it) }
                        } else {
                            bytes
                        }
                        val name = entry.name
                        when {
                            plain == null -> undecryptable++
                            // §9.4 / S4 — the one channel that is not text, sorted out here because
                            // this is the last point that still holds the bytes. One
                            // `toString(Charsets.UTF_8)` on a PNG is not recoverable afterwards.
                            name.startsWith(IMAGES_DIR_PREFIX) ->
                                images[name.removePrefix(IMAGES_DIR_PREFIX)] = plain
                            else -> result[name] = plain.toString(Charsets.UTF_8)
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        return ArchiveContents(result, images, undecryptable)
    }

    /**
     * Refuses the archive outright when any entry was encrypted and couldn't be read, instead
     * of letting it fall through as "nothing decoded".
     *
     * That distinction is the whole point on the [restoreFromBackup] path: the existing
     * readability guard there would have caught an undecryptable archive too, but only by
     * reporting "this file doesn't contain any readable Tendril data" — which reads as *the
     * file is wrong* when the truth is *the passphrase is*, and invites someone to go looking
     * for another backup instead of fixing the passphrase they still have.
     */
    private fun ArchiveContents.readable(): ArchiveContents {
        require(undecryptable == 0) {
            if (passphrase() == null) {
                "This export is encrypted (§9.4.2). Set your sync passphrase under Settings → " +
                    "Sync folder, then import again — nothing has been changed."
            } else {
                "This export couldn't be decrypted with your current sync passphrase — it was " +
                    "most likely made with a different one. Nothing has been changed."
            }
        }
        return this
    }

    /** Per-entry rather than one encrypted blob wrapping the whole zip: it matches what
     * [SnapshotSyncOrchestrator] already does to the same JSON in the sync folder, and it keeps
     * the archive a real zip whose manifest any reader can still see. */
    private fun ZipOutputStream.writeEntry(name: String, content: String, key: SecretKeySpec?) =
        writeEntry(name, content.toByteArray(Charsets.UTF_8), key)

    /**
     * The bytes half, for the [IMAGES_DIR_PREFIX] entries that are not text.
     *
     * The String overload delegates here rather than the two sharing a copy of the encrypt call:
     * one of them would eventually be changed alone, and the one left behind would be the one
     * that quietly wrote plaintext.
     */
    private fun ZipOutputStream.writeEntry(name: String, bytes: ByteArray, key: SecretKeySpec?) {
        putNextEntry(ZipEntry(name))
        write(if (key == null) bytes else SnapshotEncryption.wrapWithMagic(SnapshotEncryption.encrypt(bytes, key)))
        closeEntry()
    }
}

/** Whether the package that was just written is encrypted (§9.4.2). Reported to the person
 * rather than kept internal: §9.4.1's other use for an export is handing a file to someone
 * else, and an encrypted one is unreadable to them without the passphrase too. Silently
 * producing a file the recipient can't open is exactly the kind of surprise §9.4.2's own
 * "state the concrete consequence in plain words" rule exists to prevent. */
data class ExportResult(val encrypted: Boolean)

data class ImportResult(
    val hadManifest: Boolean,
    val entryFilesFound: Int,
    val habitFilesFound: Int,
    /**
     * Entries, habits and pages that parsed as JSON but that this build couldn't turn into rows —
     * almost always a value written by a newer version (see [PortableArchive.applyEntries], and
     * [PagesSyncEngine.mergePages] for the page half, whose count is folded in here rather than
     * kept in its own field: the person is being told how much of the file did not come across,
     * and that number is not per-table). Reported for the same reason a decryption failure is: an
     * import that quietly landed nine records out of ten looks identical to one that landed all
     * ten, and the person finds out on the day they go looking for the tenth.
     */
    val quarantinedRecords: Int = 0,
    /**
     * §9.4 / S4 — pictures written into this device's storage and linked to their blocks.
     *
     * Reported for a different reason than [quarantinedRecords] is. This is the number that says
     * the *other half* of the archive arrived: pages and their pictures travel as separate
     * entries, so an import can land every block and none of the images (an export made by a
     * build without S4, or one whose image entries were stripped) and look completely successful.
     * A count the person can compare against what they expect is the cheapest way to notice.
     */
    val imagesRestored: Int = 0,
)

/** Decoded archive text plus how many entries were encrypted and unreadable — see
 * [PortableArchive.readable], which is what turns a non-zero count into a refusal. */
private class ArchiveContents(
    val texts: Map<String, String>,
    /** §9.4 / S4 — keyed by folder-side name (`<block uid>.<extension>`), the `images/` prefix
     * already stripped, so this is the same shape [SyncFileStore.listImages] yields. */
    val images: Map<String, ByteArray>,
    val undecryptable: Int,
)

package com.tendril.app.sync

import android.content.Context
import android.net.Uri
import com.tendril.app.data.entry.EntryDao
import com.tendril.app.data.habit.HabitDao
import com.tendril.app.data.page.PageDao
import com.tendril.app.data.purge.PurgedKind
import com.tendril.app.data.purge.PurgedRecord
import com.tendril.app.domain.PurgeRegistry
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
private const val FILE_RELATIONS = "page_relations.json"
private const val FILE_PURGED = "purged_records.json"
private const val PAGES_DIR_PREFIX = "pages/"

/** Ceiling on total inflated bytes from an imported archive — see [PortableArchive.readZipEntries]. */
private const val MAX_ARCHIVE_BYTES = 128L * 1024 * 1024

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
    private val purgeRegistry: PurgeRegistry,
    private val pagesSyncEngine: PagesSyncEngine,
    /**
     * §9.4.2's passphrase, read fresh at each export/import rather than passed in per call. A
     * constructor dependency on purpose: this whole gap existed because encryption was
     * something a call site had to remember, and no call site did — there is nothing to forget
     * if the class fetches it itself. A supplier rather than the [SecretStore] itself so this
     * doesn't reach for Android Keystore, which a JVM test has no access to.
     */
    private val passphrase: () -> String?,
) {
    /** Null when §9.4.2's toggle is off — "off" is simply "no passphrase set". */
    private fun keyOrNull(): SecretKeySpec? = passphrase()?.let(SnapshotEncryption::deriveKey)

    suspend fun export(destination: Uri): ExportResult = withContext(Dispatchers.IO) {
        val allEntries = entryDao.getAll()
        val idToUid = allEntries.associate { it.id to it.uid }
        val rowIdToUid = pageDao.getAll().associate { it.id to it.uid }
        val allHabits = habitDao.getAll()
        val (active, archived) = allEntries.partition { it.isActive() }
        val pageRecords = pagesSyncEngine.exportPages()
        val relations = pagesSyncEngine.exportRelations()
        val purged = purgeRegistry.all()

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
            includedFiles = listOf(FILE_ENTRIES_ACTIVE, FILE_ENTRIES_ARCHIVED, FILE_HABITS, FILE_RELATIONS, FILE_PURGED) +
                pageRecords.map { "$PAGES_DIR_PREFIX${it.uid}.json" },
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
            }
        }
        ExportResult(encrypted = key != null)
    }

    /** Always additive — reuses the exact per-record last-write-wins merge rule §9.4
     * already defines, never a wholesale replace (§9.4.1's fix for the "one import
     * silently overwrites the other person's data" risk). */
    suspend fun importAdditive(source: Uri): ImportResult = withContext(Dispatchers.IO) {
        val contents = readZipEntries(source).readable()
        var entriesFound = 0
        var habitsFound = 0
        val pageRecords = decodePages(contents)
        // Tombstones first and applied before any record, exactly as the folder sync does: an
        // archive is just another source of the same two facts, and the later of "edited at" and
        // "purged at" wins either way. This replaces an earlier special case that lifted every
        // tombstone an import touched — that existed only because tombstones were local and
        // untimestamped, with no principled way to compare them against a record. Now there is.
        purgeRegistry.adopt(decodePurged(contents))
        purgeRegistry.applyToLocalRecords()
        pagesSyncEngine.mergePages(pageRecords)
        decodeRelations(contents).takeIf { it.isNotEmpty() }?.let { pagesSyncEngine.mergeRelations(it) }
        contents[FILE_ENTRIES_ACTIVE]?.let { applyEntries(decodeEntries(it)); entriesFound++ }
        contents[FILE_ENTRIES_ARCHIVED]?.let { applyEntries(decodeEntries(it)); entriesFound++ }
        contents[FILE_HABITS]?.let { applyHabits(decodeHabits(it)); habitsFound++ }
        ImportResult(hadManifest = contents.containsKey(MANIFEST_NAME), entryFilesFound = entriesFound, habitFilesFound = habitsFound)
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
     */
    suspend fun restoreFromBackup(source: Uri) = withContext(Dispatchers.IO) {
        // Before the decode-then-wipe sequence below, and so before anything is deleted.
        val contents = readZipEntries(source).readable()

        val pageRecords = decodePages(contents)
        val relations = decodeRelations(contents)
        val activeEntries = decodeEntries(contents[FILE_ENTRIES_ACTIVE])
        val archivedEntries = decodeEntries(contents[FILE_ENTRIES_ARCHIVED])
        val habits = decodeHabits(contents[FILE_HABITS])
        val purged = decodePurged(contents)

        require(
            pageRecords.isNotEmpty() || relations.isNotEmpty() || activeEntries.isNotEmpty() ||
                archivedEntries.isNotEmpty() || habits.isNotEmpty()
        ) { "This file doesn't contain any readable Tendril data — nothing was changed." }

        entryDao.deleteAll()
        habitDao.deleteAll()
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
        if (relations.isNotEmpty()) pagesSyncEngine.mergeRelations(relations)
        applyEntries(activeEntries)
        applyEntries(archivedEntries)
        applyHabits(habits)
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

    private fun decodeEntries(content: String?): List<EntrySnapshotRecord> {
        if (content.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<EntrySnapshotRecord>>(content) }.getOrNull() ?: emptyList()
    }

    private fun decodeHabits(content: String?): List<HabitSnapshotRecord> {
        if (content.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<HabitSnapshotRecord>>(content) }.getOrNull() ?: emptyList()
    }

    /** Deliberately the same rules as `SnapshotSyncOrchestrator`'s entry merge, because an
     * archive is just another source of the same records — an Entry purged on this device must
     * not come back through Import when it cannot come back through the folder, and
     * `providerEventId` is per-device (§3.2) so it is kept from the local row rather than
     * adopted from an archive some other device wrote. */
    private suspend fun applyEntries(records: List<EntrySnapshotRecord>) {
        if (records.isEmpty()) return
        val uidToId = entryDao.getAll().associate { it.uid to it.id }.toMutableMap()
        val rowUidToId = pageDao.getAll().associate { it.uid to it.id }
        val tombstones = purgeRegistry.tombstones(PurgedKind.ENTRY)
        for (record in records) {
            val remoteUpdatedAt = Instant.ofEpochMilli(record.updatedAt)
            if (purgeRegistry.isPurged(PurgedKind.ENTRY, record.uid, remoteUpdatedAt, tombstones)) continue
            val local = entryDao.getByUid(record.uid)
            if (local == null) {
                uidToId[record.uid] = entryDao.insert(record.toEntity(uidToId, rowUidToId))
            } else if (remoteUpdatedAt.isAfter(local.updatedAt)) {
                // providerEventId is per-device only (§9.11) and isn't in the snapshot record,
                // so `toEntity` defaults it to null — a whole-row update then wrote that null
                // over this device's real CalendarContract row id, orphaning the mirror and
                // leaving the backfill sweep to insert a duplicate. The two sibling merge
                // paths (SnapshotSyncOrchestrator, GoogleCalendarSyncEngine) already preserve
                // it; this one was the outlier.
                entryDao.update(record.toEntity(uidToId, rowUidToId).copy(id = local.id, providerEventId = local.providerEventId))
            }
        }
    }

    private suspend fun applyHabits(records: List<HabitSnapshotRecord>) {
        if (records.isEmpty()) return
        for (record in records) {
            val local = habitDao.getByUid(record.uid)
            if (local == null) {
                habitDao.insert(record.toEntity())
            } else if (Instant.ofEpochMilli(record.updatedAt).isAfter(local.updatedAt)) {
                habitDao.update(record.toEntity().copy(id = local.id))
            }
        }
    }

    /**
     * A `.tendril` archive is all JSON, so entries are held as text — but the source is a
     * file the person picked, which may be corrupt, enormous, or not ours at all. The running
     * total is capped so a malformed or deliberately-inflated zip fails with a message
     * instead of an OutOfMemoryError partway through.
     */
    private fun readZipEntries(source: Uri): ArchiveContents {
        val key = keyOrNull()
        val result = mutableMapOf<String, String>()
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
                            "This archive expands to more than ${MAX_ARCHIVE_BYTES / (1024 * 1024)} MB — too large to import."
                        }
                        // Per-entry, and driven by the magic prefix rather than the manifest's
                        // own flag: a plaintext archive still imports with a passphrase set,
                        // and an encrypted one is recognised even if its manifest is missing.
                        if (SnapshotEncryption.isEncrypted(bytes)) {
                            val plain = key?.let { SnapshotEncryption.decrypt(SnapshotEncryption.stripMagic(bytes), it) }
                            if (plain == null) undecryptable++ else result[entry.name] = plain.toString(Charsets.UTF_8)
                        } else {
                            result[entry.name] = bytes.toString(Charsets.UTF_8)
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        return ArchiveContents(result, undecryptable)
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
    private fun ArchiveContents.readable(): Map<String, String> {
        require(undecryptable == 0) {
            if (passphrase() == null) {
                "This export is encrypted (§9.4.2). Set your sync passphrase under Settings → " +
                    "Sync folder, then import again — nothing has been changed."
            } else {
                "This export couldn't be decrypted with your current sync passphrase — it was " +
                    "most likely made with a different one. Nothing has been changed."
            }
        }
        return texts
    }

    /** Per-entry rather than one encrypted blob wrapping the whole zip: it matches what
     * [SnapshotSyncOrchestrator] already does to the same JSON in the sync folder, and it keeps
     * the archive a real zip whose manifest any reader can still see. */
    private fun ZipOutputStream.writeEntry(name: String, content: String, key: SecretKeySpec?) {
        val bytes = content.toByteArray(Charsets.UTF_8)
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

data class ImportResult(val hadManifest: Boolean, val entryFilesFound: Int, val habitFilesFound: Int)

/** Decoded archive text plus how many entries were encrypted and unreadable — see
 * [PortableArchive.readable], which is what turns a non-zero count into a refusal. */
private class ArchiveContents(val texts: Map<String, String>, val undecryptable: Int)

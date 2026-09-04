package com.tendril.app.sync

import android.content.Context
import android.net.Uri
import com.tendril.app.data.entry.EntryDao
import com.tendril.app.data.habit.HabitDao
import com.tendril.app.data.page.PageDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
private const val MANIFEST_NAME = "manifest.json"
private const val FILE_ENTRIES_ACTIVE = "entries_active.json"
private const val FILE_ENTRIES_ARCHIVED = "entries_archived.json"
private const val FILE_HABITS = "habits.json"
private const val FILE_RELATIONS = "page_relations.json"
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
    private val pagesSyncEngine: PagesSyncEngine,
) {
    suspend fun export(destination: Uri): Unit = withContext(Dispatchers.IO) {
        val allEntries = entryDao.getAll()
        val idToUid = allEntries.associate { it.id to it.uid }
        val rowIdToUid = pageDao.getAll().associate { it.id to it.uid }
        val allHabits = habitDao.getAll()
        val (active, archived) = allEntries.partition { it.isActive() }
        val pageRecords = pagesSyncEngine.exportPages()
        val relations = pagesSyncEngine.exportRelations()

        val manifest = TendrilManifest(
            appVersion = "0.1.0",
            exportedAtEpochMillis = Instant.now().toEpochMilli(),
            kind = "full",
            includedFiles = listOf(FILE_ENTRIES_ACTIVE, FILE_ENTRIES_ARCHIVED, FILE_HABITS, FILE_RELATIONS) + pageRecords.map { "$PAGES_DIR_PREFIX${it.uid}.json" },
        )

        // Throw rather than no-op if the provider won't give us a stream: an export that
        // silently writes nothing and reports success is how someone discovers their backup
        // is empty at the worst possible moment.
        val out = context.contentResolver.openOutputStream(destination)
            ?: error("Couldn't open the chosen file for writing — nothing was exported.")
        out.use { stream ->
            ZipOutputStream(stream).use { zip ->
                zip.writeEntry(MANIFEST_NAME, json.encodeToString(manifest))
                zip.writeEntry(FILE_ENTRIES_ACTIVE, json.encodeToString(active.map { it.toSnapshot(idToUid, rowIdToUid) }))
                zip.writeEntry(FILE_ENTRIES_ARCHIVED, json.encodeToString(archived.map { it.toSnapshot(idToUid, rowIdToUid) }))
                zip.writeEntry(FILE_HABITS, json.encodeToString(allHabits.map { it.toSnapshot() }))
                zip.writeEntry(FILE_RELATIONS, json.encodeToString(relations))
                for (record in pageRecords) {
                    zip.writeEntry("$PAGES_DIR_PREFIX${record.uid}.json", json.encodeToString(record))
                }
            }
        }
    }

    /** Always additive — reuses the exact per-record last-write-wins merge rule §9.4
     * already defines, never a wholesale replace (§9.4.1's fix for the "one import
     * silently overwrites the other person's data" risk). */
    suspend fun importAdditive(source: Uri): ImportResult = withContext(Dispatchers.IO) {
        val contents = readZipEntries(source)
        var entriesFound = 0
        var habitsFound = 0
        pagesSyncEngine.mergePages(decodePages(contents))
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
        val contents = readZipEntries(source)

        val pageRecords = decodePages(contents)
        val relations = decodeRelations(contents)
        val activeEntries = decodeEntries(contents[FILE_ENTRIES_ACTIVE])
        val archivedEntries = decodeEntries(contents[FILE_ENTRIES_ARCHIVED])
        val habits = decodeHabits(contents[FILE_HABITS])

        require(
            pageRecords.isNotEmpty() || relations.isNotEmpty() || activeEntries.isNotEmpty() ||
                archivedEntries.isNotEmpty() || habits.isNotEmpty()
        ) { "This file doesn't contain any readable Tendril data — nothing was changed." }

        entryDao.deleteAll()
        habitDao.deleteAll()
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

    private fun decodeEntries(content: String?): List<EntrySnapshotRecord> {
        if (content.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<EntrySnapshotRecord>>(content) }.getOrNull() ?: emptyList()
    }

    private fun decodeHabits(content: String?): List<HabitSnapshotRecord> {
        if (content.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<HabitSnapshotRecord>>(content) }.getOrNull() ?: emptyList()
    }

    private suspend fun applyEntries(records: List<EntrySnapshotRecord>) {
        if (records.isEmpty()) return
        val uidToId = entryDao.getAll().associate { it.uid to it.id }.toMutableMap()
        val rowUidToId = pageDao.getAll().associate { it.uid to it.id }
        for (record in records) {
            val local = entryDao.getByUid(record.uid)
            if (local == null) {
                uidToId[record.uid] = entryDao.insert(record.toEntity(uidToId, rowUidToId))
            } else if (Instant.ofEpochMilli(record.updatedAt).isAfter(local.updatedAt)) {
                // providerEventId is per-device only (§9.11) and isn't in the snapshot record,
                // so `toEntity` defaults it to null — a whole-row update then wrote that null
                // over this device's real CalendarContract row id, orphaning the mirror and
                // leaving the backfill sweep to insert a duplicate. The two sibling merge
                // paths (SnapshotSyncOrchestrator, GoogleCalendarSyncEngine) already preserve
                // it; this one was the outlier.
                entryDao.update(
                    record.toEntity(uidToId, rowUidToId)
                        .copy(id = local.id, providerEventId = local.providerEventId)
                )
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
    private fun readZipEntries(source: Uri): Map<String, String> {
        val result = mutableMapOf<String, String>()
        var total = 0L
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
                        result[entry.name] = bytes.toString(Charsets.UTF_8)
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        return result
    }

    private fun ZipOutputStream.writeEntry(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }
}

data class ImportResult(val hadManifest: Boolean, val entryFilesFound: Int, val habitFilesFound: Int)

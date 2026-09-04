package com.tendril.app.sync

import com.tendril.app.data.entry.EntryDao
import com.tendril.app.data.habit.HabitDao
import com.tendril.app.data.page.PageDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import javax.crypto.spec.SecretKeySpec

private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

private const val FILE_ENTRIES_ACTIVE = "entries_active.json"
private const val FILE_ENTRIES_ARCHIVED = "entries_archived.json"
private const val FILE_HABITS = "habits.json"
private const val FILE_RELATIONS = "page_relations.json"

/**
 * §9.4 — the snapshot-file sync engine. Tendril never syncs the live Room database; only these
 * per-domain JSON files inside the synced folder, which an external Syncthing-fork app is already
 * syncing on its own. This class only reads/writes those files (via [SyncFileStore], not any
 * platform file API directly) and merges them into/out of Room — it has no idea Syncthing exists.
 *
 * §12.5/Milestone 2 — ported from Android's original `SnapshotSyncManager` into `shared/jvmCommon`:
 * every direct `DocumentFile`/`ContentResolver` call became a [SyncFileStore] call, so this same
 * orchestration logic (decode, merge, re-encode) now runs identically on Android and desktop. The
 * platform-specific pieces — SAF on Android, `java.nio.file` on desktop — live entirely behind
 * [SyncFileStore]'s two implementations.
 *
 * [passphrase] is optional at-rest encryption (§9.4.2, off when null) — the same passphrase must
 * be supplied on every device sharing the folder, or their snapshots become unreadable to each
 * other (a plain-language warning for that lives in each platform's own UI, not here).
 */
class SnapshotSyncOrchestrator(
    private val entryDao: EntryDao,
    private val habitDao: HabitDao,
    private val pageDao: PageDao,
    private val pagesSyncEngine: PagesSyncEngine,
) {
    /** Runs on [Dispatchers.IO]: file writes go through [SyncFileStore], and deriving the
     * encryption key is 210,000 PBKDF2 iterations — hundreds of milliseconds that must not
     * land on a UI thread. Callers can launch this from any scope without wrapping it. */
    suspend fun writeSnapshots(store: SyncFileStore, passphrase: String? = null) = withContext(Dispatchers.IO) {
        val key = passphrase?.let(SnapshotEncryption::deriveKey)
        // Writing without a key is how encryption is turned *off*, and the write is a full
        // overwrite — so with an encrypted folder and no passphrase to hand, it would replace
        // every snapshot with cleartext and report success. That is reachable without an
        // attacker: the desktop passphrase box is session-only and optional-looking, so one
        // "Sync now" before typing it published the whole dataset in the clear, into a folder
        // Syncthing then replicates everywhere. Refuse instead, and let the caller say so.
        if (key == null && hasEncryptedSnapshots(store)) {
            error(
                "This folder's snapshots are encrypted, and no passphrase was given. " +
                    "Nothing was written — entering the passphrase would have replaced them with unencrypted copies."
            )
        }
        val allEntries = entryDao.getAll()
        val idToUid = allEntries.associate { it.id to it.uid }
        val rowIdToUid = pageDao.getAll().associate { it.id to it.uid }
        val allHabits = habitDao.getAll()

        val (active, archived) = allEntries.partition { it.isActive() }
        writeJsonAtomic(store, FILE_ENTRIES_ACTIVE, json.encodeToString(active.map { it.toSnapshot(idToUid, rowIdToUid) }), key)
        writeJsonAtomic(store, FILE_ENTRIES_ARCHIVED, json.encodeToString(archived.map { it.toSnapshot(idToUid, rowIdToUid) }), key)
        writeJsonAtomic(store, FILE_HABITS, json.encodeToString(allHabits.map { it.toSnapshot() }), key)
        writeJsonAtomic(store, FILE_RELATIONS, json.encodeToString(pagesSyncEngine.exportRelations()), key)

        for (record in pagesSyncEngine.exportPages()) {
            writePageJsonAtomic(store, "${record.uid}.json", json.encodeToString(record), key)
        }
    }

    /** Reads whatever snapshot files exist in the folder (any subset — a fresh install has
     * none) plus any Syncthing conflict-marker siblings, and merges every record into Room
     * per-record last-write-wins on `updatedAt` (§9.4). Never deletes a local record just
     * because it's absent from the remote file — merge is additive/non-destructive by
     * design here, matching the spec's Import semantics (§9.4.1); a real hard-delete
     * ("Delete forever") propagates only via the deleted record's own snapshot re-write
     * omitting it, not by this pass inferring absence as intent.
     *
     * Runs on [Dispatchers.IO] for the same reasons as [writeSnapshots]. */
    suspend fun readAndMerge(store: SyncFileStore, passphrase: String? = null) = withContext(Dispatchers.IO) {
        val key = passphrase?.let(SnapshotEncryption::deriveKey)

        // Pages first — Entry's sourceRowId resolution below needs every Row's local id to
        // already exist.
        mergePagesDir(store, key)
        mergeRelationsFile(store, FILE_RELATIONS, key)
        mergeEntryFile(store, FILE_ENTRIES_ACTIVE, key)
        mergeEntryFile(store, FILE_ENTRIES_ARCHIVED, key)
        mergeHabitFile(store, FILE_HABITS, key)

        // Syncthing conflict siblings: <name>.sync-conflict-<date>-<deviceID>.json
        //
        // Deleted only once the content genuinely merged. A conflict file is the copy of the
        // data that *lost* the race, so anything that didn't merge — encrypted with a
        // passphrase this device doesn't have, truncated mid-sync, or a name none of the
        // branches below recognise — stays on disk to be retried or inspected rather than
        // being destroyed unread. `decryptText` returning "" for an undecryptable file is
        // exactly the case this guards: it's indistinguishable from an empty one at the
        // merge layer, and deleting on that basis was silent data loss.
        store.listRoot().forEach { name ->
            if (!name.contains(".sync-conflict-")) return@forEach
            val merged = when {
                name.startsWith("entries_active") || name.startsWith("entries_archived") ->
                    mergeEntryContent(readRootText(store, name, key))
                name.startsWith("habits") -> mergeHabitContent(readRootText(store, name, key))
                name.startsWith("page_relations") -> mergeRelationsContent(readRootText(store, name, key))
                else -> false
            }
            if (merged) store.deleteRoot(name)
        }
        store.listPages().forEach { name ->
            if (!name.contains(".sync-conflict-")) return@forEach
            if (mergePageContent(readPageText(store, name, key))) store.deletePage(name)
        }
    }

    private suspend fun mergePagesDir(store: SyncFileStore, key: SecretKeySpec?) {
        val records = store.listPages()
            .filter { it.endsWith(".json") && !it.contains(".sync-conflict-") }
            .mapNotNull { name -> readPageText(store, name, key).takeIf { it.isNotBlank() }?.let { decodePage(it) } }
        pagesSyncEngine.mergePages(records)
    }

    private fun decodePage(content: String): PageSnapshotRecord? =
        runCatching { json.decodeFromString<PageSnapshotRecord>(content) }.getOrNull()

    /** @return true when [content] actually decoded and was handed to the merge. The conflict
     * sweep in [readAndMerge] uses this to decide whether a file is safe to delete; a false
     * here means "couldn't read this," never "nothing to do." */
    private suspend fun mergePageContent(content: String): Boolean {
        if (content.isBlank()) return false
        val record = decodePage(content) ?: return false
        pagesSyncEngine.mergePages(listOf(record))
        return true
    }

    private suspend fun mergeRelationsFile(store: SyncFileStore, fileName: String, key: SecretKeySpec?) {
        mergeRelationsContent(readRootText(store, fileName, key))
    }

    /** @return true when the content decoded — see [mergePageContent]. */
    private suspend fun mergeRelationsContent(content: String): Boolean {
        if (content.isBlank()) return false
        val records = runCatching { json.decodeFromString<List<PageRelationSnapshotRecord>>(content) }.getOrNull() ?: return false
        pagesSyncEngine.mergeRelations(records)
        return true
    }

    private suspend fun mergeEntryFile(store: SyncFileStore, fileName: String, key: SecretKeySpec?) {
        mergeEntryContent(readRootText(store, fileName, key))
    }

    /** @return true when the content decoded — see [mergePageContent]. */
    private suspend fun mergeEntryContent(content: String): Boolean {
        if (content.isBlank()) return false
        val records = runCatching { json.decodeFromString<List<EntrySnapshotRecord>>(content) }.getOrNull() ?: return false
        // uid → local id, resolved fresh each pass so exception-row backlinks (rare, see
        // SnapshotMappers) can resolve once their target has merged in an earlier record.
        val uidToId = entryDao.getAll().associate { it.uid to it.id }.toMutableMap()
        val rowUidToId = pageDao.getAll().associate { it.uid to it.id }
        for (record in records) {
            val local = entryDao.getByUid(record.uid)
            val remoteUpdatedAt = Instant.ofEpochMilli(record.updatedAt)
            if (local == null) {
                val newId = entryDao.insert(record.toEntity(uidToId, rowUidToId))
                uidToId[record.uid] = newId
            } else if (remoteUpdatedAt.isAfter(local.updatedAt)) {
                // providerEventId is per-device only (§3.2) — never adopted from a remote
                // record, always preserved from whatever this device already had.
                entryDao.update(record.toEntity(uidToId, rowUidToId).copy(id = local.id, providerEventId = local.providerEventId))
            }
            // else: local is newer or equal — keep local, it'll win on the next write pass
        }
        return true
    }

    private suspend fun mergeHabitFile(store: SyncFileStore, fileName: String, key: SecretKeySpec?) {
        mergeHabitContent(readRootText(store, fileName, key))
    }

    /** @return true when the content decoded — see [mergePageContent]. */
    private suspend fun mergeHabitContent(content: String): Boolean {
        if (content.isBlank()) return false
        val records = runCatching { json.decodeFromString<List<HabitSnapshotRecord>>(content) }.getOrNull() ?: return false
        for (record in records) {
            val local = habitDao.getByUid(record.uid)
            val remoteUpdatedAt = Instant.ofEpochMilli(record.updatedAt)
            if (local == null) {
                habitDao.insert(record.toEntity())
            } else if (remoteUpdatedAt.isAfter(local.updatedAt)) {
                habitDao.update(record.toEntity().copy(id = local.id))
            }
        }
        return true
    }

    /** Cheap enough to run before every write: the magic prefix is the first 8 bytes, and only
     * the fixed root files need checking — per-page files are written with the same key as
     * these, never independently. */
    private suspend fun hasEncryptedSnapshots(store: SyncFileStore): Boolean =
        listOf(FILE_ENTRIES_ACTIVE, FILE_ENTRIES_ARCHIVED, FILE_HABITS, FILE_RELATIONS).any { name ->
            store.readRoot(name)?.let(SnapshotEncryption::isEncrypted) == true
        }

    private suspend fun readRootText(store: SyncFileStore, name: String, key: SecretKeySpec?): String =
        decryptText(store.readRoot(name), key)

    private suspend fun readPageText(store: SyncFileStore, name: String, key: SecretKeySpec?): String =
        decryptText(store.readPage(name), key)

    private fun decryptText(bytes: ByteArray?, key: SecretKeySpec?): String {
        if (bytes == null || bytes.isEmpty()) return ""
        if (!SnapshotEncryption.isEncrypted(bytes)) return bytes.toString(Charsets.UTF_8)
        // Encrypted content with no passphrase configured, or the wrong one, decrypts to
        // null — surfaced to the caller as "nothing to merge" rather than a crash; each
        // platform's own UI is where a wrong/missing passphrase gets explained to the person.
        val decrypted = key?.let { SnapshotEncryption.decrypt(SnapshotEncryption.stripMagic(bytes), it) }
        return decrypted?.toString(Charsets.UTF_8) ?: ""
    }

    private suspend fun writeJsonAtomic(store: SyncFileStore, name: String, content: String, key: SecretKeySpec?) {
        store.writeRoot(name, encryptText(content, key))
    }

    private suspend fun writePageJsonAtomic(store: SyncFileStore, name: String, content: String, key: SecretKeySpec?) {
        store.writePage(name, encryptText(content, key))
    }

    private fun encryptText(content: String, key: SecretKeySpec?): ByteArray {
        val bytes = content.toByteArray(Charsets.UTF_8)
        return if (key != null) SnapshotEncryption.wrapWithMagic(SnapshotEncryption.encrypt(bytes, key)) else bytes
    }
}

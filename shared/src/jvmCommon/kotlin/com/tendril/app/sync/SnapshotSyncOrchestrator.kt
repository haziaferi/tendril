package com.tendril.app.sync

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
import javax.crypto.spec.SecretKeySpec
import java.util.Base64

private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

private const val FILE_ENTRIES_ACTIVE = "entries_active.json"
private const val FILE_ENTRIES_ARCHIVED = "entries_archived.json"
private const val FILE_HABITS = "habits.json"
private const val FILE_RELATIONS = "page_relations.json"
private const val FILE_PURGED = "purged_records.json"

/** §9.4.2 — the folder's salt and its "this folder is encrypted" marker. Always plaintext: a
 * device without the key still has to read the salt to derive it. */
private const val FILE_META = "sync_meta.json"

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
    private val purgeRegistry: PurgeRegistry,
) {
    /**
     * One sync: merge in what the folder has, then write back what this device now holds. Both
     * platforms' "Sync now" buttons were sequencing these two by hand, and each passphrase-taking
     * call derives its own key — 210,000 PBKDF2 iterations apiece, paid twice for one button
     * press. Deriving once here halves that and puts the read-before-write ordering in one place.
     *
     * Everything below runs on [Dispatchers.IO] — file access plus that key derivation are
     * hundreds of milliseconds that must not land on a UI thread — so callers can launch this
     * from any scope without wrapping it.
     */
    suspend fun syncNow(store: SyncFileStore, passphrase: String? = null, allowRekey: Boolean = false) {
        // Salt first: the key cannot be derived until the folder has said which salt it uses.
        // Derived once for both halves rather than once each.
        val salt = resolveSalt(store, mint = passphrase != null)
        val key = deriveKey(passphrase, salt)
        mergeWithKey(store, ReadKey(key, isEncryptedFolder(store)))
        writeWithKey(store, key, allowRekey, salt)
    }

    /** The write half on its own, for a caller that only needs to publish. [syncNow] is what a
     * "Sync now" button wants. */
    suspend fun writeSnapshots(store: SyncFileStore, passphrase: String? = null, allowRekey: Boolean = false) {
        val salt = resolveSalt(store, mint = passphrase != null)
        writeWithKey(store, deriveKey(passphrase, salt), allowRekey, salt)
    }

    private suspend fun writeWithKey(
        store: SyncFileStore,
        key: SecretKeySpec?,
        allowRekey: Boolean = false,
        salt: ByteArray? = null,
    ) = withContext(Dispatchers.IO) {
        // The write is a full overwrite, so whatever it cannot read first, it destroys. Two ways
        // in, and neither needs an attacker:
        //
        //  - No key at all is how encryption is turned *off*. Against an encrypted folder that
        //    replaces every snapshot with cleartext and reports success — the desktop passphrase
        //    box is session-only and optional-looking, so one "Sync now" before typing it
        //    published the whole dataset in the clear, into a folder Syncthing then replicates
        //    everywhere.
        //  - A *mistyped* passphrase derives a perfectly valid key, so a null check alone lets it
        //    straight through. `decryptText` then yields "" for every file (it maps a failed
        //    decrypt to "nothing to merge" rather than crashing, which is right for reading and
        //    silent for writing), the merge takes in nothing, and the write re-encrypts the whole
        //    folder under a key nobody knows. That is the worse of the two: cleartext is at least
        //    still readable, and this is not.
        //
        // One rule covers both — never overwrite an encrypted folder this key cannot open. A
        // deliberate re-key is the one case that legitimately wants to, and says so via
        // [allowRekey]; it stays opt-in because the accidental version is indistinguishable from
        // it here, and only the caller knows which one the person meant.
        val encryptedSample = firstEncryptedSnapshot(store)
        if (encryptedSample != null) {
            if (key == null) {
                error(
                    "This folder's snapshots are encrypted, and no passphrase was given. " +
                        "Nothing was written — writing would have replaced them with unencrypted copies."
                )
            }
            if (!allowRekey && !opens(encryptedSample, key)) {
                error(
                    "This folder's snapshots are encrypted, and the passphrase given does not open them. " +
                        "Nothing was written — writing would have re-encrypted every snapshot under a key " +
                        "that reads none of the existing ones, losing them on every device. Check the " +
                        "passphrase; re-key deliberately with allowRekey once the folder has been merged " +
                        "under its current one."
                )
            }
        }
        // Only once the guard above has approved the write. The meta file is the folder's
        // identity; minting one over a folder this key cannot read would claim it wrongly.
        if (key != null && salt != null) persistSalt(store, salt)

        val allEntries = entryDao.getAll()
        val idToUid = allEntries.associate { it.id to it.uid }
        val rowIdToUid = pageDao.getAll().associate { it.id to it.uid }
        val allHabits = habitDao.getAll()

        val (active, archived) = allEntries.partition { it.isActive() }
        writeJsonAtomic(store, FILE_ENTRIES_ACTIVE, json.encodeToString(active.map { it.toSnapshot(idToUid, rowIdToUid) }), key)
        writeJsonAtomic(store, FILE_ENTRIES_ARCHIVED, json.encodeToString(archived.map { it.toSnapshot(idToUid, rowIdToUid) }), key)
        writeJsonAtomic(store, FILE_HABITS, json.encodeToString(allHabits.map { it.toSnapshot() }), key)
        writeJsonAtomic(store, FILE_RELATIONS, json.encodeToString(pagesSyncEngine.exportRelations()), key)
        // §5.5.1.1 — the tombstones travel, which is what makes "Delete forever" mean everywhere
        // rather than only here. Deliberately narrows §9.4's additive-merge rule: absence still
        // never implies deletion, but an explicit tombstone does.
        writeJsonAtomic(store, FILE_PURGED, json.encodeToString(purgeRegistry.all().map { it.toSnapshot() }), key)

        for (record in pagesSyncEngine.exportPages()) {
            writePageJsonAtomic(store, "${record.uid}.json", json.encodeToString(record), key)
        }

        // Clear the snapshot files of pages purged here (§5.5.1). Only those: a file is deleted
        // because this device recorded a deliberate "Delete forever" for that exact uid, never
        // because a file merely looks unfamiliar. Inferring deletion from absence is what the
        // conflict sweep in readAndMerge is careful not to do either, and for the same reason.
        val purgedFiles = pagesSyncEngine.purgedPageFileNames()
        if (purgedFiles.isNotEmpty()) {
            store.listPages().filter { it in purgedFiles }.forEach { store.deletePage(it) }
        }
    }

    /** Reads whatever snapshot files exist in the folder (any subset — a fresh install has
     * none) plus any Syncthing conflict-marker siblings, and merges every record into Room
     * per-record last-write-wins on `updatedAt` (§9.4). Absence still never implies deletion —
     * a record missing from the remote file is treated as "hasn't arrived", not "was deleted",
     * matching the spec's Import semantics (§9.4.1). A real hard-delete propagates instead as
     * an explicit tombstone in `purged_records.json` (§5.5.1.1), which is the one signal that
     * *does* remove local data, and which is compared against the record's own `updatedAt` so
     * a stale delete can't destroy a newer edit.
     *
     * Runs on [Dispatchers.IO] for the same reasons as [writeSnapshots]. */
    suspend fun readAndMerge(store: SyncFileStore, passphrase: String? = null) =
        // mint = false: reading must never write to the folder, and a folder with nothing in it
        // has nothing to decrypt anyway.
        mergeWithKey(store, ReadKey(deriveKey(passphrase, resolveSalt(store, mint = false)), isEncryptedFolder(store)))

    /**
     * What a merge pass needs in order to read a file: the key, and whether the folder is known
     * to be encrypted. The second is a property of the *folder*, not of any one file, which is
     * exactly why it is resolved once per pass and carried rather than re-derived per read.
     */
    private data class ReadKey(val key: SecretKeySpec?, val folderEncrypted: Boolean)

    /** A folder is known to be encrypted if it says so (`sync_meta.json`) or demonstrates it
     * (ciphertext written before that file existed). */
    private suspend fun isEncryptedFolder(store: SyncFileStore): Boolean =
        readMeta(store) != null || firstEncryptedSnapshot(store) != null

    private suspend fun mergeWithKey(store: SyncFileStore, read: ReadKey) = withContext(Dispatchers.IO) {
        // Tombstones before anything else, and applied before any record file is read: a purge
        // arriving from another device has to be in force *before* the record it kills is
        // considered, or the two cross in the same pass and the record wins by accident.
        mergePurgedFile(store, read)
        purgeRegistry.applyToLocalRecords()

        // Pages next — Entry's sourceRowId resolution below needs every Row's local id to
        // already exist.
        mergePagesDir(store, read)
        mergeRelationsFile(store, FILE_RELATIONS, read)
        mergeEntryFile(store, FILE_ENTRIES_ACTIVE, read)
        mergeEntryFile(store, FILE_ENTRIES_ARCHIVED, read)
        mergeHabitFile(store, FILE_HABITS, read)

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
                    mergeEntryContent(readRootText(store, name, read))
                name.startsWith("habits") -> mergeHabitContent(readRootText(store, name, read))
                name.startsWith("page_relations") -> mergeRelationsContent(readRootText(store, name, read))
                name.startsWith("purged_records") -> mergePurgedContent(readRootText(store, name, read))
                else -> false
            }
            if (merged) store.deleteRoot(name)
        }
        store.listPages().forEach { name ->
            if (!name.contains(".sync-conflict-")) return@forEach
            if (mergePageContent(readPageText(store, name, read))) store.deletePage(name)
        }
    }

    private suspend fun mergePagesDir(store: SyncFileStore, read: ReadKey) {
        val records = store.listPages()
            .filter { it.endsWith(".json") && !it.contains(".sync-conflict-") }
            .mapNotNull { name -> readPageText(store, name, read).takeIf { it.isNotBlank() }?.let { decodePage(it) } }
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

    private suspend fun mergePurgedFile(store: SyncFileStore, read: ReadKey) {
        mergePurgedContent(readRootText(store, FILE_PURGED, read))
    }

    /** @return true when the content decoded — see [mergePageContent]. */
    private suspend fun mergePurgedContent(content: String): Boolean {
        if (content.isBlank()) return false
        val records = runCatching { json.decodeFromString<List<PurgedRecordSnapshot>>(content) }.getOrNull() ?: return false
        // An unrecognised kind is skipped rather than failing the whole file: a newer build may
        // purge things this one has no concept of, and the rest of the file is still good.
        purgeRegistry.adopt(records.mapNotNull { it.toEntity() })
        return true
    }

    private fun PurgedRecord.toSnapshot() = PurgedRecordSnapshot(kind.name, uid, purgedAt.toEpochMilli())

    private fun PurgedRecordSnapshot.toEntity(): PurgedRecord? {
        val parsed = runCatching { PurgedKind.valueOf(kind) }.getOrNull() ?: return null
        return PurgedRecord(parsed, uid, Instant.ofEpochMilli(purgedAt))
    }

    private suspend fun mergeRelationsFile(store: SyncFileStore, fileName: String, read: ReadKey) {
        mergeRelationsContent(readRootText(store, fileName, read))
    }

    /** @return true when the content decoded — see [mergePageContent]. */
    private suspend fun mergeRelationsContent(content: String): Boolean {
        if (content.isBlank()) return false
        val records = runCatching { json.decodeFromString<List<PageRelationSnapshotRecord>>(content) }.getOrNull() ?: return false
        pagesSyncEngine.mergeRelations(records)
        return true
    }

    private suspend fun mergeEntryFile(store: SyncFileStore, fileName: String, read: ReadKey) {
        mergeEntryContent(readRootText(store, fileName, read))
    }

    /** @return true when the content decoded — see [mergePageContent]. */
    private suspend fun mergeEntryContent(content: String): Boolean {
        if (content.isBlank()) return false
        val records = runCatching { json.decodeFromString<List<EntrySnapshotRecord>>(content) }.getOrNull() ?: return false
        // uid → local id, resolved fresh each pass so exception-row backlinks (rare, see
        // SnapshotMappers) can resolve once their target has merged in an earlier record.
        val uidToId = entryDao.getAll().associate { it.uid to it.id }.toMutableMap()
        val rowUidToId = pageDao.getAll().associate { it.uid to it.id }
        val tombstones = purgeRegistry.tombstones(PurgedKind.ENTRY)
        for (record in records) {
            val remoteUpdatedAt = Instant.ofEpochMilli(record.updatedAt)
            // §5.5.1.1 — an Entry purged on any device stays purged, unless this record is the
            // newer of the two, in which case the tombstone is superseded and dropped.
            if (purgeRegistry.isPurged(PurgedKind.ENTRY, record.uid, remoteUpdatedAt, tombstones)) continue
            val local = entryDao.getByUid(record.uid)
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

    private suspend fun mergeHabitFile(store: SyncFileStore, fileName: String, read: ReadKey) {
        mergeHabitContent(readRootText(store, fileName, read))
    }

    /** @return true when the content decoded — see [mergePageContent]. */
    private suspend fun mergeHabitContent(content: String): Boolean {
        if (content.isBlank()) return false
        val records = runCatching { json.decodeFromString<List<HabitSnapshotRecord>>(content) }.getOrNull() ?: return false
        val tombstones = purgeRegistry.tombstones(PurgedKind.HABIT)
        for (record in records) {
            val local = habitDao.getByUid(record.uid)
            val remoteUpdatedAt = Instant.ofEpochMilli(record.updatedAt)
            // §5.5.1.1 — same rule the Entry and Page merges follow. Without it a Habit purged
            // on any device walks straight back in from another device's habits.json, which is
            // the resurrection the tombstones exist to stop.
            if (purgeRegistry.isPurged(PurgedKind.HABIT, record.uid, remoteUpdatedAt, tombstones)) continue
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
     * these, never independently.
     *
     * The first root snapshot in the folder that is actually encrypted, or null if none is.
     * Returns the bytes rather than a bare Boolean so the write guard can try a key against real
     * ciphertext, instead of only asking whether ciphertext exists. */
    /**
     * The salt this folder derives keys with, in strict precedence:
     *
     *  1. `sync_meta.json`, once the folder has one -- the normal case.
     *  2. Otherwise, if the folder already holds encrypted snapshots, it predates that file and
     *     was written under [SnapshotEncryption.LEGACY_SALT]. Re-deriving under anything else
     *     would lock the person out of their own data, so compatibility wins outright.
     *  3. Otherwise a fresh random salt -- but only on a write ([mint]). A read must not create
     *     files, and against a folder with nothing in it there is nothing to decrypt anyway.
     */
    private suspend fun resolveSalt(store: SyncFileStore, mint: Boolean): ByteArray {
        readMeta(store)?.let { meta ->
            val decoded = runCatching { Base64.getDecoder().decode(meta.salt) }.getOrNull()
            if (decoded != null && decoded.isNotEmpty()) return decoded
        }
        if (firstEncryptedSnapshot(store) != null) return SnapshotEncryption.LEGACY_SALT
        return if (mint) SnapshotEncryption.randomSalt() else SnapshotEncryption.LEGACY_SALT
    }

    /**
     * The winning `sync_meta.json`, across the file itself and any Syncthing conflict siblings.
     *
     * Two devices that turn encryption on before either has synced both mint a salt, and
     * Syncthing preserves the loser as a sibling rather than merging the two. Earliest
     * `createdAt` wins, ties broken on the salt text, so every device reaches the same answer
     * with nothing to negotiate -- the bootstrap case the audit flagged as the part worth
     * thinking through. The device that loses finds its own snapshots no longer open under the
     * winning salt, and the write guard refuses rather than overwriting them.
     */
    private suspend fun readMeta(store: SyncFileStore): SyncMetaRecord? =
        store.listRoot()
            .filter { it == FILE_META || (it.startsWith("sync_meta.") && it.contains(".sync-conflict-")) }
            .mapNotNull { name -> store.readRoot(name)?.let(::decodeMeta) }
            .minWithOrNull(compareBy<SyncMetaRecord>({ it.createdAt }, { it.salt }))

    private fun decodeMeta(bytes: ByteArray): SyncMetaRecord? {
        if (bytes.isEmpty() || SnapshotEncryption.isEncrypted(bytes)) return null
        return runCatching { json.decodeFromString<SyncMetaRecord>(bytes.toString(Charsets.UTF_8)) }.getOrNull()
    }

    /**
     * Writes the folder's meta file when it is missing or disagrees with the winner, and clears
     * the conflict siblings that have now been adopted. Converging here rather than at read time
     * is what keeps [readAndMerge] free of writes.
     *
     * A legacy folder -- encrypted, no meta -- gets one written holding
     * [SnapshotEncryption.LEGACY_SALT] itself. That changes no key and locks nobody out, and it
     * gives the folder the explicit "this is encrypted" marker it never had.
     */
    private suspend fun persistSalt(store: SyncFileStore, salt: ByteArray) {
        val record = readMeta(store)
            ?: SyncMetaRecord(
                salt = Base64.getEncoder().encodeToString(salt),
                createdAt = Instant.now().toEpochMilli(),
            )
        if (store.readRoot(FILE_META)?.let(::decodeMeta) != record) {
            store.writeRoot(FILE_META, json.encodeToString(record).toByteArray(Charsets.UTF_8))
        }
        store.listRoot()
            .filter { it.startsWith("sync_meta.") && it.contains(".sync-conflict-") }
            .forEach { store.deleteRoot(it) }
    }

    private suspend fun firstEncryptedSnapshot(store: SyncFileStore): ByteArray? =
        listOf(FILE_ENTRIES_ACTIVE, FILE_ENTRIES_ARCHIVED, FILE_HABITS, FILE_RELATIONS)
            .firstNotNullOfOrNull { name -> store.readRoot(name)?.takeIf(SnapshotEncryption::isEncrypted) }

    /** Whether [key] actually decrypts [encrypted] — one AES-GCM open, no key derivation, so this
     * costs nothing next to the PBKDF2 the key already came from. */
    private fun opens(encrypted: ByteArray, key: SecretKeySpec): Boolean =
        SnapshotEncryption.decrypt(SnapshotEncryption.stripMagic(encrypted), key) != null

    private suspend fun readRootText(store: SyncFileStore, name: String, read: ReadKey): String =
        decryptText(store.readRoot(name), read)

    private suspend fun readPageText(store: SyncFileStore, name: String, read: ReadKey): String =
        decryptText(store.readPage(name), read)

    private fun decryptText(bytes: ByteArray?, read: ReadKey): String {
        if (bytes == null || bytes.isEmpty()) return ""
        if (!SnapshotEncryption.isEncrypted(bytes)) {
            // audit 4.1 — in a folder known to be encrypted, an unencrypted file did not come
            // from a device holding the key. Trusting it made AES-GCM's authentication tag worth
            // nothing at the system level: anyone who could write to the synced folder could
            // inject records without knowing the passphrase, simply by not encrypting them.
            //
            // Refused as "couldn't read this" rather than deleted, which is the same treatment
            // an undecryptable file gets, and means the conflict sweep leaves it on disk to be
            // inspected instead of destroying it unread.
            return if (read.folderEncrypted) "" else bytes.toString(Charsets.UTF_8)
        }
        // Encrypted content with no passphrase configured, or the wrong one, decrypts to
        // null — surfaced to the caller as "nothing to merge" rather than a crash; each
        // platform's own UI is where a wrong/missing passphrase gets explained to the person.
        val decrypted = read.key?.let { SnapshotEncryption.decrypt(SnapshotEncryption.stripMagic(bytes), it) }
        return decrypted?.toString(Charsets.UTF_8) ?: ""
    }

    /** 210k PBKDF2 iterations — hundreds of milliseconds, and both "Sync now" buttons call in
     * from a `rememberCoroutineScope()`, which is the Main dispatcher. The merge and write
     * halves each switch to IO on their own, but the derivation happens *before* either, so
     * without this it ran on the UI thread on every press — the one thing this class's own
     * contract promises callers they need not think about. */
    private suspend fun deriveKey(passphrase: String?, salt: ByteArray): SecretKeySpec? =
        passphrase?.let { withContext(Dispatchers.IO) { SnapshotEncryption.deriveKey(it, salt) } }

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

package com.tendril.app.sync

import com.tendril.app.data.completion.EntryCompletion
import com.tendril.app.data.completion.EntryCompletionDao
import com.tendril.app.data.entry.Entry
import com.tendril.app.data.entry.EntryDao
import com.tendril.app.data.enumOrNull
import com.tendril.app.data.habit.Habit
import com.tendril.app.data.habit.HabitDao
import com.tendril.app.data.page.PageDao
import com.tendril.app.data.purge.PurgedKind
import com.tendril.app.data.reminder.Reminder
import com.tendril.app.data.reminder.ReminderDao
import com.tendril.app.data.purge.PurgedRecord
import com.tendril.app.domain.PurgeRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.time.Instant
import javax.crypto.spec.SecretKeySpec
import java.util.Base64
import kotlin.jvm.Volatile

private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

private const val FILE_ENTRIES_ACTIVE = "entries_active.json"
private const val FILE_ENTRIES_ARCHIVED = "entries_archived.json"
private const val FILE_HABITS = "habits.json"
private const val FILE_REMINDERS = "reminders.json"
private const val FILE_ENTRY_COMPLETIONS = "entry_completions.json"
private const val FILE_RELATIONS = "page_relations.json"
private const val FILE_PURGED = "purged_records.json"

/** §9.4.2 — the folder's salt and its "this folder is encrypted" marker. Always plaintext: a
 * device without the key still has to read the salt to derive it. */
private const val FILE_META = "sync_meta.json"

/**
 * The record families a folder-wide file can carry, used for one thing only: pooling suppression
 * across the files that share a family.
 *
 * Only [ENTRY] currently spans two files, and that is exactly why the pooling exists. A record
 * held out of `entries_archived.json` must silence this device's *active* row for the same uid
 * too — the peer's copy says "archived" where this device still says "active", and publishing
 * both would put one uid in two files at once. Pooling by family says that once, instead of
 * leaving each future file to remember it.
 */
private enum class RecordFamily { ENTRY, HABIT, RELATION, PURGE, REMINDER, COMPLETION }

/**
 * **Every folder-wide array file the write pass publishes — the list a new one must join.**
 *
 * §9.4's folder holds two shapes of file. A page is one file per record, so a record this build
 * cannot read is protected by simply not writing its file. Everything else is a single array file
 * per family that *every device overwrites in full from its own rows*, which means a record the
 * merge could not adopt is not merely unpublished by the next write — it is deleted from the
 * folder, for everyone. Quarantine's second half (see [QuarantinedExports]) exists to stop that,
 * and it has now been forgotten three times in a row, once per file added after the first.
 *
 * So the file name is no longer a String a caller may pass to a general-purpose writer. It is a
 * member of this enum, and [SnapshotSyncOrchestrator.publishArrayFile] — the only code that
 * writes a root array file — takes one. Two `when` blocks switch over it exhaustively and neither
 * has an `else`:
 *
 *  - [SnapshotSyncOrchestrator.writeWithKey] must say where the file's *local* records come from;
 *  - [SnapshotSyncOrchestrator.mergeWithKey] must say where the pass *held* the records it could
 *    not read for that same file.
 *
 * Adding a tenth file next year therefore does not compile until its author has answered the
 * suppression question in writing. That is the point: the previous three rounds all shipped
 * green, and the hole was only ever found by a human reading the write pass line by line.
 */
private enum class FolderArrayFile(val fileName: String, val family: RecordFamily) {
    ENTRIES_ACTIVE(FILE_ENTRIES_ACTIVE, RecordFamily.ENTRY),
    ENTRIES_ARCHIVED(FILE_ENTRIES_ARCHIVED, RecordFamily.ENTRY),
    HABITS(FILE_HABITS, RecordFamily.HABIT),
    REMINDERS(FILE_REMINDERS, RecordFamily.REMINDER),
    ENTRY_COMPLETIONS(FILE_ENTRY_COMPLETIONS, RecordFamily.COMPLETION),
    RELATIONS(FILE_RELATIONS, RecordFamily.RELATION),
    PURGED(FILE_PURGED, RecordFamily.PURGE),
}

/**
 * §5.2 — marks a snapshot this app kept a copy of because it was about to be replaced.
 *
 * Two things wear it. A page snapshot that *lost* a last-write-wins race, preserved by
 * [SnapshotSyncOrchestrator.preserveLostPages]; and a folder-wide array file whose outer shape
 * this build could not read, copied aside by
 * [SnapshotSyncOrchestrator.preserveUnreadableArrayFile] before the write pass republishes over
 * it. Both are the same act — "this device is about to overwrite something it did not put here,
 * so it leaves the original where a person can get at it" — and one marker says that once rather
 * than each site inventing its own name for it.
 *
 * Deliberately not Syncthing's own `.sync-conflict-` spelling. Those are Syncthing's to create
 * and this app's conflict sweep already claims them: a file carrying that marker gets merged and
 * then deleted once it has, which is exactly the wrong fate for a copy being preserved. A marker
 * of our own keeps the two apart, and both the pages-dir scan and the sweep skip it — a
 * preserved loser is evidence, not an input.
 */
private const val LOST_MARKER = ".tendril-lost-"

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
/**
 * What [SnapshotSyncOrchestrator.readAndMerge] found in the folder. [undecryptableFiles] counts
 * snapshot files that carried [SnapshotEncryption]'s magic prefix but would not decrypt with the
 * passphrase in hand — a wrong passphrase, or none configured against an encrypted folder.
 *
 * [quarantinedRecords] counts the other way a record can be unreadable: it decrypted and parsed
 * perfectly well, and then carried a value this build has no member for — a page, entry or habit
 * written by a *newer* build. Those are skipped rather than applied, and skipping them silently
 * is the failure that outlives the bug, so they travel out here beside the encryption count and
 * reach the person through the same `lastError` line in Settings.
 */
data class SnapshotMergeResult(
    val undecryptableFiles: Int,
    val quarantinedRecords: List<QuarantinedRecord> = emptyList(),
) {
    /**
     * True when the folder holds encrypted snapshots this device cannot read. Callers **must
     * not** follow such a merge with [SnapshotSyncOrchestrator.writeSnapshots]: nothing was
     * merged in, so the write would replace every readable file with this device's own state,
     * re-encrypted under the wrong key. §9.4.2 promises that losing the passphrase leaves the
     * folder "unreadable" and recoverable by recovering the passphrase — overwriting it makes
     * that false, and it is the *only* copy on a fresh install. The conflict sweep already
     * refuses to delete a file it couldn't decrypt for the same reason; the primary files just
     * had no equivalent guard.
     */
    val passphraseMismatch: Boolean get() = undecryptableFiles > 0
}

/**
 * The quarantine, in one line a person can act on, or null when nothing was quarantined.
 *
 * An extension rather than a member so [SnapshotMergeResult] keeps exactly one accessor for the
 * quarantine itself — the list. Callers that show sync problems (`SyncCoordinator`, the desktop
 * sync button) can put this straight into their existing error line: what it names is not a fault
 * of the folder or the passphrase, but a build difference the person can fix by updating.
 *
 * The sentence no longer promises that "nothing was overwritten", which it did while every
 * quarantine case was a record or a file left strictly alone. One case is not: a folder-wide array
 * file whose outer shape this build cannot read *is* republished over, because refusing to write
 * it silences this device's own rows permanently — and a copy of the original is kept beside it
 * instead. Saying so is the difference between a true sentence and a reassuring one.
 */
fun SnapshotMergeResult.quarantineMessage(): String? {
    if (quarantinedRecords.isEmpty()) return null
    val listed = quarantinedRecords.take(3).joinToString("; ")
    val rest = quarantinedRecords.size - 3
    return "${quarantinedRecords.size} item(s) in the sync folder were written by a newer version " +
        "of Tendril and could not be read here. This device's own copies are unchanged, and where " +
        "it had to republish over something it could not read, a copy was kept beside it. Update " +
        "Tendril on this device to take them in. ($listed" +
        (if (rest > 0) ", and $rest more)" else ")")
}

/**
 * The result of [SnapshotSyncOrchestrator.rekey].
 *
 * Re-keying is the one operation that deliberately overwrites a folder under a different key, so
 * it is the one that most needs to be able to say "no".
 */
sealed interface RekeyOutcome {
    /** The folder is now encrypted under the new passphrase. */
    data object Completed : RekeyOutcome

    /**
     * Refused: this device could not read [undecryptableFiles] of the folder's snapshots under
     * the passphrase it currently holds, so nothing was written.
     *
     * A re-key is decrypt-then-encrypt, which means it can only preserve what the re-keying
     * device can actually read — anything it could not decrypt would be replaced by whatever
     * this device happens to hold. That is why a clean read is the precondition rather than a
     * warning: a device that cannot read the folder has no business rewriting it.
     */
    data class RefusedUnreadable(val undecryptableFiles: Int) : RekeyOutcome
}

/**
 * What one folder-wide array file is holding back from this pass's write, frozen for publication.
 *
 * [records] are **raw [JsonElement]s, exactly the bytes that arrived** — deliberately not the
 * typed snapshot records they used to be. `json` is configured `ignoreUnknownKeys = true` and
 * every snapshot record is a plain data class with no catch-all map, so a field a *newer* build
 * added is stripped the moment the element is decoded into one. Republishing the decoded object
 * would therefore have quietly re-emitted a lossy copy while the KDoc above it said "verbatim" —
 * and a third device adopting that copy loses the stripped field permanently, with nothing
 * anywhere reporting it. Holding the element means nothing round-trips through a data class at
 * all, and "verbatim" is a fact about the code rather than a hope about the schema.
 *
 * [keys] are the record uids this device must therefore *not* publish its own row for: a held
 * record won its timestamp race, so this device's copy of that uid is the stale one.
 *
 * [unreadable] is the whole-file case — content that would not parse as a JSON array at all,
 * which is the shape a newer build changing the file's outer structure takes. Nothing in it was
 * absorbed, so nothing in it may be *destroyed*: the write pass copies the file aside under a
 * [LOST_MARKER] name before republishing over it, and reports the file to the person. It does not
 * skip the write, which was the first attempt at this and is a permanent gag on this device's own
 * rows — see [SnapshotSyncOrchestrator.publishArrayFile] for that reasoning in full. Before any
 * of this existed, an unparseable array file merged as "nothing to do" and was then replaced
 * wholesale by local state, unread and uncopied, on the same pass.
 */
private class HeldRecords(
    val records: List<JsonElement> = emptyList(),
    val keys: Set<String> = emptySet(),
    val unreadable: Boolean = false,
)

/** [HeldRecords] while a pass is still filling it. Frozen by [frozen] before it is published to
 * the orchestrator's field, which is read from other threads.
 *
 * Carries the [file] it belongs to and the pass's [quarantined] list so that [markUnreadable] can
 * do both halves of its job in one place: remember the fact for the write pass, and *say so* to
 * the person. The whole-file case used to set a flag and nothing else, which meant a file this
 * device had stopped publishing — possibly forever — was reported nowhere at all.
 */
private class HeldBuilder(
    private val file: FolderArrayFile,
    private val quarantined: MutableList<QuarantinedRecord>,
) {
    private val records = mutableListOf<JsonElement>()
    private val keys = mutableSetOf<String>()

    /** Set when the file's outer JSON could not be read — see [HeldRecords.unreadable]. */
    var unreadable = false
        private set

    /** [key] is the record's uid where the pass could still see one; null where the record family
     * needs no local suppression (tombstones and relations name no local row this build exports)
     * or where the element was too broken to find a uid in. */
    fun hold(record: JsonElement, key: String?) {
        records += record
        if (key != null) keys += key
    }

    /**
     * The file's outer JSON would not read as an array — the shape a newer build changing the
     * file's structure takes, and equally the shape a corrupt or truncated file takes. This build
     * cannot tell those apart, and the difference does not change what it must do.
     *
     * Reported once per pass through the same [QuarantinedRecord] channel every other unreadable
     * thing uses, named by file rather than by uid because there is no record in it to name. Idem-
     * potent so a re-entrant read of the same file cannot report it twice.
     */
    fun markUnreadable() {
        if (unreadable) return
        unreadable = true
        quarantined += QuarantinedRecord(
            kind = QuarantinedRecord.FILE,
            uid = file.fileName,
            detail = "file structure, where a JSON array was expected",
        )
    }

    fun frozen() = HeldRecords(records.toList(), keys.toSet(), unreadable)
}

/** Mutable counter threaded through one [SnapshotSyncOrchestrator.readAndMerge] pass. */
private class ReadTally {
    var undecryptable = 0

    /** Records this pass refused to apply because it could not read them — see
     * [SnapshotMergeResult.quarantinedRecords]. */
    val quarantined = mutableListOf<QuarantinedRecord>()

    /**
     * What each folder-wide array file is holding, one builder per [FolderArrayFile].
     *
     * Filled only from the primary files. A record quarantined out of a Syncthing conflict
     * sibling needs no republication: nothing ever writes a `.sync-conflict-` name, and a
     * sibling that did not fully merge is deliberately left on disk rather than deleted, so its
     * copy is already surviving the pass untouched.
     */
    val activeEntries = HeldBuilder(FolderArrayFile.ENTRIES_ACTIVE, quarantined)
    val archivedEntries = HeldBuilder(FolderArrayFile.ENTRIES_ARCHIVED, quarantined)
    val habits = HeldBuilder(FolderArrayFile.HABITS, quarantined)
    val reminders = HeldBuilder(FolderArrayFile.REMINDERS, quarantined)
    val completions = HeldBuilder(FolderArrayFile.ENTRY_COMPLETIONS, quarantined)
    val relations = HeldBuilder(FolderArrayFile.RELATIONS, quarantined)
    val purged = HeldBuilder(FolderArrayFile.PURGED, quarantined)

    /** The pages this pass has already withheld, which is what a *later* read in the same pass
     * must treat as unresolvable — see [SnapshotSyncOrchestrator.mergePageContent]. */
    fun quarantinedPageUids(): Set<String> =
        quarantined.filter { it.kind == QuarantinedRecord.PAGE }.mapTo(mutableSetOf()) { it.uid }

    /**
     * Deliberately narrowed to [SnapshotDecodeException] from the `Throwable` it used to take.
     * That is the one cause this feature has actually established, and it is the cause
     * [quarantineMessage] names to the person: "written by a newer version of Tendril — update".
     * A truncated or corrupt file is a different problem with different advice, and quarantining
     * it under this sentence sends someone to the app store to fix a broken disk. Callers now
     * catch the specific type and let everything else propagate.
     */
    fun quarantine(kind: String, uid: String, cause: SnapshotDecodeException) {
        quarantined += QuarantinedRecord(kind = kind, uid = uid, detail = cause.detail)
    }

    /**
     * The other way a record inside an array file can be unreadable: the *element* would not
     * decode into this build's snapshot class at all — a required field a newer build added, or a
     * field whose type it changed. It used to fail the whole file (and, before the file's content
     * was protected, hand the write pass a clean sheet to overwrite it with); it is now one
     * quarantined record like any other, named by whatever uid is still legible in the element.
     */
    fun quarantineElement(kind: String, element: JsonElement) {
        quarantined += QuarantinedRecord(kind, element.uidOrNull() ?: "(no readable uid)", "snapshot format")
    }
}

/** The uid out of a raw element, for a record that would not decode far enough to have one. */
private fun JsonElement.uidOrNull(): String? =
    runCatching { jsonObject["uid"]?.jsonPrimitive?.contentOrNull }.getOrNull()

/**
 * Whether an element this pass could not decode should be held back for republication — which is
 * to say, whether the peer's copy would actually have *won* the last-write-wins race against
 * [local].
 *
 * Holding is not free, and that is the whole reason this question has to be asked before the hold
 * rather than after it. A held record is republished verbatim **and** this device's own row for
 * the same uid is suppressed from the write ([QuarantinedExports.suppressedKeys]) — correct when
 * the peer's copy is the newer one, and a permanent, silent gag on this device's own better data
 * when it is not. A record that lost on time was never going to be adopted by a readable pass
 * either; the ordinary LWW outcome is that this device publishes its newer row over it, and being
 * unable to read the loser changes nothing about that.
 *
 * `updatedAt` is legible on an element that would not decode for the same reason `uid` is: the
 * failures this handles are a missing field or a changed type somewhere in the record, not a
 * broken document. Where it is *not* legible the answer is true — hold it — because an unknown
 * timestamp cannot be shown to have lost, and holding is the conservative half of this trade.
 */
private fun JsonElement.outranks(local: Instant?): Boolean {
    if (local == null) return true
    val remote = runCatching { jsonObject["updatedAt"]?.jsonPrimitive?.long }.getOrNull() ?: return true
    return Instant.ofEpochMilli(remote).isAfter(local)
}

/**
 * What one merge pass could not interpret, in the shape the *next write pass* needs it.
 *
 * Quarantine has two halves, and this is the second: do not publish this device's own view over
 * a peer record it could not read. Without it, skipping an unreadable record and then writing is
 * strictly worse than the crash it replaced — the crash aborted the pass before the write half
 * and the peer's record survived in the folder; a silent skip lets the write erase it.
 *
 * The two halves take different shapes because the files do. Pages are one file each, so
 * [pageUids] is enough: skip that file and the peer's copy is left exactly where it lies. Entries,
 * habits and tombstones live in one folder-wide array file apiece that every device overwrites in
 * full from its local rows, so there is no "skip" available at that granularity — skipping the
 * file would suppress every *other* record in it too, freezing the folder over one unknown value.
 * So those travel as the raw snapshot records themselves and are merged back in at write time.
 *
 * Those travel as the **raw [JsonElement]** each arrived as, never as the typed record — see
 * [HeldRecords] for why re-encoding a decoded data class would have made "verbatim" false. The
 * hold is possible at all because the ordinary failure is at entity-mapping time rather than
 * JSON-decode time: every field on these records is a plain String or number, so the element
 * parses perfectly and only `EntryKind.valueOf` and its siblings fail. This device therefore
 * republishes exactly what it could not interpret, byte for byte, and every record it *can* read
 * still syncs normally alongside it.
 */
private class QuarantinedExports(
    val pageUids: Set<String> = emptySet(),
    /** Keyed by [FolderArrayFile], and complete: the merge fills it from an exhaustive `when`,
     * so a file with no entry here is a file that does not exist rather than one that was
     * forgotten. */
    private val files: Map<FolderArrayFile, HeldRecords> = emptyMap(),
) {
    fun heldIn(file: FolderArrayFile): HeldRecords = files[file] ?: HeldRecords()

    /**
     * The local record keys [file] must not publish, pooled across every file in its
     * [RecordFamily] — see [RecordFamily] for why the pooling is not per-file.
     */
    fun suppressedKeys(file: FolderArrayFile): Set<String> =
        files.entries.filter { it.key.family == file.family }.flatMapTo(mutableSetOf()) { it.value.keys }
}

class SnapshotSyncOrchestrator(
    private val entryDao: EntryDao,
    private val habitDao: HabitDao,
    private val pageDao: PageDao,
    private val reminderDao: ReminderDao,
    private val entryCompletionDao: EntryCompletionDao,
    private val pagesSyncEngine: PagesSyncEngine,
    private val purgeRegistry: PurgeRegistry,
) {
    /**
     * What the last merge pass could not read, and which the next write pass must therefore
     * **not** publish over — pages by uid, every folder-wide array file as the raw elements it
     * held. See [QuarantinedExports] for why the two families need different shapes.
     *
     * This is the half of quarantine that makes it quarantine rather than a plain skip, and it is
     * the whole reason a plain skip would be worse than the bug it fixes. [writeWithKey] exports
     * every local page unconditionally, so a page whose peer file this build cannot decode would
     * have this device's older copy written straight on top of it — destroying the newer version
     * on every device in the folder, with no `.tendril-lost-` copy kept, because the record never
     * came back out of `mergePages` for [preserveLostPages] to see. The folder-wide array files
     * are worse still: they are rewritten in full from local rows, so a record the merge never
     * adopted vanishes from the folder outright.
     *
     * Held on the instance **only for [writeSnapshots]**, the one entry point that publishes
     * without merging first, and which both platforms call immediately after their own
     * [readAndMerge] (`SyncCoordinator.runPass`, the desktop sync button). The whole-pass entry
     * points do not read this field at all: [mergeWithKey] hands its answer straight to
     * [writeWithKey] as a required argument, so [syncNow] and [rekey] cannot express a write that
     * used some *earlier* pass's suppression, whatever order a future edit puts their statements
     * in. Replaced wholesale by each merge rather than accumulated — a peer file that becomes
     * readable, because this build was updated or the page was rewritten, must stop being
     * suppressed on the very next pass.
     *
     * That leaves one ordering a caller can still get wrong, and it is stated here rather than
     * left to be discovered: a [writeSnapshots] not preceded on this instance by a merge *of the
     * same folder* publishes whatever the last merge of any folder decided. Both production
     * callers merge the same store immediately before writing, and `UnknownEnumQuarantineTest`'s
     * section 9 pins both halves of that contract.
     *
     * One deliberate consequence, in the one place suppression is not obviously right: a [rekey]
     * that finds a quarantined page leaves that page's file encrypted under the *old* passphrase,
     * because re-keying is decrypt-then-encrypt and this file's content was never decoded. That is
     * the same trade every other guard here makes — the folder gets stuck and says so (the next
     * pass counts the file as undecryptable and refuses to write), rather than the peer's page
     * being quietly replaced by this device's older copy. Re-key from a device that can read the
     * folder's pages, which is what the precondition on [rekey] asks for in the first place.
     */
    @Volatile
    private var quarantinedExports: QuarantinedExports = QuarantinedExports()

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
        // The merge's own answer, threaded straight into the write rather than fetched from the
        // instance field — see [quarantinedExports]. A write here cannot be suppressed by an
        // older pass, because the only value in scope is the one this pass just produced.
        val pass = mergeWithKey(store, ReadKey(key, isEncryptedFolder(store)))
        writeWithKey(store, key, pass.held, allowRekey, salt)
    }

    /**
     * §9.4.2 — re-encrypt the whole folder under [newPassphrase].
     *
     * The precondition is the whole design: this merges under [currentPassphrase] first and
     * refuses if anything failed to decrypt. A re-key is decrypt-then-encrypt, so it can only
     * preserve what this device can read; without the read, "re-key" is indistinguishable from
     * "replace the folder with my copy and lock everyone else out of the rest".
     *
     * Other devices are not harmed by a successful re-key. They will find the folder unreadable
     * and refuse to write to it — the same guard, from the other side — and once told the new
     * passphrase they merge and republish their own rows from their local database. The case
     * that is *not* recoverable is a device that no longer exists, whose rows lived only in the
     * folder; those are what the precondition protects.
     *
     * Deliberately takes a non-null [newPassphrase]: turning encryption *off* over an encrypted
     * folder is a different operation with a different hazard (the silent cleartext downgrade
     * [writeWithKey] exists to refuse), and is not offered here.
     */
    suspend fun rekey(
        store: SyncFileStore,
        currentPassphrase: String?,
        newPassphrase: String,
    ): RekeyOutcome {
        // Straight to the internals rather than readAndMerge + writeSnapshots, for the reason
        // [syncNow] gives: each public entry point resolves the folder's salt for itself, and
        // both halves here want the same one. Two passphrases still mean two PBKDF2 derivations,
        // which is unavoidable — it is the salt lookup that would have been done twice.
        val salt = resolveSalt(store, mint = true)
        val pass = mergeWithKey(store, ReadKey(deriveKey(currentPassphrase, salt), isEncryptedFolder(store)))
        if (pass.result.passphraseMismatch) return RekeyOutcome.RefusedUnreadable(pass.result.undecryptableFiles)
        // allowRekey is reachable only from here, and only past that check.
        writeWithKey(store, deriveKey(newPassphrase, salt), pass.held, allowRekey = true, salt = salt)
        return RekeyOutcome.Completed
    }

    /**
     * The write half on its own, for a caller that only needs to publish. [syncNow] is what a
     * "Sync now" button wants.
     *
     * This is the one door that takes its suppression from the instance rather than from a merge
     * in the same expression, and it therefore carries the ordering obligation [syncNow] does not:
     * merge the same folder on this orchestrator first, or publish over records this device has
     * never seen. Both production callers do (`SyncCoordinator.runPass`, the desktop sync button),
     * for the separate reason that they need [SnapshotMergeResult.passphraseMismatch] before they
     * are allowed to write at all.
     */
    suspend fun writeSnapshots(store: SyncFileStore, passphrase: String? = null, allowRekey: Boolean = false) {
        val salt = resolveSalt(store, mint = passphrase != null)
        writeWithKey(store, deriveKey(passphrase, salt), quarantinedExports, allowRekey, salt)
    }

    /**
     * [held] is deliberately a required parameter with no default. It is the whole of quarantine's
     * second half, and the reason this function has one is that three separate rounds of this fix
     * each added a file here and each forgot to consult it — a default would let the fourth do the
     * same. See [FolderArrayFile] for the two exhaustive `when`s that make forgetting a *new* file
     * a compile error rather than a field failure.
     */
    private suspend fun writeWithKey(
        store: SyncFileStore,
        key: SecretKeySpec?,
        held: QuarantinedExports,
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
        val allReminders = reminderDao.getAll()
        val allCompletions = entryCompletionDao.getAll()
        val (active, archived) = allEntries.partition { it.isActive() }

        // Quarantine's second half for every folder-wide array file (§9.4). Each is rewritten in
        // full from local rows, so a record the merge could not interpret — and therefore never
        // adopted into Room — is *deleted from the folder* by this write, on every device. That
        // is a loss HEAD did not have: there the same input threw out of the merge, the pass never
        // reached the write, and the peer's record survived untouched.
        //
        // Freezing the whole file instead would be the other way to be wrong: one unknown value
        // from one peer would stop this device publishing every other entry it holds. So each
        // held record is republished verbatim beside the readable ones, and this device's own
        // stale row for that uid stands aside for it — see [QuarantinedExports].
        //
        // The loop is over the enum rather than the straight line of write statements this used to
        // be, and the `when` inside it has no `else`, so a file added to [FolderArrayFile] does not
        // compile until its local half is written here and its held half in [mergeWithKey]. The
        // straight line is what kept failing: suppression was written three times — pages, then
        // entries/habits/tombstones, then this — and each time the line for one more file sat in
        // it looking exactly like the others while consulting nothing. page_relations.json was the
        // one left over, and it silently erased a peer's link to any page this build quarantined.
        for (file in FolderArrayFile.entries) {
            val suppressed = held.suppressedKeys(file)
            val local: List<JsonElement> = when (file) {
                FolderArrayFile.ENTRIES_ACTIVE ->
                    elementsOf(active.filterNot { it.uid in suppressed }.map { it.toSnapshot(idToUid, rowIdToUid) })
                FolderArrayFile.ENTRIES_ARCHIVED ->
                    elementsOf(archived.filterNot { it.uid in suppressed }.map { it.toSnapshot(idToUid, rowIdToUid) })
                FolderArrayFile.HABITS ->
                    elementsOf(allHabits.filterNot { it.uid in suppressed }.map { it.toSnapshot() })
                // §4 / S2. `mapNotNull` rather than `map` only as a belt: `entryId` is a CASCADE
                // foreign key and [idToUid] is built from every Entry row, so a reminder whose
                // owner is missing is a state the database does not permit. Dropping it beats
                // inventing a uid for an Entry that is not there.
                FolderArrayFile.REMINDERS ->
                    elementsOf(
                        allReminders.filterNot { it.uid in suppressed }
                            .mapNotNull { r -> idToUid[r.entryId]?.let { r.toSnapshot(it) } }
                    )
                // §4.1 / S2 — append-only, so this file only ever grows. It is the one array file
                // with no deletion semantics of any kind to get wrong.
                FolderArrayFile.ENTRY_COMPLETIONS ->
                    elementsOf(
                        allCompletions.filterNot { it.uid in suppressed }
                            .mapNotNull { c -> idToUid[c.entryId]?.let { c.toSnapshot(it) } }
                    )
                // §3.4's manual edges. Nothing is filtered against `suppressed`: a relation names
                // no uid of its own, and what this device holds back is held whole — see
                // [mergeRelationsContent], which keeps the edges it could not attach to a local
                // page because that page was itself quarantined.
                FolderArrayFile.RELATIONS -> elementsOf(pagesSyncEngine.exportRelations())
                // §5.5.1.1 — the tombstones travel, which is what makes "Delete forever" mean
                // everywhere rather than only here. Deliberately narrows §9.4's additive-merge
                // rule: absence still never implies deletion, but an explicit tombstone does.
                //
                // Held tombstones are appended with nothing filtered out against them: what made
                // one unreadable is a `PurgedKind` this build has no member for, so by
                // construction it names no local record and can collide with none. Republishing
                // them matters more here than anywhere else — dropping a delete instruction
                // resurrects the thing it was deleting, on every device that had already carried
                // it out.
                FolderArrayFile.PURGED -> elementsOf(purgeRegistry.all().map { it.toSnapshot() })
            }
            publishArrayFile(store, file, local, held, key)
        }

        // Quarantined pages are the one exception to "publish everything local". Suppression is
        // per page, not a blanket refusal to write: one file this build cannot read must not stop
        // the device sharing everything else it holds, or a single unknown value from one peer
        // would freeze the whole folder.
        for (record in pagesSyncEngine.exportPages()) {
            publishPageFile(store, record, held, key)
        }

        // Clear the snapshot files of pages purged here (§5.5.1). Only those: a file is deleted
        // because this device recorded a deliberate "Delete forever" for that exact uid, never
        // because a file merely looks unfamiliar. Inferring deletion from absence is what the
        // conflict sweep in readAndMerge is careful not to do either, and for the same reason.
        val purgedFiles = pagesSyncEngine.purgedPageFileNames()
        if (purgedFiles.isNotEmpty()) {
            // ...and never a page this pass quarantined, even when a tombstone names it. A purge
            // and an edit are settled by comparing the two timestamps (`PurgeRegistry` supersedes
            // the tombstone when the record is newer), and that comparison lives inside
            // `mergePages` — which a page file this build cannot parse never reaches. Deleting on
            // the strength of half of a comparison destroys the peer's file on every device, and
            // it is the one deletion here that nothing else guards. The tombstone still travels in
            // purged_records.json, so a device that *can* read the page settles it properly and
            // the file goes then.
            val purgedUids = purgedFiles.mapTo(mutableSetOf()) { it.removeSuffix(".json") } - held.pageUids
            store.listPages().filter { name ->
                // The page's own snapshot, and any copy of it preserved by §5.2's lost-race
                // handling. "Delete forever" that leaves copies of the thing behind is not
                // delete forever, and a preserved loser is still the person's page content.
                purgedUids.any { name == "$it.json" || name.startsWith("$it$LOST_MARKER") }
            }.forEach { store.deletePage(it) }
        }
    }

    /**
     * **The only way a folder-wide array file may be written.** Takes a [FolderArrayFile] rather
     * than a name, and consults [held] itself, so publishing one without answering the
     * suppression question is not something a caller can express.
     *
     * The ordinary case: the held elements are appended to this device's own records, verbatim,
     * and the rows they supersede have already been filtered out by
     * [QuarantinedExports.suppressedKeys] at the call site.
     *
     * ### The whole-file case, and why "skip it" is not the steady state
     *
     * A file whose outer JSON would not read as an array (see [HeldBuilder.markUnreadable]) used
     * to be skipped outright: nothing in it was absorbed, so nothing in it may be replaced — the
     * same rule a quarantined page's own file gets. That is right for *one pass* and catastrophic
     * as a steady state, because the condition never clears on its own. `entries_active.json` in a
     * shape this build cannot read is still in that shape on the next pass, and the one after, so
     * this device stops publishing every entry it holds — to peers on the *same* build, who could
     * have read them perfectly — for as long as the file sits there. It is the mirror image of the
     * loss quarantine exists to prevent, aimed at the one dataset nobody else has a copy of.
     *
     * A page can be skipped forever because a page is one file per record: skipping it costs
     * exactly that page. A folder-wide array file is every record of its family at once, and the
     * two claims on it are irreconcilable — the peer's structure must not be destroyed unread, and
     * this device's own rows must keep reaching the folder. So both are honoured, in order:
     *
     *  1. the bytes are copied aside under a [LOST_MARKER] name, exactly as they lie, still
     *     encrypted with whatever key wrote them. Nothing is read out of them and nothing is
     *     interpreted; the copy is evidence, recoverable by hand, and inert to every reader here;
     *  2. and only then this device publishes its own rows in the shape it knows.
     *
     * Deliberately **one** copy per file rather than one per distinct content, unlike
     * [preserveLostPages]. A peer that keeps rewriting the file in a shape this build cannot read
     * would otherwise leave a new copy in the folder on every pass, forever, and each of those
     * copies is redundant: the device that wrote it still holds the authoritative rows and
     * republishes them. What the copy protects against is the folder being the *only* place that
     * content lives, and for that the most recent one is the one that matters.
     *
     * The person is told either way — [HeldBuilder.markUnreadable] reports the file through the
     * same channel as every unreadable record, which is the half that was missing entirely: before
     * this, a device could stop publishing a whole family of records permanently and nothing
     * anywhere would say so.
     */
    private suspend fun publishArrayFile(
        store: SyncFileStore,
        file: FolderArrayFile,
        localRecords: List<JsonElement>,
        held: QuarantinedExports,
        key: SecretKeySpec?,
    ) {
        val holding = held.heldIn(file)
        if (holding.unreadable) preserveUnreadableArrayFile(store, file)
        val content = json.encodeToString(JsonArray(localRecords + holding.records))
        store.writeRoot(file.fileName, encryptText(content, key))
    }

    /**
     * Copies a folder-wide array file this pass could not read aside, before [publishArrayFile]
     * writes over it. See that function for why the write happens at all.
     *
     * Byte-for-byte, with no decrypt/re-encrypt round trip: the content was never interpreted, so
     * there is nothing to re-encode, and a file that arrived encrypted stays encrypted under the
     * key that wrote it. The name reuses [LOST_MARKER], which every reader here already skips —
     * the pages listing filters it explicitly, the conflict sweep matches only `.sync-conflict-`,
     * and the meta lookup matches only `sync_meta.*`.
     */
    private suspend fun preserveUnreadableArrayFile(store: SyncFileStore, file: FolderArrayFile) {
        val bytes = store.readRoot(file.fileName) ?: return
        if (bytes.isEmpty()) return
        store.writeRoot("${file.fileName.removeSuffix(".json")}${LOST_MARKER}unreadable.json", bytes)
    }

    /**
     * The per-page equivalent, and the only way a page's own `<uid>.json` is written — for the
     * same reason [publishArrayFile] is the only way an array file is. (The `.tendril-lost-`
     * copies [preserveLostPages] writes are a different name and purely additive; they erase
     * nothing and so need no suppression.) A page needs no republication: skipping the write
     * leaves the peer's file byte-for-byte where it lies, which is the one case that was already
     * genuinely verbatim before the held records became raw elements.
     */
    private suspend fun publishPageFile(
        store: SyncFileStore,
        record: PageSnapshotRecord,
        held: QuarantinedExports,
        key: SecretKeySpec?,
    ) {
        if (record.uid in held.pageUids) return
        store.writePage("${record.uid}.json", encryptText(json.encodeToString(record), key))
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
    suspend fun readAndMerge(store: SyncFileStore, passphrase: String? = null): SnapshotMergeResult =
        // mint = false: reading must never write to the folder, and a folder with nothing in it
        // has nothing to decrypt anyway.
        mergeWithKey(store, ReadKey(deriveKey(passphrase, resolveSalt(store, mint = false)), isEncryptedFolder(store))).result

    /**
     * One merge pass's two products: what to tell the caller, and what the write half must not
     * publish over.
     *
     * They travel together so that [syncNow] and [rekey] can hand the second straight to
     * [writeWithKey] instead of going back to [quarantinedExports] for it. A field read there
     * would be a write that *happens* to be preceded by a merge; this is a write that cannot be
     * written without one.
     */
    private class MergePass(val result: SnapshotMergeResult, val held: QuarantinedExports)

    /**
     * What a merge pass needs in order to read a file: the key, and whether the folder is known
     * to be encrypted. The second is a property of the *folder*, not of any one file, which is
     * exactly why it is resolved once per pass and carried rather than re-derived per read.
     */
    private data class ReadKey(
        val key: SecretKeySpec?,
        val folderEncrypted: Boolean,
        /** Counts files this pass could not read, for [SnapshotMergeResult]. Carried here rather
         * than threaded as a fourth parameter: it travels through exactly the same nine
         * functions the key does, and two parallel plumbings of the same shape is how one of
         * them eventually gets forgotten at a call site. */
        val tally: ReadTally = ReadTally(),
    )

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
        // already exist, and (the half that was missing) needs to know which Rows this pass
        // *withheld*, so an Entry pointing at one is not adopted with its link nulled and then
        // republished that way. Both facts come out of this call: the ids into Room, the withheld
        // uids into `read.tally`. Every later family that can name a page therefore reads them
        // from the same place — see [mergeEntryContent] and [mergeRelationsContent].
        mergePagesDir(store, read)
        // After the pages, deliberately: a relation whose endpoint page was just quarantined has
        // no local row to attach to, and this is where that is noticed and the edge held back.
        mergeRelationsFile(store, read)
        mergeEntryFile(store, FolderArrayFile.ENTRIES_ACTIVE, read)
        mergeEntryFile(store, FolderArrayFile.ENTRIES_ARCHIVED, read)
        mergeHabitFile(store, read)
        // After the Entries, necessarily: both resolve `entryUid` to a local Entry id, and a
        // record whose owner has not merged here yet is held rather than dropped — see
        // [mergeReminderContent].
        mergeReminderFile(store, read)
        mergeCompletionFile(store, read)

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
                    mergeEntryContent(readRootText(store, name, read), read.tally)
                name.startsWith("habits") -> mergeHabitContent(readRootText(store, name, read), read.tally)
                name.startsWith("reminders") -> mergeReminderContent(readRootText(store, name, read), read.tally)
                name.startsWith("entry_completions") ->
                    mergeCompletionContent(readRootText(store, name, read), read.tally)
                name.startsWith("page_relations") -> mergeRelationsContent(readRootText(store, name, read))
                name.startsWith("purged_records") -> mergePurgedContent(readRootText(store, name, read))
                else -> false
            }
            if (merged) store.deleteRoot(name)
        }
        store.listPages().forEach { name ->
            if (!name.contains(".sync-conflict-")) return@forEach
            if (mergePageContent(readPageText(store, name, read), read.tally)) store.deletePage(name)
        }

        // What the write half must not overwrite. Set even when empty: an empty set is this
        // pass's answer, and leaving the previous pass's answer in place would suppress a page
        // whose file has since become readable — or republish a stale copy of an entry this
        // build has since learned to read.
        //
        // The `when` is exhaustive and has no `else` on purpose. It is the second half of the
        // compile-time question [FolderArrayFile] asks — "where does this file's quarantine come
        // from" — and a file added without an answer here stops the build. The first half is the
        // matching `when` in [writeWithKey].
        val held = QuarantinedExports(
            pageUids = read.tally.quarantinedPageUids(),
            files = FolderArrayFile.entries.associateWith { file ->
                when (file) {
                    FolderArrayFile.ENTRIES_ACTIVE -> read.tally.activeEntries
                    FolderArrayFile.ENTRIES_ARCHIVED -> read.tally.archivedEntries
                    FolderArrayFile.HABITS -> read.tally.habits
                    FolderArrayFile.REMINDERS -> read.tally.reminders
                    FolderArrayFile.ENTRY_COMPLETIONS -> read.tally.completions
                    FolderArrayFile.RELATIONS -> read.tally.relations
                    FolderArrayFile.PURGED -> read.tally.purged
                }.frozen()
            },
        )
        quarantinedExports = held

        MergePass(
            result = SnapshotMergeResult(
                undecryptableFiles = read.tally.undecryptable,
                quarantinedRecords = read.tally.quarantined.toList(),
            ),
            held = held,
        )
    }

    private suspend fun mergePagesDir(store: SyncFileStore, read: ReadKey) {
        val records = mutableListOf<PageSnapshotRecord>()
        // The uids whose files would not decode into a record at all. They never reach
        // `mergePages` as records, so its reference sweep cannot see them on its own — and these
        // are the files *furthest* ahead of this build, exactly the ones whose children would
        // otherwise be re-parented to root and republished that way.
        val unreadableUids = mutableSetOf<String>()
        val names = store.listPages()
            // A preserved loser is not an input: merging it back would either resurrect it or,
            // once it lost again, write a second copy of itself on every pass.
            .filter { it.endsWith(".json") && !it.contains(".sync-conflict-") && !it.contains(LOST_MARKER) }
        for (name in names) {
            val text = readPageText(store, name, read)
            // Blank is "couldn't decrypt it" or "empty file", already counted as undecryptable
            // where that applies — not a quarantine, and not this device's page to overwrite
            // either way (the passphrase guard refuses the whole write pass for that case).
            if (text.isBlank()) continue
            val record = decodePage(text)
            if (record == null) {
                // Well-formed enough to be here and still undecodable: a snapshot shape only a
                // later build writes. Quarantined by the filename, since the uid is the one thing
                // still legible about it, and suppressed from the export for the same reason a
                // page with an unknown enum value is.
                val uid = name.removeSuffix(".json")
                read.tally.quarantined += QuarantinedRecord(QuarantinedRecord.PAGE, uid, "snapshot format")
                unreadableUids += uid
                continue
            }
            records += record
        }
        val outcome = pagesSyncEngine.mergePages(records, unreadableUids)
        read.tally.quarantined += outcome.quarantined
        preserveLostPages(store, outcome.lost, read)
    }

    /**
     * §5.2 — writes each page record that lost its race, beside the winner that replaced it.
     *
     * §9.4 accepts whole-page last-write-wins for v1 and this keeps that: the winner still wins.
     * It only stops the loser being destroyed unread, which is the part of LWW nobody agreed to.
     *
     * Named `<uid>.tendril-lost-<the losing updatedAt>.json`, so preserving the same losing
     * version twice overwrites one file rather than accumulating copies of it. Encrypted with
     * the same key as everything else — a plaintext file in an encrypted folder is exactly what
     * this class now refuses to read (§4.1), and writing one here would mean writing evidence
     * nothing can pick up again.
     *
     * Nothing deletes these. Syncthing's own conflict files behave the same way and are cleaned
     * up by hand; a copy kept precisely because it was about to be lost is not something to then
     * expire on a timer without being asked.
     */
    private suspend fun preserveLostPages(
        store: SyncFileStore,
        lost: List<PageSnapshotRecord>,
        read: ReadKey,
    ) {
        for (record in lost) {
            val name = "${record.uid}$LOST_MARKER${record.updatedAt}.json"
            store.writePage(name, encryptText(json.encodeToString(record), read.key))
        }
    }

    private fun decodePage(content: String): PageSnapshotRecord? =
        runCatching { json.decodeFromString<PageSnapshotRecord>(content) }.getOrNull()

    /** @return true when [content] actually decoded and was handed to the merge. The conflict
     * sweep in [readAndMerge] uses this to decide whether a file is safe to delete; a false
     * here means "couldn't read this," never "nothing to do."
     *
     * A quarantined record counts as "couldn't read this" too, and for a stronger reason than the
     * other cases: a conflict file is the copy that *lost* the race, so deleting one this build
     * cannot decode destroys the only copy of an edit made on a device that is ahead of this one.
     * Its uid is quarantined as well, so the write pass leaves that page's own file alone. */
    private suspend fun mergePageContent(content: String, tally: ReadTally): Boolean {
        if (content.isBlank()) return false
        val record = decodePage(content) ?: return false
        // Everything the primary pass already withheld travels in, so a conflict sibling that
        // merely *points at* a quarantined page is quarantined too rather than adopted with the
        // pointer nulled. The sweep runs after `mergePagesDir`, so the list is complete by here.
        val outcome = pagesSyncEngine.mergePages(listOf(record), tally.quarantinedPageUids())
        tally.quarantined += outcome.quarantined
        return outcome.quarantined.isEmpty()
    }

    private suspend fun mergePurgedFile(store: SyncFileStore, read: ReadKey) {
        mergePurgedContent(readRootText(store, FolderArrayFile.PURGED.fileName, read), read.tally.purged)
    }

    /**
     * @return true when the content decoded — see [mergePageContent]. False once any tombstone in
     * it was unreadable, so the conflict sweep leaves that sibling on disk rather than deleting a
     * delete instruction it never carried out.
     *
     * [held] collects the unreadable ones verbatim for the write half. A dropped tombstone is the
     * one kind of dropped record that *undoes* work rather than merely delaying it: every device
     * that had already purged the thing gets it back from the next peer that never saw the
     * tombstone. This path predates the rest of the quarantine sweep — skipping an unrecognised
     * kind rather than failing the file was already right — and it is the same unconditional
     * folder-wide rewrite as entries and habits, so it needs the same second half.
     */
    private suspend fun mergePurgedContent(
        content: String,
        held: HeldBuilder? = null,
    ): Boolean {
        val elements = decodeArray(content, held) ?: return false
        // An unrecognised kind is skipped rather than failing the whole file: a newer build may
        // purge things this one has no concept of, and the rest of the file is still good.
        val adopted = mutableListOf<PurgedRecord>()
        var allRead = true
        for (element in elements) {
            val entity = decodeRecord<PurgedRecordSnapshot>(element)?.toEntity()
            if (entity == null) {
                allRead = false
                // No suppression key: a tombstone this build cannot read names a `PurgedKind` it
                // has no member for, so `purgeRegistry.all()` can hold nothing that collides with
                // it. There is no local row to stand aside.
                held?.hold(element, key = null)
            } else {
                adopted += entity
            }
        }
        purgeRegistry.adopt(adopted)
        return allRead
    }

    private fun PurgedRecord.toSnapshot() = PurgedRecordSnapshot(kind.name, uid, purgedAt.toEpochMilli())

    private fun PurgedRecordSnapshot.toEntity(): PurgedRecord? {
        // The tolerant decode this whole pass generalises: an unrecognised kind was already
        // skipped here rather than failing the file, long before the rest of the sweep caught up.
        val parsed = enumOrNull<PurgedKind>(kind) ?: return null
        return PurgedRecord(parsed, uid, Instant.ofEpochMilli(purgedAt))
    }

    private suspend fun mergeRelationsFile(store: SyncFileStore, read: ReadKey) {
        mergeRelationsContent(readRootText(store, FolderArrayFile.RELATIONS.fileName, read), read.tally.relations)
    }

    /**
     * §3.4's manual edges, and the third folder-wide file to need quarantine's second half.
     *
     * The loss it exists to stop has no unreadable *value* in it anywhere, which is why three
     * reviews walked past it. A peer on a newer build creates page P with a `PageKind` this build
     * has no member for and links Q→P. P is quarantined, so it is never inserted locally;
     * `PagesSyncEngine.mergeRelations` then drops any edge whose endpoint is missing, so Q→P is
     * absent from `exportRelations()`; and the write pass, publishing local state unconditionally,
     * erased the peer's link from the folder. At HEAD the same input threw before the write half
     * ran and the link survived — a regression, not an inherited wart.
     *
     * So an edge this pass could not attach is held and republished verbatim. The one exception
     * is an endpoint with a tombstone against it: that page is deliberately gone (§5.5.1.1), the
     * edge went with it, and re-emitting it would keep a deleted page's edges alive in the folder
     * forever. Absence never implies deletion here either — only an explicit tombstone does.
     *
     * @return true when every edge in [content] was genuinely absorbed. It used to return true for
     * any parseable JSON, which meant the conflict sweep deleted a `page_relations.sync-conflict-*`
     * sibling whose edges had all been silently dropped — destroying the losing copy of exactly the
     * links this method now keeps.
     */
    private suspend fun mergeRelationsContent(content: String, held: HeldBuilder? = null): Boolean {
        val elements = decodeArray(content, held) ?: return false
        val decoded = elements.map { it to decodeRecord<PageRelationSnapshotRecord>(it) }
        val unabsorbed = pagesSyncEngine.mergeRelations(decoded.mapNotNull { (_, record) -> record })
        val purgedPages = purgeRegistry.tombstones(PurgedKind.PAGE)
        var allRead = true
        for ((element, record) in decoded) {
            if (record != null && record !in unabsorbed) continue
            if (record != null && (record.fromPageUid in purgedPages || record.toPageUid in purgedPages)) continue
            allRead = false
            // No suppression key: an edge carries no uid of its own, and this device cannot be
            // exporting the edge it just failed to attach — `exportRelations` only emits edges
            // between two pages that exist locally.
            held?.hold(element, key = null)
        }
        return allRead
    }

    private suspend fun mergeEntryFile(store: SyncFileStore, file: FolderArrayFile, read: ReadKey) {
        mergeEntryContent(
            readRootText(store, file.fileName, read),
            read.tally,
            // Held back into the file it arrived in, not the one this device would have chosen:
            // active/archived is a property of the record, and this build could not read the
            // record. Republishing it under the other heading would be interpreting it after all.
            // The uid is still suppressed across *both* files — see [RecordFamily].
            held = if (file == FolderArrayFile.ENTRIES_ARCHIVED) read.tally.archivedEntries else read.tally.activeEntries,
        )
    }

    /**
     * @return true when the content decoded — see [mergePageContent].
     *
     * Entries travel as one array per file, so `EntryKind.valueOf` throwing from the middle of
     * this loop took every *later* record in the same file with it, and then aborted the pass.
     * Each record now decodes on its own and a record that will not decode is quarantined: the
     * rest of the file merges, this device's own copy of that record is untouched, and the file
     * is reported unread so the conflict sweep leaves it on disk.
     *
     * [held], when given, is where the raw record goes so the write half can republish it — the
     * clause that keeps a quarantined entry in the folder at all, since `entries_*.json` is
     * rewritten wholesale from local rows on the very next write. Null from the conflict sweep,
     * whose files are neither rewritten nor (having not fully merged) deleted.
     *
     * ### An entry is quarantined by the *page* pass too
     *
     * `PagesSyncEngine.mergePages` propagates quarantine along references, but only among pages —
     * and `sourceRowUid` is the one field anywhere else in the schema that names one (§9.4; the
     * only other cross-file reference, §3.4's relations, is handled in [mergeRelationsContent],
     * and Reminders never travel in a snapshot at all, so this is the whole list). The peer writes
     * a database row `R` this build must quarantine and an Entry `E` whose `sourceRowUid` is `R`.
     * `mergePagesDir` withholds `R` correctly and suppresses `pages/R.json` — and then this loop
     * builds `rowUidToId` from `pageDao.getAll()`, which has no `R` precisely *because* the
     * quarantine never inserted it. `toEntity` resolves the link to null, `E` is adopted with the
     * link destroyed, and the next write republishes `E` that way over the folder. The page family
     * learned to propagate; the entry family never heard about it, and HEAD did not have this loss
     * — there the same input threw out of `mergePages` before the write half ran.
     *
     * The refinement is the same one the page sweep applies, at the same granularity: the link
     * only *dangles* when the row this device holds no local page for was withheld by this very
     * pass. A row quarantined but already present locally resolves perfectly well and the Entry
     * merges as normal; a row that is merely late is an ordinary drop-and-self-heal
     * (`SnapshotMappers.toEntity`), not a quarantine. A row with a tombstone against it is
     * excluded for the reason `mergePages` excludes it: that page is gone on purpose (§5.5.1.1)
     * and the reference is *meant* to resolve to nothing.
     */
    private suspend fun mergeEntryContent(
        content: String,
        tally: ReadTally,
        held: HeldBuilder? = null,
    ): Boolean {
        val elements = decodeArray(content, held) ?: return false
        // uid → local id, resolved fresh each pass so exception-row backlinks (rare, see
        // SnapshotMappers) can resolve once their target has merged in an earlier record.
        val uidToId = entryDao.getAll().associate { it.uid to it.id }.toMutableMap()
        val rowUidToId = pageDao.getAll().associate { it.uid to it.id }
        val tombstones = purgeRegistry.tombstones(PurgedKind.ENTRY)
        // The rows this pass withheld — the page pass's answer, read here rather than recomputed,
        // so the two families cannot drift apart. A deliberately purged page is not one this
        // build failed to read; see the KDoc.
        val withheldRows = tally.quarantinedPageUids() - purgeRegistry.tombstones(PurgedKind.PAGE).keys
        var allRead = true
        for (element in elements) {
            val record = decodeRecord<EntrySnapshotRecord>(element)
            if (record == null) {
                allRead = false
                tally.quarantineElement(QuarantinedRecord.ENTRY, element)
                // Held only if the peer's copy would have won — see [outranks]. This used to hold
                // unconditionally, several lines above the timestamp comparison that governs every
                // other record in this loop, so a peer record this build could not read suppressed
                // this device's *newer* row for the same uid from every future write. The device
                // withheld its own better data, permanently, and said nothing about it.
                val uid = element.uidOrNull()
                if (element.outranks(uid?.let { entryDao.getByUid(it) }?.updatedAt)) held?.hold(element, uid)
                continue
            }
            val remoteUpdatedAt = Instant.ofEpochMilli(record.updatedAt)
            // §5.5.1.1 — an Entry purged on any device stays purged, unless this record is the
            // newer of the two, in which case the tombstone is superseded and dropped.
            if (purgeRegistry.isPurged(PurgedKind.ENTRY, record.uid, remoteUpdatedAt, tombstones)) continue
            val local = entryDao.getByUid(record.uid)
            // Local is newer or equal — keep local, it'll win on the next write pass.
            if (local != null && !remoteUpdatedAt.isAfter(local.updatedAt)) continue
            // The row this Entry was made from was withheld by the page pass and there is no
            // local page to resolve it against, so adopting the Entry means writing
            // `sourceRowId = null` and republishing it with the link gone. Held instead — the
            // peer's bytes go back verbatim and this device's stale row for the uid stands aside,
            // exactly as for an Entry whose own fields would not decode. Checked after the
            // timestamp comparison for the reason [outranks] gives: a record that lost the race
            // was never going to be adopted anyway, and holding one is a silent gag on this
            // device's better copy.
            val danglingRow = record.sourceRowUid?.takeIf { it !in rowUidToId && it in withheldRows }
            if (danglingRow != null) {
                allRead = false
                // Not [ReadTally.quarantine]: nothing threw. The record read perfectly and it is
                // what it *names* that this build could not. "linked row" rather than the page
                // sweep's "linked page" because that is the word on the screen the person came
                // from — an Entry's `sourceRowUid` is always a database row.
                tally.quarantined += QuarantinedRecord(
                    kind = QuarantinedRecord.ENTRY,
                    uid = record.uid,
                    detail = unrecognisedValueDetail("linked row", danglingRow),
                )
                held?.hold(element, record.uid)
                continue
            }
            // Decoded before either branch below writes anything, and only once it is known to
            // matter: a record that loses on time is never decoded at all, so a value this build
            // cannot read costs nothing when the local copy was going to win anyway.
            val entity = record.decodeOrQuarantine(uidToId, rowUidToId, tally)
            if (entity == null) {
                allRead = false
                held?.hold(element, record.uid)
                continue
            }
            if (local == null) {
                uidToId[record.uid] = entryDao.insert(entity)
            } else {
                // providerEventId is per-device only (§3.2) — never adopted from a remote
                // record, always preserved from whatever this device already had.
                entryDao.update(entity.copy(id = local.id, providerEventId = local.providerEventId))
            }
        }
        return allRead
    }

    /**
     * The record as an [Entry], or null with the reason recorded — quarantine, in one line.
     *
     * Catches [SnapshotDecodeException] and nothing else. `runCatching` here swallowed every
     * Throwable, which meant a truncated file or an unparseable date arrived at the person as
     * [quarantineMessage]'s "written by a newer version of Tendril — update to take them in":
     * advice that will never fix it, on a cause this code never established. Real corruption goes
     * back to failing loudly, which is what HEAD did with all of these.
     */
    private fun EntrySnapshotRecord.decodeOrQuarantine(
        uidToId: Map<String, Long>,
        rowUidToId: Map<String, Long>,
        tally: ReadTally,
    ): Entry? = try {
        toEntity(uidToId, rowUidToId)
    } catch (e: SnapshotDecodeException) {
        tally.quarantine(QuarantinedRecord.ENTRY, uid, e)
        null
    }

    private suspend fun mergeHabitFile(store: SyncFileStore, read: ReadKey) {
        mergeHabitContent(readRootText(store, FolderArrayFile.HABITS.fileName, read), read.tally, held = read.tally.habits)
    }

    /** @return true when the content decoded — see [mergePageContent] and [mergeEntryContent];
     * one habit whose frequency this build cannot read costs that habit, not the file. [held]
     * carries the raw record through to the write half for the same reason, and is null for the
     * same callers. */
    private suspend fun mergeHabitContent(
        content: String,
        tally: ReadTally,
        held: HeldBuilder? = null,
    ): Boolean {
        val elements = decodeArray(content, held) ?: return false
        val tombstones = purgeRegistry.tombstones(PurgedKind.HABIT)
        var allRead = true
        for (element in elements) {
            val record = decodeRecord<HabitSnapshotRecord>(element)
            if (record == null) {
                allRead = false
                tally.quarantineElement(QuarantinedRecord.HABIT, element)
                // Same order as the entry path, for the same reason: hold only once the peer's
                // copy is known to have won, or an unreadable *stale* habit silences this
                // device's newer one for good. See [outranks].
                val uid = element.uidOrNull()
                if (element.outranks(uid?.let { habitDao.getByUid(it) }?.updatedAt)) held?.hold(element, uid)
                continue
            }
            val local = habitDao.getByUid(record.uid)
            val remoteUpdatedAt = Instant.ofEpochMilli(record.updatedAt)
            // §5.5.1.1 — same rule the Entry and Page merges follow. Without it a Habit purged
            // on any device walks straight back in from another device's habits.json, which is
            // the resurrection the tombstones exist to stop.
            if (purgeRegistry.isPurged(PurgedKind.HABIT, record.uid, remoteUpdatedAt, tombstones)) continue
            if (local != null && !remoteUpdatedAt.isAfter(local.updatedAt)) continue
            // Narrowly caught, for the reason [decodeOrQuarantine] gives: only a value this build
            // has no member for is a "newer version of Tendril", and that is the sentence the
            // person is shown.
            val entity: Habit? = try {
                record.toEntity()
            } catch (e: SnapshotDecodeException) {
                tally.quarantine(QuarantinedRecord.HABIT, record.uid, e)
                null
            }
            if (entity == null) {
                allRead = false
                held?.hold(element, record.uid)
                continue
            }
            if (local == null) habitDao.insert(entity) else habitDao.update(entity.copy(id = local.id))
        }
        return allRead
    }

    private suspend fun mergeReminderFile(store: SyncFileStore, read: ReadKey) {
        mergeReminderContent(
            readRootText(store, FolderArrayFile.REMINDERS.fileName, read),
            read.tally,
            held = read.tally.reminders,
        )
    }

    /**
     * §4 / S2 — reminders. **Not a last-write-wins merge, and it needs no timestamp to be one.**
     * `ReminderDao` has no update path, so a uid is inserted once and tombstoned once; the only
     * transition is live → deleted, and it is monotonic. "Deleted on any device wins" therefore
     * converges whatever order files arrive in, which is why [ReminderSnapshotRecord] carries no
     * `updatedAt` for this to compare.
     *
     * A record whose Entry has not merged here yet is **held**, not dropped. Dropping is what the
     * Entry mappers do for `originalEntryUid`, and it is safe there because the link self-heals on
     * the next pass. It would not self-heal here: this device rewrites `reminders.json` in full,
     * so a dropped reminder is *deleted from the folder for everyone* on the very next write, and
     * the record that would have healed it is then gone. See [FolderArrayFile].
     *
     * @return true when the content decoded — one unreadable reminder costs that reminder, not
     * the file. Same contract as [mergeHabitContent].
     */
    private suspend fun mergeReminderContent(
        content: String,
        tally: ReadTally,
        held: HeldBuilder? = null,
    ): Boolean {
        val elements = decodeArray(content, held) ?: return false
        val entryUidToId = entryDao.getAll().associate { it.uid to it.id }
        var allRead = true
        for (element in elements) {
            val record = decodeRecord<ReminderSnapshotRecord>(element)
            if (record == null) {
                allRead = false
                tally.quarantineElement(QuarantinedRecord.REMINDER, element)
                // Held unconditionally, unlike the Entry and Habit paths, which hold only once
                // the peer's copy is known to have won. Those two compare `updatedAt`; a reminder
                // has none, so there is no sense in which this device's row could be "newer" and
                // nothing to outrank. Not holding would simply delete the peer's record.
                held?.hold(element, element.uidOrNull())
                continue
            }
            val entryId = entryUidToId[record.entryUid]
            if (entryId == null) {
                allRead = false
                held?.hold(element, record.uid)
                continue
            }
            val entity: Reminder? = try {
                record.toEntity(entryId)
            } catch (e: SnapshotDecodeException) {
                tally.quarantine(QuarantinedRecord.REMINDER, record.uid, e)
                null
            }
            if (entity == null) {
                allRead = false
                held?.hold(element, record.uid)
                continue
            }
            val local = reminderDao.getByUid(record.uid)
            val remoteDeletedAt = entity.deletedAt
            when {
                // Includes an arriving tombstone for a reminder this device never had: storing it
                // is what makes this device republish the delete instead of staying silent about
                // it, which is the same reason §5.5.1.1's tombstones travel.
                local == null -> reminderDao.insert(entity)
                local.deletedAt == null && remoteDeletedAt != null ->
                    reminderDao.softDelete(local.id, remoteDeletedAt)
                // Live-over-deleted is deliberately not applied: the delete wins. Nothing else can
                // differ — offset and anchorTime are immutable once written.
                else -> Unit
            }
        }
        return allRead
    }

    private suspend fun mergeCompletionFile(store: SyncFileStore, read: ReadKey) {
        mergeCompletionContent(
            readRootText(store, FolderArrayFile.ENTRY_COMPLETIONS.fileName, read),
            read.tally,
            held = read.tally.completions,
        )
    }

    /**
     * §4.1 / S2 — completions. A plain union by uid, and the strongest guarantee any merge here
     * has: the table is append-only (`EntryCompletionDao` has neither an update nor a delete), so
     * a grow-only set converges regardless of arrival order, with nothing to compare and no
     * tombstone to carry.
     *
     * Held on an unresolvable Entry for the reason [mergeReminderContent] gives.
     */
    private suspend fun mergeCompletionContent(
        content: String,
        tally: ReadTally,
        held: HeldBuilder? = null,
    ): Boolean {
        val elements = decodeArray(content, held) ?: return false
        val entryUidToId = entryDao.getAll().associate { it.uid to it.id }
        var allRead = true
        for (element in elements) {
            val record = decodeRecord<EntryCompletionSnapshotRecord>(element)
            if (record == null) {
                allRead = false
                tally.quarantineElement(QuarantinedRecord.COMPLETION, element)
                held?.hold(element, element.uidOrNull())
                continue
            }
            // Already held locally: nothing to do, and nothing that *could* be done — a completion
            // is never edited, so an existing row and an arriving one with the same uid are the
            // same fact by construction.
            if (entryCompletionDao.getByUid(record.uid) != null) continue
            val entryId = entryUidToId[record.entryUid]
            if (entryId == null) {
                allRead = false
                held?.hold(element, record.uid)
                continue
            }
            val entity: EntryCompletion? = try {
                record.toEntity(entryId)
            } catch (e: SnapshotDecodeException) {
                tally.quarantine(QuarantinedRecord.COMPLETION, record.uid, e)
                null
            }
            if (entity == null) {
                allRead = false
                held?.hold(element, record.uid)
                continue
            }
            entryCompletionDao.insert(entity)
        }
        return allRead
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
            if (read.folderEncrypted) {
                read.tally.undecryptable++
                return ""
            }
            return bytes.toString(Charsets.UTF_8)
        }
        // Encrypted content with no passphrase configured, or the wrong one, decrypts to
        // null — surfaced to the caller as "nothing to merge" rather than a crash; each
        // platform's own UI is where a wrong/missing passphrase gets explained to the person.
        val decrypted = read.key?.let { SnapshotEncryption.decrypt(SnapshotEncryption.stripMagic(bytes), it) }
        if (decrypted == null) read.tally.undecryptable++
        return decrypted?.toString(Charsets.UTF_8) ?: ""
    }

    /** 210k PBKDF2 iterations — hundreds of milliseconds, and both "Sync now" buttons call in
     * from a `rememberCoroutineScope()`, which is the Main dispatcher. The merge and write
     * halves each switch to IO on their own, but the derivation happens *before* either, so
     * without this it ran on the UI thread on every press — the one thing this class's own
     * contract promises callers they need not think about. */
    private suspend fun deriveKey(passphrase: String?, salt: ByteArray): SecretKeySpec? =
        passphrase?.let { withContext(Dispatchers.IO) { SnapshotEncryption.deriveKey(it, salt) } }

    /**
     * A folder-wide file's records, as the raw elements [publishArrayFile] appends the held ones
     * to. Encoding through [JsonElement] rather than straight to text is what lets the two halves
     * meet in one array: this device's rows and a peer's untouched bytes are the same type by the
     * time they are joined, and only one of them has been through a data class.
     */
    private inline fun <reified T> elementsOf(records: List<T>): List<JsonElement> =
        json.encodeToJsonElement(records).jsonArray

    /**
     * A folder-wide file's outer JSON array, or null when this pass cannot use it.
     *
     * Blank is the ordinary "no such file yet", plus the encrypted-and-undecryptable case, which
     * is already counted in [ReadTally.undecryptable] and stops the write pass outright. Non-blank
     * content that is not a JSON array is the interesting one: it is what a newer build changing
     * the file's *shape* looks like, and [held] is marked [HeldRecords.unreadable] so the write
     * leaves the file alone instead of replacing a structure it never read with local state.
     */
    private fun decodeArray(content: String, held: HeldBuilder?): List<JsonElement>? {
        if (content.isBlank()) return null
        val array = runCatching { json.parseToJsonElement(content).jsonArray }.getOrNull()
        if (array == null) {
            held?.markUnreadable()
            return null
        }
        return array
    }

    /** One element as a typed snapshot record, or null when this build's class cannot hold it. */
    private inline fun <reified T> decodeRecord(element: JsonElement): T? =
        runCatching { json.decodeFromJsonElement<T>(element) }.getOrNull()

    private fun encryptText(content: String, key: SecretKeySpec?): ByteArray {
        val bytes = content.toByteArray(Charsets.UTF_8)
        return if (key != null) SnapshotEncryption.wrapWithMagic(SnapshotEncryption.encrypt(bytes, key)) else bytes
    }
}

package com.tendril.app.sync

import android.content.Context
import android.net.Uri
import com.tendril.app.storage.SecretStore
import com.tendril.app.storage.SyncFolderManager
import com.tendril.app.storage.SyncStatusPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/** What one snapshot-sync pass did. Named to keep it distinct from
 * `com.tendril.app.googlecalendar.SyncOutcome`, which is a different mechanism (§9.5.1). */
sealed interface SnapshotSyncOutcome {
    /** No SAF folder granted yet (§9.3) — not a failure, just nothing to sync with. */
    data object NoFolder : SnapshotSyncOutcome

    /** Another pass was already in flight and this one was dropped rather than queued. */
    data object AlreadyRunning : SnapshotSyncOutcome

    /** Merged in, wrote back, "last synced at" updated. */
    data object Completed : SnapshotSyncOutcome

    /** The folder holds snapshots this device can't decrypt, so nothing was written (§9.4.2). */
    data class PassphraseMismatch(val undecryptableFiles: Int) : SnapshotSyncOutcome

    data class Failed(val message: String) : SnapshotSyncOutcome
}

/**
 * The one place a snapshot sync pass runs from (§9.4).
 *
 * Added 2026-09-04. Before this, the only callers of
 * [SnapshotSyncOrchestrator.readAndMerge]/[SnapshotSyncOrchestrator.writeSnapshots] anywhere in
 * the app were the Settings "Sync now" button and its desktop counterpart — so three things
 * §9.4 states flatly were simply not happening:
 *
 *  - *"Tendril checks for these [`.sync-conflict-*` files] on resume/launch."* It didn't. A
 *    conflict file sat undetected until someone opened Settings and tapped a button.
 *  - The 2026-08-04 flush decision's `onStop`/backgrounding trigger. Nothing wrote on
 *    backgrounding, so an editing session reached the folder only if the person went looking
 *    for the button afterwards.
 *  - *"the periodic background sync pass already implied by §9.4's 'last synced at' UI."*
 *
 * A pass is always **read-merge-then-write**, never a bare write, and that ordering is
 * load-bearing rather than incidental: [SnapshotSyncOrchestrator.writeSnapshots] rewrites each
 * domain file wholesale from this device's rows, so writing without merging first would drop
 * records only the other device has from the file. They survive on that device and return on
 * its next pass, but merging first keeps the window as small as the design allows.
 */
class SyncCoordinator(
    private val context: Context,
    private val folderManager: SyncFolderManager,
    private val orchestrator: SnapshotSyncOrchestrator,
    private val secretStore: SecretStore,
    private val statusPreferences: SyncStatusPreferences,
) {
    /**
     * Application-scoped, deliberately not the caller's.
     *
     * The `onStop` flush fires as the Activity goes away, and an Activity-scoped coroutine
     * would be cancelled part-way through. That matters more here than it usually would:
     * `AndroidSafSyncFileStore`'s write is not atomic (it renames the existing file aside,
     * moves the temp file into place, then deletes the old one), so a write cancelled at the
     * wrong moment leaves the sync folder with no copy of that file at all — in a folder
     * Syncthing is actively watching, which would propagate the absence.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Non-reentrant: a second request while a pass is running is dropped, not queued. A
     * lifecycle trigger must never sit behind a long manual sync waiting to start. */
    private val gate = Mutex()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    /** Null once a pass succeeds. Surfaced by Settings; a lifecycle-triggered failure would
     * otherwise be invisible, since nothing is on screen to report it at the time. */
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    /** Fire-and-forget, for the lifecycle triggers. See [scope] for why it isn't the caller's. */
    fun syncInBackground() {
        scope.launch { sync() }
    }

    suspend fun sync(): SnapshotSyncOutcome {
        val uri = folderManager.folderUri.value ?: return SnapshotSyncOutcome.NoFolder
        if (!gate.tryLock()) return SnapshotSyncOutcome.AlreadyRunning
        _running.value = true
        return try {
            // Dispatchers.IO because the button calls this straight from a Compose scope on
            // Main; NonCancellable for the same reason the scope is application-wide — once a
            // write has begun, stopping half way is worse than finishing.
            withContext(Dispatchers.IO + NonCancellable) { runPass(uri) }
        } finally {
            _running.value = false
            gate.unlock()
        }
    }

    private suspend fun runPass(uri: Uri): SnapshotSyncOutcome = try {
        val store = AndroidSafSyncFileStore.create(context, uri)
        if (store == null) {
            fail("Couldn't open the sync folder — try choosing it again.")
        } else {
            val passphrase = secretStore.syncPassphrase.value
            val merge = orchestrator.readAndMerge(store, passphrase)
            if (merge.passphraseMismatch) {
                // Never write over a folder we couldn't read: nothing merged in, so the write
                // would replace its only copy with this device's state under the wrong key.
                _lastError.value =
                    "${merge.undecryptableFiles} file(s) in the sync folder couldn't be decrypted — " +
                        "check the passphrase. Nothing was written, so the folder is untouched."
                SnapshotSyncOutcome.PassphraseMismatch(merge.undecryptableFiles)
            } else {
                orchestrator.writeSnapshots(store, passphrase)
                statusPreferences.markSyncedNow()
                _lastError.value = null
                SnapshotSyncOutcome.Completed
            }
        }
    } catch (e: Exception) {
        fail(e.message ?: "Sync failed.")
    }

    private fun fail(message: String): SnapshotSyncOutcome.Failed {
        _lastError.value = message
        return SnapshotSyncOutcome.Failed(message)
    }
}

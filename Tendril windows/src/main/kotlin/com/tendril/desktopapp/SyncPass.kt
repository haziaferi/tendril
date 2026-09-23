package com.tendril.desktopapp

import com.tendril.app.sync.SnapshotSyncOrchestrator
import com.tendril.app.sync.SyncFileStore
import com.tendril.app.sync.quarantineMessage

/**
 * One sync pass, lifted out of [SyncBar]'s `onClick` so that what it *does* can be tested apart
 * from how the bar draws.
 *
 * Audit 1.17 — the row this closes was pinned by "the compiler and the walk", on the stated
 * grounds that "the desktop module has no test source set". That stopped being true on
 * 2026-09-23, and what remained in the way was placement rather than capability: the re-plan sat
 * inside a composable's click lambda, reachable only through Compose UI testing, and the desktop
 * `ui-test-junit4` artifact is not in the offline cache. Moving the decision out of the lambda is
 * what makes it reachable at all.
 *
 * The two branches are the point. A pass that merged something must re-plan, because a folder
 * merge passes neither the `EntryScheduleCoordinator` callbacks nor the startup call and a
 * reminder set on the phone would otherwise sit here unplanned until the next launch. A pass that
 * could not decrypt must **not** re-plan, and must not write either: nothing merged in, so a write
 * would replace the folder's only copy with this device's state under the wrong key (§9.4.2).
 * Both are asserted in `SyncPassTest`; the second is the one a "is it called?" test would miss.
 *
 * Returns the notice to show, or null on a clean pass — so the caller holds no logic of its own
 * beyond assigning it. Exceptions are caught here rather than by the caller for the same reason:
 * `SyncFileStore`'s contract is that a write which cannot complete throws, and where that is
 * turned into a message is part of what a pass does.
 */
internal suspend fun runSyncPass(
    orchestrator: SnapshotSyncOrchestrator,
    store: SyncFileStore,
    passphrase: String?,
    rearmReminders: () -> Unit,
): String? = try {
    val merge = orchestrator.readAndMerge(store, passphrase)
    if (merge.passphraseMismatch) {
        // Writing here would overwrite the folder's only copy with this device's state under the
        // wrong key (§9.4.2). Nothing merged, so there is also nothing to re-plan.
        "${merge.undecryptableFiles} file(s) couldn't be decrypted — " +
            "check the passphrase. Nothing was written."
    } else {
        orchestrator.writeSnapshots(store, passphrase)
        // Quarantine is a survivable pass, but never a silent one — same reasoning and same
        // channel as Android's SyncCoordinator. Null on a clean pass, so the notice clears itself.
        val notice = merge.quarantineMessage()
        // §9.7 on this side, the same seam Android's SyncCoordinator closes (audit 2026-09-22
        // row 1.3, which recorded only the phone's half). Note against row 1.6: this is a third
        // caller of `replan`, though not a new race — the caller's `syncing` flag keeps two
        // passes from overlapping, and `replan` is `@Synchronized` since 1.6 was fixed.
        rearmReminders()
        notice
    }
} catch (e: Exception) {
    e.message ?: "Sync failed."
}

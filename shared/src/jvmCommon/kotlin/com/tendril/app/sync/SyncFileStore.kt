package com.tendril.app.sync

/**
 * §12.5/Milestone 2 — the platform-abstraction point that lets [SnapshotSyncOrchestrator] live in
 * `jvmCommon` instead of being duplicated per platform. File access only, deliberately no JSON
 * decode/encode and no encryption here — those stay in the orchestrator, above this interface.
 *
 * Implementations own the "create the pages directory if it doesn't exist yet" rule on writes and
 * tolerate a missing pages directory on reads (empty list / null), and own the write-then-swap
 * dance (temp file, then a rename/move) internally.
 *
 * **What a caller of [writeRoot]/[writePage] can rely on:** either the new content is in place, or
 * the previous content still is — a write never leaves the destination missing. It does *not*
 * promise a single atomic filesystem operation everywhere: `DesktopFileSyncFileStore` gets a true
 * `ATOMIC_MOVE`, while SAF on Android has no atomic-replace primitive, so
 * `AndroidSafSyncFileStore` approximates it by moving the old file aside and restoring it if the
 * rename fails. An interrupted Android write can therefore leave a `<name>.bak` or `<name>.tmp`
 * sibling behind; both are inert to every reader here and can be cleaned up by hand.
 *
 * A write that cannot complete **throws**. Callers must handle that rather than treating a
 * returned-normally call as proof the snapshot landed.
 */
interface SyncFileStore {
    suspend fun readRoot(name: String): ByteArray?
    suspend fun writeRoot(name: String, bytes: ByteArray)
    suspend fun listRoot(): List<String>
    suspend fun deleteRoot(name: String)

    suspend fun readPage(name: String): ByteArray?
    suspend fun writePage(name: String, bytes: ByteArray)
    suspend fun listPages(): List<String>
    suspend fun deletePage(name: String)
}

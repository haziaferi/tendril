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

    /**
     * §9.4 / S4 — the `images/` channel, the folder's only non-JSON content.
     *
     * A third set rather than a generalised `read(dir, name)`, for the reason the pages set is its
     * own: every caller of these is a different kind of thing, and a store that took a directory
     * name as a parameter would let a caller invent one. The names here are
     * `<block uid>.<extension>` and nothing else creates them.
     *
     * The bytes are an image file, not text — which this interface has always been able to carry,
     * since it deals in `ByteArray` and leaves JSON above it. What is genuinely new is that the
     * *orchestrator* must stop assuming everything in the folder decodes as text, which is why the
     * snapshot walker's `.endsWith(".json")` filter matters and is dealt with above this layer.
     */
    suspend fun readImage(name: String): ByteArray?
    suspend fun writeImage(name: String, bytes: ByteArray)
    suspend fun listImages(): List<String>
    suspend fun deleteImage(name: String)
}

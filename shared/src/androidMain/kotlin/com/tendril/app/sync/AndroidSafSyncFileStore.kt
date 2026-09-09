package com.tendril.app.sync

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

private const val PAGES_DIR = "pages"
private const val IMAGES_DIR = "images"

/**
 * §12.5/Milestone 2 — the Android [SyncFileStore], wrapping SAF (`DocumentFile`/`ContentResolver`)
 * exactly as the original (pre-split) `SnapshotSyncManager` did directly.
 */
class AndroidSafSyncFileStore private constructor(
    private val context: Context,
    private val folder: DocumentFile,
) : SyncFileStore {

    companion object {
        fun create(context: Context, folderUri: Uri): AndroidSafSyncFileStore? =
            DocumentFile.fromTreeUri(context, folderUri)?.let { AndroidSafSyncFileStore(context, it) }

        /**
         * Builds a store over an already-resolved [DocumentFile] instead of a tree Uri.
         *
         * Exists so instrumented tests can exercise [writeAtomic] against a real on-device
         * `DocumentFile` without a user having to grant a SAF folder through the picker first.
         * Production code should keep using [create].
         */
        @androidx.annotation.VisibleForTesting
        fun forDocumentFile(context: Context, folder: DocumentFile): AndroidSafSyncFileStore =
            AndroidSafSyncFileStore(context, folder)
    }

    override suspend fun readRoot(name: String): ByteArray? = readBytes(folder.findFile(name))
    override suspend fun writeRoot(name: String, bytes: ByteArray) = writeAtomic(folder, name, bytes)
    override suspend fun listRoot(): List<String> = folder.listFiles().mapNotNull { it.name }
    override suspend fun deleteRoot(name: String) { folder.findFile(name)?.delete() }

    override suspend fun readPage(name: String): ByteArray? = readBytes(pagesDirOrNull()?.findFile(name))
    override suspend fun writePage(name: String, bytes: ByteArray) = writeAtomic(pagesDirOrCreate(), name, bytes)
    override suspend fun listPages(): List<String> = pagesDirOrNull()?.listFiles()?.mapNotNull { it.name } ?: emptyList()
    override suspend fun deletePage(name: String) { pagesDirOrNull()?.findFile(name)?.delete() }

    override suspend fun readImage(name: String): ByteArray? = readBytes(imagesDirOrNull()?.findFile(name))
    override suspend fun writeImage(name: String, bytes: ByteArray) = writeAtomic(imagesDirOrCreate(), name, bytes)
    override suspend fun listImages(): List<String> =
        imagesDirOrNull()?.listFiles()?.mapNotNull { it.name } ?: emptyList()
    override suspend fun deleteImage(name: String) { imagesDirOrNull()?.findFile(name)?.delete() }

    private fun pagesDirOrNull(): DocumentFile? = folder.findFile(PAGES_DIR)?.takeIf { it.isDirectory }
    private fun pagesDirOrCreate(): DocumentFile =
        pagesDirOrNull() ?: folder.createDirectory(PAGES_DIR) ?: error("Could not create '$PAGES_DIR' in sync folder")

    private fun imagesDirOrNull(): DocumentFile? = folder.findFile(IMAGES_DIR)?.takeIf { it.isDirectory }
    private fun imagesDirOrCreate(): DocumentFile =
        imagesDirOrNull() ?: folder.createDirectory(IMAGES_DIR) ?: error("Could not create '$IMAGES_DIR' in sync folder")

    private fun readBytes(file: DocumentFile?): ByteArray? {
        file ?: return null
        return context.contentResolver.openInputStream(file.uri)?.use { it.readBytes() }
    }

    /**
     * Write-then-swap. SAF offers no atomic replace, so the closest safe sequence is: write a
     * temp file in full, move the existing file aside, rename temp into place, then drop the
     * backup. The previous version deleted the destination *before* renaming, which left a
     * window where a process kill — or simply a provider that refused the rename — destroyed
     * the snapshot outright. Now the worst case leaves the previous snapshot recoverable
     * under `<name>.bak` instead of leaving nothing at all.
     *
     * Failures throw rather than returning quietly: a sync that writes nothing and reports
     * success is how a folder silently stops being a backup. `.tmp`/`.bak` siblings are
     * ignored by every reader (`mergePagesDir` filters on `.json`, and the conflict sweep on
     * `.sync-conflict-`), so a leftover from an interrupted write is inert.
     */
    private fun writeAtomic(dir: DocumentFile, name: String, bytes: ByteArray) {
        val tempName = "$name.tmp"
        val backupName = "$name.bak"
        dir.findFile(tempName)?.delete()
        // application/octet-stream, not application/json — some SAF providers normalize a
        // display name against the given mime type's expected extension, which would
        // mangle the intentional ".tmp" suffix (e.g. re-appending ".json" after it).
        val temp = dir.createFile("application/octet-stream", tempName)
            ?: error("Sync folder: could not create '$tempName'")

        val wrote = context.contentResolver.openOutputStream(temp.uri)
            ?.use { it.write(bytes); true } ?: false
        if (!wrote) {
            temp.delete()
            error("Sync folder: could not open '$tempName' for writing")
        }

        val existing = dir.findFile(name)
        dir.findFile(backupName)?.delete()
        val backedUp = existing != null && existing.renameTo(backupName)
        if (existing != null && !backedUp) {
            // Couldn't move the old file aside — leave it exactly as it is rather than
            // risking the replace.
            temp.delete()
            error("Sync folder: could not move '$name' aside to replace it")
        }

        if (!temp.renameTo(name)) {
            val restored = !backedUp || dir.findFile(backupName)?.renameTo(name) == true
            temp.delete()
            // If even the restore is refused, the previous snapshot is still on disk under
            // .bak — say so, rather than leaving someone to conclude it's gone.
            error(
                if (restored) "Sync folder: could not rename '$tempName' to '$name'"
                else "Sync folder: could not rename '$tempName' to '$name'; " +
                    "your previous snapshot is still there as '$backupName' — rename it back by hand"
            )
        }
        if (backedUp) dir.findFile(backupName)?.delete()
    }
}

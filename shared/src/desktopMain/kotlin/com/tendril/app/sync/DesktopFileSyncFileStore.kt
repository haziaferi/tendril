package com.tendril.app.sync

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name

/**
 * §12.5/Milestone 2 — the desktop [SyncFileStore], wrapping `java.nio.file` — desktop just needs
 * ordinary filesystem access to the synced folder (§12.1), no SAF equivalent required.
 */
class DesktopFileSyncFileStore(private val root: Path) : SyncFileStore {

    private val pagesDir get() = root.resolve("pages")

    override suspend fun readRoot(name: String): ByteArray? = readBytes(root.resolve(name))
    override suspend fun writeRoot(name: String, bytes: ByteArray) = writeAtomic(root, name, bytes)
    override suspend fun listRoot(): List<String> =
        Files.list(root).use { stream -> stream.filter { !it.isDirectory() }.map { it.name }.toList() }
    override suspend fun deleteRoot(name: String) { Files.deleteIfExists(root.resolve(name)) }

    override suspend fun readPage(name: String): ByteArray? = readBytes(pagesDir.resolve(name))
    override suspend fun writePage(name: String, bytes: ByteArray) {
        Files.createDirectories(pagesDir)
        writeAtomic(pagesDir, name, bytes)
    }
    override suspend fun listPages(): List<String> {
        if (!pagesDir.exists()) return emptyList()
        return Files.list(pagesDir).use { stream -> stream.map { it.name }.toList() }
    }
    override suspend fun deletePage(name: String) { Files.deleteIfExists(pagesDir.resolve(name)) }

    private fun readBytes(path: Path): ByteArray? = if (path.exists()) Files.readAllBytes(path) else null

    private fun writeAtomic(dir: Path, name: String, bytes: ByteArray) {
        val temp = dir.resolve("$name.tmp")
        Files.write(temp, bytes)
        val target = dir.resolve(name)
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            // Falls back to a plain move — matters if the sync folder is FUSE/network-mounted
            // (plausible for a Syncthing-watched folder), where a real atomic rename can fail
            // even within what looks like "one folder."
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

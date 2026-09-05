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

    override suspend fun readRoot(name: String): ByteArray? = readBytes(resolveInside(root, name))
    override suspend fun writeRoot(name: String, bytes: ByteArray) = writeAtomic(root, name, bytes)
    override suspend fun listRoot(): List<String> =
        Files.list(root).use { stream -> stream.filter { !it.isDirectory() }.map { it.name }.toList() }
    override suspend fun deleteRoot(name: String) { Files.deleteIfExists(resolveInside(root, name)) }

    override suspend fun readPage(name: String): ByteArray? = readBytes(resolveInside(pagesDir, name))
    override suspend fun writePage(name: String, bytes: ByteArray) {
        Files.createDirectories(pagesDir)
        writeAtomic(pagesDir, name, bytes)
    }
    override suspend fun listPages(): List<String> {
        if (!pagesDir.exists()) return emptyList()
        return Files.list(pagesDir).use { stream -> stream.map { it.name }.toList() }
    }
    override suspend fun deletePage(name: String) { Files.deleteIfExists(resolveInside(pagesDir, name)) }

    private fun readBytes(path: Path): ByteArray? = if (path.exists()) Files.readAllBytes(path) else null

    /**
     * `Path.resolve` honours `..` and absolute paths, so a name that came from synced content
     * rather than from this app could address a file anywhere the process can reach. Page
     * snapshots are named after a record's `uid`, which arrives from whatever a peer wrote into
     * the folder — validated upstream too, but this is the sink, and the sink is where the
     * guarantee has to hold no matter which caller is at fault.
     */
    private fun resolveInside(dir: Path, name: String): Path {
        val candidate = dir.resolve(name).normalize()
        require(candidate.startsWith(dir.normalize()) && candidate != dir.normalize()) {
            "Refusing to touch '$name': resolves outside the sync folder"
        }
        return candidate
    }

    private fun writeAtomic(dir: Path, name: String, bytes: ByteArray) {
        val target = resolveInside(dir, name)
        val temp = resolveInside(dir, "$name.tmp")
        Files.write(temp, bytes)
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

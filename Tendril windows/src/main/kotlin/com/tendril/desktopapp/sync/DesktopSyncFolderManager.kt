package com.tendril.desktopapp.sync

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.prefs.Preferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val KEY_FOLDER = "sync_folder_path"

/**
 * §12.5/Milestone 2 — desktop's equivalent of Android's `SyncFolderManager`: same shape (a
 * `StateFlow<Path?>` seeded from a persisted value, re-validated on load), but no OS-level
 * permission grant to track — desktop just needs ordinary filesystem access (§12.1), so the only
 * thing worth persisting is the path itself, via `java.util.prefs.Preferences` rather than SAF's
 * `persistedUriPermissions`.
 */
class DesktopSyncFolderManager {
    private val prefs = Preferences.userNodeForPackage(DesktopSyncFolderManager::class.java)

    private val _folderPath = MutableStateFlow(
        prefs.get(KEY_FOLDER, null)?.let(Paths::get)?.takeIf(Files::isDirectory)
    )
    val folderPath: StateFlow<Path?> = _folderPath.asStateFlow()

    fun setFolder(path: Path) {
        _folderPath.value = path
        prefs.put(KEY_FOLDER, path.toString())
    }
}

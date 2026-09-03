package com.tendril.app.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PREFS_NAME = "tendril_sync_prefs"
private const val KEY_FOLDER_URI = "sync_folder_uri"

/**
 * SAF folder grant for the sync/export folder (§3.5, §9.3) — no Shizuku, no root.
 * `ACTION_OPEN_DOCUMENT_TREE` + `takePersistableUriPermission` is the whole mechanism;
 * an external Syncthing-fork app reads/writes the same physical folder independently (§9.4).
 * This class only manages the grant itself — the actual snapshot read/write format (§9.4)
 * is a later phase.
 */
class SyncFolderManager(private val context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _folderUri = MutableStateFlow(
        prefs.getString(KEY_FOLDER_URI, null)?.let { Uri.parse(it) }?.takeIf { isGrantStillValid(it) }
    )
    val folderUri: StateFlow<Uri?> = _folderUri.asStateFlow()

    /** Human-readable folder name/path for the Settings summary row. */
    fun displayNameFor(uri: Uri): String =
        DocumentFile.fromTreeUri(context, uri)?.name ?: uri.lastPathSegment ?: uri.toString()

    private fun isGrantStillValid(uri: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission && it.isWritePermission
        }

    /** Call from the ActivityResultLauncher<Uri?> callback for ACTION_OPEN_DOCUMENT_TREE. */
    fun onFolderGranted(uri: Uri?) {
        if (uri == null) return
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        _folderUri.value = uri
        prefs.edit { putString(KEY_FOLDER_URI, uri.toString()) }
    }
}

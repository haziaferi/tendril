package com.tendril.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/**
 * Desktop's: AWT's native [FileDialog] in save mode, as the image picker uses it for opening, and
 * off the UI thread for the same reason — `isVisible = true` blocks until the person chooses.
 */
@Composable
actual fun rememberFileSaver(onResult: (SaveResult) -> Unit): (fileName: String, mimeType: String, bytes: ByteArray) -> Unit {
    val scope = rememberCoroutineScope()
    val current by rememberUpdatedState(onResult)
    return { name, _, bytes ->
        scope.launch {
            val target = withContext(Dispatchers.IO) {
                val dialog = FileDialog(null as Frame?, name, FileDialog.SAVE)
                dialog.file = name
                dialog.isVisible = true
                dialog.file?.let { File(dialog.directory ?: "", it) }
            }
            if (target == null) { current(SaveResult.CANCELLED); return@launch }
            val ok = withContext(Dispatchers.IO) { runCatching { target.writeBytes(bytes) }.isSuccess }
            current(if (ok) SaveResult.SAVED else SaveResult.FAILED)
        }
    }
}

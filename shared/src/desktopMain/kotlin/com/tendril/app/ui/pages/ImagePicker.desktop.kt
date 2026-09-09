package com.tendril.app.ui.pages

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
 * Desktop's picker: AWT's own [FileDialog] rather than Swing's `JFileChooser`, because it is the
 * platform's native dialog and looks like one.
 *
 * Opened off the UI thread. `FileDialog.isVisible = true` blocks until the person chooses, and
 * running that on the Compose frame thread would freeze the window it was opened from.
 */
@Composable
actual fun rememberImagePicker(onPicked: (fileName: String, bytes: ByteArray) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    val current by rememberUpdatedState(onPicked)
    return {
        scope.launch {
            val chosen = withContext(Dispatchers.IO) {
                val dialog = FileDialog(null as Frame?, "Choose an image", FileDialog.LOAD)
                dialog.isVisible = true
                dialog.file?.let { File(dialog.directory ?: "", it) }
            }
            val file = chosen?.takeIf { it.isFile } ?: return@launch
            val bytes = withContext(Dispatchers.IO) { runCatching { file.readBytes() }.getOrNull() }
            if (bytes != null) current(file.name, bytes)
        }
    }
}

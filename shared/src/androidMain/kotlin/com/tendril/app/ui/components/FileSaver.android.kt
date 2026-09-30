package com.tendril.app.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** `ACTION_CREATE_DOCUMENT` with the type chosen per file — `CreateDocument` fixes one type per registration. */
private class CreateTypedDocument : ActivityResultContract<Pair<String, String>, Uri?>() {
    override fun createIntent(context: Context, input: Pair<String, String>): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(input.second).putExtra(Intent.EXTRA_TITLE, input.first)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? = intent?.data
}

/**
 * Android's: the system's *save to* picker (the Storage Access Framework), which needs no storage
 * permission — the grant is for the one document the person creates, as Settings' exports use.
 */
@Composable
actual fun rememberFileSaver(onResult: (SaveResult) -> Unit): (fileName: String, mimeType: String, bytes: ByteArray) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val current by rememberUpdatedState(onResult)
    // The bytes wait here while the picker is open; a process death in between loses them, and the
    // person simply exports again.
    val pending = remember { arrayOfNulls<ByteArray>(1) }
    val launcher = rememberLauncherForActivityResult(CreateTypedDocument()) { uri ->
        val bytes = pending[0]
        pending[0] = null
        if (uri == null || bytes == null) { current(SaveResult.CANCELLED); return@rememberLauncherForActivityResult }
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } != null }.getOrDefault(false)
            }
            current(if (ok) SaveResult.SAVED else SaveResult.FAILED)
        }
    }
    return { name, mime, bytes -> pending[0] = bytes; launcher.launch(name to mime) }
}

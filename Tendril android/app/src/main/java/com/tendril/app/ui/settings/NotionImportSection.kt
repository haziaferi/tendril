package com.tendril.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tendril.app.data.page.Page
import com.tendril.app.data.pagedatabase.PageDatabase
import com.tendril.app.data.pagedatabase.Property
import com.tendril.app.domain.DatabaseSyncManager
import com.tendril.app.notionimport.ImportedDatabase
import com.tendril.app.notionimport.NotionImportSummary
import com.tendril.app.notionimport.NotionImporter
import com.tendril.app.ui.pages.EnableSyncSheet
import kotlinx.coroutines.launch
import com.tendril.app.ui.theme.body

/**
 * Settings → Notion import (§7): a one-shot additive import from a Notion Markdown & CSV
 * export zip (§9.4.1's Import path, never Restore — this only ever adds to existing data).
 * Deliberately its own section rather than folded into [PortableBackupSection] above: that one
 * round-trips Tendril's own format, this one translates a foreign one, and the two shouldn't
 * read as the same operation.
 */
@Composable
fun NotionImportSection(importer: NotionImporter, databaseSyncManager: DatabaseSyncManager) {
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf<NotionImportSummary?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }
    var pendingBind by remember { mutableStateOf<ImportedDatabase?>(null) }
    var boundDatabaseIds by remember { mutableStateOf(setOf<Long>()) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            importing = true
            scope.launch {
                // The picker accepts any file, and `import` throws on purpose — an unopenable
                // stream, an over-cap text payload, or a file that isn't a zip at all. Left
                // uncaught this crashed the app and stranded `importing` at true, disabling the
                // button for good. Same runCatching-and-report shape PortableBackupSection uses.
                try {
                    summary = importer.import(uri)
                    boundDatabaseIds = emptySet()
                    importError = null
                } catch (e: Exception) {
                    summary = null
                    importError = e.message ?: "Import failed — that file couldn't be read as a Notion export."
                } finally {
                    importing = false
                }
            }
        },
    )

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.UploadFile, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Import from Notion", style = MaterialTheme.typography.body)
                Text(
                    "A Notion Markdown & CSV export .zip — pages and databases are added " +
                        "alongside your existing ones, nothing here is replaced.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(enabled = !importing, onClick = { importLauncher.launch(arrayOf("application/zip", "*/*")) }) {
            Text(if (importing) "Importing…" else "Choose export .zip")
        }

        importError?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        summary?.let { result ->
            Spacer(Modifier.height(12.dp))
            Text(
                "Imported ${result.pagesImported} page(s), ${result.databasesImported.size} database(s) " +
                    "(${result.rowsImported} row(s) total), ${result.assetsImported} image/file(s).",
                style = MaterialTheme.typography.bodyMedium,
            )
            result.databasesImported.forEach { db ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(db.name, style = MaterialTheme.typography.bodyMedium)
                    if (db.databaseId in boundDatabaseIds) {
                        Text("Synced to Tasks", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        TextButton(onClick = { pendingBind = db }) { Text("Sync to Tasks…") }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            result.notices.forEach { notice ->
                Text("• $notice", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    pendingBind?.let { db ->
        NotionDatabaseBindSheet(
            importer = importer,
            database = db,
            onDismiss = { pendingBind = null },
            onConfirm = { pageDatabase, donePropertyId, deadlinePropertyId, recurrencePropertyId, rowIds, dueDatePropertyId ->
                scope.launch {
                    databaseSyncManager.enableSync(pageDatabase, donePropertyId, deadlinePropertyId, recurrencePropertyId, rowIds, dueDatePropertyId = dueDatePropertyId)
                    boundDatabaseIds = boundDatabaseIds + db.databaseId
                    pendingBind = null
                }
            },
        )
    }
}

/** Loads the freshly-imported database's own [Property]/[Page] rows, then hands off to the
 * exact same [EnableSyncSheet] a locally-created to-do database's own "Sync to Tasks" toggle
 * uses (§5.2/§5.2.1) — not a second, import-specific binding UI. */
@Composable
private fun NotionDatabaseBindSheet(
    importer: NotionImporter,
    database: ImportedDatabase,
    onDismiss: () -> Unit,
    onConfirm: (PageDatabase, Long, Long?, Long?, List<Long>, Long?) -> Unit,
) {
    var properties by remember(database.databaseId) { mutableStateOf<List<Property>?>(null) }
    var rows by remember(database.databaseId) { mutableStateOf<List<Page>?>(null) }
    var pageDatabase by remember(database.databaseId) { mutableStateOf<PageDatabase?>(null) }

    LaunchedEffect(database.databaseId) {
        properties = importer.propertiesFor(database.databaseId)
        rows = importer.rowsFor(database.databaseId)
        pageDatabase = importer.databaseFor(database.databaseId)
    }

    val currentProperties = properties
    val currentRows = rows
    val currentDatabase = pageDatabase
    if (currentProperties != null && currentRows != null && currentDatabase != null) {
        EnableSyncSheet(
            properties = currentProperties,
            rows = currentRows,
            onDismiss = onDismiss,
            onConfirm = { done, deadline, recurrence, rowIds, dueDate -> onConfirm(currentDatabase, done, deadline, recurrence, rowIds, dueDate) },
        )
    }
}
